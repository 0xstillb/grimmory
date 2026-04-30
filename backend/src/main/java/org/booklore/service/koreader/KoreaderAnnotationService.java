package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderAnnotationDto;
import org.booklore.model.dto.koreader.KoreaderBatchResultDto;
import org.booklore.model.dto.koreader.KoreaderBookmarkDto;
import org.booklore.model.dto.koreader.KoreaderRatingDto;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.KoreaderAnnotationEntity;
import org.booklore.model.entity.KoreaderBookmarkEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderAnnotationRepository;
import org.booklore.repository.koreader.KoreaderBookmarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * KOReader-native annotation, bookmark and rating sync service.
 *
 * <p>Important policy invariants:
 * <ul>
 *   <li>This service NEVER deletes any book record or library file. It only writes
 *       into the {@code koreader_annotations}, {@code koreader_bookmarks} and
 *       {@code user_book_progress.personal_rating} columns.</li>
 *   <li>It does NOT touch the existing CFI-based {@code AnnotationEntity} /
 *       {@code BookMarkEntity} tables used by the Web Reader. Web Reader bridge
 *       and EPUB CFI conversion are intentionally out of scope for Prompt 6.</li>
 *   <li>It does NOT call any shelf removal or library delete code path.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class KoreaderAnnotationService {

    private final KoreaderAnnotationRepository annotationRepository;
    private final KoreaderBookmarkRepository bookmarkRepository;
    private final UserBookProgressRepository userBookProgressRepository;
    private final BookRepository bookRepository;
    private final KoreaderSecurityContextService securityContextService;

    // ---------- Annotations ----------

    public List<KoreaderAnnotationDto> listAnnotations(Long bookId, Long sinceEpochSec) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);
        if (sinceEpochSec != null) {
            LocalDateTime since = LocalDateTime.ofInstant(Instant.ofEpochSecond(sinceEpochSec), ZoneOffset.UTC);
            return annotationRepository
                    .findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(reader.getId(), book.getId(), since)
                    .stream().map(this::toAnnotationDto).toList();
        }
        return annotationRepository.findByUserIdAndBookId(reader.getId(), book.getId()).stream()
                .map(this::toAnnotationDto)
                .toList();
    }

    @Transactional
    public KoreaderBatchResultDto upsertAnnotations(Long bookId, List<KoreaderAnnotationDto> items) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);
        List<String> errors = new ArrayList<>();
        int inserted = 0, updated = 0, skipped = 0, failed = 0;
        int received = items != null ? items.size() : 0;
        if (items == null || items.isEmpty()) {
            return KoreaderBatchResultDto.builder().received(0).build();
        }

        for (KoreaderAnnotationDto dto : items) {
            try {
                if (dto.getDedupeKey() == null || dto.getDedupeKey().isBlank()) {
                    skipped++;
                    errors.add("missing dedupeKey");
                    continue;
                }
                var existingOpt = annotationRepository
                        .findByUserIdAndBookIdAndDedupeKey(reader.getId(), book.getId(), dto.getDedupeKey());

                if (existingOpt.isPresent()) {
                    KoreaderAnnotationEntity existing = existingOpt.get();
                    boolean changed = false;
                    if (dto.getKoreaderUpdatedAt() != null && existing.getKoreaderUpdatedAt() != null
                            && dto.getKoreaderUpdatedAt() <= existing.getKoreaderUpdatedAt()) {
                        // remote is older or equal — keep existing
                        skipped++;
                        continue;
                    }
                    if (dto.getText() != null && !dto.getText().equals(existing.getText())) { existing.setText(dto.getText()); changed = true; }
                    if (dto.getNote() != null && !dto.getNote().equals(existing.getNote())) { existing.setNote(dto.getNote()); changed = true; }
                    if (dto.getColor() != null) { existing.setColor(dto.getColor()); changed = true; }
                    if (dto.getDrawer() != null) { existing.setDrawer(dto.getDrawer()); changed = true; }
                    if (dto.getChapter() != null) { existing.setChapter(dto.getChapter()); changed = true; }
                    if (dto.getPage() != null) { existing.setPage(dto.getPage()); changed = true; }
                    if (dto.getKoreaderUpdatedAt() != null) { existing.setKoreaderUpdatedAt(dto.getKoreaderUpdatedAt()); changed = true; }
                    if (changed) {
                        annotationRepository.save(existing);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    KoreaderAnnotationEntity entity = KoreaderAnnotationEntity.builder()
                            .user(reader)
                            .book(book)
                            .dedupeKey(dto.getDedupeKey())
                            .koreaderPos(dto.getKoreaderPos())
                            .page(dto.getPage())
                            .chapter(dto.getChapter())
                            .text(dto.getText())
                            .note(dto.getNote())
                            .color(dto.getColor())
                            .drawer(dto.getDrawer())
                            .source(dto.getSource() != null ? dto.getSource() : "KOREADER")
                            .koreaderCreatedAt(dto.getKoreaderCreatedAt())
                            .koreaderUpdatedAt(dto.getKoreaderUpdatedAt())
                            .build();
                    annotationRepository.save(entity);
                    inserted++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(safeError(e));
                log.warn("GrimmLink annotation upsert failed (bookId={}, dedupeKey={}): {}",
                        bookId, dto.getDedupeKey(), e.getMessage());
            }
        }

        return KoreaderBatchResultDto.builder()
                .received(received).inserted(inserted).updated(updated).skipped(skipped).failed(failed)
                .errors(errors.isEmpty() ? null : errors)
                .build();
    }

    // ---------- Bookmarks ----------

    public List<KoreaderBookmarkDto> listBookmarks(Long bookId, Long sinceEpochSec) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);
        if (sinceEpochSec != null) {
            LocalDateTime since = LocalDateTime.ofInstant(Instant.ofEpochSecond(sinceEpochSec), ZoneOffset.UTC);
            return bookmarkRepository
                    .findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(reader.getId(), book.getId(), since)
                    .stream().map(this::toBookmarkDto).toList();
        }
        return bookmarkRepository.findByUserIdAndBookId(reader.getId(), book.getId()).stream()
                .map(this::toBookmarkDto)
                .toList();
    }

    @Transactional
    public KoreaderBatchResultDto upsertBookmarks(Long bookId, List<KoreaderBookmarkDto> items) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);
        List<String> errors = new ArrayList<>();
        int inserted = 0, updated = 0, skipped = 0, failed = 0;
        int received = items != null ? items.size() : 0;
        if (items == null || items.isEmpty()) {
            return KoreaderBatchResultDto.builder().received(0).build();
        }

        for (KoreaderBookmarkDto dto : items) {
            try {
                if (dto.getDedupeKey() == null || dto.getDedupeKey().isBlank()) {
                    skipped++;
                    errors.add("missing dedupeKey");
                    continue;
                }
                var existingOpt = bookmarkRepository
                        .findByUserIdAndBookIdAndDedupeKey(reader.getId(), book.getId(), dto.getDedupeKey());

                if (existingOpt.isPresent()) {
                    KoreaderBookmarkEntity existing = existingOpt.get();
                    boolean changed = false;
                    if (dto.getText() != null && !dto.getText().equals(existing.getText())) { existing.setText(dto.getText()); changed = true; }
                    if (dto.getNote() != null && !dto.getNote().equals(existing.getNote())) { existing.setNote(dto.getNote()); changed = true; }
                    if (dto.getChapter() != null) { existing.setChapter(dto.getChapter()); changed = true; }
                    if (dto.getPage() != null) { existing.setPage(dto.getPage()); changed = true; }
                    if (changed) {
                        bookmarkRepository.save(existing);
                        updated++;
                    } else {
                        skipped++;
                    }
                } else {
                    KoreaderBookmarkEntity entity = KoreaderBookmarkEntity.builder()
                            .user(reader)
                            .book(book)
                            .dedupeKey(dto.getDedupeKey())
                            .koreaderPos(dto.getKoreaderPos())
                            .page(dto.getPage())
                            .chapter(dto.getChapter())
                            .text(dto.getText())
                            .note(dto.getNote())
                            .source(dto.getSource() != null ? dto.getSource() : "KOREADER")
                            .koreaderCreatedAt(dto.getKoreaderCreatedAt())
                            .build();
                    bookmarkRepository.save(entity);
                    inserted++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(safeError(e));
                log.warn("GrimmLink bookmark upsert failed (bookId={}, dedupeKey={}): {}",
                        bookId, dto.getDedupeKey(), e.getMessage());
            }
        }

        return KoreaderBatchResultDto.builder()
                .received(received).inserted(inserted).updated(updated).skipped(skipped).failed(failed)
                .errors(errors.isEmpty() ? null : errors)
                .build();
    }

    // ---------- Rating ----------

    public KoreaderRatingDto getRating(Long bookId) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);
        Integer rating = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .map(UserBookProgressEntity::getPersonalRating)
                .orElse(null);
        return KoreaderRatingDto.builder().bookId(book.getId()).rating(rating).build();
    }

    @Transactional
    public KoreaderRatingDto updateRating(Long bookId, KoreaderRatingDto request) {
        BookLoreUserEntity reader = securityContextService.requireCurrentReaderEntity(true);
        BookEntity book = requireAccessibleBook(reader, bookId);

        Integer rating = request != null ? request.getRating() : null;
        if (rating != null && (rating < 1 || rating > 10)) {
            throw ApiError.GENERIC_BAD_REQUEST.createException("Rating must be between 1 and 10 or null");
        }

        UserBookProgressEntity progress = userBookProgressRepository
                .findByUserIdAndBookId(reader.getId(), book.getId())
                .orElseGet(() -> {
                    UserBookProgressEntity p = new UserBookProgressEntity();
                    p.setUser(reader);
                    p.setBook(book);
                    return p;
                });
        progress.setPersonalRating(rating);
        userBookProgressRepository.save(progress);

        return KoreaderRatingDto.builder().bookId(book.getId()).rating(rating).build();
    }

    // ---------- Helpers ----------

    private BookEntity requireAccessibleBook(BookLoreUserEntity reader, Long bookId) {
        BookEntity book = bookRepository.findById(bookId)
                .orElseThrow(() -> ApiError.BOOK_NOT_FOUND.createException(bookId));
        if (!canAccessBook(reader, book)) {
            throw ApiError.FORBIDDEN.createException("Book is not accessible to the authenticated user");
        }
        return book;
    }

    private boolean canAccessBook(BookLoreUserEntity reader, BookEntity book) {
        if (reader.getPermissions() != null && reader.getPermissions().isPermissionAdmin()) return true;
        if (reader.getLibraries() == null || book.getLibrary() == null) return false;
        return reader.getLibraries().stream()
                .anyMatch(library -> library.getId().equals(book.getLibrary().getId()));
    }

    private KoreaderAnnotationDto toAnnotationDto(KoreaderAnnotationEntity e) {
        return KoreaderAnnotationDto.builder()
                .id(e.getId())
                .bookId(e.getBook() != null ? e.getBook().getId() : null)
                .type("annotation")
                .dedupeKey(e.getDedupeKey())
                .koreaderPos(e.getKoreaderPos())
                .page(e.getPage())
                .chapter(e.getChapter())
                .text(e.getText())
                .note(e.getNote())
                .color(e.getColor())
                .drawer(e.getDrawer())
                .source(e.getSource())
                .koreaderCreatedAt(e.getKoreaderCreatedAt())
                .koreaderUpdatedAt(e.getKoreaderUpdatedAt())
                .createdAt(toEpochSeconds(e.getCreatedAt()))
                .updatedAt(toEpochSeconds(e.getUpdatedAt()))
                .build();
    }

    private KoreaderBookmarkDto toBookmarkDto(KoreaderBookmarkEntity e) {
        return KoreaderBookmarkDto.builder()
                .id(e.getId())
                .bookId(e.getBook() != null ? e.getBook().getId() : null)
                .type("bookmark")
                .dedupeKey(e.getDedupeKey())
                .koreaderPos(e.getKoreaderPos())
                .page(e.getPage())
                .chapter(e.getChapter())
                .text(e.getText())
                .note(e.getNote())
                .source(e.getSource())
                .koreaderCreatedAt(e.getKoreaderCreatedAt())
                .createdAt(toEpochSeconds(e.getCreatedAt()))
                .updatedAt(toEpochSeconds(e.getUpdatedAt()))
                .build();
    }

    private Long toEpochSeconds(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.toEpochSecond(ZoneOffset.UTC);
    }

    private String safeError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        return msg.length() > 250 ? msg.substring(0, 250) : msg;
    }
}
