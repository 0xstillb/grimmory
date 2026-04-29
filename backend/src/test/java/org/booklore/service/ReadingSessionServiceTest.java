package org.booklore.service;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionItemRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadingSessionServiceTest {

    @Mock
    AuthenticationService authenticationService;
    @Mock
    ReadingSessionRepository readingSessionRepository;
    @Mock
    BookRepository bookRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    UserBookProgressRepository userBookProgressRepository;

    @InjectMocks
    ReadingSessionService service;

    private BookLoreUserEntity userEntity;
    private BookEntity book;

    @BeforeEach
    void setUp() {
        LibraryEntity library = LibraryEntity.builder().id(1L).build();
        userEntity = BookLoreUserEntity.builder()
                .id(10L)
                .username("reader")
                .permissions(UserPermissionsEntity.builder().permissionAdmin(false).build())
                .libraries(new HashSet<>())
                .build();
        userEntity.getLibraries().add(library);

        book = BookEntity.builder()
                .id(20L)
                .library(library)
                .build();
        book.setBookFiles(new ArrayList<>());
        book.getBookFiles().add(BookFileEntity.builder()
                .book(book)
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .currentHash("hash-1")
                .initialHash("hash-1")
                .fileName("book.epub")
                .fileSubPath("")
                .build());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordSession_supportsKoreaderHeaderAuthentication() {
        setKoreaderPrincipal();
        when(userRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(20L)).thenReturn(Optional.of(book));
        when(bookRepository.findByCurrentOrInitialHash("hash-1")).thenReturn(Optional.of(book));
        when(readingSessionRepository.findFirstByUserIdAndBookIdAndStartTimeAndEndTimeAndDurationSecondsAndStartLocationAndEndLocationAndDeviceId(
                eq(10L), eq(20L), any(), any(), eq(300), eq("start"), eq("end"), eq("device-1")
        )).thenReturn(Optional.empty());
        when(readingSessionRepository.save(any(ReadingSessionEntity.class))).thenAnswer(invocation -> {
            ReadingSessionEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return entity;
        });

        ReadingSessionRequest request = new ReadingSessionRequest(
                20L,
                "hash-1",
                BookFileType.EPUB,
                Instant.parse("2026-04-29T10:00:00Z"),
                Instant.parse("2026-04-29T10:05:00Z"),
                300,
                "5m",
                10.0f,
                12.0f,
                2.0f,
                "KOReader",
                "device-1",
                "start",
                "end"
        );

        service.recordSession(request);

        verify(readingSessionRepository).save(argThat(session ->
                session.getBookHash().equals("hash-1")
                        && session.getDevice().equals("KOReader")
                        && session.getDeviceId().equals("device-1")
        ));
    }

    @Test
    void recordSessionsBatch_reusesExactDuplicate() {
        setKoreaderPrincipal();
        when(userRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(20L)).thenReturn(Optional.of(book));
        when(bookRepository.findByCurrentOrInitialHash("hash-1")).thenReturn(Optional.of(book));
        ReadingSessionEntity existing = ReadingSessionEntity.builder()
                .id(55L)
                .startTime(Instant.parse("2026-04-29T10:00:00Z"))
                .endTime(Instant.parse("2026-04-29T10:05:00Z"))
                .durationSeconds(300)
                .build();
        when(readingSessionRepository.findFirstByUserIdAndBookIdAndStartTimeAndEndTimeAndDurationSecondsAndStartLocationAndEndLocationAndDeviceId(
                eq(10L), eq(20L), any(), any(), eq(300), eq("start"), eq("end"), eq("device-1")
        )).thenReturn(Optional.of(existing));

        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest(
                20L,
                "hash-1",
                BookFileType.EPUB,
                "KOReader",
                "device-1",
                List.of(new ReadingSessionItemRequest(
                        Instant.parse("2026-04-29T10:00:00Z"),
                        Instant.parse("2026-04-29T10:05:00Z"),
                        300,
                        "5m",
                        10.0f,
                        12.0f,
                        2.0f,
                        "start",
                        "end"
                ))
        );

        ReadingSessionBatchResponse response = service.recordSessionsBatch(request);

        assertEquals(1, response.getSuccessCount());
        assertEquals(55L, response.getResults().getFirst().getSessionId());
        verify(readingSessionRepository, never()).save(any());
    }

    @Test
    void getReadingSessionsForBook_usesAuthenticatedUserScope() {
        setJwtPrincipal();
        when(userRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(20L)).thenReturn(Optional.of(book));
        when(readingSessionRepository.findByUserIdAndBookId(eq(10L), eq(20L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ReadingSessionEntity.builder()
                        .id(1L)
                        .user(userEntity)
                        .book(book)
                        .bookHash("hash-1")
                        .bookType(BookFileType.EPUB)
                        .startTime(Instant.parse("2026-04-29T10:00:00Z"))
                        .endTime(Instant.parse("2026-04-29T10:05:00Z"))
                        .durationSeconds(300)
                        .device("KOReader")
                        .deviceId("device-1")
                        .build())));

        Page<ReadingSessionResponse> page = service.getReadingSessionsForBook(20L, 0, 5);

        assertEquals(1, page.getTotalElements());
        assertEquals("hash-1", page.getContent().getFirst().getBookHash());
    }

    @Test
    void recordSession_rejectsMismatchedBookHash() {
        setKoreaderPrincipal();
        when(userRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(20L)).thenReturn(Optional.of(book));

        BookEntity otherBook = BookEntity.builder().id(21L).library(book.getLibrary()).build();
        when(bookRepository.findByCurrentOrInitialHash("other-hash")).thenReturn(Optional.of(otherBook));

        ReadingSessionRequest request = new ReadingSessionRequest(
                20L,
                "other-hash",
                BookFileType.EPUB,
                Instant.now(),
                Instant.now().plusSeconds(60),
                60,
                "1m",
                10.0f,
                12.0f,
                2.0f,
                "KOReader",
                "device-1",
                "start",
                "end"
        );

        assertThrows(APIException.class, () -> service.recordSession(request));
    }

    private void setKoreaderPrincipal() {
        KoreaderUserDetails details = new KoreaderUserDetails(
                "reader",
                "md5",
                true,
                false,
                10L,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())
        );
    }

    private void setJwtPrincipal() {
        BookLoreUser principal = BookLoreUser.builder().id(10L).username("reader").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, null)
        );
    }
}
