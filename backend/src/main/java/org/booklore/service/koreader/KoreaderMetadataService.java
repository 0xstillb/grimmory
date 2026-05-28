package org.booklore.service.koreader;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.koreader.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderMetadataItemEntity;
import org.booklore.model.enums.KoreaderMetadataItemType;
import org.booklore.model.enums.KoreaderMetadataSource;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderMetadataItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@AllArgsConstructor
public class KoreaderMetadataService {

    private static final Set<String> MUTABLE_SOURCE_NAMES = Set.of("KOREADER", "GRIMMLINK");

    private final KoreaderSecurityContextService securityContextService;
    private final BookRepository bookRepository;
    private final BookFileRepository bookFileRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final KoreaderMetadataItemRepository metadataItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MetadataSyncResponse syncMetadata(MetadataSyncRequest request) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        MetadataSyncRequest normalized = normalizeRequest(request);

        ResolvedBook resolvedBook = resolveBook(reader, normalized);
        var resultsBuilder = MetadataSyncResults.builder();
        List<ItemResult> annotationResults = new ArrayList<>();
        List<ItemResult> bookmarkResults = new ArrayList<>();
        resultsBuilder.annotations(annotationResults);
        resultsBuilder.bookmarks(bookmarkResults);

        if (!resolvedBook.found()) {
            ItemResult ratingFailure = null;
            if (normalized.getRating() != null) {
                ratingFailure = buildFailureResult("rating", normalized.getRating().getDedupeKey(), "book_not_found");
            }
            resultsBuilder.rating(ratingFailure);
            for (AnnotationPayload annotation : normalized.getAnnotations()) {
                annotationResults.add(buildFailureResult("annotation", annotation.getDedupeKey(), "book_not_found"));
            }
            for (BookmarkPayload bookmark : normalized.getBookmarks()) {
                bookmarkResults.add(buildFailureResult("bookmark", bookmark.getDedupeKey(), "book_not_found"));
            }
            return MetadataSyncResponse.builder()
                    .bookId(null)
                    .ok(false)
                    .results(resultsBuilder.build())
                    .build();
        }

        if (normalized.getRating() != null) {
            resultsBuilder.rating(processRating(reader, resolvedBook, normalized, normalized.getRating()));
        }
        for (AnnotationPayload annotation : normalized.getAnnotations()) {
            annotationResults.add(processAnnotation(reader, resolvedBook, normalized, annotation));
        }
        for (BookmarkPayload bookmark : normalized.getBookmarks()) {
            bookmarkResults.add(processBookmark(reader, resolvedBook, normalized, bookmark));
        }

        MetadataSyncResults results = resultsBuilder.build();
        boolean ok = isResponseOk(results);
        log.info(
                "GrimmLink metadata sync source=koreader direction=push apiStatus={} userId={} bookId={} ratingStatus={} annotationCount={} bookmarkCount={}",
                ok ? "ok" : "partial",
                reader.getId(),
                resolvedBook.book().getId(),
                results.getRating() != null ? results.getRating().getStatus() : "none",
                results.getAnnotations().size(),
                results.getBookmarks().size()
        );

