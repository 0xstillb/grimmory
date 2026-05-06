package org.booklore.app.service;

import jakarta.persistence.EntityManager;
import org.booklore.app.dto.UpdateProgressRequest;
import org.booklore.app.mapper.AppBookMapper;
import org.booklore.config.security.service.AuthenticationService;
import org.booklore.exception.APIException;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.dto.Library;
import org.booklore.model.dto.request.BookFileProgress;
import org.booklore.model.dto.request.ReadProgressRequest;
import org.booklore.app.dto.AppBookDetail;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.LibraryPathEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.service.book.BookService;
import org.booklore.service.opds.MagicShelfBookService;
import org.booklore.util.koreader.EpubCfiService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppBookServiceProgressTest {

    @TempDir
    Path tempDir;

    @Mock private BookRepository bookRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private UserBookFileProgressRepository userBookFileProgressRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private AuthenticationService authenticationService;
    @Mock private AppBookMapper mobileBookMapper;
    @Mock private BookService bookService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private EntityManager entityManager;
    @Mock private KoreaderProgressRepository koreaderProgressRepository;
    @Mock private EpubCfiService epubCfiService;

    private AppBookService service;

    private final Long userId = 1L;
    private final Long bookId = 42L;
    private final Long libraryId = 5L;

    @BeforeEach
    void setUp() {
        service = new AppBookService(
                bookRepository, userBookProgressRepository, userBookFileProgressRepository,
                shelfRepository, authenticationService, mobileBookMapper,
                bookService, magicShelfBookService, entityManager,
                koreaderProgressRepository, epubCfiService
        );
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — success
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_success_delegatesToBookServiceWithPathBookId() {
        mockAdminUser();
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, "pos", "href", 0.5f, null, null));

        service.updateBookProgress(bookId, request);

        ArgumentCaptor<ReadProgressRequest> captor = ArgumentCaptor.forClass(ReadProgressRequest.class);
        verify(bookService).updateReadProgress(captor.capture());

        ReadProgressRequest delegated = captor.getValue();
        assertEquals(bookId, delegated.getBookId());
        assertNotNull(delegated.getFileProgress());
        assertEquals(0.5f, delegated.getFileProgress().progressPercent());
    }

    @Test
    void updateBookProgress_success_mapsAllFieldsFromAppRequest() {
        mockAdminUser();
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.75f, null, null));

        service.updateBookProgress(bookId, request);

        ArgumentCaptor<ReadProgressRequest> captor = ArgumentCaptor.forClass(ReadProgressRequest.class);
        verify(bookService).updateReadProgress(captor.capture());

        ReadProgressRequest delegated = captor.getValue();
        assertEquals(bookId, delegated.getBookId());
        assertEquals(0.75f, delegated.getFileProgress().progressPercent());
        assertNull(delegated.getEpubProgress());
        assertNull(delegated.getPdfProgress());
        assertNull(delegated.getCbxProgress());
        assertNull(delegated.getAudiobookProgress());
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — forbidden access
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_nonAdminWithoutAccess_throwsForbidden() {
        mockNonAdminUser(Set.of(99L));
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        assertThrows(APIException.class, () -> service.updateBookProgress(bookId, request));
        verify(bookService, never()).updateReadProgress(any());
    }

    @Test
    void updateBookProgress_nonAdminWithAccess_succeeds() {
        mockNonAdminUser(Set.of(libraryId));
        mockBookWithLibrary(bookId, libraryId);

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        service.updateBookProgress(bookId, request);

        verify(bookService).updateReadProgress(any(ReadProgressRequest.class));
    }

    // -------------------------------------------------------------------------
    // updateBookProgress — book not found
    // -------------------------------------------------------------------------

    @Test
    void updateBookProgress_bookNotFound_throwsException() {
        mockAdminUser();
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.empty());

        UpdateProgressRequest request = new UpdateProgressRequest();
        request.setFileProgress(new BookFileProgress(1L, null, null, 0.5f, null, null));

        assertThrows(APIException.class, () -> service.updateBookProgress(bookId, request));
        verify(bookService, never()).updateReadProgress(any());
    }

    @Test
    void getBookDetail_resolvesExactEpubProgressFromKoreaderSync() throws Exception {
        mockAdminUser();

        Path libraryRoot = tempDir.resolve("library");
        Files.createDirectories(libraryRoot);
        Path epubFile = Files.createFile(libraryRoot.resolve("book.epub"));

        LibraryEntity library = LibraryEntity.builder().id(libraryId).build();
        LibraryPathEntity libraryPath = new LibraryPathEntity();
        libraryPath.setPath(libraryRoot.toString());

        BookEntity book = new BookEntity();
        book.setId(bookId);
        book.setLibrary(library);
        book.setLibraryPath(libraryPath);

        BookFileEntity bookFile = new BookFileEntity();
        bookFile.setId(11L);
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.EPUB);
        bookFile.setFileSubPath("");
        bookFile.setFileName(epubFile.getFileName().toString());
        book.setBookFiles(List.of(bookFile));

        UserBookProgressEntity progress = new UserBookProgressEntity();
        progress.setUser(newUserEntity());
        progress.setBook(book);
        progress.setKoreaderLastSyncTime(Instant.parse("2026-05-06T11:43:56Z"));
        progress.setKoreaderProgressPercent(0.102f);

        KoreaderProgressEntity nativeProgress = new KoreaderProgressEntity();
        nativeProgress.setLocation("/body/DocFragment[12]/body/div[3]/p[7]/text().245");
        nativeProgress.setProgress("/body/DocFragment[12]/body/div[3]/p[7]/text().245");
        nativeProgress.setPercentage(10.2f);

        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
        when(userBookProgressRepository.findByUserIdAndBookId(userId, bookId)).thenReturn(Optional.of(progress));
        when(userBookFileProgressRepository.findMostRecentByUserIdAndBookId(userId, bookId)).thenReturn(Optional.empty());
        when(koreaderProgressRepository.findByUserIdAndBookFileId(userId, bookFile.getId())).thenReturn(Optional.of(nativeProgress));
        when(epubCfiService.convertXPointerToCfi(eq(epubFile), eq("/body/DocFragment[12]/body/div[3]/p[7]/text().245")))
                .thenReturn("epubcfi(/6/48!/4/2/6/1:245)");
        when(epubCfiService.resolveCfiLocation(eq(epubFile), eq("epubcfi(/6/48!/4/2/6/1:245)")))
                .thenReturn(Optional.of(new EpubCfiService.CfiLocation("OEBPS/Text/chapter002.xhtml", 42.4f)));
        when(mobileBookMapper.toDetail(eq(book), eq(progress), isNull(), any(AppBookDetail.EpubProgress.class)))
                .thenReturn(AppBookDetail.builder().id(bookId).build());

        AppBookDetail detail = service.getBookDetail(bookId);

        assertEquals(bookId, detail.getId());
        ArgumentCaptor<AppBookDetail.EpubProgress> captor = ArgumentCaptor.forClass(AppBookDetail.EpubProgress.class);
        verify(mobileBookMapper).toDetail(eq(book), eq(progress), isNull(), captor.capture());
        assertNotNull(captor.getValue());
        assertEquals("epubcfi(/6/48!/4/2/6/1:245)", captor.getValue().getCfi());
        assertEquals("OEBPS/Text/chapter002.xhtml", captor.getValue().getHref());
        assertEquals(42.4f, captor.getValue().getContentSourceProgressPercent());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void mockAdminUser() {
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(true);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    private void mockNonAdminUser(Set<Long> libraryIds) {
        List<Library> assignedLibraries = libraryIds.stream()
                .map(id -> Library.builder().id(id).build())
                .toList();
        var permissions = new BookLoreUser.UserPermissions();
        permissions.setAdmin(false);
        BookLoreUser user = BookLoreUser.builder()
                .id(userId)
                .permissions(permissions)
                .assignedLibraries(assignedLibraries)
                .build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
    }

    private void mockBookWithLibrary(Long bookId, Long libraryId) {
        LibraryEntity library = LibraryEntity.builder().id(libraryId).build();
        BookEntity book = BookEntity.builder().id(bookId).library(library).build();
        when(bookRepository.findByIdWithBookFiles(bookId)).thenReturn(Optional.of(book));
    }

    private org.booklore.model.entity.BookLoreUserEntity newUserEntity() {
        org.booklore.model.entity.BookLoreUserEntity user = new org.booklore.model.entity.BookLoreUserEntity();
        user.setId(userId);
        return user;
    }
}
