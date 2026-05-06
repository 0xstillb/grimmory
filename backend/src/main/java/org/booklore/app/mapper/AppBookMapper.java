package org.booklore.app.mapper;

import org.booklore.app.dto.AppBookDetail;
import org.booklore.app.dto.AppBookFile;
import org.booklore.app.dto.AppBookProgressResponse;
import org.booklore.app.dto.AppBookSummary;
import org.booklore.app.dto.AppLibrarySummary;
import org.booklore.app.dto.AppMagicShelfSummary;
import org.booklore.app.dto.AppShelfSummary;
import org.booklore.model.entity.*;
import org.booklore.model.enums.BookFileType;
import org.mapstruct.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AppBookMapper {

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "title", source = "book.metadata.title")
    @Mapping(target = "authors", source = "book.metadata.authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "thumbnailUrl", source = "book", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "readStatus", source = "progress.readStatus")
    @Mapping(target = "personalRating", source = "progress.personalRating")
    @Mapping(target = "seriesName", source = "book.metadata.seriesName")
    @Mapping(target = "seriesNumber", source = "book.metadata.seriesNumber")
    @Mapping(target = "libraryId", source = "book.library.id")
    @Mapping(target = "addedOn", source = "book.addedOn")
    @Mapping(target = "lastReadTime", source = "progress.lastReadTime")
    @Mapping(target = "readProgress", source = "progress", qualifiedByName = "mapReadProgress")
    @Mapping(target = "primaryFileType", source = "book", qualifiedByName = "mapPrimaryFileType")
    @Mapping(target = "coverUpdatedOn", source = "book.metadata.coverUpdatedOn")
    @Mapping(target = "audiobookCoverUpdatedOn", source = "book.metadata.audiobookCoverUpdatedOn")
    @Mapping(target = "isPhysical", source = "book.isPhysical")
    @Mapping(target = "publishedDate", source = "book.metadata.publishedDate")
    @Mapping(target = "pageCount", source = "book.metadata.pageCount")
    @Mapping(target = "ageRating", source = "book.metadata.ageRating")
    @Mapping(target = "contentRating", source = "book.metadata.contentRating")
    @Mapping(target = "metadataMatchScore", source = "book.metadataMatchScore")
    @Mapping(target = "fileSizeKb", source = "book", qualifiedByName = "mapFileSizeKb")
    AppBookSummary toSummary(BookEntity book, UserBookProgressEntity progress);

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "title", source = "book.metadata.title")
    @Mapping(target = "authors", source = "book.metadata.authors", qualifiedByName = "mapAuthors")
    @Mapping(target = "thumbnailUrl", source = "book", qualifiedByName = "mapThumbnailUrl")
    @Mapping(target = "readStatus", source = "progress.readStatus")
    @Mapping(target = "personalRating", source = "progress.personalRating")
    @Mapping(target = "seriesName", source = "book.metadata.seriesName")
    @Mapping(target = "seriesNumber", source = "book.metadata.seriesNumber")
    @Mapping(target = "libraryId", source = "book.library.id")
    @Mapping(target = "addedOn", source = "book.addedOn")
    @Mapping(target = "lastReadTime", source = "progress.lastReadTime")
    @Mapping(target = "subtitle", source = "book.metadata.subtitle")
    @Mapping(target = "description", source = "book.metadata.description")
    @Mapping(target = "categories", source = "book.metadata.categories", qualifiedByName = "mapCategories")
    @Mapping(target = "publisher", source = "book.metadata.publisher")
    @Mapping(target = "publishedDate", source = "book.metadata.publishedDate")
    @Mapping(target = "pageCount", source = "book.metadata.pageCount")
    @Mapping(target = "isbn13", source = "book.metadata.isbn13")
    @Mapping(target = "language", source = "book.metadata.language")
    @Mapping(target = "goodreadsRating", source = "book.metadata.goodreadsRating")
    @Mapping(target = "goodreadsReviewCount", source = "book.metadata.goodreadsReviewCount")
    @Mapping(target = "libraryName", source = "book.library.name")
    @Mapping(target = "shelves", source = "book.shelves", qualifiedByName = "mapShelves")
    @Mapping(target = "readProgress", source = "progress", qualifiedByName = "mapReadProgress")
    @Mapping(target = "primaryFileType", source = "book", qualifiedByName = "mapPrimaryFileType")
    @Mapping(target = "coverUpdatedOn", source = "book.metadata.coverUpdatedOn")
    @Mapping(target = "audiobookCoverUpdatedOn", source = "book.metadata.audiobookCoverUpdatedOn")
    @Mapping(target = "isPhysical", source = "book.isPhysical")
    @Mapping(target = "fileTypes", source = "book", qualifiedByName = "mapFileTypes")
    @Mapping(target = "files", source = "book", qualifiedByName = "mapFiles")
    @Mapping(target = "epubProgress", expression = "java(mapEpubProgress(progress, fileProgress))")
    @Mapping(target = "pdfProgress", expression = "java(mapPdfProgress(progress, fileProgress))")
    @Mapping(target = "cbxProgress", expression = "java(mapCbxProgress(progress, fileProgress))")
    @Mapping(target = "audiobookProgress", expression = "java(mapAudiobookProgress(fileProgress))")
    @Mapping(target = "koreaderProgress", expression = "java(mapKoreaderProgress(progress, book))")
    AppBookDetail toDetail(BookEntity book, UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress);

    default AppBookProgressResponse toProgressResponse(UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress) {
        return AppBookProgressResponse.builder()
                .readProgress(mapReadProgress(progress))
                .readStatus(progress != null && progress.getReadStatus() != null
                        ? progress.getReadStatus().name() : null)
                .lastReadTime(progress != null ? progress.getLastReadTime() : null)
                .epubProgress(mapEpubProgress(progress, fileProgress))
                .pdfProgress(mapPdfProgress(progress, fileProgress))
                .cbxProgress(mapCbxProgress(progress, fileProgress))
                .audiobookProgress(mapAudiobookProgress(fileProgress))
                .koreaderProgress(mapKoreaderProgress(progress, null))
                .build();
    }

    @Named("mapAuthors")
    default List<String> mapAuthors(List<AuthorEntity> authors) {
        if (authors == null || authors.isEmpty()) {
            return Collections.emptyList();
        }
        return authors.stream()
                .map(AuthorEntity::getName)
                .toList();
    }

    @Named("mapCategories")
    default Set<String> mapCategories(Set<CategoryEntity> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptySet();
        }
        return categories.stream()
                .map(CategoryEntity::getName)
                .collect(Collectors.toSet());
    }

    @Named("mapThumbnailUrl")
    default String mapThumbnailUrl(BookEntity book) {
        if (book == null || book.getId() == null) {
            return null;
        }
        return "/api/books/" + book.getId() + "/cover";
    }

    @Named("mapFileSizeKb")
    default Long mapFileSizeKb(BookEntity book) {
        if (book == null) return null;
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        return primaryFile != null ? primaryFile.getFileSizeKb() : null;
    }

    @Named("mapShelves")
    default List<AppShelfSummary> mapShelves(Set<ShelfEntity> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return Collections.emptyList();
        }
        return shelves.stream()
                .map(this::toShelfSummary)
                .collect(Collectors.toList());
    }

    default AppShelfSummary toShelfSummary(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return AppShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookCount())
                .publicShelf(shelf.isPublic())
                .build();
    }

    @Named("mapReadProgress")
    default Float mapReadProgress(UserBookProgressEntity progress) {
        if (progress == null) {
            return null;
        }
        if (progress.getKoreaderProgressPercent() != null) {
            if (progress.getEpubProgressPercent() != null
                    && isNewerOrUntracked(progress.getLastReadTime(), progress.getKoreaderLastSyncTime())) {
                return progress.getEpubProgressPercent();
            }
            return progress.getKoreaderProgressPercent();
        }
        if (progress.getKoboProgressPercent() != null) {
            return progress.getKoboProgressPercent();
        }
        if (progress.getEpubProgressPercent() != null) {
            return progress.getEpubProgressPercent();
        }
        if (progress.getPdfProgressPercent() != null) {
            return progress.getPdfProgressPercent();
        }
        if (progress.getCbxProgressPercent() != null) {
            return progress.getCbxProgressPercent();
        }
        return null;
    }

    @Named("mapEpubProgress")
    default AppBookDetail.EpubProgress mapEpubProgress(UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress) {
        String cfi = null;
        String href = null;
        String anchor = null;
        Float contentSourceProgressPercent = null;
        Float percentage = null;
        Long bookFileId = null;
        String locatorPrecision = null;
        Instant updatedAt = null;
        Instant koreaderLastSyncTime = progress != null ? progress.getKoreaderLastSyncTime() : null;

        if (progress != null && hasValidEpubCfi(progress.getEpubProgress())
                && isNewerOrUntracked(progress.getLastReadTime(), koreaderLastSyncTime)) {
            cfi = progress.getEpubProgress();
            href = progress.getEpubProgressHref();
            anchor = extractAnchor(href);
            percentage = progress.getEpubProgressPercent();
            locatorPrecision = "exact";
            updatedAt = progress.getLastReadTime();
        }

        if (cfi == null && fileProgress != null && fileProgress.getBookFile() != null
                && fileProgress.getBookFile().getBookType() != null
                && switch (fileProgress.getBookFile().getBookType()) {
                    case EPUB, FB2, MOBI, AZW3 -> true;
                    default -> false;
                }
                && hasValidEpubCfi(fileProgress.getPositionData())
                && isNewerOrUntracked(fileProgress.getLastReadTime(), koreaderLastSyncTime)) {
            cfi = fileProgress.getPositionData();
            href = fileProgress.getPositionHref();
            anchor = extractAnchor(href);
            contentSourceProgressPercent = fileProgress.getContentSourceProgressPercent();
            percentage = fileProgress.getProgressPercent();
            bookFileId = fileProgress.getBookFile().getId();
            locatorPrecision = contentSourceProgressPercent != null ? "exact" : "percentage_only";
            updatedAt = fileProgress.getLastReadTime();
        }

        if (cfi == null && progress != null && progress.getKoreaderProgressPercent() != null) {
            percentage = progress.getKoreaderProgressPercent() * 100f;
            updatedAt = koreaderLastSyncTime != null ? koreaderLastSyncTime : progress.getLastReadTime();
            locatorPrecision = "percentage_only";
        }

        if (locatorPrecision == null) {
            locatorPrecision = (cfi != null || href != null) ? "exact" : "percentage_only";
        }

        if (cfi == null && percentage == null && contentSourceProgressPercent == null) {
            return null;
        }
        return AppBookDetail.EpubProgress.builder()
                .cfi(cfi)
                .href(href)
                .anchor(anchor)
                .contentSourceProgressPercent(contentSourceProgressPercent)
                .bookFileId(bookFileId)
                .locatorPrecision(locatorPrecision)
                .percentage(percentage)
                .updatedAt(updatedAt)
                .build();
    }

    private boolean hasValidEpubCfi(String cfi) {
        return cfi != null && cfi.startsWith("epubcfi(");
    }

    private boolean isNewerOrUntracked(Instant candidate, Instant baseline) {
        if (candidate == null) {
            return baseline == null;
        }
        return baseline == null || candidate.isAfter(baseline);
    }

    @Named("mapPdfProgress")
    default AppBookDetail.PdfProgress mapPdfProgress(UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress) {
        Integer page = progress != null ? progress.getPdfProgress() : null;
        Float percentage = progress != null ? progress.getPdfProgressPercent() : null;
        Long bookFileId = null;
        String locatorPrecision = null;
        java.time.Instant updatedAt = progress != null ? progress.getLastReadTime()
                : (fileProgress != null ? fileProgress.getLastReadTime() : null);

        if (fileProgress != null && fileProgress.getBookFile() != null && fileProgress.getBookFile().getBookType() == BookFileType.PDF) {
            page = parseIntOrNull(fileProgress.getPositionData());
            percentage = fileProgress.getProgressPercent();
            bookFileId = fileProgress.getBookFile().getId();
            locatorPrecision = page != null ? "exact" : "percentage_only";
        }

        if (page == null && percentage == null) {
            return null;
        }
        return AppBookDetail.PdfProgress.builder()
                .page(page)
                .bookFileId(bookFileId)
                .locatorPrecision(locatorPrecision)
                .percentage(percentage)
                .updatedAt(updatedAt)
                .build();
    }

    @Named("mapCbxProgress")
    default AppBookDetail.CbxProgress mapCbxProgress(UserBookProgressEntity progress, UserBookFileProgressEntity fileProgress) {
        Integer page = progress != null ? progress.getCbxProgress() : null;
        Float percentage = progress != null ? progress.getCbxProgressPercent() : null;
        Long bookFileId = null;
        String locatorPrecision = null;
        java.time.Instant updatedAt = progress != null ? progress.getLastReadTime()
                : (fileProgress != null ? fileProgress.getLastReadTime() : null);

        if (fileProgress != null && fileProgress.getBookFile() != null && fileProgress.getBookFile().getBookType() == BookFileType.CBX) {
            page = parseIntOrNull(fileProgress.getPositionData());
            percentage = fileProgress.getProgressPercent();
            bookFileId = fileProgress.getBookFile().getId();
            locatorPrecision = page != null ? "exact" : "percentage_only";
        }

        if (page == null && percentage == null) {
            return null;
        }
        return AppBookDetail.CbxProgress.builder()
                .page(page)
                .bookFileId(bookFileId)
                .locatorPrecision(locatorPrecision)
                .percentage(percentage)
                .updatedAt(updatedAt)
                .build();
    }

    @Named("mapKoreaderProgress")
    default AppBookDetail.KoreaderProgress mapKoreaderProgress(UserBookProgressEntity progress, BookEntity book) {
        if (progress == null || progress.getKoreaderProgressPercent() == null) {
            return null;
        }
        BookFileEntity bookFile = book != null ? book.getPrimaryBookFile() : null;
        return AppBookDetail.KoreaderProgress.builder()
                .percentage(progress.getKoreaderProgressPercent())
                .device(progress.getKoreaderDevice())
                .deviceId(progress.getKoreaderDeviceId())
                .bookFileId(bookFile != null ? bookFile.getId() : null)
                .bookHash(bookFile != null ? bookFile.getCurrentHash() : null)
                .currentHash(bookFile != null ? bookFile.getCurrentHash() : null)
                .initialHash(bookFile != null ? bookFile.getInitialHash() : null)
                .fileFormat(bookFile != null && bookFile.getBookType() != null ? bookFile.getBookType().name() : null)
                .source("KOREADER")
                .progressVersion(progress.getKoreaderLastSyncTime() != null ? progress.getKoreaderLastSyncTime().getEpochSecond() : null)
                .lastSyncTime(progress.getKoreaderLastSyncTime())
                .build();
    }

    @Named("mapAudiobookProgress")
    default AppBookDetail.AudiobookProgress mapAudiobookProgress(UserBookFileProgressEntity fileProgress) {
        if (fileProgress == null) return null;
        if (fileProgress.getBookFile() == null ||
            fileProgress.getBookFile().getBookType() != BookFileType.AUDIOBOOK) {
            return null;
        }

        return AppBookDetail.AudiobookProgress.builder()
                .positionMs(parseLongOrNull(fileProgress.getPositionData()))
                .trackIndex(parseIntOrNull(fileProgress.getPositionHref()))
                .bookFileId(fileProgress.getBookFile() != null ? fileProgress.getBookFile().getId() : null)
                .percentage(fileProgress.getProgressPercent())
                .updatedAt(fileProgress.getLastReadTime())
                .build();
    }

    @AfterMapping
    default void overlayFileProgress(BookEntity bookEntity,
                                     UserBookProgressEntity progress,
                                     UserBookFileProgressEntity fileProgress,
                                     @MappingTarget AppBookDetail book) {
        if (fileProgress == null || fileProgress.getBookFile() == null || fileProgress.getBookFile().getBookType() == null) {
            return;
        }
        switch (fileProgress.getBookFile().getBookType()) {
            case EPUB, FB2, MOBI, AZW3 -> book.setEpubProgress(mapEpubProgress(progress, fileProgress));
            case PDF -> book.setPdfProgress(mapPdfProgress(progress, fileProgress));
            case CBX -> book.setCbxProgress(mapCbxProgress(progress, fileProgress));
            case AUDIOBOOK -> book.setAudiobookProgress(mapAudiobookProgress(fileProgress));
        }
    }

    default String extractAnchor(String href) {
        if (href == null) return null;
        int anchorIndex = href.indexOf('#');
        if (anchorIndex < 0 || anchorIndex >= href.length() - 1) return null;
        return href.substring(anchorIndex + 1);
    }

    default Long parseLongOrNull(String value) {
        if (value == null) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    default Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Named("mapPrimaryFileType")
    default String mapPrimaryFileType(BookEntity book) {
        if (book == null) {
            return null;
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        if (primaryFile != null && primaryFile.getBookType() != null) {
            return primaryFile.getBookType().name();
        }
        return null;
    }

    @Named("mapFileTypes")
    default List<String> mapFileTypes(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null)
                .map(bf -> bf.getBookType().name())
                .distinct()
                .collect(Collectors.toList());
    }

    @Named("mapFiles")
    default List<AppBookFile> mapFiles(BookEntity book) {
        if (book == null || book.getBookFiles() == null || book.getBookFiles().isEmpty()) {
            return Collections.emptyList();
        }
        BookFileEntity primaryFile = book.getPrimaryBookFile();
        Long primaryId = primaryFile != null ? primaryFile.getId() : null;

        return book.getBookFiles().stream()
                .filter(bf -> bf.getBookType() != null && bf.isBook())
                .map(bf -> {
                    String extension = null;
                    try {
                        String fileName = bf.getFileName();
                        int lastDot = fileName.lastIndexOf('.');
                        if (lastDot > 0) {
                            extension = fileName.substring(lastDot + 1);
                        }
                    } catch (Exception e) {
                        // Handle case where extension cannot be extracted
                    }

                    return AppBookFile.builder()
                            .id(bf.getId())
                            .bookId(bf.getBook() != null ? bf.getBook().getId() : null)
                            .fileName(bf.getFileName())
                            .isBook(bf.isBook())
                            .folderBased(bf.isFolderBased())
                            .bookType(bf.getBookType().name())
                            .archiveType(bf.getArchiveType() != null ? bf.getArchiveType().name() : null)
                            .fileSizeKb(bf.getFileSizeKb())
                            .extension(extension)
                            .addedOn(bf.getAddedOn())
                            .isPrimary(bf.getId().equals(primaryId))
                            .build();
                })
                .collect(Collectors.toList());
    }

    default AppLibrarySummary toLibrarySummary(LibraryEntity library, long bookCount) {
        if (library == null) {
            return null;
        }
        List<AppLibrarySummary.PathSummary> paths = Collections.emptyList();
        if (library.getLibraryPaths() != null && !library.getLibraryPaths().isEmpty()) {
            paths = library.getLibraryPaths().stream()
                    .map(lp -> AppLibrarySummary.PathSummary.builder()
                            .id(lp.getId())
                            .path(lp.getPath())
                            .build())
                    .collect(Collectors.toList());
        }
        return AppLibrarySummary.builder()
                .id(library.getId())
                .name(library.getName())
                .icon(library.getIcon())
                .bookCount(bookCount)
                .allowedFormats(library.getAllowedFormats())
                .paths(paths)
                .build();
    }

    default AppShelfSummary toShelfSummaryFromEntity(ShelfEntity shelf) {
        if (shelf == null) {
            return null;
        }
        return AppShelfSummary.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .icon(shelf.getIcon())
                .bookCount(shelf.getBookCount())
                .publicShelf(shelf.isPublic())
                .build();
    }

    default AppMagicShelfSummary toMagicShelfSummary(MagicShelfEntity magicShelf) {
        if (magicShelf == null) {
            return null;
        }
        return AppMagicShelfSummary.builder()
                .id(magicShelf.getId())
                .name(magicShelf.getName())
                .icon(magicShelf.getIcon())
                .iconType(magicShelf.getIconType() != null ? magicShelf.getIconType().name() : null)
                .publicShelf(magicShelf.isPublic())
                .build();
    }
}
