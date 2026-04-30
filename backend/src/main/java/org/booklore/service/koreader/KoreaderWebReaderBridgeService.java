package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderCfiResolveRequest;
import org.booklore.model.dto.koreader.KoreaderCfiResolveResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressUpdateRequest;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookFileProgressEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.booklore.model.enums.BookFileType;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookFileProgressRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderProgressRepository;
import org.booklore.util.koreader.EpubCfiService;
import org.grimmory.epub4j.cfi.XPointerResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KoreaderWebReaderBridgeService {

    private final BookRepository bookRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final UserBookFileProgressRepository userBookFileProgressRepository;
    private final KoreaderProgressRepository koreaderProgressRepository;
    private final KoreaderSecurityContextService securityContextService;
    private final EpubCfiService epubCfiService;

    @Transactional(readOnly = true)
    public KoreaderWebProgressResponse getWebProgress(Long bookId) {
        BridgeContext context = loadContext(bookId);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);
        UserBookFileProgressEntity fileProgress = userBookFileProgressRepository
                .findMostRecentByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);
        KoreaderProgressEntity nativeProgress = koreaderProgressRepository
                .findByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);

        return buildResponse(context, progress, fileProgress, nativeProgress, null, false, false, null, null);
    }

    @Transactional
    public KoreaderWebProgressResponse updateWebProgress(Long bookId, KoreaderWebProgressUpdateRequest request) {
        BridgeContext context = loadContext(bookId);
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);
        UserBookFileProgressEntity fileProgress = context.bridgeBookFile() == null
                ? null
                : userBookFileProgressRepository.findByUserIdAndBookFileId(context.reader().getId(), context.bridgeBookFile().getId())
                .orElse(null);
        KoreaderProgressEntity nativeProgress = koreaderProgressRepository
                .findByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);

        Instant currentUpdatedAt = resolveWebUpdatedAt(progress, fileProgress);
        Instant requestTimestamp = normalizeTimestamp(request.getTimestamp(), Instant.now());
        Instant expectedUpdatedAt = normalizeTimestamp(request.getExpectedUpdatedAt(), null);

        if (shouldRejectUpdate(currentUpdatedAt, requestTimestamp, expectedUpdatedAt, request.isForce())) {
            return buildResponse(
                    context,
                    progress,
                    fileProgress,
                    nativeProgress,
                    request,
                    false,
                    true,
                    "remote_newer",
                    "Web Reader progress is newer than this bridge update. Keeping the remote position."
            );
        }

        if (progress == null) {
            progress = new UserBookProgressEntity();
            progress.setUser(context.reader());
            progress.setBook(context.book());
        }

        Float normalizedPercentage = normalizePercent(request.getPercentage());
        String normalizedCfi = trimToNull(request.getEpubCfi());
        String normalizedHref = trimToNull(request.getPositionHref());
        Float normalizedContentProgress = normalizePercent(request.getContentSourceProgressPercent());

        progress.setLastReadTime(requestTimestamp);
        progress.setEpubProgress(normalizedCfi);
        progress.setEpubProgressHref(normalizedHref);
        progress.setEpubProgressPercent(normalizedPercentage);
        progress = userBookProgressRepository.save(progress);

        if (context.bridgeBookFile() != null) {
            if (fileProgress == null) {
                fileProgress = new UserBookFileProgressEntity();
                fileProgress.setUser(context.reader());
                fileProgress.setBookFile(context.bridgeBookFile());
            }
            fileProgress.setLastReadTime(requestTimestamp);
            fileProgress.setPositionData(normalizedCfi);
            fileProgress.setPositionHref(normalizedHref);
            fileProgress.setProgressPercent(normalizedPercentage);
            fileProgress.setContentSourceProgressPercent(normalizedContentProgress);
            fileProgress = userBookFileProgressRepository.save(fileProgress);
        }

        log.info("Saved GrimmLink Web Reader bridge progress for userId={} bookId={} percentage={} cfiPresent={}",
                context.reader().getId(), bookId, normalizedPercentage, normalizedCfi != null);

        return buildResponse(context, progress, fileProgress, nativeProgress, request, true, false, "updated",
                "Web Reader bridge progress updated.");
    }

    @Transactional(readOnly = true)
    public KoreaderCfiResolveResponse resolveCfi(Long bookId, KoreaderCfiResolveRequest request) {
        BridgeContext context = loadContext(bookId);
        Path bridgePath = resolveBridgePath(context.bridgeBookFile());
        if (bridgePath == null) {
            return failedResolve(bookId, request, "No EPUB-family book file is available for bridge conversion.", "unsupported_format");
        }

        String epubCfi = trimToNull(request.getEpubCfi());
        if (epubCfi != null) {
            try {
                XPointerResult result = epubCfiService.convertCfiToXPointer(bridgePath, epubCfi);
                String resolvedXPointer = result != null ? result.getXpointer() : null;
                EpubCfiService.CfiLocation location = epubCfiService.resolveCfiLocation(bridgePath, epubCfi).orElse(null);
                return KoreaderCfiResolveResponse.builder()
                        .bookId(bookId)
                        .converted(isNotBlank(resolvedXPointer))
                        .conversionStatus(isNotBlank(resolvedXPointer) ? "cfi_to_xpointer" : "conversion_failed")
                        .conversionConfidence(isNotBlank(resolvedXPointer) ? 0.95f : 0.0f)
                        .reason(isNotBlank(resolvedXPointer) ? null : "Unable to resolve EPUB CFI to KOReader location")
                        .epubCfi(epubCfi)
                        .positionHref(location != null ? location.href() : null)
                        .contentSourceProgressPercent(location != null ? location.contentSourceProgressPercent() : null)
                        .rawLocation(resolvedXPointer)
                        .rawKoreaderXPointer(resolvedXPointer)
                        .currentPage(request.getCurrentPage())
                        .totalPages(request.getTotalPages())
                        .percentage(normalizePercent(request.getPercentage()))
                        .build();
            } catch (Exception e) {
                log.debug("Failed GrimmLink CFI -> xpointer conversion for bookId={}: {}", bookId, e.getMessage());
                return failedResolve(bookId, request, "Unable to resolve EPUB CFI to KOReader location", "conversion_failed");
            }
        }

        String rawXPointer = chooseRawXPointer(request.getRawKoreaderXPointer(), request.getRawKoreaderLocation());
        if (rawXPointer != null) {
            try {
                String resolvedCfi = epubCfiService.convertXPointerToCfi(bridgePath, rawXPointer);
                EpubCfiService.CfiLocation location = epubCfiService.resolveCfiLocation(bridgePath, resolvedCfi).orElse(null);
                return KoreaderCfiResolveResponse.builder()
                        .bookId(bookId)
                        .converted(isNotBlank(resolvedCfi))
                        .conversionStatus(isNotBlank(resolvedCfi) ? "xpointer_to_cfi" : "conversion_failed")
                        .conversionConfidence(isNotBlank(resolvedCfi) ? 0.85f : 0.0f)
                        .reason(isNotBlank(resolvedCfi) ? null : "Unable to resolve KOReader location to EPUB CFI")
                        .epubCfi(resolvedCfi)
                        .positionHref(location != null ? location.href() : null)
                        .contentSourceProgressPercent(location != null ? location.contentSourceProgressPercent() : null)
                        .rawLocation(rawXPointer)
                        .rawKoreaderXPointer(rawXPointer)
                        .currentPage(request.getCurrentPage())
                        .totalPages(request.getTotalPages())
                        .percentage(normalizePercent(request.getPercentage()))
                        .build();
            } catch (Exception e) {
                log.debug("Failed GrimmLink xpointer -> CFI conversion for bookId={}: {}", bookId, e.getMessage());
                return failedResolve(bookId, request, "Unable to resolve KOReader location to EPUB CFI", "conversion_failed");
            }
        }

        return failedResolve(bookId, request, "Unable to resolve KOReader location to EPUB CFI", "conversion_failed");
    }

    private BridgeContext loadContext(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return new BridgeContext(reader, book, findBridgeBookFile(book).orElse(null));
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) {
            return true;
        }
        return reader.getLibraries().stream()
                .filter(Objects::nonNull)
                .anyMatch(library -> book.getLibrary() != null && Objects.equals(library.getId(), book.getLibrary().getId()));
    }

    private Optional<BookFileEntity> findBridgeBookFile(BookEntity book) {
        if (book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Optional.empty();
        }
        return book.getBookFiles().stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(bookFile -> isEpubFamily(bookFile.getBookType()))
                .findFirst()
                .or(() -> {
                    BookFileEntity primary = book.getPrimaryBookFile();
                    return primary != null && isEpubFamily(primary.getBookType()) ? Optional.of(primary) : Optional.empty();
                });
    }

    private boolean isEpubFamily(BookFileType type) {
        return type == BookFileType.EPUB
                || type == BookFileType.FB2
                || type == BookFileType.MOBI
                || type == BookFileType.AZW3;
    }

    private Path resolveBridgePath(BookFileEntity bridgeBookFile) {
        if (bridgeBookFile == null) {
            return null;
        }
        try {
            Path path = bridgeBookFile.getFullFilePath();
            if (path != null && Files.exists(path)) {
                return path;
            }
        } catch (Exception e) {
            log.debug("Unable to resolve GrimmLink bridge path: {}", e.getMessage());
        }
        return null;
    }

    private KoreaderWebProgressResponse buildResponse(
            BridgeContext context,
            UserBookProgressEntity progress,
            UserBookFileProgressEntity fileProgress,
            KoreaderProgressEntity nativeProgress,
            KoreaderWebProgressUpdateRequest request,
            boolean updated,
            boolean conflictDetected,
            String explicitStatus,
            String message
    ) {
        Path bridgePath = resolveBridgePath(context.bridgeBookFile());
        String epubCfi = firstNonBlank(
                fileProgress != null ? fileProgress.getPositionData() : null,
                progress != null ? progress.getEpubProgress() : null
        );
        Float percentage = firstNonNull(
                fileProgress != null ? fileProgress.getProgressPercent() : null,
                progress != null ? progress.getEpubProgressPercent() : null,
                request != null ? normalizePercent(request.getPercentage()) : null
        );
        String rawLocation = firstNonBlank(
                nativeProgress != null ? nativeProgress.getLocation() : null,
                request != null ? request.getRawKoreaderLocation() : null
        );
        String rawProgress = firstNonBlank(
                nativeProgress != null ? nativeProgress.getProgress() : null,
                request != null ? request.getRawKoreaderProgress() : null
        );
        String rawXPointer = chooseRawXPointer(
                request != null ? request.getRawKoreaderXPointer() : null,
                rawLocation,
                rawProgress
        );
        Instant updatedAt = resolveWebUpdatedAt(progress, fileProgress);
        String conversionStatus = explicitStatus != null ? explicitStatus : deriveConversionStatus(bridgePath, epubCfi, percentage);
        Float conversionConfidence = deriveConversionConfidence(conversionStatus);

        return KoreaderWebProgressResponse.builder()
                .bookId(context.book().getId())
                .percentage(percentage)
                .currentPage(firstNonNull(nativeProgress != null ? nativeProgress.getCurrentPage() : null,
                        request != null ? request.getCurrentPage() : null))
                .totalPages(firstNonNull(nativeProgress != null ? nativeProgress.getTotalPages() : null,
                        request != null ? request.getTotalPages() : null))
                .epubCfi(epubCfi)
                .positionHref(firstNonBlank(
                        fileProgress != null ? fileProgress.getPositionHref() : null,
                        progress != null ? progress.getEpubProgressHref() : null,
                        request != null ? request.getPositionHref() : null
                ))
                .contentSourceProgressPercent(firstNonNull(
                        fileProgress != null ? fileProgress.getContentSourceProgressPercent() : null,
                        request != null ? normalizePercent(request.getContentSourceProgressPercent()) : null
                ))
                .rawKoreaderLocation(rawLocation)
                .rawKoreaderProgress(rawProgress)
                .rawKoreaderXPointer(rawXPointer)
                .source(firstNonBlank(request != null ? request.getSource() : null, "WEB_READER"))
                .device(firstNonBlank(request != null ? request.getDevice() : null, "Web Reader"))
                .deviceId(firstNonBlank(request != null ? request.getDeviceId() : null, "web-reader"))
                .timestamp(updatedAt != null ? updatedAt.getEpochSecond() : null)
                .updatedAt(updatedAt)
                .conversionStatus(conversionStatus)
                .conversionConfidence(conversionConfidence)
                .updated(updated)
                .conflictDetected(conflictDetected)
                .message(message)
                .build();
    }

    private boolean shouldRejectUpdate(Instant currentUpdatedAt, Instant requestTimestamp, Instant expectedUpdatedAt, boolean force) {
        if (force || currentUpdatedAt == null) {
            return false;
        }
        if (expectedUpdatedAt != null && currentUpdatedAt.isAfter(expectedUpdatedAt)) {
            return true;
        }
        return requestTimestamp != null && currentUpdatedAt.isAfter(requestTimestamp);
    }

    private Instant resolveWebUpdatedAt(UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress) {
        Instant progressUpdatedAt = progress != null ? progress.getLastReadTime() : null;
        Instant fileUpdatedAt = fileProgress != null ? fileProgress.getLastReadTime() : null;
        if (progressUpdatedAt == null) {
            return fileUpdatedAt;
        }
        if (fileUpdatedAt == null) {
            return progressUpdatedAt;
        }
        return progressUpdatedAt.isAfter(fileUpdatedAt) ? progressUpdatedAt : fileUpdatedAt;
    }

    private String deriveConversionStatus(Path bridgePath, String epubCfi, Float percentage) {
        if (isNotBlank(epubCfi) && bridgePath != null && epubCfiService.validateCfi(bridgePath.toFile(), epubCfi)) {
            return "cfi_available";
        }
        if (isNotBlank(epubCfi)) {
            return bridgePath == null ? "unsupported_format" : "cfi_invalid";
        }
        if (percentage != null) {
            return "percentage_only";
        }
        return "not_available";
    }

    private Float deriveConversionConfidence(String conversionStatus) {
        return switch (conversionStatus) {
            case "cfi_to_xpointer", "xpointer_to_cfi", "cfi_available", "updated" -> 0.95f;
            case "percentage_only" -> 0.35f;
            case "remote_newer" -> 0.0f;
            case "unsupported_format", "cfi_invalid", "conversion_failed", "not_available" -> 0.0f;
            default -> 0.5f;
        };
    }

    private KoreaderCfiResolveResponse failedResolve(Long bookId, KoreaderCfiResolveRequest request, String reason, String status) {
        return KoreaderCfiResolveResponse.builder()
                .bookId(bookId)
                .converted(false)
                .reason(reason)
                .conversionStatus(status)
                .conversionConfidence(0.0f)
                .epubCfi(trimToNull(request.getEpubCfi()))
                .rawLocation(trimToNull(request.getRawKoreaderLocation()))
                .rawKoreaderXPointer(trimToNull(request.getRawKoreaderXPointer()))
                .currentPage(request.getCurrentPage())
                .totalPages(request.getTotalPages())
                .percentage(normalizePercent(request.getPercentage()))
                .build();
    }

    private Float normalizePercent(Float percentage) {
        if (percentage == null) {
            return null;
        }
        float value = percentage;
        if (value >= 0f && value <= 1f) {
            value = value * 100f;
        }
        if (value < 0f) {
            value = 0f;
        }
        if (value > 100f) {
            value = 100f;
        }
        return Math.round(value * 10f) / 10f;
    }

    private Instant normalizeTimestamp(Long epochSeconds, Instant fallback) {
        if (epochSeconds == null) {
            return fallback;
        }
        return Instant.ofEpochSecond(epochSeconds);
    }

    private String chooseRawXPointer(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && looksLikeXPointer(trimmed)) {
                return trimmed;
            }
        }
        return null;
    }

    private boolean looksLikeXPointer(String value) {
        return value != null && value.startsWith("/");
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isNotBlank(String value) {
        return trimToNull(value) != null;
    }

    private record BridgeContext(
            BookLoreUserEntity reader,
            BookEntity book,
            BookFileEntity bridgeBookFile
    ) {
    }
}
