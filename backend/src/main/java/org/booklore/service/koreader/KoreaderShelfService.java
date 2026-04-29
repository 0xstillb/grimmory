package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.book.BookDownloadService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class KoreaderShelfService {

    private final ShelfRepository shelfRepository;
    private final BookRepository bookRepository;
    private final BookDownloadService bookDownloadService;
    private final KoreaderSecurityContextService securityContextService;

    public List<KoreaderShelfSummary> listShelves() {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        return shelfRepository.findByUserIdOrPublicShelfTrue(reader.getId()).stream()
                .map(shelf -> KoreaderShelfSummary.builder()
                        .id(shelf.getId())
                        .name(shelf.getName())
                        .type(shelf.isPublic() ? "PUBLIC" : "PERSONAL")
                        .bookCount(shelf.getBookCount())
                        .build())
                .toList();
    }

    public List<KoreaderBookSummary> listShelfBooks(Long shelfId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);

        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));

        if (!canReadShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException("Shelf is not accessible to the authenticated user");
        }

        return bookRepository.findAllWithMetadataByShelfId(shelfId).stream()
                .filter(book -> canAccessBook(reader, book))
                .map(book -> toBookSummary(book))
                .toList();
    }

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));

        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }

        log.info("GrimmLink shelf download: userId={} bookId={}", reader.getId(), bookId);
        return bookDownloadService.downloadBook(bookId);
    }

    private KoreaderBookSummary toBookSummary(BookEntity book) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        String title = book.getMetadata() != null ? book.getMetadata().getTitle() : null;
        String author = resolveAuthor(book);
        String fileName = primaryFile != null ? primaryFile.getFileName() : null;
        String fileFormat = primaryFile != null && primaryFile.getBookType() != null
                ? primaryFile.getBookType().name() : null;
        Long fileSizeKb = primaryFile != null ? primaryFile.getFileSizeKb() : null;
        String bookHash = resolveHash(primaryFile);

        return KoreaderBookSummary.builder()
                .bookId(book.getId())
                .title(title)
                .author(author)
                .fileName(fileName)
                .fileFormat(fileFormat)
                .fileSizeKb(fileSizeKb)
                .bookHash(bookHash)
                .build();
    }

    private String resolveAuthor(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null
                || book.getMetadata().getAuthors().isEmpty()) {
            return null;
        }
        return book.getMetadata().getAuthors().stream()
                .map(AuthorEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String resolveHash(BookFileEntity primaryFile) {
        if (primaryFile == null) return null;
        if (primaryFile.getCurrentHash() != null && !primaryFile.getCurrentHash().isBlank()) {
            return primaryFile.getCurrentHash();
        }
        return primaryFile.getInitialHash();
    }

    private boolean canReadShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        if (isAdmin(reader)) return true;
        return shelf.isPublic() || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (isAdmin(reader)) return true;
        return reader.getLibraries().stream()
                .anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private boolean isAdmin(BookLoreUserEntity reader) {
        return reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin();
    }
}
