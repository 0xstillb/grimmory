package org.booklore.service;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderCfiResolveRequest;
import org.booklore.model.dto.koreader.KoreaderCfiResolveResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressUpdateRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.booklore.service.koreader.KoreaderWebReaderBridgeService;
import org.booklore.util.koreader.EpubCfiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KoreaderWebReaderBridgeServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserBookProgressRepository userBookProgressRepository;

    @Mock
    private UserBookFileProgressRepository userBookFileProgressRepository;

    @Mock
    private KoreaderProgressRepository koreaderProgressRepository;

    @Mock
    private KoreaderSecurityContextService securityContextService;

    @Mock
    private EpubCfiService epubCfiService;

    @InjectMocks
    private KoreaderWebReaderBridgeService service;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getWebProgress_keepsNativeKoreaderFieldsSeparate() throws IOException {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = accessibleEpubBook(42L, 30L);
        UserBookProgressEntity webProgress = new UserBookProgressEntity();
        webProgress.setLastReadTime(Instant.ofEpochSecond(200));
        webProgress.setEpubProgress("epubcfi(/6/2!/4/2)");
        webProgress.setEpubProgressPercent(23.4f);
        KoreaderProgressEntity nativeProgress = KoreaderProgressEntity.builder()
                .location("/body/DocFragment[1]/body/div[1]/p[2]")
                .progress("/body/DocFragment[1]/body/div[1]/p[2]")
                .currentPage(12)
                .totalPages(100)
                .timestamp(Instant.ofEpochSecond(210))
                .build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));
        when(userBookProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.of(webProgress));
        when(userBookFileProgressRepository.findMostRecentByUserIdAndBookId(1L, 42L)).thenReturn(Optional.empty());
        when(koreaderProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.of(nativeProgress));
        when(epubCfiService.validateCfi(any(java.io.File.class), any(String.class))).thenReturn(true);

        KoreaderWebProgressResponse response = service.getWebProgress(42L);

        assertEquals("epubcfi(/6/2!/4/2)", response.getEpubCfi());
        assertEquals("/body/DocFragment[1]/body/div[1]/p[2]", response.getRawKoreaderLocation());
        assertEquals(12, response.getCurrentPage());
        assertEquals("cfi_available", response.getConversionStatus());
    }

    @Test
    void updateWebProgress_rejectsOverwriteWhenRemoteIsNewer() throws IOException {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = accessibleEpubBook(42L, 30L);
        UserBookProgressEntity webProgress = new UserBookProgressEntity();
        webProgress.setLastReadTime(Instant.ofEpochSecond(300));
        webProgress.setEpubProgress("epubcfi(/6/2!/4/2)");
        webProgress.setEpubProgressPercent(40.0f);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));
        when(userBookProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.of(webProgress));
        when(userBookFileProgressRepository.findByUserIdAndBookFileId(1L, 420L)).thenReturn(Optional.empty());
        when(koreaderProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.empty());
        when(epubCfiService.validateCfi(any(java.io.File.class), any(String.class))).thenReturn(true);

        KoreaderWebProgressResponse response = service.updateWebProgress(42L, KoreaderWebProgressUpdateRequest.builder()
                .percentage(20.0f)
                .timestamp(250L)
                .expectedUpdatedAt(250L)
                .build());

        assertTrue(response.getConflictDetected());
        assertFalse(response.getUpdated());
        verify(userBookProgressRepository, never()).save(any(UserBookProgressEntity.class));
        verify(userBookFileProgressRepository, never()).save(any(UserBookFileProgressEntity.class));
    }

    @Test
    void updateWebProgress_updatesOnlyWebReaderFields() throws IOException {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = accessibleEpubBook(42L, 30L);
        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setUser(reader);
        progress.setBook(book);
        progress.setKoreaderProgress("existing-native");
        progress.setKoreaderProgressPercent(11.0f);
        KoreaderProgressEntity nativeProgress = KoreaderProgressEntity.builder()
                .location("/body/DocFragment[1]/body/div[1]")
                .progress("/body/DocFragment[1]/body/div[1]")
                .currentPage(33)
                .totalPages(120)
                .timestamp(Instant.ofEpochSecond(410))
                .build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));
        when(userBookProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.of(progress));
        when(userBookFileProgressRepository.findByUserIdAndBookFileId(1L, 420L)).thenReturn(Optional.of(new UserBookFileProgressEntity()));
        when(koreaderProgressRepository.findByUserIdAndBookId(1L, 42L)).thenReturn(Optional.of(nativeProgress));
        when(userBookProgressRepository.save(any(UserBookProgressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userBookFileProgressRepository.save(any(UserBookFileProgressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoreaderWebProgressResponse response = service.updateWebProgress(42L, KoreaderWebProgressUpdateRequest.builder()
                .percentage(66.6f)
                .epubCfi("epubcfi(/6/2!/4/2)")
                .positionHref("chapter1.xhtml")
                .contentSourceProgressPercent(55.0f)
                .timestamp(500L)
                .build());

        assertTrue(response.getUpdated());
        assertFalse(response.getConflictDetected());
        assertEquals("existing-native", progress.getKoreaderProgress());
        assertEquals(11.0f, progress.getKoreaderProgressPercent());
        assertEquals("epubcfi(/6/2!/4/2)", progress.getEpubProgress());
        verify(userBookProgressRepository).save(progress);
        verify(userBookFileProgressRepository).save(any(UserBookFileProgressEntity.class));
    }

    @Test
    void resolveCfi_returnsFallbackWhenConversionFails() throws IOException {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = accessibleEpubBook(42L, 30L);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));
        when(epubCfiService.convertXPointerToCfi(any(Path.class), any(String.class)))
                .thenThrow(new IllegalArgumentException("bad xpointer"));

        KoreaderCfiResolveResponse response = service.resolveCfi(42L, KoreaderCfiResolveRequest.builder()
                .rawKoreaderXPointer("/body/DocFragment[1]/body/div[1]/bad")
                .currentPage(12)
                .totalPages(200)
                .percentage(6.0f)
                .build());

        assertFalse(response.isConverted());
        assertEquals("conversion_failed", response.getConversionStatus());
        assertEquals("/body/DocFragment[1]/body/div[1]/bad", response.getRawKoreaderXPointer());
        assertEquals(12, response.getCurrentPage());
    }

    @Test
    void getWebProgress_forbiddenWhenBookOutsideAccessibleLibraries() throws IOException {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = accessibleEpubBook(42L, 99L);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));

        APIException exception = assertThrows(APIException.class, () -> service.getWebProgress(42L));
        assertEquals(ApiError.FORBIDDEN.getStatus(), exception.getStatus());
    }

    @Test
    void resolveCfi_returnsUnsupportedWhenNoEpubFileExists() {
        BookLoreUserEntity reader = readerWithLibrary(1L, 30L, false);
        BookEntity book = BookEntity.builder()
                .id(42L)
                .library(LibraryEntity.builder().id(30L).name("Main").build())
                .bookFiles(new java.util.ArrayList<>(java.util.List.of(
                        BookFileEntity.builder()
                                .id(421L)
                                .bookType(BookFileType.PDF)
                                .isBookFormat(true)
                                .fileName("guide.pdf")
                                .fileSubPath("")
                                .build()
                )))
                .build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(42L)).thenReturn(Optional.of(book));

        KoreaderCfiResolveResponse response = service.resolveCfi(42L, new KoreaderCfiResolveRequest());

        assertFalse(response.isConverted());
        assertEquals("unsupported_format", response.getConversionStatus());
        assertEquals("No EPUB-family book file is available for bridge conversion.", response.getReason());
    }

    private BookLoreUserEntity readerWithLibrary(Long userId, Long libraryId, boolean admin) {
        BookLoreUserEntity reader = BookLoreUserEntity.builder()
                .id(userId)
                .username("reader" + userId)
                .name("Reader " + userId)
                .build();
        reader.setLibraries(new HashSet<>(java.util.Set.of(
                LibraryEntity.builder().id(libraryId).name("Library " + libraryId).build()
        )));
        if (admin) {
            reader.setPermissions(UserPermissionsEntity.builder().permissionAdmin(true).build());
        }
        return reader;
    }

    private BookEntity accessibleEpubBook(Long bookId, Long libraryId) throws IOException {
        Path libraryRoot = Files.createDirectories(tempDir.resolve("library-" + libraryId));
        Path epubFile = libraryRoot.resolve("novel.epub");
        Files.writeString(epubFile, "stub");

        BookEntity book = BookEntity.builder()
                .id(bookId)
                .library(LibraryEntity.builder().id(libraryId).name("Library " + libraryId).build())
                .bookFiles(new java.util.ArrayList<>())
                .build();

        BookFileEntity bookFile = BookFileEntity.builder()
                .id(bookId * 10)
                .book(book)
                .bookType(BookFileType.EPUB)
                .isBookFormat(true)
                .fileName(epubFile.getFileName().toString())
                .fileSubPath("")
                .build();
        book.setLibraryPath(org.booklore.model.entity.LibraryPathEntity.builder().path(libraryRoot.toString()).build());
        book.getBookFiles().add(bookFile);
        return book;
    }
}
