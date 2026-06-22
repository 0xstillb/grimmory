package org.booklore.grimmlink.repository;

import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GrimmLink-specific book lookups by hash.
 * <p>
 * Methods here are fork-specific additions that should not live
 * in the upstream {@code BookRepository} to reduce sync conflicts.
 */
@Repository
public interface GrimmLinkBookLookupRepository extends JpaRepository<BookEntity, Long> {

    /**
     * Find all active (non-deleted) books whose BookFiles match the given hash
     * on either currentHash or initialHash.
     */
    @Query("SELECT b FROM BookEntity b JOIN FETCH b.bookFiles bf " +
           "WHERE (bf.currentHash = :hash OR bf.initialHash = :hash) " +
           "AND bf.isBookFormat = true " +
           "AND (b.deleted IS NULL OR b.deleted = false)")
    List<BookEntity> findAllByBookHash(@Param("hash") String hash);
}
