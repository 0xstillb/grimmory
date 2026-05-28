package org.booklore.repository.koreader;

import org.booklore.model.entity.koreader.KoreaderMetadataItemEntity;
import org.booklore.model.enums.KoreaderMetadataItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KoreaderMetadataItemRepository extends JpaRepository<KoreaderMetadataItemEntity, Long> {

    Optional<KoreaderMetadataItemEntity> findByUserIdAndBookIdAndItemTypeAndDedupeKey(
            Long userId,
            Long bookId,
            KoreaderMetadataItemType itemType,
            String dedupeKey
    );
}
