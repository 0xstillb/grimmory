package org.booklore.model.entity.koreader;

import jakarta.persistence.*;
import lombok.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "koreader_progress",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_koreader_progress_user_book_file", columnNames = {"user_id", "book_file_id"}),
                @UniqueConstraint(name = "uk_koreader_progress_user_book_hash", columnNames = {"user_id", "book_id", "book_hash"})
        },
        indexes = {
                @Index(name = "idx_koreader_progress_user_hash", columnList = "user_id, book_hash"),
                @Index(name = "idx_koreader_progress_book", columnList = "book_id"),
                @Index(name = "idx_koreader_progress_book_file", columnList = "book_file_id"),
                @Index(name = "idx_koreader_progress_updated_at", columnList = "updated_at")
        }
)
public class KoreaderProgressEntity {

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

    @Column(name = "book_hash", nullable = false, length = 128)
    private String bookHash;

    @Column(name = "document", length = 512)
    private String document;

    @Column(name = "file_format", length = 32)
    private String fileFormat;

    @Column(name = "progress", length = 2000)
    private String progress;

    @Column(name = "location", length = 2000)
    private String location;

    @Column(name = "percentage")
    private Float percentage;

    @Column(name = "current_page")
    private Integer currentPage;

    @Column(name = "total_pages")
    private Integer totalPages;

    @Column(name = "device", length = 100)
    private String device;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

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
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
