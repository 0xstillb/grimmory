package org.booklore.controller;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.service.koreader.KoreaderShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KoreaderShelfControllerTest {

    @Mock
    private KoreaderShelfService koreaderShelfService;

    @InjectMocks
    private KoreaderShelfController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listShelves_returnsShelfList() {
        List<KoreaderShelfSummary> shelves = List.of(
                KoreaderShelfSummary.builder().id(1L).name("Reading").type("PERSONAL").bookCount(3).build(),
                KoreaderShelfSummary.builder().id(2L).name("Shared").type("PUBLIC").bookCount(5).build()
        );
        when(koreaderShelfService.listShelves()).thenReturn(shelves);

        ResponseEntity<List<KoreaderShelfSummary>> resp = controller.listShelves();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().size());
        assertEquals("Reading", resp.getBody().get(0).getName());
        assertEquals("PUBLIC", resp.getBody().get(1).getType());
    }

    @Test
    void listShelves_serviceThrowsForbidden_propagates() {
        when(koreaderShelfService.listShelves())
                .thenThrow(ApiError.FORBIDDEN.createException("Not authorized"));

        assertThrows(RuntimeException.class, () -> controller.listShelves());
    }

    @Test
    void listShelfBooks_success() {
        List<KoreaderBookSummary> books = List.of(
                KoreaderBookSummary.builder()
                        .bookId(42L).title("Dune").author("Frank Herbert")
                        .fileName("Dune.epub").fileFormat("EPUB").fileSizeKb(512L)
                        .bookHash("abc123").build()
        );
        when(koreaderShelfService.listShelfBooks(1L)).thenReturn(books);

        ResponseEntity<List<KoreaderBookSummary>> resp = controller.listShelfBooks(1L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        assertEquals(42L, resp.getBody().get(0).getBookId());
        assertEquals("abc123", resp.getBody().get(0).getBookHash());
    }

    @Test
    void listShelfBooks_shelfNotFound_propagates() {
        when(koreaderShelfService.listShelfBooks(99L))
                .thenThrow(ApiError.SHELF_NOT_FOUND.createException(99L));

        assertThrows(RuntimeException.class, () -> controller.listShelfBooks(99L));
    }

    @Test
    void listShelfBooks_forbidden_propagates() {
        when(koreaderShelfService.listShelfBooks(5L))
                .thenThrow(ApiError.FORBIDDEN.createException("Shelf not accessible"));

        assertThrows(RuntimeException.class, () -> controller.listShelfBooks(5L));
    }

    @Test
    void downloadBook_success() {
        Resource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(koreaderShelfService.downloadBook(42L))
                .thenReturn(ResponseEntity.ok(resource));

        ResponseEntity<Resource> resp = controller.downloadBook(42L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    void downloadBook_forbidden_propagates() {
        when(koreaderShelfService.downloadBook(42L))
                .thenThrow(ApiError.FORBIDDEN.createException("Book not accessible"));

        assertThrows(RuntimeException.class, () -> controller.downloadBook(42L));
    }

    @Test
    void downloadBook_notFound_propagates() {
        when(koreaderShelfService.downloadBook(999L))
                .thenThrow(ApiError.BOOK_NOT_FOUND.createException(999L));

        assertThrows(RuntimeException.class, () -> controller.downloadBook(999L));
    }

    @Test
    void removeBookFromShelf_success() {
        KoreaderShelfRemovalResponse responseBody = new KoreaderShelfRemovalResponse(10L, 20L, true, false);
        when(koreaderShelfService.removeBookFromShelf(10L, 20L)).thenReturn(responseBody);

        ResponseEntity<KoreaderShelfRemovalResponse> resp = controller.removeBookFromShelf(10L, 20L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().removedFromShelf());
        assertFalse(resp.getBody().deletedFromLibrary());
    }
}
