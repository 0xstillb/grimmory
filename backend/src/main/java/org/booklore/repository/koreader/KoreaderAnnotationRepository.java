package org.booklore.repository.koreader;

import org.booklore.model.entity.KoreaderAnnotationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KoreaderAnnotationRepository extends JpaRepository<KoreaderAnnotationEntity, Long> {

    List<KoreaderAnnotationEntity> findByUserIdAndBookId(Long userId, Long bookId);

    Optional<KoreaderAnnotationEntity> findByUserIdAndBookIdAndDedupeKey(Long userId, Long bookId, String dedupeKey);
}
