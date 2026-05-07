package org.booklore.repository.koreader;

import org.booklore.model.entity.koreader.KoreaderProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KoreaderProgressRepository extends JpaRepository<KoreaderProgressEntity, Long> {

    Optional<KoreaderProgressEntity> findByUserIdAndBookFileId(Long userId, Long bookFileId);

    Optional<KoreaderProgressEntity> findByUserIdAndBookIdAndBookHash(Long userId, Long bookId, String bookHash);

    @Query("""
            SELECT kp FROM KoreaderProgressEntity kp
            WHERE kp.user.id = :userId
              AND kp.book.id = :bookId
            ORDER BY kp.updatedAt DESC
            """)
    Optional<KoreaderProgressEntity> findMostRecentByUserIdAndBookId(
            @Param("userId") Long userId,
            @Param("bookId") Long bookId);
}