        return MetadataSyncResponse.builder()
                .bookId(resolvedBook.book().getId())
                .ok(ok)
                .results(results)
                .build();
    }

    static String dedupePrefix(String dedupeKey) {
        if (dedupeKey == null) {
            return "";
        }
        String trimmed = dedupeKey.trim();
        if (trimmed.length() <= 16) {
            return trimmed;
        }
        return trimmed.substring(0, 16);
    }

    private ItemResult processRating(BookLoreUserEntity reader,
                                     ResolvedBook resolvedBook,
                                     MetadataSyncRequest request,
                                     RatingPayload payload) {
        String dedupeKey = trimToNull(payload.getDedupeKey());
        Integer scale = payload.getScale();
        Integer value = payload.getValue();
        if (dedupeKey == null || scale == null || value == null) {
            return buildInvalidResult("rating", dedupeKey, "invalid_rating_payload");
        }
        if ((scale != 5 && scale != 10) || value < 1 || value > scale) {
            return buildInvalidResult("rating", dedupeKey, "invalid_rating_scale_or_value");
        }

        int normalizedRating10 = scale == 5 ? value * 2 : value;
        KoreaderMetadataSource source = resolveSource(payload.getSource());
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("itemType", "rating");
        canonical.put("value", value);
        canonical.put("scale", scale);
        canonical.put("normalizedRating10", normalizedRating10);
        canonical.put("updatedAt", payload.getUpdatedAt());
        canonical.put("source", source.name());
        String contentHash = sha256Hex(toJson(canonical));

        Optional<KoreaderMetadataItemEntity> existingOpt = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(),
                        resolvedBook.book().getId(),
                        KoreaderMetadataItemType.RATING,
                        dedupeKey
                );
        KoreaderMetadataItemEntity entity = existingOpt.orElseGet(KoreaderMetadataItemEntity::new);
        boolean isNew = existingOpt.isEmpty();
        boolean sameContent = !isNew && Objects.equals(existingOpt.get().getContentHash(), contentHash);
        if (sameContent) {
            return buildResult("rating", dedupeKey, "duplicate", String.valueOf(existingOpt.get().getId()), null);
        }
        if (!isNew && !isMutableSource(existingOpt.get().getSource())) {
            return buildResult("rating", dedupeKey, "skipped", String.valueOf(existingOpt.get().getId()), "immutable_source");
        }

        entity.setUser(reader);
        entity.setBook(resolvedBook.book());
        entity.setBookFile(resolvedBook.bookFile());
        entity.setItemType(KoreaderMetadataItemType.RATING);
        entity.setDedupeKey(dedupeKey);
        entity.setSource(source);
        entity.setDevice(trimToNull(request.getDevice()));
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setOriginalRatingValue(value);
        entity.setOriginalRatingScale(scale);
        entity.setNormalizedRating10(normalizedRating10);
        entity.setText(null);
        entity.setNote(null);
        entity.setColor(null);
        entity.setDrawer(null);
        entity.setStyle(null);
        entity.setChapter(null);
        entity.setPage(null);
        entity.setAnnotationType("rating");
        entity.setLocationJson(null);
        entity.setPayloadJson(toJson(payload));
        entity.setContentHash(contentHash);
        entity.setClientCreatedAt(null);
        entity.setClientUpdatedAt(payload.getUpdatedAt());
        entity.setSyncedAt(Instant.now());
        KoreaderMetadataItemEntity saved = metadataItemRepository.save(entity);

        applyRatingIfAllowed(reader, resolvedBook.book(), normalizedRating10);
        return buildResult("rating", dedupeKey, isNew ? "synced" : "updated", String.valueOf(saved.getId()), null);
    }

    private ItemResult processAnnotation(BookLoreUserEntity reader,
                                         ResolvedBook resolvedBook,
                                         MetadataSyncRequest request,
                                         AnnotationPayload payload) {
        String dedupeKey = trimToNull(payload.getDedupeKey());
        String text = trimToNull(payload.getText());
        String note = trimToNull(payload.getNote());
        if (dedupeKey == null) {
            return buildInvalidResult("annotation", null, "missing_dedupe_key");
        }
        if (text == null && note == null) {
            return buildInvalidResult("annotation", dedupeKey, "missing_text_or_note");
        }

        KoreaderMetadataSource source = KoreaderMetadataSource.GRIMMLINK;
        String annotationType = trimToNull(payload.getType());
        if (annotationType == null) {
            annotationType = "highlight";
        }
        LocationPayload location = normalizeLocation(payload.getLocation());

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("itemType", "annotation");
        canonical.put("type", annotationType);
        canonical.put("text", text);
        canonical.put("note", note);
        canonical.put("color", trimToNull(payload.getColor()));
        canonical.put("drawer", trimToNull(payload.getDrawer()));
        canonical.put("style", trimToNull(payload.getStyle()));
        canonical.put("chapter", trimToNull(payload.getChapter()));
        canonical.put("page", payload.getPage());
        canonical.put("location", location);
        canonical.put("createdAt", payload.getCreatedAt());
        canonical.put("updatedAt", payload.getUpdatedAt());
        canonical.put("source", source.name());
        String contentHash = sha256Hex(toJson(canonical));

        Optional<KoreaderMetadataItemEntity> existingOpt = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(),
                        resolvedBook.book().getId(),
                        KoreaderMetadataItemType.ANNOTATION,
                        dedupeKey
                );
        KoreaderMetadataItemEntity entity = existingOpt.orElseGet(KoreaderMetadataItemEntity::new);
        boolean isNew = existingOpt.isEmpty();
        boolean sameContent = !isNew && Objects.equals(existingOpt.get().getContentHash(), contentHash);
        if (sameContent) {
            return buildResult("annotation", dedupeKey, "duplicate", String.valueOf(existingOpt.get().getId()), null);
        }
        if (!isNew && !isMutableSource(existingOpt.get().getSource())) {
            return buildResult("annotation", dedupeKey, "skipped", String.valueOf(existingOpt.get().getId()), "immutable_source");
        }

        entity.setUser(reader);
        entity.setBook(resolvedBook.book());
        entity.setBookFile(resolvedBook.bookFile());
        entity.setItemType(KoreaderMetadataItemType.ANNOTATION);
        entity.setDedupeKey(dedupeKey);
        entity.setSource(source);
        entity.setDevice(trimToNull(request.getDevice()));
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setOriginalRatingValue(null);
        entity.setOriginalRatingScale(null);
        entity.setNormalizedRating10(null);
        entity.setAnnotationType(annotationType);
        entity.setText(text);
        entity.setNote(note);
        entity.setColor(trimToNull(payload.getColor()));
        entity.setDrawer(trimToNull(payload.getDrawer()));
        entity.setStyle(trimToNull(payload.getStyle()));
        entity.setChapter(trimToNull(payload.getChapter()));
        entity.setPage(payload.getPage());
        entity.setLocationJson(toJson(location));
        entity.setPayloadJson(toJson(payload));
        entity.setContentHash(contentHash);
        entity.setClientCreatedAt(payload.getCreatedAt());
        entity.setClientUpdatedAt(payload.getUpdatedAt());
        entity.setSyncedAt(Instant.now());
        KoreaderMetadataItemEntity saved = metadataItemRepository.save(entity);

        log.info(
                "GrimmLink metadata item processed itemType=annotation status={} bookId={} dedupePrefix={}",
                isNew ? "synced" : "updated",
                resolvedBook.book().getId(),
                dedupePrefix(dedupeKey)
        );
        return buildResult("annotation", dedupeKey, isNew ? "synced" : "updated", String.valueOf(saved.getId()), null);
    }

    private ItemResult processBookmark(BookLoreUserEntity reader,
                                       ResolvedBook resolvedBook,
                                       MetadataSyncRequest request,
                                       BookmarkPayload payload) {
        String dedupeKey = trimToNull(payload.getDedupeKey());
        String title = trimToNull(payload.getTitle());
        String notes = trimToNull(payload.getNotes());
        String chapter = trimToNull(payload.getChapter());
        Integer page = payload.getPage();
        LocationPayload location = normalizeLocation(payload.getLocation());
        boolean hasLocation = page != null
                || chapter != null
                || title != null
                || notes != null
                || trimToNull(location.getPos0()) != null
                || trimToNull(location.getPos1()) != null
                || location.getPageno() != null
                || trimToNull(location.getRaw()) != null
                || trimToNull(location.getCfi()) != null;

        if (dedupeKey == null) {
            return buildInvalidResult("bookmark", null, "missing_dedupe_key");
        }
        if (!hasLocation) {
            return buildInvalidResult("bookmark", dedupeKey, "missing_bookmark_location");
        }

        KoreaderMetadataSource source = KoreaderMetadataSource.GRIMMLINK;
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("itemType", "bookmark");
        canonical.put("title", title);
        canonical.put("notes", notes);
        canonical.put("chapter", chapter);
        canonical.put("page", page);
        canonical.put("location", location);
        canonical.put("createdAt", payload.getCreatedAt());
        canonical.put("updatedAt", payload.getUpdatedAt());
        canonical.put("source", source.name());
        String contentHash = sha256Hex(toJson(canonical));

        Optional<KoreaderMetadataItemEntity> existingOpt = metadataItemRepository
                .findByUserIdAndBookIdAndItemTypeAndDedupeKey(
                        reader.getId(),
                        resolvedBook.book().getId(),
                        KoreaderMetadataItemType.BOOKMARK,
                        dedupeKey
                );
        KoreaderMetadataItemEntity entity = existingOpt.orElseGet(KoreaderMetadataItemEntity::new);
        boolean isNew = existingOpt.isEmpty();
        boolean sameContent = !isNew && Objects.equals(existingOpt.get().getContentHash(), contentHash);
        if (sameContent) {
            return buildResult("bookmark", dedupeKey, "duplicate", String.valueOf(existingOpt.get().getId()), null);
        }
        if (!isNew && !isMutableSource(existingOpt.get().getSource())) {
            return buildResult("bookmark", dedupeKey, "skipped", String.valueOf(existingOpt.get().getId()), "immutable_source");
        }

        entity.setUser(reader);
        entity.setBook(resolvedBook.book());
        entity.setBookFile(resolvedBook.bookFile());
        entity.setItemType(KoreaderMetadataItemType.BOOKMARK);
        entity.setDedupeKey(dedupeKey);
        entity.setSource(source);
        entity.setDevice(trimToNull(request.getDevice()));
        entity.setDeviceId(trimToNull(request.getDeviceId()));
        entity.setOriginalRatingValue(null);
        entity.setOriginalRatingScale(null);
        entity.setNormalizedRating10(null);
        entity.setAnnotationType("bookmark");
        entity.setText(title);
        entity.setNote(notes);
        entity.setColor(null);
        entity.setDrawer(null);
        entity.setStyle(null);
        entity.setChapter(chapter);
        entity.setPage(page);
        entity.setLocationJson(toJson(location));
        entity.setPayloadJson(toJson(payload));
        entity.setContentHash(contentHash);
        entity.setClientCreatedAt(payload.getCreatedAt());
        entity.setClientUpdatedAt(payload.getUpdatedAt());
        entity.setSyncedAt(Instant.now());
        KoreaderMetadataItemEntity saved = metadataItemRepository.save(entity);

        log.info(
                "GrimmLink metadata item processed itemType=bookmark status={} bookId={} dedupePrefix={}",
                isNew ? "synced" : "updated",
                resolvedBook.book().getId(),
                dedupePrefix(dedupeKey)
        );
        return buildResult("bookmark", dedupeKey, isNew ? "synced" : "updated", String.valueOf(saved.getId()), null);
    }

    private void applyRatingIfAllowed(BookLoreUserEntity reader, BookEntity book, int normalizedRating10) {
        UserBookProgressEntity progress = userBookProgressRepository.findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(UserBookProgressEntity::new);
        Integer existing = progress.getPersonalRating();
        if (existing != null && !Objects.equals(existing, normalizedRating10)) {
            log.info(
                    "GrimmLink metadata rating preserved manual rating userId={} bookId={} incoming={} existing={}",
                    reader.getId(),
                    book.getId(),
                    normalizedRating10,
                    existing
            );
            return;
        }
        progress.setUser(reader);
        progress.setBook(book);
        progress.setPersonalRating(normalizedRating10);
        userBookProgressRepository.save(progress);
    }

    private MetadataSyncRequest normalizeRequest(MetadataSyncRequest request) {
        MetadataSyncRequest normalized = request == null ? new MetadataSyncRequest() : request;
        if (normalized.getSchemaVersion() == null) {
            normalized.setSchemaVersion(1);
        }
        if (trimToNull(normalized.getSyncMode()) == null) {
            normalized.setSyncMode("incremental");
        }
        if (normalized.getAnnotations() == null) {
            normalized.setAnnotations(new ArrayList<>());
        }
        if (normalized.getBookmarks() == null) {
            normalized.setBookmarks(new ArrayList<>());
        }
        return normalized;
    }

    private boolean isResponseOk(MetadataSyncResults results) {
        if (results.getRating() != null && "failed".equals(results.getRating().getStatus())) {
            return false;
        }
        for (ItemResult item : results.getAnnotations()) {
            if ("failed".equals(item.getStatus())) {
                return false;
            }
        }
        for (ItemResult item : results.getBookmarks()) {
            if ("failed".equals(item.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private ResolvedBook resolveBook(BookLoreUserEntity reader, MetadataSyncRequest request) {
        if (request.getBookId() != null) {
            Optional<BookEntity> bookOpt = bookRepository.findByIdWithBookFiles(request.getBookId());
            if (bookOpt.isPresent() && canAccessBook(reader, bookOpt.get())) {
                BookEntity book = bookOpt.get();
                BookFileEntity bookFile = null;
                if (request.getBookFileId() != null) {
                    bookFile = book.getBookFiles().stream()
                            .filter(file -> Objects.equals(file.getId(), request.getBookFileId()))
                            .findFirst()
                            .orElse(null);
                    if (bookFile == null) {
                        return ResolvedBook.notFound();
                    }
                }
                return new ResolvedBook(true, book, bookFile);
            }
            return ResolvedBook.notFound();
        }

        if (request.getBookFileId() != null) {
            Optional<BookFileEntity> fileOpt = bookFileRepository.findByIdWithBookAndLibraryPath(request.getBookFileId());
            if (fileOpt.isPresent()) {
                BookFileEntity file = fileOpt.get();
                BookEntity book = file.getBook();
                if (book != null && canAccessBook(reader, book)) {
                    return new ResolvedBook(true, book, file);
                }
            }
            return ResolvedBook.notFound();
        }

        String requestedHash = trimToNull(request.getBookHash());
        if (requestedHash != null) {
            List<BookEntity> candidates = bookRepository.findAllByBookHash(requestedHash);
            for (BookEntity candidate : candidates) {
                if (!canAccessBook(reader, candidate)) {
                    continue;
                }
                BookFileEntity matchedFile = null;
                if (candidate.getBookFiles() != null) {
                    for (BookFileEntity file : candidate.getBookFiles()) {
                        if (Objects.equals(requestedHash, trimToNull(file.getCurrentHash()))
                                || Objects.equals(requestedHash, trimToNull(file.getInitialHash()))) {
                            matchedFile = file;
                            break;
                        }
                    }
                }
                return new ResolvedBook(true, candidate, matchedFile);
            }
        }
        return ResolvedBook.notFound();
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) {
            return true;
        }
        if (reader.getLibraries() == null || book.getLibrary() == null) {
            return false;
        }
        return reader.getLibraries().stream()
                .anyMatch(library -> Objects.equals(library.getId(), book.getLibrary().getId()));
    }

    private KoreaderMetadataSource resolveSource(String source) {
        String normalized = trimToNull(source);
        if (normalized == null) {
            return KoreaderMetadataSource.KOREADER;
        }
        if ("grimmlink".equalsIgnoreCase(normalized)) {
            return KoreaderMetadataSource.GRIMMLINK;
        }
        return KoreaderMetadataSource.KOREADER;
    }

    private boolean isMutableSource(KoreaderMetadataSource source) {
        if (source == null) {
            return true;
        }
        return MUTABLE_SOURCE_NAMES.contains(source.name());
    }

    private LocationPayload normalizeLocation(LocationPayload location) {
        LocationPayload normalized = location == null ? new LocationPayload() : location;
        if (trimToNull(normalized.getKind()) == null) {
            normalized.setKind("koreader");
        }
        return normalized;
    }

    private ItemResult buildResult(String itemType, String dedupeKey, String status, String serverId, String error) {
        return ItemResult.builder()
                .itemType(itemType)
                .dedupeKey(trimToNull(dedupeKey))
                .status(status)
                .serverId(serverId)
                .error(error)
                .build();
    }

    private ItemResult buildInvalidResult(String itemType, String dedupeKey, String error) {
        log.info(
                "GrimmLink metadata item invalid itemType={} dedupePrefix={} error={}",
                itemType,
                dedupePrefix(dedupeKey),
                error
        );
        return buildResult(itemType, dedupeKey, "invalid", null, error);
    }

    private ItemResult buildFailureResult(String itemType, String dedupeKey, String error) {
        return buildResult(itemType, dedupeKey, "failed", null, error);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(Objects.hashCode(value));
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ResolvedBook(boolean found, BookEntity book, BookFileEntity bookFile) {
        static ResolvedBook notFound() {
            return new ResolvedBook(false, null, null);
        }
    }
}
