package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.BookRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared helper for resolving a book by hash with explicit preference:
 * currentHash > initialHash > accessible candidate fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrimmlinkHashMatcher {

    private final BookRepository bookRepository;

    /**
     * Resolve an accessible book by hash.
     * <p>
     * Priority:
     * <ol>
     *   <li>Exact currentHash match</li>
     *   <li>initialHash match among candidates</li>
     *   <li>First accessible candidate from findAllByBookHash</li>
     * </ol>
     *
     * @param reader  the requesting user (for access check)
     * @param bookHash the hash to resolve
     * @return the matching BookEntity
     * @throws ApiError if no accessible book is found
     */
    public BookEntity resolveAccessibleBookByHash(BookLoreUserEntity reader, String bookHash) {
        // 1. Try currentHash first
        BookEntity book = bookRepository.findByCurrentHash(bookHash).orElse(null);
        if (book != null) {
            if (canAccess(reader, book)) {
                return book;
            }
            log.debug("currentHash match found but not accessible for user {}", reader.getId());
        }

        // 2. Fallback to initialHash among candidates
        List<BookEntity> candidates = bookRepository.findAllByBookHash(bookHash);
        if (candidates != null && !candidates.isEmpty()) {
            // Try to find a candidate where the bookHash matches initialHash
            for (BookEntity candidate : candidates) {
                if (candidate.getPrimaryBookFile() != null
                        && bookHash.equals(candidate.getPrimaryBookFile().getInitialHash())
                        && canAccess(reader, candidate)) {
                    return candidate;
                }
            }
            // 3. Any accessible candidate
            for (BookEntity candidate : candidates) {
                if (canAccess(reader, candidate)) {
                    return candidate;
                }
            }
        }

        throw ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash);
    }

    private boolean canAccess(BookLoreUserEntity reader, BookEntity book) {
        return reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()
                || reader.getLibraries().stream().anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }
}
