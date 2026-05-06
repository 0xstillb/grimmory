package org.booklore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.booklore.exception.APIException;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.booklore.service.koreader.KoreaderService;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class KoreaderServiceTest {

    @Mock
    KoreaderProgressRepository progressRepo;
    @Mock
    BookRepository bookRepo;
    @Mock
    BookMapper bookMapper;
    @Mock
    KoreaderSecurityContextService securityContextService;
    @Mock
    HardcoverSyncService hardcoverSyncService;
    @Mock
    UserBookProgressRepository userBookProgressRepository;

    @InjectMocks
    KoreaderService service;

    private BookLoreUserEntity reader;
    private BookEntity book;

    @BeforeEach
    void setUp() {
        LibraryEntity library = LibraryEntity.builder().id(5L).build();
        reader = BookLoreUserEntity.builder()
                .id(42L)
                .username("u")
                .libraries(new HashSet<>())
                .permissions(UserPermissionsEntity.builder().permissionAdmin(false).build())
                .build();
        reader.getLibraries().add(library);

        book = BookEntity.builder()
                .id(99L)
                .library(library)
                .build();
        book.setBookFiles(new java.util.ArrayList<>());
        book.getBookFiles().add(BookFileEntity.builder()
                .book(book)
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .currentHash("hash")
                .initialHash("hash")
                .fileName("test.epub")
                .fileSubPath("")
                .build());
        lenient().when(userBookProgressRepository.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.empty());
    }

    @Test
    void authorizeUser_success() {
        when(securityContextService.requireCurrentReader(false))
                .thenReturn(new KoreaderSecurityContextService.AuthenticatedReader(42L, "u", true, true, true));

        ResponseEntity<Map<String, Object>> resp = service.authorizeUser();
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("u", resp.getBody().get("username"));
        assertEquals("ok", resp.getBody().get("status"));
        assertEquals(true, resp.getBody().get("syncWithGrimmoryReader"));
    }

    @Test
    void getBookByHash_returnsMappedBook() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        Book mapped = Book.builder().id(99L).title("Example").build();
        when(bookMapper.toBook(book)).thenReturn(mapped);

        ResponseEntity<Book> response = service.getBookByHash("hash");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(mapped, response.getBody());
    }

    @Test
    void getProgress_returnsEmptyWhenNoProgressExists() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        when(progressRepo.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.empty());

        KoreaderProgress response = service.getProgress("hash");

        assertEquals("hash", response.getBookHash());
        assertEquals(99L, response.getBookId());
        assertEquals("EPUB", response.getFileFormat());
        assertNull(response.getPercentage());
    }

    @Test
    void getProgress_returnsStoredKoreaderData() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        KoreaderProgressEntity entity = KoreaderProgressEntity.builder()
                .bookHash("hash")
                .document("hash")
                .progress("xp://1")
                .location("loc-1")
                .percentage(55.5f)
                .device("KOReader")
                .deviceId("device-1")
                .timestamp(Instant.ofEpochSecond(1234))
                .build();
        when(progressRepo.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.of(entity));

        KoreaderProgress out = service.getProgress("hash");
        assertEquals("xp://1", out.getProgress());
        assertEquals("loc-1", out.getLocation());
        assertEquals(55.5F, out.getPercentage());
        assertEquals(1234L, out.getTimestamp());
    }

    @Test
    void saveProgress_linksMatchingBookFileWhenHashMatchesAlternateFile() {
        BookFileEntity altFile = BookFileEntity.builder()
                .id(777L)
                .book(book)
                .isBookFormat(true)
                .bookType(BookFileType.EPUB)
                .currentHash("alt-current")
                .initialHash("alt-initial")
                .fileName("alt.epub")
                .fileSubPath("")
                .build();
        book.getBookFiles().add(altFile);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("alt-current")).thenReturn(Optional.of(book));
        when(progressRepo.findByUserIdAndBookFileId(42L, 777L)).thenReturn(Optional.empty());
        when(progressRepo.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.empty());

        var dto = KoreaderProgress.builder()
                .document("alt-current")
                .bookHash("alt-current")
                .progress("xp://alt")
                .location("loc-alt")
                .percentage(12.5F)
                .device("KOReader")
                .device_id("device-2")
                .currentPage(4)
                .totalPages(200)
                .build();

        service.saveProgress("alt-current", dto);

        ArgumentCaptor<KoreaderProgressEntity> cap = ArgumentCaptor.forClass(KoreaderProgressEntity.class);
        verify(progressRepo).save(cap.capture());
        KoreaderProgressEntity saved = cap.getValue();
        assertEquals(777L, saved.getBookFile().getId());
        assertEquals("alt-current", saved.getCurrentHash());
        assertEquals("alt-initial", saved.getInitialHash());
        assertEquals("EPUB", saved.getFileFormat());
        assertEquals("KOREADER", saved.getSource());
        assertEquals(1L, saved.getProgressVersion());
    }

    @Test
    void saveProgress_createsOrUpdatesSeparateProgressEntity() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        when(progressRepo.findByUserIdAndBookId(42L, 99L))
                .thenReturn(Optional.empty());

        var dto = KoreaderProgress.builder()
                .document("hash")
                .bookHash("hash")
                .progress("xp://1")
                .location("loc-1")
                .percentage(0.6F)
                .device("KOReader")
                .device_id("device-1")
                .currentPage(12)
                .totalPages(100)
                .build();
        service.saveProgress("hash", dto);

        ArgumentCaptor<KoreaderProgressEntity> cap = ArgumentCaptor.forClass(KoreaderProgressEntity.class);
        verify(progressRepo).save(cap.capture());
        KoreaderProgressEntity saved = cap.getValue();
        assertEquals("hash", saved.getBookHash());
        assertEquals("xp://1", saved.getProgress());
        assertEquals("loc-1", saved.getLocation());
        assertEquals(60.0F, saved.getPercentage());
        assertEquals("KOReader", saved.getDevice());
        assertEquals("device-1", saved.getDeviceId());
        verify(hardcoverSyncService).syncProgressToHardcover(99L, 60.0F, 42L);
    }

    @Test
    void saveProgress_samePercentage_doesNotSyncHardcoverAgain() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        KoreaderProgressEntity existing = KoreaderProgressEntity.builder().percentage(60.0f).build();
        when(progressRepo.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.of(existing));

        var dto = KoreaderProgress.builder()
                .document("hash").progress("y").percentage(60.0F).device("d").device_id("id").build();
        service.saveProgress("hash", dto);

        verify(hardcoverSyncService, never()).syncProgressToHardcover(any(), any(), any());
    }

    @Test
    void saveProgress_forEpubClearsStaleWebReaderLocatorFields() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        when(progressRepo.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.empty());

        UserBookProgressEntity existingProgress = UserBookProgressEntity.builder()
                .user(reader)
                .book(book)
                .lastReadTime(Instant.parse("2026-05-06T11:33:56Z"))
                .epubProgress("epubcfi(/6/526!/4,/90/1:146,/134/1:91)")
                .epubProgressHref("OEBPS/Text/0261.xhtml")
                .epubProgressPercent(0.132f)
                .build();
        when(userBookProgressRepository.findByUserIdAndBookId(42L, 99L)).thenReturn(Optional.of(existingProgress));

        var dto = KoreaderProgress.builder()
                .timestamp(Instant.parse("2026-05-06T11:43:56Z").getEpochSecond())
                .document("hash")
                .bookHash("hash")
                .progress("/body/DocFragment[4]/body/h3/text().0")
                .location("/body/DocFragment[4]/body/h3/text().0")
                .percentage(10.2F)
                .device("KOReader")
                .device_id("device-1")
                .currentPage(17)
                .totalPages(16653)
                .build();

        service.saveProgress("hash", dto);

        ArgumentCaptor<UserBookProgressEntity> cap = ArgumentCaptor.forClass(UserBookProgressEntity.class);
        verify(userBookProgressRepository).save(cap.capture());
        UserBookProgressEntity saved = cap.getValue();
        assertNull(saved.getEpubProgress());
        assertNull(saved.getEpubProgressHref());
        assertNull(saved.getEpubProgressPercent());
        assertEquals(0.102F, saved.getKoreaderProgressPercent());
        assertEquals(Instant.parse("2026-05-06T11:43:56Z"), saved.getKoreaderLastSyncTime());
        assertEquals(Instant.parse("2026-05-06T11:43:56Z"), saved.getLastReadTime());
    }

    @Test
    void saveProgress_rejectsNegativePercentage() {
        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepo.findByCurrentOrInitialHash("hash")).thenReturn(Optional.of(book));
        var dto = KoreaderProgress.builder().document("hash").percentage(-1.0F).build();
        assertThrows(APIException.class, () -> service.saveProgress("hash", dto));
    }
}
