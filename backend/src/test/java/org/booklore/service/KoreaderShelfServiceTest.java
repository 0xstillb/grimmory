package org.booklore.service;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.exception.APIException;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.koreader.KoreaderSecurityContextService;
import org.booklore.service.koreader.KoreaderShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KoreaderShelfServiceTest {

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookDownloadService bookDownloadService;

    @Mock
    private KoreaderSecurityContextService securityContextService;

    @InjectMocks
    private KoreaderShelfService service;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void removeBookFromShelf_removesShelfMembershipOnly() {
        BookLoreUserEntity reader = adminReader(1L);
        ShelfEntity shelf = ShelfEntity.builder().id(10L).user(reader).name("Reading").build();
        BookEntity book = BookEntity.builder()
                .id(20L)
                .library(LibraryEntity.builder().id(30L).name("Main").build())
                .shelves(new HashSet<>(java.util.Set.of(shelf)))
                .build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));
        when(bookRepository.findByIdWithBookFiles(20L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(BookEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KoreaderShelfRemovalResponse response = service.removeBookFromShelf(10L, 20L);

        assertEquals(10L, response.shelfId());
        assertEquals(20L, response.bookId());
        assertTrue(response.removedFromShelf());
        assertFalse(response.deletedFromLibrary());
        assertTrue(book.getShelves().isEmpty());
        verify(bookRepository).save(book);
        verify(bookRepository, never()).delete(any(BookEntity.class));
        verify(bookRepository, never()).deleteById(anyLong());
    }

    @Test
    void downloadBook_successDelegatesToBookDownloadService() {
        BookLoreUserEntity reader = adminReader(1L);
        BookEntity book = BookEntity.builder().id(20L).library(LibraryEntity.builder().id(30L).name("Main").build()).build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(20L)).thenReturn(Optional.of(book));
        when(bookDownloadService.downloadBook(20L)).thenReturn(ResponseEntity.ok(mock(Resource.class)));

        ResponseEntity<Resource> response = service.downloadBook(20L);

        assertEquals(200, response.getStatusCode().value());
        verify(bookDownloadService).downloadBook(20L);
    }

    @Test
    void removeBookFromShelf_forbiddenWhenShelfNotAccessible() {
        BookLoreUserEntity reader = regularReader(1L);
        ShelfEntity shelf = ShelfEntity.builder().id(10L).user(adminReader(2L)).name("Reading").build();

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(shelfRepository.findByIdWithUser(10L)).thenReturn(Optional.of(shelf));

        APIException exception = assertThrows(APIException.class, () -> service.removeBookFromShelf(10L, 20L));
        assertEquals(ApiError.FORBIDDEN.getStatus(), exception.getStatus());
    }

    private BookLoreUserEntity adminReader(Long id) {
        BookLoreUserEntity reader = regularReader(id);
        reader.setPermissions(UserPermissionsEntity.builder().permissionAdmin(true).build());
        return reader;
    }

    private BookLoreUserEntity regularReader(Long id) {
        BookLoreUserEntity reader = BookLoreUserEntity.builder()
                .id(id)
                .username("reader" + id)
                .name("Reader " + id)
                .build();
        reader.setLibraries(new HashSet<>(java.util.Set.of(LibraryEntity.builder().id(30L).name("Main").build())));
        return reader;
    }
}
