package org.booklore.grimmlink.service;

import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrimmlinkHashMatcherTest {

    @Mock private BookRepository bookRepository;

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
        book1.setBookFiles(List.of(file1));

        book2 = new BookEntity();
        book2.setId(200L);
        book2.setLibrary(library);

        BookFileEntity file2 = new BookFileEntity();
        file2.setId(2L);
        file2.setCurrentHash("current-hash-2");
        file2.setInitialHash("initial-hash-2");
        file2.setBookType(BookFileType.PDF);
        file2.setBook(book2);
        book2.setBookFiles(List.of(file2));
    }

    @Test
    void resolveByCurrentHash_exactMatch() {
        when(bookRepository.findByCurrentHash("current-hash-1")).thenReturn(Optional.of(book1));

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "current-hash-1");

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void resolveByInitialHash_fallback() {
        when(bookRepository.findByCurrentHash("initial-hash-2")).thenReturn(Optional.empty());
        when(bookRepository.findAllByBookHash("initial-hash-2")).thenReturn(List.of(book2));

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "initial-hash-2");

        assertNotNull(result);
        assertEquals(200L, result.getId());
    }

    @Test
    void resolveByAccessibleCandidate_fallback() {
        when(bookRepository.findByCurrentHash("unknown-hash")).thenReturn(Optional.empty());
        when(bookRepository.findAllByBookHash("unknown-hash")).thenReturn(List.of(book1));

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "unknown-hash");

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void throwsNotFound_whenNoMatch() {
        when(bookRepository.findByCurrentHash("nonexistent")).thenReturn(Optional.empty());
        when(bookRepository.findAllByBookHash("nonexistent")).thenReturn(List.of());

        assertThrows(APIException.class, () ->
                hashMatcher.resolveAccessibleBookByHash(reader, "nonexistent"));
    }

    @Test
    void prefersCurrentHashOverInitialHash() {
        // book1 has currentHash="current-hash-1"
        // but both books could match "hash-to-find"
        // book2 would match via initialHash but book1 should win via currentHash

        BookFileEntity file2b = new BookFileEntity();
        file2b.setId(3L);
        file2b.setCurrentHash("other-hash");
        file2b.setInitialHash("hash-to-find");
        file2b.setBookType(BookFileType.PDF);
        file2b.setBook(book2);
        book2.setBookFiles(List.of(file2b));

        when(bookRepository.findByCurrentHash("hash-to-find")).thenReturn(Optional.of(book1));

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
        restrictedBook.setBookFiles(List.of(file));

        when(bookRepository.findByCurrentHash("restricted-hash")).thenReturn(Optional.of(restrictedBook));

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
        anyBook.setBookFiles(List.of(file));

        // Admin permission
        UserPermissionsEntity adminPerm = new UserPermissionsEntity();
        adminPerm.setPermissionAdmin(true);
        reader.setPermissions(adminPerm);

        when(bookRepository.findByCurrentHash("admin-hash")).thenReturn(Optional.of(anyBook));

        BookEntity result = hashMatcher.resolveAccessibleBookByHash(reader, "admin-hash");

        assertNotNull(result);
        assertEquals(400L, result.getId());
    }
}
