package org.booklore.service;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderAnnotationDto;
import org.booklore.model.dto.koreader.KoreaderBookmarkDto;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderAnnotationEntity;
import org.booklore.model.entity.KoreaderBookmarkEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderAnnotationRepository;
import org.booklore.repository.koreader.KoreaderBookmarkRepository;
import org.booklore.service.koreader.KoreaderAnnotationService;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KoreaderAnnotationServiceTest {

    @Mock
    private KoreaderAnnotationRepository annotationRepository;

    @Mock
    private KoreaderBookmarkRepository bookmarkRepository;

    @Mock
    private UserBookProgressRepository userBookProgressRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private KoreaderSecurityContextService securityContextService;

    @InjectMocks
    private KoreaderAnnotationService service;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listAnnotations_sinceUsesIncrementalRepositoryAndReturnsMergeMetadata() {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L);
        BookEntity book = accessibleBook(42L, 30L);
        KoreaderAnnotationEntity entity = KoreaderAnnotationEntity.builder()
                .id(11L)
                .book(book)
                .user(reader)
                .dedupeKey("ann-1")
                .koreaderPos("/body/p[7]")
                .text("quote")
                .note("note")
                .source("KOREADER")
                .koreaderCreatedAt(100L)
                .koreaderUpdatedAt(150L)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 29, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 4, 29, 10, 5));

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(annotationRepository.findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(entity));

        List<KoreaderAnnotationDto> items = service.listAnnotations(42L, 1714471200L);

        assertEquals(1, items.size());
        assertEquals(42L, items.get(0).getBookId());
        assertEquals("annotation", items.get(0).getType());
        assertEquals("/body/p[7]", items.get(0).getKoreaderPos());
        verify(annotationRepository).findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(any(), any(), any());
        verify(annotationRepository, never()).findByUserIdAndBookId(any(), any());
    }

    @Test
    void listBookmarks_sinceUsesIncrementalRepositoryAndReturnsMergeMetadata() {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L);
        BookEntity book = accessibleBook(42L, 30L);
        KoreaderBookmarkEntity entity = KoreaderBookmarkEntity.builder()
                .id(12L)
                .book(book)
                .user(reader)
                .dedupeKey("bm-1")
                .koreaderPos("/body/p[8]")
                .text("line")
                .note("memo")
                .source("KOREADER")
                .koreaderCreatedAt(200L)
                .build();
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 29, 11, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 4, 29, 11, 7));

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));
        when(bookmarkRepository.findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(entity));

        List<KoreaderBookmarkDto> items = service.listBookmarks(42L, 1714471200L);

        assertEquals(1, items.size());
        assertEquals(42L, items.get(0).getBookId());
        assertEquals("bookmark", items.get(0).getType());
        assertEquals("/body/p[8]", items.get(0).getKoreaderPos());
        verify(bookmarkRepository).findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(any(), any(), any());
        verify(bookmarkRepository, never()).findByUserIdAndBookId(any(), any());
    }

    @Test
    void listAnnotations_forbiddenWhenBookIsOutsideAccessibleLibraries() {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L);
        BookEntity book = accessibleBook(42L, 99L);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findById(42L)).thenReturn(Optional.of(book));

        APIException exception = assertThrows(APIException.class, () -> service.listAnnotations(42L, null));
        assertEquals(ApiError.FORBIDDEN.getStatus(), exception.getStatus());
        verify(annotationRepository, never()).findByUserIdAndBookId(any(), any());
    }

    private BookLoreUserEntity readerWithLibrary(Long userId, Long libraryId) {
        BookLoreUserEntity reader = BookLoreUserEntity.builder()
                .id(userId)
                .username("reader" + userId)
                .name("Reader " + userId)
                .build();
        reader.setLibraries(new HashSet<>(java.util.Set.of(
                LibraryEntity.builder().id(libraryId).name("Library " + libraryId).build()
        )));
        return reader;
    }

    private BookEntity accessibleBook(Long bookId, Long libraryId) {
        return BookEntity.builder()
                .id(bookId)
                .library(LibraryEntity.builder().id(libraryId).name("Library " + libraryId).build())
                .build();
    }
}
