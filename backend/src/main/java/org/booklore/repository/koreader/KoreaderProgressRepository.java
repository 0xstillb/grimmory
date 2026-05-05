package org.booklore.repository.koreader;

import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoreaderProgressRepository extends JpaRepository<KoreaderProgressEntity, Long> {

    Optional<KoreaderProgressEntity> findByUserIdAndBookId(Long userId, Long bookId);

    Optional<KoreaderProgressEntity> findByUserIdAndBookFileId(Long userId, Long bookFileId);
}
