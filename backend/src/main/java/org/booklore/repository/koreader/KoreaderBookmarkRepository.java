package org.booklore.repository.koreader;

import org.booklore.model.entity.KoreaderBookmarkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KoreaderBookmarkRepository extends JpaRepository<KoreaderBookmarkEntity, Long> {

    List<KoreaderBookmarkEntity> findByUserIdAndBookId(Long userId, Long bookId);

    /** Used by the plugin's incremental pull (since=updatedAt watermark). */
    List<KoreaderBookmarkEntity> findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(
            Long userId, Long bookId, LocalDateTime updatedAt);

    Optional<KoreaderBookmarkEntity> findByUserIdAndBookIdAndDedupeKey(Long userId, Long bookId, String dedupeKey);
}
