package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.*;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.service.hardcover.HardcoverSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class KoreaderService {

    private final KoreaderProgressRepository koreaderProgressRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final KoreaderSecurityContextService securityContextService;
    private final HardcoverSyncService hardcoverSyncService;

    public ResponseEntity<Map<String, Object>> authorizeUser() {
        KoreaderSecurityContextService.AuthenticatedReader reader = securityContextService.requireCurrentReader(false);
        log.info("User '{}' authorized for GrimmLink", reader.username());
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "username", reader.username(),
                "userId", reader.userId(),
                "syncEnabled", reader.syncEnabled(),
                "syncWithGrimmoryReader", false
        ));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Book> getBookByHash(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);
        Book mappedBook = bookMapper.toBook(book);
        return ResponseEntity.ok(mappedBook);
    }

    public KoreaderProgress getProgress(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);

        return koreaderProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .map(entity -> KoreaderProgress.builder()
                        .timestamp(entity.getTimestamp() != null ? entity.getTimestamp().getEpochSecond() : null)
                        .document(entity.getDocument())
                        .bookHash(entity.getBookHash())
                        .bookId(book.getId())
                        .fileFormat(entity.getFileFormat())
                        .progress(entity.getProgress())
                        .location(entity.getLocation())
                        .percentage(entity.getPercentage())
                        .currentPage(entity.getCurrentPage())
                        .totalPages(entity.getTotalPages())
                        .device(entity.getDevice())
                        .device_id(entity.getDeviceId())
                        .build())
                .orElseGet(() -> KoreaderProgress.builder()
                        .document(bookHash)
                        .bookHash(bookHash)
                        .bookId(book.getId())
                        .fileFormat(resolveBookType(book))
                        .build());
    }

    @Transactional
    public void saveProgress(String bookHash, KoreaderProgress koProgress) {
        if (bookHash == null || bookHash.isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash/document is required");
        }

        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);

        KoreaderProgressEntity entity = koreaderProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(KoreaderProgressEntity::new);

        Float previousPercentage = entity.getPercentage();
        Float normalizedPercentage = normalizePercentage(koProgress.getPercentage());
        Instant clientTimestamp = normalizeTimestamp(koProgress.getTimestamp());
        validatePageData(koProgress);

        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookHash(bookHash.trim());
        entity.setDocument(resolveDocument(bookHash, koProgress));
        entity.setFileFormat(resolveFileFormat(book, koProgress));
        entity.setProgress(koProgress.getProgress());
        entity.setLocation(resolveLocation(koProgress));
        entity.setPercentage(normalizedPercentage);
        entity.setCurrentPage(koProgress.getCurrentPage());
        entity.setTotalPages(koProgress.getTotalPages());
        entity.setDevice(koProgress.getDevice());
        entity.setDeviceId(koProgress.getDevice_id());
        entity.setTimestamp(clientTimestamp);

        koreaderProgressRepository.save(entity);

        updateUserBookProgress(reader, book, koProgress, normalizedPercentage, clientTimestamp);

        if (!Objects.equals(previousPercentage, normalizedPercentage) && normalizedPercentage != null) {
            hardcoverSyncService.syncProgressToHardcover(book.getId(), normalizedPercentage, reader.getId());
        }

        log.info("Saved GrimmLink progress for userId={} bookId={} hash={} percentage={}",
                reader.getId(), book.getId(), bookHash, normalizedPercentage);
    }

    private void updateUserBookProgress(BookLoreUserEntity reader, BookEntity book,
                                        KoreaderProgress koProgress, Float normalizedPercentage,
                                        Instant timestamp) {
        UserBookProgressEntity ubp = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(() -> {
                    UserBookProgressEntity e = new UserBookProgressEntity();
                    e.setUser(reader);
                    e.setBook(book);
                    return e;
                });

        if (normalizedPercentage != null) {
            ubp.setKoreaderProgressPercent(normalizedPercentage / 100f);
        }
        ubp.setKoreaderProgress(koProgress.getProgress());
        ubp.setKoreaderDevice(koProgress.getDevice());
        ubp.setKoreaderDeviceId(koProgress.getDevice_id());
        ubp.setKoreaderLastSyncTime(timestamp);

        if (ubp.getLastReadTime() == null || timestamp.isAfter(ubp.getLastReadTime())) {
            ubp.setLastReadTime(timestamp);
        }

        if (ubp.getReadStatus() == null || ubp.getReadStatus() == ReadStatus.UNREAD) {
            ubp.setReadStatus(ReadStatus.READING);
        }

        userBookProgressRepository.save(ubp);
    }

    private BookEntity findAccessibleBookByHash(String bookHash, BookLoreUserEntity reader) {
        String normalizedHash = normalizeHash(bookHash);
        BookEntity book = bookRepository.findByCurrentOrInitialHash(normalizedHash)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + normalizedHash));

        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) {
            return true;
        }
        return reader.getLibraries().stream()
                .anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private String normalizeHash(String bookHash) {
        if (bookHash == null || bookHash.isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Book hash is required");
        }
        return bookHash.trim();
    }

    private Float normalizePercentage(Float percentage) {
        if (percentage == null) {
            return null;
        }
        if (percentage < 0.0f) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Progress percentage cannot be negative");
        }
        if (percentage <= 1.0f) {
            return Math.round((percentage * 100.0f) * 10.0f) / 10.0f;
        }
        if (percentage > 100.0f) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Progress percentage cannot exceed 100");
        }
        return Math.round(percentage * 10.0f) / 10.0f;
    }

    private Instant normalizeTimestamp(Long timestamp) {
        if (timestamp == null) {
            return Instant.now();
        }
        return Instant.ofEpochSecond(timestamp);
    }

    private void validatePageData(KoreaderProgress progress) {
        if (progress.getCurrentPage() != null && progress.getCurrentPage() < 0) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("currentPage cannot be negative");
        }
        if (progress.getTotalPages() != null && progress.getTotalPages() < 0) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("totalPages cannot be negative");
        }
        if (progress.getCurrentPage() != null && progress.getTotalPages() != null
                && progress.getCurrentPage() > progress.getTotalPages()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("currentPage cannot exceed totalPages");
        }
    }

    private String resolveDocument(String bookHash, KoreaderProgress progress) {
        if (progress.getDocument() != null && !progress.getDocument().isBlank()) {
            return progress.getDocument().trim();
        }
        return bookHash.trim();
    }

    private String resolveLocation(KoreaderProgress progress) {
        if (progress.getLocation() != null && !progress.getLocation().isBlank()) {
            return progress.getLocation();
        }
        return progress.getProgress();
    }

    private String resolveFileFormat(BookEntity book, KoreaderProgress progress) {
        if (progress.getFileFormat() != null && !progress.getFileFormat().isBlank()) {
            return progress.getFileFormat().trim().toUpperCase();
        }
        return resolveBookType(book);
    }

    private String resolveBookType(BookEntity book) {
        BookFileEntity primaryBookFile = book.getPrimaryBookFile();
        return primaryBookFile != null && primaryBookFile.getBookType() != null
                ? primaryBookFile.getBookType().name()
                : null;
    }
}
