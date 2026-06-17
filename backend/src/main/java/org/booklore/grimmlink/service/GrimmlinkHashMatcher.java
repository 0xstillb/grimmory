package org.booklore.grimmlink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.exception.ApiError;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.springframework.stereotype.Component;

/**
 * Shared helper for resolving a book by hash with explicit preference:
 * currentHash > initialHash > accessible candidate fallback.
 * <p>
 * Delegates to {@link GrimmLinkBookMatchService} for the actual matching logic,
 * keeping this class as a thin adapter to minimize disruption for callers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrimmlinkHashMatcher {

    private final GrimmLinkBookMatchService grimmLinkBookMatchService;

    /**
     * Resolve an accessible book by hash.
     *
     * @param reader  the requesting user (for access check)
     * @param bookHash the hash to resolve
     * @return the matching BookEntity
     * @throws ApiError if no accessible book is found
     */
    public BookEntity resolveAccessibleBookByHash(BookLoreUserEntity reader, String bookHash) {
        try {
            return grimmLinkBookMatchService.resolveAccessibleBookByHash(reader, bookHash);
        } catch (APIException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected error resolving book by hash {} for user {}", bookHash, reader.getId(), e);
            throw ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + bookHash);
        }
    }
}
