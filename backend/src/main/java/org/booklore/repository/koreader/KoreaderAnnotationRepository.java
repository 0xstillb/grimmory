package org.booklore.repository.koreader;

import org.booklore.model.entity.KoreaderAnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KoreaderAnnotationRepository extends JpaRepository<KoreaderAnnotationEntity, Long> {

    List<KoreaderAnnotationEntity> findByUserIdAndBookId(Long userId, Long bookId);

    /** Used by the plugin's incremental pull (since=updatedAt watermark). */
    List<KoreaderAnnotationEntity> findByUserIdAndBookIdAndUpdatedAtGreaterThanEqual(
            Long userId, Long bookId, LocalDateTime updatedAt);

    Optional<KoreaderAnnotationEntity> findByUserIdAndBookIdAndDedupeKey(Long userId, Long bookId, String dedupeKey);
}
