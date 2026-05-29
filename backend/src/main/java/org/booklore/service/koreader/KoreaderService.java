package org.booklore.service.koreader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.mapper.BookMapper;
import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderUserEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.model.enums.ReadStatus;
import org.booklore.repository.BookRepository;
import org.booklore.repository.KoreaderUserRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.UserRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
@Service
public class KoreaderService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final UserRepository userRepository;
    private final KoreaderUserRepository koreaderUserRepository;
    private final UserBookProgressRepository progressRepository;
    private final UserBookFileProgressRepository fileProgressRepository;
    private final KoreaderProgressRepository koreaderProgressRepository;
    private final KoreaderSecurityContextService securityContextService;
    private static final int LARGE_BOOK_PAGE_COUNT_THRESHOLD = 1000;
    private static final int LARGE_BOOK_READING_PAGE_THRESHOLD = 3;
    private static final List<ReadStatus> SUPPORTED_MANUAL_READ_STATUSES = List.of(
            ReadStatus.UNREAD,
            ReadStatus.READING,
            ReadStatus.READ,
            ReadStatus.PAUSED,
            ReadStatus.ABANDONED,
            ReadStatus.RE_READING
    );

    public ResponseEntity<Map<String, Object>> authorizeUser() {
        KoreaderUserDetails authDetails = getAuthDetails();
        KoreaderUserEntity koreaderUser = findKoreaderUser(authDetails.getUsername());
        validatePassword(koreaderUser, authDetails);

        if (koreaderUser.getBookLoreUser() == null) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("KOReader user is not linked to a Grimmory user");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("username", koreaderUser.getUsername());
        response.put("userId", koreaderUser.getBookLoreUser().getId());
        response.put("syncEnabled", koreaderUser.isSyncEnabled());
        response.put("syncWithWebReader", koreaderUser.isSyncWithWebReader());
        return ResponseEntity.ok(response);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Book> getBookByHash(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        ResolvedBookMatch match = resolveAccessibleBookMatchByHash(bookHash, reader);
        return ResponseEntity.ok(bookMapper.toBook(match.book()));
    }

    @Transactional(readOnly = true)
    public KoreaderProgress getProgress(String bookHash) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        ResolvedBookMatch match = resolveAccessibleBookMatchByHash(bookHash, reader);
        KoreaderProgressEntity entity = resolveProgressEntity(reader.getId(), match.book(), match.bookFile(), normalizeHash(bookHash)).orElse(null);
        return mapProgress(match.book(), match.bookFile(), entity, normalizeHash(bookHash), false, false, false, null, null);
    }

    @Transactional
    public void saveProgress(KoreaderProgress request) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = resolveNativeBook(reader, request);
        BookFileEntity bookFile = resolveNativeBookFile(book, request);
        String bookHash = resolvePersistedBookHash(book, bookFile, request);
        Float normalizedPercentage = normalizePercentage(request.getPercentage());
        Instant clientTimestamp = normalizeTimestamp(request.getTimestamp());

        KoreaderProgressEntity entity = resolveProgressEntity(reader.getId(), book, bookFile, bookHash)
                .orElseGet(KoreaderProgressEntity::new);

        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookFile(bookFile);
        entity.setBookHash(bookHash);
        entity.setDocument(trimToNull(request.getDocument()));
        entity.setFileFormat(resolveFileFormat(bookFile, request.getFileFormat()));
        entity.setProgress(trimToNull(request.getProgress()));
        entity.setLocation(trimToNull(request.getLocation()));
        entity.setPercentage(normalizedPercentage);
        entity.setCurrentPage(request.getCurrentPage());
        entity.setTotalPages(request.getTotalPages());
        entity.setDevice(trimToNull(request.getDevice()));
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setClientTimestamp(clientTimestamp);
        koreaderProgressRepository.save(entity);

        updateLegacyProgress(book, bookFile, request, normalizedPercentage, clientTimestamp);

        log.info("GrimmLink progress sync source=koreader direction=push apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} device={} deviceId={} userId={}",
                book.getId(),
                bookFile != null ? bookFile.getId() : null,
                bookHash,
                resolveFileFormat(bookFile, request.getFileFormat()),
                request.getCurrentPage(),
                request.getTotalPages(),
                normalizedPercentage,
                request.getProgress(),
                request.getLocation(),
                request.getDevice(),
                request.getDeviceId(),
                reader.getId());
    }

    @Transactional(readOnly = true)
    public KoreaderProgress getPdfProgress(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(bookId, reader);
        BookFileEntity pdfFile = resolvePdfBridgeFile(book, null, null, null);
        KoreaderProgressEntity nativeEntity = resolveLatestPdfProgress(reader.getId(), book, pdfFile).orElse(null);
        return mapProgress(book, pdfFile, nativeEntity, resolveBookHash(pdfFile), true, nativeEntity != null, false, null, null);
    }

    @Transactional
    public KoreaderProgress updatePdfProgress(Long bookId, KoreaderProgress request) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(bookId, reader);
        BookFileEntity pdfFile = resolvePdfBridgeFile(book, request.getBookFileId(), request.getBookHash(), request.getFileFormat());

        Instant requestTimestamp = normalizeTimestamp(request.getTimestamp());
        Instant currentTimestamp = resolveCurrentPdfTimestamp(reader.getId(), book, pdfFile);
        boolean stale = currentTimestamp != null && requestTimestamp != null && currentTimestamp.isAfter(requestTimestamp);
        if (stale && !Boolean.TRUE.equals(request.getForce())) {
            KoreaderProgressEntity nativeEntity = resolveLatestPdfProgress(reader.getId(), book, pdfFile).orElse(null);
            return mapProgress(book, pdfFile, nativeEntity, resolveBookHash(pdfFile), true, false, true, "remote_newer", "Remote PDF progress is newer than this update.");
        }

        Float normalizedPercentage = normalizePercentage(request.getPercentage());
        String bookHash = resolvePersistedBookHash(book, pdfFile, request);
        KoreaderProgressEntity entity = resolveLatestPdfProgress(reader.getId(), book, pdfFile)
                .orElseGet(KoreaderProgressEntity::new);

        entity.setUser(reader);
        entity.setBook(book);
        entity.setBookFile(pdfFile);
        entity.setBookHash(bookHash);
        entity.setDocument(trimToNull(request.getDocument()));
        entity.setFileFormat("PDF");
        entity.setProgress(trimToNull(firstNonBlank(request.getRawKoreaderProgress(), request.getProgress())));
        entity.setLocation(trimToNull(firstNonBlank(request.getRawKoreaderLocation(), request.getLocation())));
        entity.setPercentage(normalizedPercentage);
        entity.setCurrentPage(request.getCurrentPage());
        entity.setTotalPages(request.getTotalPages());
        entity.setDevice(trimToNull(request.getDevice()));
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setClientTimestamp(requestTimestamp);
        koreaderProgressRepository.save(entity);

        savePdfLegacyProgress(reader, book, pdfFile, request, normalizedPercentage, requestTimestamp);

        return mapProgress(book, pdfFile, entity, bookHash, true, true, false, "updated", "PDF progress updated.");
    }

    @Transactional(readOnly = true)
    public List<String> getSupportedReadStatuses() {
        return SUPPORTED_MANUAL_READ_STATUSES.stream()
                .map(Enum::name)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateReadStatus(Long bookId, String requestedStatus) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = loadAccessibleBookById(bookId, reader);
        ReadStatus status = normalizeManualReadStatus(requestedStatus);

        UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(reader.getId(), bookId)
                .orElseGet(UserBookProgressEntity::new);

        progress.setUser(reader);
        progress.setBook(book);
        progress.setReadStatus(status);
        progress.setReadStatusModifiedTime(Instant.now());
        if (status == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }
        progressRepository.save(progress);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("bookId", bookId);
        response.put("readStatus", status.name());
        response.put("dateFinished", progress.getDateFinished());
        return response;
    }

    private void updateLegacyProgress(BookEntity book, BookFileEntity bookFile, KoreaderProgress request, Float percentage, Instant clientTimestamp) {
        UserBookProgressEntity userProgress = progressRepository.findByUserIdAndBookId(getCurrentUserId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);

        userProgress.setUser(loadCurrentReader());
        userProgress.setBook(book);
        userProgress.setKoreaderProgress(trimToNull(request.getProgress()));
        userProgress.setKoreaderProgressPercent(percentage != null ? percentage / 100f : null);
        userProgress.setKoreaderDevice(trimToNull(request.getDevice()));
        userProgress.setKoreaderDeviceId(trimToNull(request.getDeviceId()));
        updateReadStatusFromKoreaderProgress(userProgress, percentage, request.getCurrentPage(), request.getTotalPages());
        userProgress.setKoreaderLastSyncTime(Instant.now());
        userProgress.setLastReadTime(clientTimestamp);

        if (bookFile != null && request.getCurrentPage() != null) {
            if (bookFile.getBookType() == BookFileType.PDF) {
                userProgress.setPdfProgress(request.getCurrentPage());
                userProgress.setPdfProgressPercent(percentage);
            } else if (bookFile.getBookType() == BookFileType.CBX) {
                userProgress.setCbxProgress(request.getCurrentPage());
                userProgress.setCbxProgressPercent(percentage);
            }
        }

        progressRepository.save(userProgress);

        if (bookFile != null) {
            UserBookFileProgressEntity fileProgress = fileProgressRepository.findByUserIdAndBookFileId(getCurrentUserId(), bookFile.getId())
                    .orElseGet(UserBookFileProgressEntity::new);
            fileProgress.setUser(loadCurrentReader());
            fileProgress.setBookFile(bookFile);
            fileProgress.setPositionData(trimToNull(request.getProgress()));
            fileProgress.setPositionHref(trimToNull(request.getLocation()));
            fileProgress.setProgressPercent(percentage);
            fileProgress.setLastReadTime(clientTimestamp);
            fileProgressRepository.save(fileProgress);
        }
    }

    private void savePdfLegacyProgress(BookLoreUserEntity reader, BookEntity book, BookFileEntity pdfFile,
                                       KoreaderProgress request, Float percentage, Instant clientTimestamp) {
        UserBookProgressEntity progress = progressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);
        progress.setUser(reader);
        progress.setBook(book);
        progress.setPdfProgress(request.getCurrentPage());
        progress.setPdfProgressPercent(percentage);
        progress.setKoreaderProgress(trimToNull(request.getRawKoreaderProgress()));
        progress.setKoreaderProgressPercent(percentage != null ? percentage / 100f : null);
        progress.setKoreaderDevice(trimToNull(request.getDevice()));
        progress.setKoreaderDeviceId(trimToNull(request.getDeviceId()));
        updateReadStatusFromKoreaderProgress(progress, percentage, request.getCurrentPage(), request.getTotalPages());
        progress.setKoreaderLastSyncTime(Instant.now());
        progress.setLastReadTime(clientTimestamp);
        progressRepository.save(progress);

        UserBookFileProgressEntity fileProgress = fileProgressRepository.findByUserIdAndBookFileId(reader.getId(), pdfFile.getId())
                .orElseGet(UserBookFileProgressEntity::new);
        fileProgress.setUser(reader);
        fileProgress.setBookFile(pdfFile);
        fileProgress.setPositionData(request.getCurrentPage() != null ? String.valueOf(request.getCurrentPage()) : null);
        fileProgress.setPositionHref(trimToNull(firstNonBlank(request.getRawKoreaderLocation(), request.getLocation())));
        fileProgress.setProgressPercent(percentage);
        fileProgress.setLastReadTime(clientTimestamp);
        fileProgressRepository.save(fileProgress);
    }

    private void updateReadStatusFromKoreaderProgress(UserBookProgressEntity progress,
                                                      Float percentage,
                                                      Integer currentPage,
                                                      Integer totalPages) {
        if (percentage == null || shouldPreserveCurrentStatus(progress)) {
            return;
        }

        ReadStatus derivedStatus = deriveStatusFromKoreaderProgress(percentage, currentPage, totalPages);
        progress.setReadStatus(derivedStatus);

        if (derivedStatus == ReadStatus.READ && progress.getDateFinished() == null) {
            progress.setDateFinished(Instant.now());
        }
    }

    private boolean shouldPreserveCurrentStatus(UserBookProgressEntity progress) {
        Instant statusModifiedTime = progress.getReadStatusModifiedTime();
        if (statusModifiedTime == null) {
            return false;
        }

        Instant koreaderLastSyncTime = progress.getKoreaderLastSyncTime();
        return koreaderLastSyncTime == null || statusModifiedTime.isAfter(koreaderLastSyncTime);
    }

    private ReadStatus deriveStatusFromKoreaderProgress(float progressPercent, Integer currentPage, Integer totalPages) {
        if (progressPercent >= 99f) {
            return ReadStatus.READ;
        }
        if (progressPercent >= 1f) {
            return ReadStatus.READING;
        }
        if (totalPages != null
                && currentPage != null
                && totalPages >= LARGE_BOOK_PAGE_COUNT_THRESHOLD
                && currentPage >= LARGE_BOOK_READING_PAGE_THRESHOLD) {
            return ReadStatus.READING;
        }
        return ReadStatus.UNREAD;
    }

    private Optional<KoreaderProgressEntity> resolveProgressEntity(Long userId, BookEntity book, BookFileEntity bookFile, String bookHash) {
        if (bookFile != null) {
            return koreaderProgressRepository.findByUserIdAndBookFileId(userId, bookFile.getId());
        }
        if (bookHash != null) {
            return koreaderProgressRepository.findByUserIdAndBookIdAndBookHash(userId, book.getId(), bookHash);
        }
        return koreaderProgressRepository.findMostRecentByUserIdAndBookId(userId, book.getId());
    }

    private Optional<KoreaderProgressEntity> resolveLatestPdfProgress(Long userId, BookEntity book, BookFileEntity pdfFile) {
        if (pdfFile != null) {
            return koreaderProgressRepository.findByUserIdAndBookFileId(userId, pdfFile.getId());
        }
        return koreaderProgressRepository.findMostRecentByUserIdAndBookId(userId, book.getId());
    }

    private Instant resolveCurrentPdfTimestamp(Long userId, BookEntity book, BookFileEntity pdfFile) {
        return resolveLatestPdfProgress(userId, book, pdfFile)
                .map(KoreaderProgressEntity::getClientTimestamp)
                .orElse(null);
    }

    private KoreaderProgress mapProgress(BookEntity book, BookFileEntity bookFile, KoreaderProgressEntity entity,
                                         String fallbackBookHash, boolean pdfMode, boolean updated, boolean conflictDetected,
                                         String conversionStatus, String message) {
        KoreaderProgress.KoreaderProgressBuilder builder = KoreaderProgress.builder()
                .bookId(book.getId())
                .bookFileId(bookFile != null ? bookFile.getId() : null)
                .bookHash(entity != null ? entity.getBookHash() : fallbackBookHash)
                .currentHash(bookFile != null ? bookFile.getCurrentHash() : null)
                .initialHash(bookFile != null ? bookFile.getInitialHash() : null)
                .fileFormat(entity != null ? entity.getFileFormat() : resolveFileFormat(bookFile, (String) null))
                .document(entity != null ? entity.getDocument() : null)
                .progress(entity != null ? entity.getProgress() : null)
                .location(entity != null ? entity.getLocation() : null)
                .percentage(entity != null ? entity.getPercentage() : null)
                .currentPage(entity != null ? entity.getCurrentPage() : null)
                .totalPages(entity != null ? entity.getTotalPages() : null)
                .device(entity != null ? entity.getDevice() : null)
                .deviceId(entity != null ? entity.getDeviceId() : null)
                .timestamp(entity != null && entity.getClientTimestamp() != null ? entity.getClientTimestamp().getEpochSecond() : null)
                .updatedAt(entity != null ? entity.getUpdatedAt() : null)
                .updated(updated)
                .conflictDetected(conflictDetected)
                .conversionStatus(conversionStatus != null ? conversionStatus : (pdfMode ? "pdf_page" : null))
                .message(message)
                .source(pdfMode ? "pdf_bridge" : "koreader");
        if (entity != null && entity.getClientTimestamp() != null) {
            builder.timestamp(entity.getClientTimestamp().getEpochSecond());
        }
        return builder.build();
    }

    private KoreaderProgress mapProgress(BookEntity book, BookFileEntity bookFile, KoreaderProgressEntity entity,
                                         String fallbackBookHash, boolean pdfMode, boolean updated, String message) {
        return mapProgress(book, bookFile, entity, fallbackBookHash, pdfMode, updated, false, pdfMode ? "pdf_page" : null, message);
    }

    private ResolvedBookMatch resolveAccessibleBookMatchByHash(String bookHash, BookLoreUserEntity reader) {
        String normalizedHash = normalizeHash(bookHash);
        List<BookEntity> candidates = bookRepository.findAllByBookHash(normalizedHash);
        if (candidates.isEmpty()) {
            throw ApiError.GENERIC_NOT_FOUND.createException("Book not found for hash " + normalizedHash);
        }

        return candidates.stream()
                .filter(book -> canAccessBook(reader, book))
                .map(book -> new ResolvedBookMatch(book, resolveExactMatchingBookFile(book, normalizedHash).orElse(book.getPrimaryBookFile())))
                .sorted(Comparator
                        .comparingInt((ResolvedBookMatch match) -> match.priority(normalizedHash))
                        .thenComparing(match -> match.book().getId()))
                .findFirst()
                .orElseThrow(() -> ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user"));
    }

    private BookEntity loadAccessibleBookById(Long bookId, BookLoreUserEntity reader) {
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    private BookEntity resolveNativeBook(BookLoreUserEntity reader, KoreaderProgress request) {
        if (request.resolveBookHash() != null) {
            return resolveAccessibleBookMatchByHash(request.resolveBookHash(), reader).book();
        }
        if (request.getBookId() != null) {
            return loadAccessibleBookById(request.getBookId(), reader);
        }
        throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash or bookId is required");
    }

    private BookFileEntity resolveNativeBookFile(BookEntity book, KoreaderProgress request) {
        if (request.getBookFileId() != null) {
            return book.getBookFiles().stream()
                    .filter(file -> file.getId().equals(request.getBookFileId()))
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("Book file not found: " + request.getBookFileId()));
        }
        if (request.resolveBookHash() != null) {
            return resolveExactMatchingBookFile(book, request.resolveBookHash()).orElseGet(book::getPrimaryBookFile);
        }
        return book.getPrimaryBookFile();
    }

    private BookFileEntity resolvePdfBridgeFile(BookEntity book, Long bookFileId, String bookHash, String fileFormat) {
        if (fileFormat != null && !fileFormat.isBlank() && !"PDF".equalsIgnoreCase(fileFormat.trim())) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_format");
        }

        if (bookFileId != null) {
            BookFileEntity file = book.getBookFiles().stream()
                    .filter(candidate -> candidate.getId().equals(bookFileId))
                    .findFirst()
                    .orElseThrow(() -> ApiError.FILE_NOT_FOUND.createException("exact_file_not_found"));
            if (file.getBookType() != BookFileType.PDF) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_format");
            }
            if (bookHash != null) {
                String resolvedHash = resolveBookHash(file);
                if (!bookHash.equals(resolvedHash)) {
                    throw ApiError.GENERIC_BAD_REQUEST.createException("file_mismatch");
                }
            }
            return file;
        }

        if (bookHash != null && !bookHash.isBlank()) {
            BookFileEntity exact = resolveExactMatchingBookFile(book, bookHash).orElse(null);
            if (exact == null) {
                throw ApiError.FILE_NOT_FOUND.createException("exact_file_not_found");
            }
            if (exact.getBookType() != BookFileType.PDF) {
                throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_format");
            }
            return exact;
        }

        BookFileEntity primary = book.getPrimaryBookFile();
        if (primary == null || primary.getBookType() != BookFileType.PDF) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_format");
        }
        return primary;
    }

    private Optional<BookFileEntity> resolveExactMatchingBookFile(BookEntity book, String bookHash) {
        if (book.getBookFiles() == null) {
            return Optional.empty();
        }
        return book.getBookFiles().stream()
                .filter(file -> file.getCurrentHash() != null && file.getCurrentHash().equals(bookHash))
                .findFirst()
                .or(() -> book.getBookFiles().stream()
                        .filter(file -> file.getInitialHash() != null && file.getInitialHash().equals(bookHash))
                        .findFirst());
    }

    private String resolvePersistedBookHash(BookEntity book, BookFileEntity bookFile, KoreaderProgress request) {
        String requestedHash = trimToNull(request.resolveBookHash());
        if (requestedHash != null) {
            return requestedHash;
        }
        String fileHash = resolveBookHash(bookFile);
        if (fileHash != null) {
            return fileHash;
        }
        BookFileEntity primary = book.getPrimaryBookFile();
        if (primary != null) {
            String primaryHash = resolveBookHash(primary);
            if (primaryHash != null) {
                return primaryHash;
            }
        }
        if (book.getId() != null) {
            return String.valueOf(book.getId());
        }
        throw ApiError.GENERIC_BAD_REQUEST.createException("bookHash/document is required");
    }

    private String resolveBookHash(BookFileEntity bookFile) {
        if (bookFile == null) {
            return null;
        }
        if (bookFile.getCurrentHash() != null && !bookFile.getCurrentHash().isBlank()) {
            return bookFile.getCurrentHash();
        }
        if (bookFile.getInitialHash() != null && !bookFile.getInitialHash().isBlank()) {
            return bookFile.getInitialHash();
        }
        return null;
    }

    private String resolveFileFormat(BookFileEntity bookFile, String requestedFormat) {
        if (requestedFormat != null && !requestedFormat.isBlank()) {
            return requestedFormat.trim().toUpperCase();
        }
        return bookFile != null && bookFile.getBookType() != null ? bookFile.getBookType().name() : null;
    }

    private String resolveFileFormat(BookFileEntity bookFile, KoreaderProgress request) {
        return resolveFileFormat(bookFile, request != null ? request.getFileFormat() : null);
    }

    private String resolveBookHash(BookFileEntity bookFile, KoreaderProgress request) {
        String requestHash = request != null ? trimToNull(request.resolveBookHash()) : null;
        return requestHash != null ? requestHash : resolveBookHash(bookFile);
    }

    private String normalizeHash(String bookHash) {
        if (bookHash == null || bookHash.isBlank()) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Book hash is required");
        }
        return bookHash.trim();
    }

    private ReadStatus normalizeManualReadStatus(String requestedStatus) {
        String normalized = trimToNull(requestedStatus);
        if (normalized == null) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("status is required");
        }

        String upper = normalized.toUpperCase();
        if ("ON_HOLD".equals(upper)) {
            upper = "PAUSED";
        }

        ReadStatus status;
        try {
            status = ReadStatus.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_status");
        }

        if (!SUPPORTED_MANUAL_READ_STATUSES.contains(status)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("unsupported_status");
        }
        return status;
    }

    private Float normalizePercentage(Float percentage) {
        if (percentage == null) {
            return null;
        }
        if (percentage < 0.0f) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Progress percentage cannot be negative");
        }
        if (percentage <= 1.0f) {
            return Math.round(percentage * 1000f) / 10f;
        }
        if (percentage > 100.0f) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Progress percentage cannot exceed 100");
        }
        return Math.round(percentage * 10f) / 10f;
    }

    private Instant normalizeTimestamp(Long timestamp) {
        return timestamp == null ? Instant.now() : Instant.ofEpochSecond(timestamp);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String trimmedFirst = trimToNull(first);
        if (trimmedFirst != null) {
            return trimmedFirst;
        }
        return trimToNull(second);
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) {
            return true;
        }
        return reader.getLibraries().stream().anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private KoreaderUserEntity findKoreaderUser(String username) {
        return koreaderUserRepository.findByUsername(username)
                .orElseThrow(() -> ApiError.GENERIC_NOT_FOUND.createException("KOReader user not found"));
    }

    private void validatePassword(KoreaderUserEntity koreaderUser, KoreaderUserDetails authDetails) {
        if (koreaderUser.getPasswordMD5() == null ||
                !koreaderUser.getPasswordMD5().equalsIgnoreCase(authDetails.getPassword())) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Invalid credentials");
        }
    }

    private KoreaderUserDetails getAuthDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof KoreaderUserDetails details)) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("User not authenticated");
        }
        return details;
    }

    private Long getCurrentUserId() {
        return loadCurrentReader().getId();
    }

    private BookLoreUserEntity loadCurrentReader() {
        return securityContextService.requireCurrentReaderEntity(true);
    }

    private record ResolvedBookMatch(BookEntity book, BookFileEntity bookFile) {
        int priority(String hash) {
            if (bookFile == null) {
                return 2;
            }
            if (Objects.equals(bookFile.getCurrentHash(), hash)) {
                return 0;
            }
            if (Objects.equals(bookFile.getInitialHash(), hash)) {
                return 1;
            }
            return 2;
        }
    }
}
