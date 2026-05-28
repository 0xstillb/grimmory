package org.booklore.model.entity.koreader;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.enums.KoreaderMetadataItemType;
import org.booklore.model.enums.KoreaderMetadataSource;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "koreader_metadata_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_koreader_metadata_user_book_type_dedupe",
                        columnNames = {"user_id", "book_id", "item_type", "dedupe_key"}
                )
        },
        indexes = {
                @Index(name = "idx_koreader_metadata_user_book", columnList = "user_id, book_id"),
                @Index(name = "idx_koreader_metadata_book_file", columnList = "book_file_id"),
                @Index(name = "idx_koreader_metadata_item_type", columnList = "item_type"),
                @Index(name = "idx_koreader_metadata_updated_at", columnList = "updated_at")
        }
)
public class KoreaderMetadataItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_file_id")
    private BookFileEntity bookFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private KoreaderMetadataItemType itemType;

    @Column(name = "dedupe_key", nullable = false, length = 512)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private KoreaderMetadataSource source;

    @Column(name = "device", length = 100)
    private String device;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "original_rating_value")
    private Integer originalRatingValue;

    @Column(name = "original_rating_scale")
    private Integer originalRatingScale;

    @Column(name = "normalized_rating_10")
    private Integer normalizedRating10;

    @Column(name = "annotation_type", length = 64)
    private String annotationType;

    @Column(name = "text", length = 8000)
    private String text;

    @Column(name = "note", length = 8000)
    private String note;

    @Column(name = "color", length = 64)
    private String color;

    @Column(name = "drawer", length = 64)
    private String drawer;

    @Column(name = "style", length = 64)
    private String style;

    @Column(name = "chapter", length = 1024)
    private String chapter;

    @Column(name = "page")
    private Integer page;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Lob
    @Column(name = "location_json")
    private String locationJson;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "client_created_at")
    private Instant clientCreatedAt;

    @Column(name = "client_updated_at")
    private Instant clientUpdatedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (syncedAt == null) {
            syncedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (syncedAt == null) {
            syncedAt = updatedAt;
        }
    }
}
