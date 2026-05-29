package org.booklore.service.koreader;

import org.booklore.exception.APIException;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.opds.MagicShelfBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreaderShelfServiceTest {

    @Mock private ShelfRepository shelfRepository;
    @Mock private MagicShelfRepository magicShelfRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookDownloadService bookDownloadService;
    @Mock private MagicShelfBookService magicShelfBookService;
    @Mock private KoreaderSecurityContextService securityContextService;

    @InjectMocks
    private KoreaderShelfService service;

    private BookLoreUserEntity reader;
    private LibraryEntity library;
    private ShelfEntity shelf;
    private BookEntity book;
    private BookFileEntity bookFile;

    @BeforeEach
    void setUp() {
        reader = new BookLoreUserEntity();
        reader.setId(42L);
        library = new LibraryEntity();
        library.setId(7L);
        reader.setLibraries(Set.of(library));

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);

        shelf = ShelfEntity.builder()
                .id(5L)
                .name("Sci-Fi")
                .isPublic(true)
                .user(reader)
                .build();

        book = new BookEntity();
        book.setId(1L);
        book.setLibrary(library);
        book.setMetadata(BookMetadataEntity.builder().title("Title").build());

        bookFile = new BookFileEntity();
        bookFile.setId(10L);
        bookFile.setBook(book);
        bookFile.setBookType(BookFileType.PDF);
        bookFile.setCurrentHash("hash");
        bookFile.setFileName("title.pdf");
        bookFile.setFileSubPath("books");
        book.setBookFiles(List.of(bookFile));
    }

    @Test
    void listShelves_returnsAccessibleShelves() {
        when(shelfRepository.findByUserIdOrPublicShelfTrue(42L)).thenReturn(List.of(shelf));
        when(magicShelfRepository.findAllByUserId(42L)).thenReturn(List.of());
        when(magicShelfRepository.findAllByIsPublicIsTrue()).thenReturn(List.of());

        List<KoreaderShelfSummary> shelves = service.listShelves();

        assertEquals(1, shelves.size());
        assertEquals("Sci-Fi", shelves.get(0).getName());
        assertEquals("regular", shelves.get(0).getType());
        assertEquals("public", shelves.get(0).getVisibility());
    }

    @Test
    void listShelves_includesMagicShelves() {
        MagicShelfEntity magicShelf = MagicShelfEntity.builder()
                .id(99L)
                .userId(42L)
                .name("Unread PDFs")
                .isPublic(false)
                .filterJson("{\"all\":true}")
                .build();

        when(shelfRepository.findByUserIdOrPublicShelfTrue(42L)).thenReturn(List.of(shelf));
        when(magicShelfRepository.findAllByUserId(42L)).thenReturn(List.of(magicShelf));
        when(magicShelfRepository.findAllByIsPublicIsTrue()).thenReturn(List.of());
        when(magicShelfBookService.getBookIdsByMagicShelfId(42L, 99L)).thenReturn(List.of(1L, 2L));

        List<KoreaderShelfSummary> shelves = service.listShelves();

        assertEquals(2, shelves.size());
        KoreaderShelfSummary magic = shelves.stream().filter(item -> "magic".equals(item.getType())).findFirst().orElse(null);
        assertNotNull(magic);
        assertEquals("Unread PDFs", magic.getName());
        assertEquals(2, magic.getBookCount());
    }

    @Test
    void listShelves_typeFilterMagicReturnsOnlyMagic() {
        MagicShelfEntity magicShelf = MagicShelfEntity.builder()
                .id(99L)
                .userId(42L)
                .name("Unread PDFs")
                .isPublic(false)
                .filterJson("{\"all\":true}")
                .build();

        when(magicShelfRepository.findAllByUserId(42L)).thenReturn(List.of(magicShelf));
        when(magicShelfRepository.findAllByIsPublicIsTrue()).thenReturn(List.of());
        when(magicShelfBookService.getBookIdsByMagicShelfId(42L, 99L)).thenReturn(List.of());

        List<KoreaderShelfSummary> shelves = service.listShelves("magic");

        assertEquals(1, shelves.size());
        assertEquals("magic", shelves.get(0).getType());
    }

    @Test
    void listShelfBooks_returnsBookHashAndFileInfo() {
        when(shelfRepository.findByIdWithUser(5L)).thenReturn(Optional.of(shelf));
        when(bookRepository.findAllWithMetadataByShelfId(5L)).thenReturn(List.of(book));

        List<KoreaderBookSummary> books = service.listShelfBooks(5L);

        assertEquals(1, books.size());
        assertEquals("hash", books.get(0).getBookHash());
        assertEquals("title.pdf", books.get(0).getFileName());
    }

    @Test
    void listShelfBooks_magicShelf_works() {
        when(magicShelfBookService.getBookIdsByMagicShelfId(42L, 99L)).thenReturn(List.of(1L));
        when(bookRepository.findAllForSummaryByIds(List.of(1L))).thenReturn(List.of(book));

        List<KoreaderBookSummary> books = service.listShelfBooks("magic", 99L);

        assertEquals(1, books.size());
        assertEquals(1L, books.get(0).getBookId());
        assertEquals(10L, books.get(0).getBookFileId());
    }

    @Test
    void listShelfBooks_magicShelf_emptyReturnsEmptyList() {
        when(magicShelfBookService.getBookIdsByMagicShelfId(42L, 99L)).thenReturn(List.of());

        List<KoreaderBookSummary> books = service.listShelfBooks("magic", 99L);

        assertTrue(books.isEmpty());
    }

    @Test
    void listShelfBooks_regularForbidden_throws() {
        BookLoreUserEntity otherUser = new BookLoreUserEntity();
        otherUser.setId(1000L);
        shelf.setUser(otherUser);
        shelf.setPublic(false);

        when(shelfRepository.findByIdWithUser(5L)).thenReturn(Optional.of(shelf));

        APIException ex = assertThrows(APIException.class, () -> service.listShelfBooks("regular", 5L));

        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void listShelfBooks_includesSeriesMetadata() {
        book.getMetadata().setSeriesName("Foundation");
        book.getMetadata().setSeriesNumber(1.0f);
        when(shelfRepository.findByIdWithUser(5L)).thenReturn(Optional.of(shelf));
        when(bookRepository.findAllWithMetadataByShelfId(5L)).thenReturn(List.of(book));

        List<KoreaderBookSummary> books = service.listShelfBooks(5L);

        assertEquals(1, books.size());
        assertEquals("Foundation", books.get(0).getSeriesName());
        assertEquals(1.0f, books.get(0).getSeriesNumber());
    }

    @Test
    void listShelfBooks_seriesFieldsNullWhenNoMetadata() {
        book.setMetadata(null);
        when(shelfRepository.findByIdWithUser(5L)).thenReturn(Optional.of(shelf));
        when(bookRepository.findAllWithMetadataByShelfId(5L)).thenReturn(List.of(book));

        List<KoreaderBookSummary> books = service.listShelfBooks(5L);

        assertEquals(1, books.size());
        assertNull(books.get(0).getSeriesName());
        assertNull(books.get(0).getSeriesNumber());
    }

    @Test
    void downloadBook_delegatesAfterAccessCheck() {
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        ResponseEntity<Resource> expected = ResponseEntity.ok(new ByteArrayResource(new byte[]{1, 2, 3}));
        when(bookDownloadService.downloadBook(1L)).thenReturn(expected);

        ResponseEntity<Resource> response = service.downloadBook(1L);

        assertEquals(expected, response);
        verify(bookDownloadService).downloadBook(1L);
    }

    @Test
    void removeBookFromShelf_removesMembershipOnly() {
        shelf.setUser(reader);
        book.setShelves(new java.util.HashSet<>(Set.of(shelf)));
        when(shelfRepository.findByIdWithUser(5L)).thenReturn(Optional.of(shelf));
        when(bookRepository.findByIdWithBookFiles(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(BookEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoreaderShelfRemovalResponse response = service.removeBookFromShelf(5L, 1L);

        assertTrue(response.isRemoved());
        assertEquals(5L, response.getShelfId());
        assertEquals(1L, response.getBookId());
        assertEquals("regular", response.getShelfType());
        verify(bookRepository).save(any(BookEntity.class));
    }

    @Test
    void removeBookFromMagicShelf_returnsUnsupported() {
        KoreaderShelfRemovalResponse response = service.removeBookFromShelf("magic", 99L, 1L);

        assertFalse(response.isRemoved());
        assertEquals("magic", response.getShelfType());
        assertEquals("unsupported", response.getStatus());
    }

    @Test
    void listShelves_invalidType_throwsBadRequest() {
        APIException ex = assertThrows(APIException.class, () -> service.listShelves("unsupported"));
        assertEquals(400, ex.getStatus().value());
    }
}
