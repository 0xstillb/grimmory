package org.booklore.service;

import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionItemRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ReadingSessionEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ReadingSessionRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingSessionServiceTest {

    @Mock private AuthenticationService authenticationService;
    @Mock private ReadingSessionRepository readingSessionRepository;
    @Mock private BookRepository bookRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private KoreaderSecurityContextService koreaderSecurityContextService;

    @InjectMocks
    private ReadingSessionService service;

    private BookLoreUser authUser;
    private BookLoreUserEntity userEntity;
    private BookEntity book;

    @BeforeEach
    void setUp() {
        authUser = BookLoreUser.builder().id(42L).username("reader").build();
        userEntity = BookLoreUserEntity.builder().id(42L).username("reader").build();
        book = new BookEntity();
        book.setId(1L);
        BookFileEntity file = new BookFileEntity();
        file.setId(10L);
        file.setBook(book);
        file.setBookType(BookFileType.PDF);
        file.setCurrentHash("hash");
        file.setFileName("sample.pdf");
        file.setFileSubPath("books");
        book.setBookFiles(List.of(file));

        when(authenticationService.getAuthenticatedUser()).thenReturn(authUser);
        when(userRepository.findById(42L)).thenReturn(Optional.of(userEntity));
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(koreaderSecurityContextService.requireCurrentReaderEntity(true)).thenReturn(userEntity);
        when(readingSessionRepository.save(any(ReadingSessionEntity.class))).thenAnswer(invocation -> {
            ReadingSessionEntity session = invocation.getArgument(0);
            session.setId(100L);
            return session;
        });
    }

    @Test
    void recordSession_persistsSingleSession() {
        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(10L));
        request.setEndTime(Instant.ofEpochSecond(20L));
        request.setDurationSeconds(10);
        request.setDurationFormatted("10s");
        request.setStartProgress(0.1F);
        request.setEndProgress(0.2F);
        request.setProgressDelta(0.1F);
        request.setStartLocation("start");
        request.setEndLocation("end");
        request.setCurrentPage(2);
        request.setTotalPages(100);

        service.recordSession(request);

        ArgumentCaptor<ReadingSessionEntity> captor = ArgumentCaptor.forClass(ReadingSessionEntity.class);
        verify(readingSessionRepository).save(captor.capture());
        verify(koreaderSecurityContextService).requireCurrentReaderEntity(true);
        ReadingSessionEntity saved = captor.getValue();
        assertEquals(1L, saved.getBook().getId());
        assertEquals("hash", saved.getBookHash());
        assertEquals(BookFileType.PDF, saved.getBookType());
        assertEquals(2, saved.getCurrentPage());
        assertEquals(100, saved.getTotalPages());
    }

    @Test
    void recordSessionsBatch_persistsBatch() {
        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest();
        request.setBookId(1L);
        request.setBookHash("hash");
        request.setBookType("PDF");
        request.setSessions(List.of(
                new ReadingSessionItemRequest(
                        Instant.ofEpochSecond(10L),
                        Instant.ofEpochSecond(20L),
                        10,
                        "10s",
                        0.1F,
                        0.2F,
                        0.1F,
                        "start",
                        "end",
                        2,
                        100
                )));

        ReadingSessionBatchResponse response = service.recordSessionsBatch(request);

        assertEquals(1, response.getTotalRequested());
        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getResults().size());
        assertEquals(100L, response.getResults().get(0).getSessionId());
        verify(koreaderSecurityContextService).requireCurrentReaderEntity(true);
    }

    @Test
    void recordSessionsBatch_emptyBatchFails() {
        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest();
        request.setBookId(1L);
        request.setSessions(List.of());

        APIException ex = assertThrows(APIException.class, () -> service.recordSessionsBatch(request));
        assertEquals(400, ex.getStatus().value());
    }
}
