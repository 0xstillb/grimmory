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
        BookFileEntity bridgeFile = resolveProgressBridgeFile(context, progress, fileProgress, nativeProgress);
        if (bridgeFile != null && !isSupportedBridgeFile(bridgeFile)) {
            bridgeFile = findFallbackBridgeBookFile(context.book()).orElse(null);
        }
        if (bridgeFile != null) {
            KoreaderProgressEntity exactNativeProgress = koreaderProgressRepository
                    .findByUserIdAndBookFileId(context.reader().getId(), bridgeFile.getId())
                    .orElse(null);
            if (exactNativeProgress != null) {
                nativeProgress = exactNativeProgress;
            }
        } else {
            log.info("GrimmLink web reader bridge disabled for unsupported book format bookId={} userId={}",
                    bookId, context.reader().getId());
            return buildResponse(context, null, null, null, null, null, false, false,
                    "unsupported_format", "The web reader bridge is disabled for EPUB-like files.");
        }

        KoreaderWebProgressResponse response = buildResponse(context, bridgeFile, progress, fileProgress, nativeProgress, null, false, false, null, null);
        log.info("GrimmLink progress sync source=web direction=bridge apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} epubCfi={} href={} conversionStatus={} userId={}",
                bookId,
                response.getBookFileId(),
                response.getBookHash(),
                response.getFileFormat(),
                response.getCurrentPage(),
                response.getTotalPages(),
                response.getPercentage(),
                response.getRawKoreaderProgress(),
                response.getRawKoreaderLocation(),
                response.getEpubCfi(),
                response.getPositionHref(),
                response.getConversionStatus(),
                context.reader().getId());
        return response;
    }

    @Transactional
    public KoreaderWebProgressResponse updateWebProgress(Long bookId, KoreaderWebProgressUpdateRequest request) {
        BridgeContext context = loadContext(bookId);
        BridgeFileSelection bridgeSelection = resolveBridgeFile(context.book(), request.getBookFileId(), request.getBookHash(), request.getFileFormat());
        if (bridgeSelection.failureStatus() != null) {
            if ("unsupported_format".equals(bridgeSelection.failureStatus())) {
                return buildResponse(context, null, null, null, null, null, false, false,
                        bridgeSelection.failureStatus(), bridgeSelection.failureReason());
            }
            return buildResponse(context, bridgeSelection.file(), null, null, null, request, false, false,
                    bridgeSelection.failureStatus(), bridgeSelection.failureReason());
        }

        BookFileEntity bridgeFile = bridgeSelection.file();
        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(context.reader().getId(), bookId)
                .orElse(null);
        UserBookFileProgressEntity fileProgress = bridgeFile == null
                ? null
                : userBookFileProgressRepository.findByUserIdAndBookFileId(context.reader().getId(), bridgeFile.getId())
                .orElse(null);
        KoreaderProgressEntity nativeProgress = bridgeFile != null
                ? koreaderProgressRepository.findByUserIdAndBookFileId(context.reader().getId(), bridgeFile.getId()).orElse(null)
                : null;
        if (nativeProgress == null) {
            nativeProgress = koreaderProgressRepository
                    .findByUserIdAndBookId(context.reader().getId(), bookId)
                    .orElse(null);
        }

        Instant currentUpdatedAt = resolveWebUpdatedAt(progress, fileProgress);
        Instant requestTimestamp = normalizeTimestamp(request.getTimestamp(), Instant.now());
        Instant expectedUpdatedAt = normalizeTimestamp(request.getExpectedUpdatedAt(), null);

        if (shouldRejectUpdate(currentUpdatedAt, requestTimestamp, expectedUpdatedAt, request.isForce())) {
            KoreaderWebProgressResponse response = buildResponse(
                    context,
                    bridgeFile,
                    progress,
                    fileProgress,
                    nativeProgress,
                    request,
                    false,
                    true,
                    "remote_newer",
                    "Web Reader progress is newer than this bridge update. Keeping the remote position."
            );
            log.info("GrimmLink progress sync source=web direction=bridge apiStatus=conflict bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} epubCfi={} href={} conversionStatus={} userId={}",
                    bookId,
                    bridgeFile != null ? bridgeFile.getId() : null,
                    bridgeFile != null && bridgeFile.getCurrentHash() != null ? bridgeFile.getCurrentHash() : null,
                    bridgeFile != null && bridgeFile.getBookType() != null ? bridgeFile.getBookType().name() : null,
                    response.getCurrentPage(),
                    response.getTotalPages(),
                    response.getPercentage(),
                    response.getRawKoreaderProgress(),
                    response.getRawKoreaderLocation(),
                    response.getEpubCfi(),
                    response.getPositionHref(),
                    response.getConversionStatus(),
                    context.reader().getId());
            return response;
        }

        if (progress == null) {
            progress = new UserBookProgressEntity();
            progress.setUser(context.reader());
            progress.setBook(context.book());
        }

        Float normalizedPercentage = normalizePercent(request.getPercentage());
        boolean pdfBridge = isPdfBridgeFile(bridgeFile);
        String normalizedCfi = pdfBridge ? null : trimToNull(request.getEpubCfi());
        String normalizedHref = pdfBridge ? null : trimToNull(request.getPositionHref());
        Float normalizedContentProgress = normalizePercent(request.getContentSourceProgressPercent());
        String existingEpubCfi = progress.getEpubProgress();
        String existingEpubHref = progress.getEpubProgressHref();
        Float existingEpubPercent = progress.getEpubProgressPercent();
        Integer existingPdfPage = progress.getPdfProgress();
        Float existingPdfPercent = progress.getPdfProgressPercent();
        String existingPositionData = fileProgress != null ? fileProgress.getPositionData() : null;
        String existingPositionHref = fileProgress != null ? fileProgress.getPositionHref() : null;
        Float existingFileProgressPercent = fileProgress != null ? fileProgress.getProgressPercent() : null;
        Float existingContentSourceProgress = fileProgress != null ? fileProgress.getContentSourceProgressPercent() : null;

        progress.setLastReadTime(requestTimestamp);
        if (pdfBridge) {
            progress.setPdfProgress(firstNonNull(request.getCurrentPage(), existingPdfPage));
            progress.setPdfProgressPercent(normalizedPercentage != null
                    ? normalizedPercentage / 100f
                    : existingPdfPercent);
        } else {
            progress.setEpubProgress(normalizedCfi != null ? normalizedCfi : existingEpubCfi);
            progress.setEpubProgressHref(normalizedHref != null ? normalizedHref : existingEpubHref);
            progress.setEpubProgressPercent(normalizedPercentage != null ? normalizedPercentage : existingEpubPercent);
        }
        progress = userBookProgressRepository.save(progress);

        if (bridgeFile != null) {
            if (fileProgress == null) {
                fileProgress = new UserBookFileProgressEntity();
                fileProgress.setUser(context.reader());
                fileProgress.setBookFile(bridgeFile);
            }
            fileProgress.setLastReadTime(requestTimestamp);
            fileProgress.setPositionData(pdfBridge
                    ? currentPageAsString(firstNonNull(request.getCurrentPage(), existingPdfPage))
                    : normalizedCfi != null ? normalizedCfi : existingPositionData);
            fileProgress.setPositionHref(pdfBridge
                    ? null
                    : normalizedHref != null ? normalizedHref : existingPositionHref);
            fileProgress.setProgressPercent(normalizedPercentage != null ? normalizedPercentage : existingFileProgressPercent);
            fileProgress.setContentSourceProgressPercent(normalizedContentProgress != null ? normalizedContentProgress : existingContentSourceProgress);
            fileProgress = userBookFileProgressRepository.save(fileProgress);
        }

        boolean savedLocatorPresent = pdfBridge
                ? progress.getPdfProgress() != null
                : progress.getEpubProgress() != null || (fileProgress != null && fileProgress.getPositionData() != null);
        log.info("Saved GrimmLink Web Reader bridge progress for userId={} bookId={} percentage={} cfiPresent={}",
                context.reader().getId(), bookId, normalizedPercentage, savedLocatorPresent);

        KoreaderWebProgressResponse response = buildResponse(context, bridgeFile, progress, fileProgress, nativeProgress, request, true, false,
                pdfBridge ? "pdf_page" : "updated", "Web Reader bridge progress updated.");
        log.info("GrimmLink progress sync source=web direction=bridge apiStatus=ok bookId={} bookFileId={} bookHash={} fileFormat={} currentPage={} totalPages={} percentage={} progress={} location={} epubCfi={} href={} conversionStatus={} userId={}",
                bookId,
                response.getBookFileId(),
                response.getBookHash(),
                response.getFileFormat(),
                response.getCurrentPage(),
                response.getTotalPages(),
                response.getPercentage(),
                response.getRawKoreaderProgress(),
                response.getRawKoreaderLocation(),
                response.getEpubCfi(),
                response.getPositionHref(),
                response.getConversionStatus(),
                context.reader().getId());
        return response;
    }

    @Transactional(readOnly = true)
    public KoreaderCfiResolveResponse resolveCfi(Long bookId, KoreaderCfiResolveRequest request) {
        BridgeContext context = loadContext(bookId);
        BridgeFileSelection bridgeSelection = resolveBridgeFile(context.book(), request.getBookFileId(), request.getBookHash(), request.getFileFormat());
        if (bridgeSelection.failureStatus() != null) {
            if ("unsupported_format".equals(bridgeSelection.failureStatus())) {
                return failedResolve(bookId, request, bridgeSelection.failureReason(), bridgeSelection.failureStatus(), bridgeSelection.file());
            }
            return failedResolve(bookId, request, bridgeSelection.failureReason(), bridgeSelection.failureStatus(), bridgeSelection.file());
        }

        BookFileEntity bridgeFile = bridgeSelection.file();
        Path bridgePath = resolveBridgePath(bridgeFile);
        if (bridgePath == null) {
            return failedResolve(bookId, request, "No supported bridge file is available for conversion.", "unsupported_format", bridgeFile);
        }

        String epubCfi = trimToNull(request.getEpubCfi());
        if (isPdfBridgeFile(bridgeFile)) {
            Integer page = firstNonNull(request.getCurrentPage(), extractPageFromLocation(request.getRawKoreaderLocation()), extractPageFromLocation(request.getRawKoreaderXPointer()));
            if (page == null) {
                return failedResolve(bookId, request, "PDF bridge requires a current page.", "unsupported_format", bridgeFile);
            }
            return KoreaderCfiResolveResponse.builder()
                    .bookId(bookId)
                    .bookFileId(bridgeFile.getId())
                    .bookHash(firstNonBlank(bridgeFile.getCurrentHash(), bridgeFile.getInitialHash()))
                    .currentHash(bridgeFile.getCurrentHash())
                    .initialHash(bridgeFile.getInitialHash())
                    .fileFormat(bridgeFile.getBookType() != null ? bridgeFile.getBookType().name() : null)
                    .converted(false)
                    .conversionStatus("pdf_page")
                    .locatorPrecision(deriveLocatorPrecision("pdf_page"))
                    .conversionConfidence(1.0f)
                    .reason(null)
                    .epubCfi(null)
                    .epubHref(null)
                    .epubAnchor(null)
                    .positionHref(null)
                    .contentSourceProgressPercent(normalizePercent(request.getPercentage()))
                    .rawLocation(page.toString())
                    .koreaderLocation(page.toString())
                    .rawKoreaderXPointer(null)
                    .koreaderXPointer(null)
                    .currentPage(page)
                    .totalPages(request.getTotalPages())
                    .percentage(normalizePercent(request.getPercentage()))
                    .webPercentDisplayOnly(normalizePercent(request.getPercentage()))
                    .koreaderPercentDisplayOnly(normalizePercent(request.getPercentage()))
                    .build();
        }

        if (epubCfi != null) {
            try {
                XPointerResult result = epubCfiService.convertCfiToXPointer(bridgePath, epubCfi);
                String resolvedXPointer = result != null ? result.getXpointer() : null;
                EpubCfiService.CfiLocation location = epubCfiService.resolveCfiLocation(bridgePath, epubCfi).orElse(null);
                return KoreaderCfiResolveResponse.builder()
                        .bookId(bookId)
                        .bookFileId(bridgeFile != null ? bridgeFile.getId() : null)
                        .bookHash(bridgeFile != null ? firstNonBlank(bridgeFile.getCurrentHash(), bridgeFile.getInitialHash()) : null)
                        .currentHash(bridgeFile != null ? bridgeFile.getCurrentHash() : null)
                        .initialHash(bridgeFile != null ? bridgeFile.getInitialHash() : null)
                        .fileFormat(bridgeFile != null && bridgeFile.getBookType() != null ? bridgeFile.getBookType().name() : null)
                        .converted(isNotBlank(resolvedXPointer))
                        .conversionStatus(isNotBlank(resolvedXPointer) ? "cfi_to_xpointer" : "conversion_failed")
                        .locatorPrecision(deriveLocatorPrecision(isNotBlank(resolvedXPointer) ? "cfi_to_xpointer" : "conversion_failed"))
                        .conversionConfidence(isNotBlank(resolvedXPointer) ? 0.95f : 0.0f)
                        .reason(isNotBlank(resolvedXPointer) ? null : "Unable to resolve EPUB CFI to KOReader location")
                        .epubCfi(epubCfi)
                        .epubHref(location != null ? location.href() : null)
                        .epubAnchor(extractHrefAnchor(location != null ? location.href() : null))
                        .positionHref(location != null ? location.href() : null)
                        .contentSourceProgressPercent(location != null ? location.contentSourceProgressPercent() : null)
                        .rawLocation(resolvedXPointer)
                        .koreaderLocation(resolvedXPointer)
                        .rawKoreaderXPointer(resolvedXPointer)
                        .koreaderXPointer(resolvedXPointer)
                        .currentPage(request.getCurrentPage())
                        .totalPages(request.getTotalPages())
                        .percentage(normalizePercent(request.getPercentage()))
                        .webPercentDisplayOnly(normalizePercent(request.getPercentage()))
                        .koreaderPercentDisplayOnly(normalizePercent(request.getPercentage()))
                        .build();
            } catch (Exception e) {
                log.debug("Failed GrimmLink CFI -> xpointer conversion for bookId={}: {}", bookId, e.getMessage());
                return failedResolve(bookId, request, "Unable to resolve EPUB CFI to KOReader location", "conversion_failed", bridgeFile);
            }
        }

        String rawXPointer = chooseRawXPointer(request.getRawKoreaderXPointer(), request.getRawKoreaderLocation());
        if (rawXPointer != null) {
            try {
                String resolvedCfi = epubCfiService.convertProgressXPointerToCfi(bridgePath, rawXPointer);
                EpubCfiService.CfiLocation location = epubCfiService.resolveCfiLocation(bridgePath, resolvedCfi).orElse(null);
                return KoreaderCfiResolveResponse.builder()
                        .bookId(bookId)
                        .bookFileId(bridgeFile != null ? bridgeFile.getId() : null)
                        .bookHash(bridgeFile != null ? firstNonBlank(bridgeFile.getCurrentHash(), bridgeFile.getInitialHash()) : null)
                        .currentHash(bridgeFile != null ? bridgeFile.getCurrentHash() : null)
                        .initialHash(bridgeFile != null ? bridgeFile.getInitialHash() : null)
                        .fileFormat(bridgeFile != null && bridgeFile.getBookType() != null ? bridgeFile.getBookType().name() : null)
                        .converted(isNotBlank(resolvedCfi))
                        .conversionStatus(isNotBlank(resolvedCfi) ? "xpointer_to_cfi" : "conversion_failed")
                        .locatorPrecision(deriveLocatorPrecision(isNotBlank(resolvedCfi) ? "xpointer_to_cfi" : "conversion_failed"))
                        .conversionConfidence(isNotBlank(resolvedCfi) ? 0.85f : 0.0f)
                        .reason(isNotBlank(resolvedCfi) ? null : "Unable to resolve KOReader location to EPUB CFI")
                        .epubCfi(resolvedCfi)
                        .epubHref(location != null ? location.href() : null)
                        .epubAnchor(extractHrefAnchor(location != null ? location.href() : null))
                        .positionHref(location != null ? location.href() : null)
                        .contentSourceProgressPercent(location != null ? location.contentSourceProgressPercent() : null)
                        .rawLocation(rawXPointer)
                        .koreaderLocation(rawXPointer)
                        .rawKoreaderXPointer(rawXPointer)
                        .koreaderXPointer(rawXPointer)
                        .currentPage(request.getCurrentPage())
                        .totalPages(request.getTotalPages())
                        .percentage(normalizePercent(request.getPercentage()))
                        .webPercentDisplayOnly(normalizePercent(request.getPercentage()))
                        .koreaderPercentDisplayOnly(normalizePercent(request.getPercentage()))
                        .build();
            } catch (Exception e) {
                log.debug("Failed GrimmLink xpointer -> CFI conversion for bookId={}: {}", bookId, e.getMessage());
                return failedResolve(bookId, request, "Unable to resolve KOReader location to EPUB CFI", "conversion_failed", bridgeFile);
            }
        }

        return failedResolve(bookId, request, "Unable to resolve KOReader location to EPUB CFI", "conversion_failed", bridgeFile);
    }

    private BridgeContext loadContext(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = bookRepository.findByIdWithBookFiles(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return new BridgeContext(reader, book, findFallbackBridgeBookFile(book).orElse(null));
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) {
            return true;
        }
        return reader.getLibraries().stream()
                .filter(Objects::nonNull)
                .anyMatch(library -> book.getLibrary() != null && Objects.equals(library.getId(), book.getLibrary().getId()));
    }

    private Optional<BookFileEntity> findFallbackBridgeBookFile(BookEntity book) {
        if (book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Optional.empty();
        }
        BookFileEntity primary = book.getPrimaryBookFile();
        if (primary != null && isSupportedBridgeFile(primary)) {
            return Optional.of(primary);
        }
        return book.getBookFiles().stream()
                .filter(BookFileEntity::isBookFormat)
                .filter(this::isSupportedBridgeFile)
                .findFirst();
    }

    private boolean isSupportedBridgeFile(BookFileEntity bookFile) {
        return bookFile != null && isSupportedBridgeFile(bookFile.getBookType());
    }

    private boolean isSupportedBridgeFile(BookFileType type) {
        return type == BookFileType.PDF;
    }

    private boolean isPdfBridgeFile(BookFileEntity bookFile) {
        return bookFile != null && bookFile.getBookType() == BookFileType.PDF;
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

    private BookFileEntity resolveProgressBridgeFile(BridgeContext context, UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress, KoreaderProgressEntity nativeProgress) {
        if (fileProgress != null && fileProgress.getBookFile() != null) {
            return fileProgress.getBookFile();
        }
        if (nativeProgress != null && nativeProgress.getBookFile() != null) {
            return nativeProgress.getBookFile();
        }
        if (progress != null && progress.getBook() != null && progress.getBook().getPrimaryBookFile() != null) {
            return progress.getBook().getPrimaryBookFile();
        }
        return context.bridgeBookFile();
    }

    private BridgeFileSelection resolveBridgeFile(BookEntity book, Long bookFileId, String bookHash, String fileFormat) {
        String normalizedHash = trimToNull(bookHash);
        String normalizedFormat = normalizeFileFormat(fileFormat);

        if (book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return new BridgeFileSelection(null, normalizedHash != null || bookFileId != null || normalizedFormat != null ? "exact_file_not_found" : "unsupported_format",
                    "No matching book file is available for bridge conversion.");
        }

        BookFileEntity selected = null;
        if (bookFileId != null) {
            selected = book.getBookFiles().stream()
                    .filter(file -> file != null && bookFileId.equals(file.getId()))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                return new BridgeFileSelection(null, "exact_file_not_found", "No book file matches the requested bookFileId.");
            }
        } else if (normalizedHash != null) {
            selected = book.getBookFiles().stream()
                    .filter(Objects::nonNull)
                    .filter(file -> normalizedHash.equals(file.getCurrentHash()) || normalizedHash.equals(file.getInitialHash()))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                return new BridgeFileSelection(null, "exact_file_not_found", "No book file matches the requested bookHash.");
            }
        } else if (normalizedFormat != null) {
            selected = book.getBookFiles().stream()
                    .filter(Objects::nonNull)
                    .filter(file -> matchesFileFormat(file, normalizedFormat))
                    .findFirst()
                    .orElse(null);
            if (selected == null) {
                return new BridgeFileSelection(null, "exact_file_not_found", "No book file matches the requested fileFormat.");
            }
        } else {
            selected = findFallbackBridgeBookFile(book).orElse(null);
            if (selected == null) {
                return new BridgeFileSelection(null, "unsupported_format", "No supported bridge file is available for conversion.");
            }
        }

        if (selected == null) {
            return new BridgeFileSelection(null, "exact_file_not_found", "No matching book file is available for bridge conversion.");
        }

        if (normalizedFormat != null && !matchesFileFormat(selected, normalizedFormat)) {
            return new BridgeFileSelection(selected, "file_mismatch", "The requested fileFormat does not match the resolved book file.");
        }
        if (!isSupportedBridgeFile(selected)) {
            return new BridgeFileSelection(selected, "unsupported_format", "The resolved book file format is not supported by the bridge.");
        }
        return new BridgeFileSelection(selected, null, null);
    }

    private boolean matchesFileFormat(BookFileEntity file, String requestedFormat) {
        if (file == null || file.getBookType() == null || requestedFormat == null) {
            return false;
        }
        return file.getBookType().name().equalsIgnoreCase(requestedFormat);
    }

    private String normalizeFileFormat(String fileFormat) {
        String normalized = trimToNull(fileFormat);
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private KoreaderWebProgressResponse buildResponse(
            BridgeContext context,
            BookFileEntity bridgeFile,
            UserBookProgressEntity progress,
            UserBookFileProgressEntity fileProgress,
            KoreaderProgressEntity nativeProgress,
            KoreaderWebProgressUpdateRequest request,
            boolean updated,
            boolean conflictDetected,
            String explicitStatus,
            String message
    ) {
        Path bridgePath = resolveBridgePath(bridgeFile);
        boolean pdfBridge = isPdfBridgeFile(bridgeFile);
        Float pdfProgressPercent = progress != null && progress.getPdfProgressPercent() != null
                ? Math.round(progress.getPdfProgressPercent() * 1000f) / 10f
                : null;
        Integer pdfCurrentPage = firstNonNull(
                progress != null ? progress.getPdfProgress() : null,
                nativeProgress != null ? nativeProgress.getCurrentPage() : null,
                request != null ? request.getCurrentPage() : null
        );
        Integer pdfTotalPages = firstNonNull(
                request != null ? request.getTotalPages() : null,
                nativeProgress != null ? nativeProgress.getTotalPages() : null
        );
        String epubCfi = pdfBridge ? null : firstNonBlank(
                fileProgress != null ? fileProgress.getPositionData() : null,
                progress != null ? progress.getEpubProgress() : null,
                request != null ? trimToNull(request.getEpubCfi()) : null
        );
        Float percentage = firstNonNull(
                fileProgress != null ? fileProgress.getProgressPercent() : null,
                pdfBridge ? pdfProgressPercent : progress != null ? progress.getEpubProgressPercent() : null,
                request != null ? normalizePercent(request.getPercentage()) : null
        );
        String rawLocation = firstNonBlank(
                nativeProgress != null ? nativeProgress.getLocation() : null,
                request != null ? request.getRawKoreaderLocation() : null,
                pdfBridge ? currentPageAsString(pdfCurrentPage) : null
        );
        String rawProgress = firstNonBlank(
                nativeProgress != null ? nativeProgress.getProgress() : null,
                request != null ? request.getRawKoreaderProgress() : null,
                pdfBridge ? currentPageAsString(pdfCurrentPage) : null
        );
        String rawXPointer = chooseRawXPointer(
                request != null ? request.getRawKoreaderXPointer() : null,
                rawLocation,
                rawProgress
        );
        Instant updatedAt = resolveWebUpdatedAt(progress, fileProgress);
        String conversionStatus = explicitStatus != null ? explicitStatus : deriveConversionStatus(bridgeFile, bridgePath, epubCfi, rawXPointer, percentage);
        Float conversionConfidence = deriveConversionConfidence(conversionStatus);
        String epubHref = firstNonBlank(
                pdfBridge ? null : fileProgress != null ? fileProgress.getPositionHref() : null,
                pdfBridge ? null : progress != null ? progress.getEpubProgressHref() : null,
                request != null ? request.getPositionHref() : null
        );
        String bookHash = bridgeFile != null ? firstNonBlank(bridgeFile.getCurrentHash(), bridgeFile.getInitialHash()) : request != null ? trimToNull(request.getBookHash()) : null;
        Long bookFileId = bridgeFile != null ? bridgeFile.getId() : request != null ? request.getBookFileId() : null;
        String fileFormat = bridgeFile != null && bridgeFile.getBookType() != null
                ? bridgeFile.getBookType().name()
                : request != null ? normalizeFileFormat(request.getFileFormat()) : null;

        return KoreaderWebProgressResponse.builder()
                .bookId(context.book().getId())
                .bookFileId(bookFileId)
                .bookHash(bookHash)
                .currentHash(bridgeFile != null ? bridgeFile.getCurrentHash() : null)
                .initialHash(bridgeFile != null ? bridgeFile.getInitialHash() : null)
                .fileFormat(fileFormat)
                .percentage(percentage)
                .pdfCurrentPage(pdfBridge ? pdfCurrentPage : firstNonNull(nativeProgress != null ? nativeProgress.getCurrentPage() : null,
                        request != null ? request.getCurrentPage() : null))
                .pdfTotalPages(pdfBridge ? pdfTotalPages : firstNonNull(nativeProgress != null ? nativeProgress.getTotalPages() : null,
                        request != null ? request.getTotalPages() : null))
                .pdfProgressPercent(pdfBridge ? percentage : pdfProgressPercent)
                .webPercentDisplayOnly(percentage)
                .koreaderPercentDisplayOnly(percentage)
                .currentPage(pdfBridge ? pdfCurrentPage : firstNonNull(nativeProgress != null ? nativeProgress.getCurrentPage() : null,
                        request != null ? request.getCurrentPage() : null))
                .totalPages(pdfBridge ? pdfTotalPages : firstNonNull(nativeProgress != null ? nativeProgress.getTotalPages() : null,
                        request != null ? request.getTotalPages() : null))
                .epubCfi(epubCfi)
                .epubHref(epubHref)
                .epubAnchor(extractHrefAnchor(epubHref))
                .positionHref(epubHref)
                .contentSourceProgressPercent(firstNonNull(
                        fileProgress != null ? fileProgress.getContentSourceProgressPercent() : null,
                        request != null ? normalizePercent(request.getContentSourceProgressPercent()) : null
                ))
                .rawKoreaderLocation(rawLocation)
                .rawKoreaderProgress(rawProgress)
                .rawKoreaderXPointer(rawXPointer)
                .koreaderLocation(rawLocation)
                .koreaderXPointer(rawXPointer)
                .source(firstNonBlank(request != null ? request.getSource() : null, "WEB_READER"))
                .device(firstNonBlank(request != null ? request.getDevice() : null, "Web Reader"))
                .deviceId(firstNonBlank(request != null ? request.getDeviceId() : null, "web-reader"))
                .timestamp(updatedAt != null ? updatedAt.getEpochSecond() : null)
                .updatedAt(updatedAt)
                .locatorPrecision(deriveLocatorPrecision(conversionStatus))
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

    private String deriveConversionStatus(BookFileEntity bridgeFile, Path bridgePath, String epubCfi, String rawXPointer, Float percentage) {
        if (isPdfBridgeFile(bridgeFile)) {
            return "pdf_page";
        }
        if (isNotBlank(epubCfi) && bridgePath != null && epubCfiService.validateCfi(bridgePath.toFile(), epubCfi)) {
            return "cfi_available";
        }
        if (isNotBlank(epubCfi)) {
            return bridgePath == null ? "unsupported_format" : "cfi_invalid";
        }
        if (isNotBlank(rawXPointer) && bridgePath != null) {
            return "koreader_locator_available";
        }
        if (percentage != null) {
            return "percentage_only";
        }
        return "koreader_locator_available";
    }

    private Float deriveConversionConfidence(String conversionStatus) {
        return switch (conversionStatus) {
            case "cfi_to_xpointer", "xpointer_to_cfi", "cfi_available", "pdf_page" -> 0.95f;
            case "koreader_locator_available" -> 0.85f;
            case "updated" -> 0.95f;
            case "percentage_only" -> 0.35f;
            case "remote_newer" -> 0.0f;
            case "unsupported_format", "cfi_invalid", "conversion_failed", "exact_file_not_found", "file_mismatch" -> 0.0f;
            default -> 0.5f;
        };
    }

    private String deriveLocatorPrecision(String conversionStatus) {
        return switch (conversionStatus) {
            case "cfi_to_xpointer", "xpointer_to_cfi", "cfi_available", "koreader_locator_available", "pdf_page" -> "exact";
            case "updated" -> "converted";
            case "percentage_only" -> "percentage_only";
            case "remote_newer", "unsupported_format", "cfi_invalid", "conversion_failed", "exact_file_not_found", "file_mismatch" -> "failed";
            default -> "approximate";
        };
    }

    private String extractHrefAnchor(String href) {
        if (href == null) {
            return null;
        }
        int anchorIndex = href.indexOf('#');
        if (anchorIndex < 0 || anchorIndex >= href.length() - 1) {
            return null;
        }
        return href.substring(anchorIndex + 1);
    }

    private KoreaderCfiResolveResponse failedResolve(Long bookId, KoreaderCfiResolveRequest request, String reason, String status, BookFileEntity bridgeFile) {
        Float normalizedPercentage = normalizePercent(request.getPercentage());
        String resolvedStatus = "conversion_failed".equals(status) && normalizedPercentage != null
                ? "percentage_only"
                : status;
        return KoreaderCfiResolveResponse.builder()
                .bookId(bookId)
                .bookFileId(bridgeFile != null ? bridgeFile.getId() : request.getBookFileId())
                .bookHash(bridgeFile != null ? firstNonBlank(bridgeFile.getCurrentHash(), bridgeFile.getInitialHash()) : trimToNull(request.getBookHash()))
                .currentHash(bridgeFile != null ? bridgeFile.getCurrentHash() : null)
                .initialHash(bridgeFile != null ? bridgeFile.getInitialHash() : null)
                .fileFormat(bridgeFile != null && bridgeFile.getBookType() != null ? bridgeFile.getBookType().name() : normalizeFileFormat(request.getFileFormat()))
                .locatorPrecision(deriveLocatorPrecision(resolvedStatus))
                .converted(false)
                .reason(reason)
                .conversionStatus(resolvedStatus)
                .conversionConfidence(deriveConversionConfidence(resolvedStatus))
                .epubCfi(trimToNull(request.getEpubCfi()))
                .epubHref(null)
                .epubAnchor(null)
                .positionHref(null)
                .rawLocation(trimToNull(request.getRawKoreaderLocation()))
                .koreaderLocation(trimToNull(request.getRawKoreaderLocation()))
                .rawKoreaderXPointer(trimToNull(request.getRawKoreaderXPointer()))
                .koreaderXPointer(trimToNull(request.getRawKoreaderXPointer()))
                .currentPage(request.getCurrentPage())
                .totalPages(request.getTotalPages())
                .percentage(normalizedPercentage)
                .webPercentDisplayOnly(normalizedPercentage)
                .koreaderPercentDisplayOnly(normalizedPercentage)
                .build();
    }

    private String currentPageAsString(Integer page) {
        return page == null ? null : Integer.toString(page);
    }

    private Integer extractPageFromLocation(String location) {
        String trimmed = trimToNull(location);
        if (trimmed == null) {
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private record BridgeFileSelection(BookFileEntity file, String failureStatus, String failureReason) {
    }
}
