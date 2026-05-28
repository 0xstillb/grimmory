package org.booklore.service;

import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.*;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.booklore.service.koreader.KoreaderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreaderServiceTest {

    @Mock private BookRepository bookRepository;
    @Mock private BookMapper bookMapper;
    @Mock private UserRepository userRepository;
    @Mock private KoreaderUserRepository koreaderUserRepository;
    @Mock private UserBookProgressRepository progressRepository;
    @Mock private UserBookFileProgressRepository fileProgressRepository;
    @Mock private KoreaderProgressRepository koreaderProgressRepository;
    @Mock private KoreaderSecurityContextService securityContextService;

    @InjectMocks
    private KoreaderService service;

    private BookLoreUserEntity reader;
    private LibraryEntity library;
    private BookEntity book;
    private BookFileEntity pdfFile;

    @BeforeEach
    void setUp() {
        reader = new BookLoreUserEntity();
        reader.setId(42L);
        reader.setUsername("reader");
        library = new LibraryEntity();
        library.setId(7L);
        reader.setLibraries(new HashSet<>(Set.of(library)));

        book = new BookEntity();
        book.setId(1L);
        book.setLibrary(library);

        pdfFile = new BookFileEntity();
        pdfFile.setId(10L);
        pdfFile.setBook(book);
        pdfFile.setBookType(BookFileType.PDF);
        pdfFile.setBookFormat(true);
        pdfFile.setCurrentHash("hash");
        pdfFile.setInitialHash("initial-hash");
        pdfFile.setFileName("sample.pdf");
        pdfFile.setFileSubPath("books");
        book.setBookFiles(List.of(pdfFile));

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizeUser_success() {
        var authUser = new KoreaderUserEntity();
        authUser.setUsername("reader");
        authUser.setPasswordMD5("md5");
        authUser.setBookLoreUser(reader);
        authUser.setSyncEnabled(true);
        authUser.setSyncWithWebReader(true);

        setKoreaderAuth("reader", "md5");
        when(koreaderUserRepository.findByUsername("reader")).thenReturn(Optional.of(authUser));

        ResponseEntity<Map<String, Object>> resp = service.authorizeUser();

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("ok", resp.getBody().get("status"));
        assertEquals("reader", resp.getBody().get("username"));
        assertEquals(42L, resp.getBody().get("userId"));
        assertEquals(Boolean.TRUE, resp.getBody().get("syncEnabled"));
        assertEquals(Boolean.TRUE, resp.getBody().get("syncWithWebReader"));
    }

    @Test
    void getBookByHash_matchesCurrentHash() {
        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(bookMapper.toBook(book)).thenReturn(Book.builder().id(1L).title("Sample").build());

        ResponseEntity<Book> resp = service.getBookByHash("hash");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals("Sample", resp.getBody().getTitle());
    }

    @Test
    void getBookByHash_matchesInitialHash() {
        pdfFile.setCurrentHash(null);
        when(bookRepository.findAllByBookHash("initial-hash")).thenReturn(List.of(book));
        when(bookMapper.toBook(book)).thenReturn(Book.builder().id(1L).title("Sample").build());

        ResponseEntity<Book> resp = service.getBookByHash("initial-hash");

        assertEquals(200, resp.getStatusCode().value());
        assertEquals(1L, resp.getBody().getId());
    }

    @Test
    void getBookByHash_inaccessibleBookThrowsForbidden() {
        reader.setLibraries(new HashSet<>());
        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));

        APIException ex = assertThrows(APIException.class, () -> service.getBookByHash("hash"));
        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void saveProgress_persistsRawNativeProgressForEpub() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        epubFile.setFileName("sample.epub");
        epubFile.setFileSubPath("books");
        book.setBookFiles(List.of(epubFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .progress("chapter=1")
                .location("loc-1")
                .percentage(0.5F)
                .currentPage(12)
                .totalPages(100)
                .device("KOReader")
                .deviceId("device-1")
                .timestamp(123L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<KoreaderProgressEntity> captor = ArgumentCaptor.forClass(KoreaderProgressEntity.class);
        verify(koreaderProgressRepository).save(captor.capture());
        KoreaderProgressEntity saved = captor.getValue();
        assertEquals("hash", saved.getBookHash());
        assertEquals("chapter=1", saved.getProgress());
        assertEquals("loc-1", saved.getLocation());
        assertEquals(50.0F, saved.getPercentage());
        assertEquals(12, saved.getCurrentPage());
        assertEquals(100, saved.getTotalPages());
        assertEquals("KOReader", saved.getDevice());
        assertEquals("device-1", saved.getDeviceId());
        assertEquals(Instant.ofEpochSecond(123L), saved.getClientTimestamp());
        assertEquals("EPUB", saved.getFileFormat());

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(ReadStatus.READING, savedProgress.getReadStatus());
        assertEquals(Instant.ofEpochSecond(123L), savedProgress.getLastReadTime());
    }

    @Test
    void saveProgress_100Percent_setsReadAndDateFinished() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .progress("chapter=done")
                .percentage(1.0F)
                .timestamp(222L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        UserBookProgressEntity savedProgress = progressCaptor.getValue();
        assertEquals(ReadStatus.READ, savedProgress.getReadStatus());
        assertNotNull(savedProgress.getDateFinished());
    }

    @Test
    void saveProgress_0Percent_setsUnread() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .percentage(0.0F)
                .timestamp(333L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ReadStatus.UNREAD, progressCaptor.getValue().getReadStatus());
    }

    @Test
    void saveProgress_largeBookThreePages_setsReadingEvenWhenPercentBelowOne() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .percentage(0.00075F)
                .currentPage(3)
                .totalPages(4000)
                .timestamp(335L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ReadStatus.READING, progressCaptor.getValue().getReadStatus());
    }

    @Test
    void saveProgress_preservesManualReadStatusWhenManualChangeIsNewerThanKoreaderSync() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        UserBookProgressEntity existingProgress = new UserBookProgressEntity();
        existingProgress.setUser(reader);
        existingProgress.setBook(book);
        existingProgress.setReadStatus(ReadStatus.READ);
        existingProgress.setReadStatusModifiedTime(Instant.parse("2026-05-26T12:00:00Z"));
        existingProgress.setKoreaderLastSyncTime(Instant.parse("2026-05-25T12:00:00Z"));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .percentage(0.5F)
                .timestamp(444L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.of(existingProgress));
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ReadStatus.READ, progressCaptor.getValue().getReadStatus());
    }

    @Test
    void saveProgress_nullPercentage_doesNotDeriveOrOverrideReadStatus() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(11L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        UserBookProgressEntity existingProgress = new UserBookProgressEntity();
        existingProgress.setUser(reader);
        existingProgress.setBook(book);
        existingProgress.setReadStatus(ReadStatus.READING);

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(11L)
                .fileFormat("EPUB")
                .progress("chapter=2")
                .timestamp(555L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.of(existingProgress));
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 11L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ReadStatus.READING, progressCaptor.getValue().getReadStatus());
    }

    @Test
    void saveProgress_updatesPdfAndLegacyProgress() {
        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookId(1L)
                .bookFileId(10L)
                .fileFormat("PDF")
                .progress("page=12")
                .location("page-12")
                .percentage(0.75F)
                .currentPage(12)
                .totalPages(400)
                .device("KOReader")
                .deviceId("device-2")
                .timestamp(123L)
                .build();

        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        UserBookProgressEntity saved = progressCaptor.getValue();
        assertEquals(12, saved.getPdfProgress());
        assertEquals(75.0F, saved.getPdfProgressPercent());
        assertEquals("page=12", saved.getKoreaderProgress());
        assertEquals("KOReader", saved.getKoreaderDevice());
        assertEquals("device-2", saved.getKoreaderDeviceId());
        assertEquals(Instant.ofEpochSecond(123L), saved.getLastReadTime());
    }

    @Test
    void saveProgress_updatesCbxProgressForCbx() {
        BookFileEntity cbxFile = new BookFileEntity();
        cbxFile.setId(12L);
        cbxFile.setBook(book);
        cbxFile.setBookType(BookFileType.CBX);
        cbxFile.setCurrentHash("cbx-hash");
        cbxFile.setFileName("sample.cbx");
        cbxFile.setFileSubPath("books");
        book.setBookFiles(List.of(cbxFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("cbx-hash")
                .bookId(1L)
                .bookFileId(12L)
                .fileFormat("CBX")
                .progress("cbx-pos")
                .location("cbx-loc")
                .percentage(0.25F)
                .currentPage(7)
                .totalPages(20)
                .timestamp(321L)
                .build();

        when(bookRepository.findAllByBookHash("cbx-hash")).thenReturn(List.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 12L)).thenReturn(Optional.empty());

        service.saveProgress(request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(7, progressCaptor.getValue().getCbxProgress());
        assertEquals(25.0F, progressCaptor.getValue().getCbxProgressPercent());
    }

    @Test
    void getProgress_returnsLatestStoredProgress() {
        KoreaderProgressEntity entity = KoreaderProgressEntity.builder()
                .user(reader)
                .book(book)
                .bookFile(pdfFile)
                .bookHash("hash")
                .progress("page=12")
                .location("page-12")
                .percentage(75.0F)
                .currentPage(12)
                .totalPages(400)
                .device("KOReader")
                .deviceId("device-2")
                .clientTimestamp(Instant.ofEpochSecond(123L))
                .fileFormat("PDF")
                .build();
        when(bookRepository.findAllByBookHash("hash")).thenReturn(List.of(book));
        when(koreaderProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.of(entity));

        KoreaderProgress progress = service.getProgress("hash");

        assertEquals("hash", progress.getBookHash());
        assertEquals("page=12", progress.getProgress());
        assertEquals("page-12", progress.getLocation());
        assertEquals(75.0F, progress.getPercentage());
        assertEquals(12, progress.getCurrentPage());
        assertEquals(400, progress.getTotalPages());
        assertEquals("device-2", progress.getDeviceId());
        assertEquals(123L, progress.getTimestamp());
    }

    @Test
    void getPdfProgress_returnsPdfMetadata() {
        KoreaderProgressEntity entity = KoreaderProgressEntity.builder()
                .user(reader)
                .book(book)
                .bookFile(pdfFile)
                .bookHash("hash")
                .currentPage(12)
                .totalPages(400)
                .percentage(75.0F)
                .clientTimestamp(Instant.ofEpochSecond(123L))
                .fileFormat("PDF")
                .build();
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(koreaderProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.of(entity));

        KoreaderProgress progress = service.getPdfProgress(1L);

        assertEquals(1L, progress.getBookId());
        assertEquals(10L, progress.getBookFileId());
        assertEquals("PDF", progress.getFileFormat());
        assertEquals(12, progress.getCurrentPage());
        assertEquals(400, progress.getTotalPages());
        assertEquals("pdf_page", progress.getConversionStatus());
    }

    @Test
    void updatePdfProgress_conflictDetectedWhenRemoteNewer() {
        KoreaderProgressEntity entity = KoreaderProgressEntity.builder()
                .user(reader)
                .book(book)
                .bookFile(pdfFile)
                .bookHash("hash")
                .clientTimestamp(Instant.ofEpochSecond(200L))
                .fileFormat("PDF")
                .build();
        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookFileId(10L)
                .fileFormat("PDF")
                .currentPage(10)
                .percentage(50.0F)
                .timestamp(100L)
                .build();

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(koreaderProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.of(entity));

        KoreaderProgress progress = service.updatePdfProgress(1L, request);

        assertTrue(progress.getConflictDetected());
        assertFalse(progress.getUpdated());
        assertEquals("remote_newer", progress.getConversionStatus());
        verify(koreaderProgressRepository, never()).save(any());
    }

    @Test
    void updatePdfProgress_forceOverwritesNewerProgress() {
        KoreaderProgressEntity entity = KoreaderProgressEntity.builder()
                .user(reader)
                .book(book)
                .bookFile(pdfFile)
                .bookHash("hash")
                .clientTimestamp(Instant.ofEpochSecond(200L))
                .fileFormat("PDF")
                .build();
        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookFileId(10L)
                .fileFormat("PDF")
                .currentPage(10)
                .percentage(50.0F)
                .timestamp(100L)
                .force(true)
                .build();

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(koreaderProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.of(entity));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.empty());

        KoreaderProgress progress = service.updatePdfProgress(1L, request);

        assertFalse(progress.getConflictDetected());
        assertTrue(progress.getUpdated());
        verify(koreaderProgressRepository, atLeastOnce()).save(any(KoreaderProgressEntity.class));
    }

    @Test
    void updatePdfProgress_setsReadStatusFromPercentageForPdfBridge() {
        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookFileId(10L)
                .fileFormat("PDF")
                .currentPage(10)
                .percentage(50.0F)
                .timestamp(500L)
                .build();

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(koreaderProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.empty());
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());
        when(fileProgressRepository.findByUserIdAndBookFileId(42L, 10L)).thenReturn(Optional.empty());

        service.updatePdfProgress(1L, request);

        ArgumentCaptor<UserBookProgressEntity> progressCaptor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ReadStatus.READING, progressCaptor.getValue().getReadStatus());
    }

    @Test
    void updatePdfProgress_rejectsUnsupportedFormat() {
        BookFileEntity epubFile = new BookFileEntity();
        epubFile.setId(10L);
        epubFile.setBook(book);
        epubFile.setBookType(BookFileType.EPUB);
        epubFile.setCurrentHash("hash");
        book.setBookFiles(List.of(epubFile));

        KoreaderProgress request = KoreaderProgress.builder()
                .bookHash("hash")
                .bookFileId(10L)
                .fileFormat("EPUB")
                .currentPage(10)
                .percentage(50.0F)
                .timestamp(100L)
                .build();

        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

        APIException ex = assertThrows(APIException.class, () -> service.updatePdfProgress(1L, request));
        assertTrue(ex.getStatus().is4xxClientError());
    }

    @Test
    void getSupportedReadStatuses_includesManualStatuses() {
        List<String> statuses = service.getSupportedReadStatuses();

        assertTrue(statuses.contains("UNREAD"));
        assertTrue(statuses.contains("READING"));
        assertTrue(statuses.contains("READ"));
        assertTrue(statuses.contains("PAUSED"));
        assertTrue(statuses.contains("ABANDONED"));
        assertTrue(statuses.contains("RE_READING"));
    }

    @Test
    void updateReadStatus_setsReadAndDateFinished() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.empty());

        Map<String, Object> result = service.updateReadStatus(1L, "READ");

        assertEquals("ok", result.get("status"));
        assertEquals("READ", result.get("readStatus"));
        assertNotNull(result.get("dateFinished"));
        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository).save(captor.capture());
        assertEquals(ReadStatus.READ, captor.getValue().getReadStatus());
        assertNotNull(captor.getValue().getDateFinished());
        assertNotNull(captor.getValue().getReadStatusModifiedTime());
    }

    @Test
    void updateReadStatus_mapsOnHoldAliasToPaused() {
        UserBookProgressEntity existing = new UserBookProgressEntity();
        existing.setUser(reader);
        existing.setBook(book);
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(progressRepository.findByUserIdAndBookId(42L, 1L)).thenReturn(Optional.of(existing));

        Map<String, Object> result = service.updateReadStatus(1L, "ON_HOLD");

        assertEquals("PAUSED", result.get("readStatus"));
        ArgumentCaptor<UserBookProgressEntity> captor = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(progressRepository).save(captor.capture());
        assertEquals(ReadStatus.PAUSED, captor.getValue().getReadStatus());
    }

    @Test
    void updateReadStatus_rejectsUnsupportedStatus() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));

        APIException ex = assertThrows(APIException.class, () -> service.updateReadStatus(1L, "PARTIALLY_READ"));
        assertEquals(400, ex.getStatus().value());
    }

    private void setKoreaderAuth(String username, String passwordMd5) {
        KoreaderUserDetails details = new KoreaderUserDetails(
                username,
                passwordMd5,
                true,
                true,
                42L,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextImpl context = new SecurityContextImpl();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }
}
