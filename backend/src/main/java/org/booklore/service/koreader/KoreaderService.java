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
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
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
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class KoreaderService {

    private final KoreaderProgressRepository koreaderProgressRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final KoreaderSecurityContextService securityContextService;
    private final HardcoverSyncService hardcoverSyncService;

    public ResponseEntity<Map<String, Object>> authorizeUser() {
        KoreaderSecurityContextService.AuthenticatedReader reader = securityContextService.requireCurrentReader(false);
        log.info("GrimmLink progress sync source=koreader direction=auth apiStatus=ok bookId=null bookFileId=null bookHash=null fileFormat=null currentPage=null totalPages=null percentage=null device=null deviceId=null userId={}",
                reader.userId());
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "username", reader.username(),
                "userId", reader.userId(),
                "syncEnabled", reader.syncEnabled(),
                "syncWithGrimmoryReader", reader.syncWithBookloreReader()
        ));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Book> getBookByHash(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);
        Book mappedBook = bookMapper.toBook(book);
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        log.info("GrimmLink progress sync source=koreader direction=pull apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage=null totalPages=null percentage=null progress=null location=null device=null deviceId=null",
                book.getId(),
                primaryFile != null ? primaryFile.getId() : null,
                primaryFile != null && primaryFile.getCurrentHash() != null ? primaryFile.getCurrentHash() : bookHash,
                primaryFile != null && primaryFile.getBookType() != null ? primaryFile.getBookType().name() : null);
        return ResponseEntity.ok(mappedBook);
    }

    public KoreaderProgress getProgress(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);
        BookFileEntity bookFile = resolveBookFileForHash(book, bookHash);
        KoreaderProgress progress = findProgressRecord(reader.getId(), book.getId(), bookFile != null ? bookFile.getId() : null)
                .map(entity -> KoreaderProgress.builder()
                        .timestamp(entity.getTimestamp() != null ? entity.getTimestamp().getEpochSecond() : null)
                        .updatedAt(entity.getUpdatedAt())
                        .document(entity.getDocument())
                        .bookHash(entity.getBookHash())
                        .bookId(book.getId())
                        .bookFileId(entity.getBookFile() != null ? entity.getBookFile().getId() : (bookFile != null ? bookFile.getId() : null))
                        .currentHash(entity.getCurrentHash())
                        .initialHash(entity.getInitialHash())
                        .source(entity.getSource())
                        .progressVersion(entity.getProgressVersion())
                        .fileFormat(entity.getFileFormat())
                        .progress(entity.getProgress())
                        .location(entity.getLocation())
                        .percentage(entity.getPercentage())
                        .pdfCurrentPage(entity.getCurrentPage())
                        .pdfTotalPages(entity.getTotalPages())
                        .pdfProgressPercent(entity.getPercentage())
                        .currentPage(entity.getCurrentPage())
                        .totalPages(entity.getTotalPages())
                        .device(entity.getDevice())
                        .device_id(entity.getDeviceId())
                        .build())
                .orElseGet(() -> KoreaderProgress.builder()
                        .document(bookHash)
                        .bookHash(bookHash)
                        .bookId(book.getId())
                        .bookFileId(bookFile != null ? bookFile.getId() : null)
                        .currentHash(bookFile != null ? bookFile.getCurrentHash() : null)
                        .initialHash(bookFile != null ? bookFile.getInitialHash() : null)
                        .fileFormat(resolveBookType(bookFile))
                        .build());

        log.info("GrimmLink progress sync source=koreader direction=pull apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} device={} deviceId={}",
                book.getId(),
                progress.getBookFileId(),
                progress.getBookHash(),
                progress.getFileFormat(),
                progress.getCurrentPage(),
                progress.getTotalPages(),
                progress.getPercentage(),
                progress.getProgress(),
                progress.getLocation(),
                progress.getDevice(),
                progress.getDevice_id());
        return progress;
    }

    @Transactional
    public void saveProgress(String bookHash, KoreaderProgress koProgress) {
        if (bookHash == null || bookHash.isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash/document is required");
        }

        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = findAccessibleBookByHash(bookHash, reader);
        BookFileEntity bookFile = resolveBookFileForHash(book, bookHash);

        KoreaderProgressEntity entity = findProgressEntity(reader.getId(), book.getId(), bookFile != null ? bookFile.getId() : null)
                .orElseGet(KoreaderProgressEntity::new);

        Float previousPercentage = entity.getPercentage();
        Float normalizedPercentage = normalizePercentage(koProgress.getPercentage());
        Instant clientTimestamp = normalizeTimestamp(koProgress.getTimestamp());
        validatePageData(koProgress);
        Long nextProgressVersion = entity.getProgressVersion() == null ? 1L : entity.getProgressVersion() + 1L;

        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookFile(bookFile);
        entity.setBookHash(bookHash.trim());
        entity.setDocument(resolveDocument(bookHash, koProgress));
        entity.setCurrentHash(bookFile != null ? bookFile.getCurrentHash() : null);
        entity.setInitialHash(bookFile != null ? bookFile.getInitialHash() : null);
        entity.setFileFormat(resolveFileFormat(bookFile, koProgress));
        entity.setSource(resolveSource(koProgress));
        entity.setProgressVersion(nextProgressVersion);
        entity.setProgress(koProgress.getProgress());
        entity.setLocation(resolveLocation(koProgress));
        entity.setPercentage(normalizedPercentage);
        entity.setCurrentPage(koProgress.getCurrentPage());
        entity.setTotalPages(koProgress.getTotalPages());
        entity.setDevice(koProgress.getDevice());
        entity.setDeviceId(koProgress.getDevice_id());
        entity.setTimestamp(clientTimestamp);

        koreaderProgressRepository.save(entity);

        updateUserBookProgress(reader, book, bookFile, koProgress, normalizedPercentage, clientTimestamp);

        if (!Objects.equals(previousPercentage, normalizedPercentage) && normalizedPercentage != null) {
            hardcoverSyncService.syncProgressToHardcover(book.getId(), normalizedPercentage, reader.getId());
        }

        log.info("GrimmLink progress sync source=koreader direction=push apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} device={} deviceId={} userId={}",
                book.getId(),
                bookFile != null ? bookFile.getId() : null,
                bookFile != null && bookFile.getCurrentHash() != null ? bookFile.getCurrentHash() : bookHash,
                resolveFileFormat(bookFile, koProgress),
                koProgress.getCurrentPage(),
                koProgress.getTotalPages(),
                normalizedPercentage,
                koProgress.getProgress(),
                resolveLocation(koProgress),
                koProgress.getDevice(),
                koProgress.getDevice_id(),
                reader.getId());
    }

    private void updateUserBookProgress(BookLoreUserEntity reader, BookEntity book, BookFileEntity bookFile,
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

        BookFileEntity progressBookFile = bookFile;
        if (progressBookFile != null && koProgress.getCurrentPage() != null && koProgress.getCurrentPage() > 0) {
            if (progressBookFile.getBookType() == BookFileType.PDF) {
                ubp.setPdfProgress(koProgress.getCurrentPage());
                if (normalizedPercentage != null) {
                    ubp.setPdfProgressPercent(normalizedPercentage / 100f);
                }
            }
        }

        if (ubp.getLastReadTime() == null || timestamp.isAfter(ubp.getLastReadTime())) {
            ubp.setLastReadTime(timestamp);
        }

        if (ubp.getReadStatus() == null || ubp.getReadStatus() == ReadStatus.UNREAD) {
            ubp.setReadStatus(ReadStatus.READING);
        }

        userBookProgressRepository.save(ubp);

        updateUserBookFileProgress(reader, bookFile, koProgress, normalizedPercentage, timestamp);
    }

    private void updateUserBookFileProgress(BookLoreUserEntity reader, BookFileEntity bookFile,
                                            KoreaderProgress koProgress, Float normalizedPercentage,
                                            Instant timestamp) {
        if (bookFile == null) {
            return;
        }

        UserBookFileProgressEntity fp = userBookFileProgressRepository
                .findByUserIdAndBookFileId(reader.getId(), bookFile.getId())
                .orElseGet(() -> {
                    UserBookFileProgressEntity e = new UserBookFileProgressEntity();
                    e.setUser(reader);
                    e.setBookFile(bookFile);
                    return e;
                });

        if (bookFile.getBookType() == BookFileType.PDF) {
            if (koProgress.getCurrentPage() != null && koProgress.getCurrentPage() > 0) {
                fp.setPositionData(String.valueOf(koProgress.getCurrentPage()));
            }
        }

        if (normalizedPercentage != null) {
            fp.setProgressPercent(normalizedPercentage / 100f);
        }
        fp.setLastReadTime(timestamp);

        userBookFileProgressRepository.save(fp);
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

    private String resolveFileFormat(BookFileEntity bookFile, KoreaderProgress progress) {
        if (progress.getFileFormat() != null && !progress.getFileFormat().isBlank()) {
            return progress.getFileFormat().trim().toUpperCase();
        }
        return resolveBookType(bookFile);
    }

    private String resolveBookType(BookFileEntity bookFile) {
        return bookFile != null && bookFile.getBookType() != null
                ? bookFile.getBookType().name()
                : null;
    }

    private Optional<KoreaderProgressEntity> findProgressEntity(Long userId, Long bookId, Long bookFileId) {
        if (bookFileId != null) {
            Optional<KoreaderProgressEntity> byBookFile = koreaderProgressRepository.findByUserIdAndBookFileId(userId, bookFileId);
            if (byBookFile != null && byBookFile.isPresent()) {
                return byBookFile;
            }
        }
        Optional<KoreaderProgressEntity> byBookId = koreaderProgressRepository.findByUserIdAndBookId(userId, bookId);
        return byBookId != null ? byBookId : Optional.empty();
    }

    private Optional<KoreaderProgressEntity> findProgressRecord(Long userId, Long bookId, Long bookFileId) {
        return findProgressEntity(userId, bookId, bookFileId);
    }

    private BookFileEntity resolveBookFileForHash(BookEntity book, String bookHash) {
        String normalizedHash = normalizeHash(bookHash);
        if (book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return null;
        }
        return book.getBookFiles().stream()
                .filter(Objects::nonNull)
                .filter(file -> normalizedHash.equals(file.getCurrentHash()) || normalizedHash.equals(file.getInitialHash()))
                .findFirst()
                .orElse(null);
    }

    private String resolveSource(KoreaderProgress progress) {
        if (progress.getSource() != null && !progress.getSource().isBlank()) {
            return progress.getSource().trim().toUpperCase();
        }
        return "KOREADER";
    }
}
