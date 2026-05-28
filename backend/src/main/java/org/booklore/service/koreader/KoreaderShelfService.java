package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.model.entity.AuthorEntity;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.MagicShelfEntity;
import org.booklore.model.entity.ShelfEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.MagicShelfRepository;
import org.booklore.repository.ShelfRepository;
import org.booklore.service.book.BookDownloadService;
import org.booklore.service.opds.MagicShelfBookService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class KoreaderShelfService {

    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final BookRepository bookRepository;
    private final BookDownloadService bookDownloadService;
    private final MagicShelfBookService magicShelfBookService;
    private final KoreaderSecurityContextService securityContextService;

    public List<KoreaderShelfSummary> listShelves() {
        return listShelves(null);
    }

    public List<KoreaderShelfSummary> listShelves(String typeFilter) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        String normalizedType = normalizeShelfTypeOrNull(typeFilter);

        List<KoreaderShelfSummary> summaries = new java.util.ArrayList<>();
        if (normalizedType == null || "regular".equals(normalizedType)) {
            summaries.addAll(shelfRepository.findByUserIdOrPublicShelfTrue(reader.getId()).stream()
                    .filter(shelf -> canReadShelf(reader, shelf))
                    .map(shelf -> KoreaderShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("regular")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(shelf.getBookCount())
                            .build())
                    .toList());
        }

        if (normalizedType == null || "magic".equals(normalizedType)) {
            List<MagicShelfEntity> magicShelves = new java.util.ArrayList<>(magicShelfRepository.findAllByUserId(reader.getId()));
            Map<Long, MagicShelfEntity> seen = new HashMap<>();
            for (MagicShelfEntity shelf : magicShelves) {
                seen.put(shelf.getId(), shelf);
            }
            for (MagicShelfEntity shelf : magicShelfRepository.findAllByIsPublicIsTrue()) {
                if (!seen.containsKey(shelf.getId())) {
                    magicShelves.add(shelf);
                }
            }

            summaries.addAll(magicShelves.stream()
                    .filter(shelf -> canReadMagicShelf(reader, shelf))
                    .map(shelf -> KoreaderShelfSummary.builder()
                            .id(shelf.getId())
                            .name(shelf.getName())
                            .type("magic")
                            .visibility(shelf.isPublic() ? "public" : "personal")
                            .bookCount(countMagicShelfBooks(reader, shelf.getId()))
                            .description("Rule-based Magic Shelf")
                            .build())
                    .toList());
        }

        return summaries;
    }

    public List<KoreaderBookSummary> listShelfBooks(Long shelfId) {
        return listShelfBooks("regular", shelfId);
    }

    public List<KoreaderBookSummary> listShelfBooks(String shelfType, Long shelfId) {
        String normalizedType = normalizeShelfType(shelfType);
        if ("magic".equals(normalizedType)) {
            return listMagicShelfBooks(shelfId);
        }
        return listRegularShelfBooks(shelfId);
    }

    private List<KoreaderBookSummary> listRegularShelfBooks(Long shelfId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);

        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!canReadShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException("Shelf is not accessible to the authenticated user");
        }

        return bookRepository.findAllWithMetadataByShelfId(shelfId).stream()
                .filter(book -> canAccessBook(reader, book))
                .map(this::toBookSummary)
                .toList();
    }

    private List<KoreaderBookSummary> listMagicShelfBooks(Long shelfId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        List<Long> bookIds = magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId);
        if (bookIds == null || bookIds.isEmpty()) {
            return List.of();
        }

        List<BookEntity> books = bookRepository.findAllForSummaryByIds(bookIds);
        Map<Long, BookEntity> booksById = books.stream().collect(Collectors.toMap(BookEntity::getId, b -> b));
        List<KoreaderBookSummary> result = new java.util.ArrayList<>();
        for (Long bookId : bookIds) {
            BookEntity book = booksById.get(bookId);
            if (book != null && canAccessBook(reader, book)) {
                result.add(toBookSummary(book));
            }
        }
        return result;
    }

    public ResponseEntity<Resource> downloadBook(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return bookDownloadService.downloadBook(bookId);
    }

    @Transactional
    public KoreaderShelfRemovalResponse removeBookFromShelf(Long shelfId, Long bookId) {
        return removeBookFromShelf("regular", shelfId, bookId);
    }

    @Transactional
    public KoreaderShelfRemovalResponse removeBookFromShelf(String shelfType, Long shelfId, Long bookId) {
        String normalizedType = normalizeShelfType(shelfType);
        if ("magic".equals(normalizedType)) {
            return KoreaderShelfRemovalResponse.builder()
                    .shelfId(shelfId)
                    .bookId(bookId)
                    .shelfType("magic")
                    .removed(false)
                    .status("unsupported")
                    .message("Magic Shelf is rule-based and cannot be manually removed from")
                    .build();
        }

        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);

        ShelfEntity shelf = shelfRepository.findByIdWithUser(shelfId)
                .orElseThrow(() -> ApiError.SHELF_NOT_FOUND.createException(shelfId));
        if (!canModifyShelf(reader, shelf)) {
            throw ApiError.FORBIDDEN.createException("Shelf membership can only be modified by the shelf owner or an admin");
        }

        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }

        boolean removed = book.getShelves().removeIf(existingShelf -> existingShelf.getId().equals(shelfId));
        if (removed) {
            bookRepository.save(book);
        }
        return KoreaderShelfRemovalResponse.builder()
                .shelfId(shelfId)
                .bookId(bookId)
                .shelfType("regular")
                .removed(removed)
                .status(removed ? "removed" : "noop")
                .message(removed
                        ? "Shelf membership removed"
                        : "Book is not currently in this shelf")
                .build();
    }

    private KoreaderBookSummary toBookSummary(BookEntity book) {
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        String title = book.getMetadata() != null ? book.getMetadata().getTitle() : null;
        String author = resolveAuthor(book);
        return KoreaderBookSummary.builder()
                .bookId(book.getId())
                .bookFileId(primaryFile != null ? primaryFile.getId() : null)
                .title(title)
                .author(author)
                .fileName(primaryFile != null ? primaryFile.getFileName() : null)
                .originalFileName(primaryFile != null ? primaryFile.getFileName() : null)
                .extension(resolveExtension(primaryFile != null ? primaryFile.getFileName() : null))
                .fileFormat(primaryFile != null && primaryFile.getBookType() != null ? primaryFile.getBookType().name() : null)
                .fileSizeKb(primaryFile != null ? primaryFile.getFileSizeKb() : null)
                .fileSize(primaryFile != null && primaryFile.getFileSizeKb() != null ? primaryFile.getFileSizeKb() * 1024 : null)
                .bookHash(resolveHash(primaryFile))
                .seriesName(book.getMetadata() != null ? book.getMetadata().getSeriesName() : null)
                .seriesNumber(book.getMetadata() != null ? book.getMetadata().getSeriesNumber() : null)
                .build();
    }

    private String resolveExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveAuthor(BookEntity book) {
        if (book.getMetadata() == null || book.getMetadata().getAuthors() == null || book.getMetadata().getAuthors().isEmpty()) {
            return null;
        }
        return book.getMetadata().getAuthors().stream()
                .map(AuthorEntity::getName)
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String resolveHash(BookFileEntity primaryFile) {
        if (primaryFile == null) {
            return null;
        }
        if (primaryFile.getCurrentHash() != null && !primaryFile.getCurrentHash().isBlank()) {
            return primaryFile.getCurrentHash();
        }
        return primaryFile.getInitialHash();
    }

    private boolean canReadShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return isAdmin(reader) || shelf.isPublic() || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canModifyShelf(BookLoreUserEntity reader, ShelfEntity shelf) {
        return isAdmin(reader) || shelf.getUser().getId().equals(reader.getId());
    }

    private boolean canReadMagicShelf(BookLoreUserEntity reader, MagicShelfEntity shelf) {
        return isAdmin(reader) || shelf.isPublic() || shelf.getUserId().equals(reader.getId());
    }

    private Integer countMagicShelfBooks(BookLoreUserEntity reader, Long shelfId) {
        try {
            return magicShelfBookService.getBookIdsByMagicShelfId(reader.getId(), shelfId).size();
        } catch (Exception ex) {
            log.debug("Unable to resolve magic shelf count for shelf {}: {}", shelfId, ex.getMessage());
            return null;
        }
    }

    private String normalizeShelfTypeOrNull(String shelfType) {
        if (shelfType == null || shelfType.isBlank()) {
            return null;
        }
        return normalizeShelfType(shelfType);
    }

    private String normalizeShelfType(String shelfType) {
        String normalized = shelfType == null ? "regular" : shelfType.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "regular";
        }
        if (!"regular".equals(normalized) && !"magic".equals(normalized)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Unsupported shelf type: " + shelfType);
        }
        return normalized;
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        return isAdmin(reader) || reader.getLibraries().stream().anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private boolean isAdmin(BookLoreUserEntity reader) {
        return reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin();
    }
}
