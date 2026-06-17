package org.booklore.grimmlink.service;

import org.booklore.exception.APIException;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.BookFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrimmlinkHashMatcherTest {

    @Mock private GrimmLinkBookMatchService grimmLinkBookMatchService;

    @InjectMocks
    private GrimmlinkHashMatcher hashMatcher;

    private BookLoreUserEntity reader;
    private BookEntity book1;
    private BookEntity book2;
    private LibraryEntity library;

    @BeforeEach
    void setUp() {
        library = new LibraryEntity();
        library.setId(10L);

        reader = new BookLoreUserEntity();
        reader.setId(1L);
        reader.setLibraries(Set.of(library));

        book1 = new BookEntity();
        book1.setId(100L);
        book1.setLibrary(library);

        BookFileEntity file1 = new BookFileEntity();
        file1.setId(1L);
        file1.setCurrentHash("current-hash-1");
        file1.setInitialHash("initial-hash-1");
        file1.setBookType(BookFileType.EPUB);
        file1.setBook(book1);
        book1.setBookFiles(java.util.List.of(file1));

        book2 = new BookEntity();
        book2.setId(200L);
        book2.setLibrary(library);

        BookFileEntity file2 = new BookFileEntity();
        file2.setId(2L);
        file2.setCurrentHash("current-hash-2");
        file2.setInitialHash("initial-hash-2");
        file2.setBookType(BookFileType.PDF);
        file2.setBook(book2);
        book2.setBookFiles(java.util.List.of(file2));
    }

    @Test
    void resolveByCurrentHash_exactMatch() {
        when(grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, "current-hash-1")).thenReturn(book1);

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "current-hash-1");

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void throwsNotFound_whenNoMatch() {
        when(grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, "nonexistent"))
                .thenThrow(APIException.class);

        assertThrows(APIException.class, () ->
                hashMatcher.resolveAccessibleBookByHash(reader, "nonexistent"));
    }

    @Test
    void prefersCurrentHashOverInitialHash() {
        when(grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, "hash-to-find")).thenReturn(book1);

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "hash-to-find");

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void throwForbidden_whenNotAccessible() {
        LibraryEntity otherLibrary = new LibraryEntity();
        otherLibrary.setId(999L);

        BookEntity restrictedBook = new BookEntity();
        restrictedBook.setId(300L);
        restrictedBook.setLibrary(otherLibrary);

        BookFileEntity file = new BookFileEntity();
        file.setId(5L);
        file.setCurrentHash("restricted-hash");
        file.setBookType(BookFileType.EPUB);
        file.setBook(restrictedBook);
        restrictedBook.setBookFiles(java.util.List.of(file));

        when(grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, "restricted-hash"))
                .thenThrow(APIException.class);

        assertThrows(APIException.class, () ->
                hashMatcher.resolveAccessibleBookByHash(reader, "restricted-hash"));
    }

    @Test
    void adminUserCanAccessAnyBook() {
        LibraryEntity otherLibrary = new LibraryEntity();
        otherLibrary.setId(999L);

        BookEntity anyBook = new BookEntity();
        anyBook.setId(400L);
        anyBook.setLibrary(otherLibrary);

        BookFileEntity file = new BookFileEntity();
        file.setId(5L);
        file.setCurrentHash("admin-hash");
        file.setBookType(BookFileType.EPUB);
        file.setBook(anyBook);
        anyBook.setBookFiles(java.util.List.of(file));

        // Admin permission
        UserPermissionsEntity adminPerm = new UserPermissionsEntity();
        adminPerm.setPermissionAdmin(true);
        reader.setPermissions(adminPerm);

        when(grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, "admin-hash")).thenReturn(anyBook);

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "admin-hash");

        assertNotNull(result);
        assertEquals(400L, result.getId());
    }
}
