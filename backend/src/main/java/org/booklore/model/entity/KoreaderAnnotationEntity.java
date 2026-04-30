package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * KOReader-native annotation (highlight + optional note).
 *
 * <p>Separate from {@link AnnotationEntity} which is CFI-based and used by the Web Reader.
 * This table preserves the raw KOReader location data (xpointer / page) so we never
 * have to convert into EPUB CFI for the GrimmLink companion sync.
 *
 * <p>Dedupe is performed via {@code dedupeKey} which is computed by the plugin from
 * stable fields (book_id + user_id + koreader_pos + text_hash). The unique constraint
 * on {@code (user_id, book_id, dedupe_key)} prevents duplicate inserts on retry.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "koreader_annotations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_koreader_annotations_user_book_key",
                        columnNames = {"user_id", "book_id", "dedupe_key"}
                )
        },
        indexes = {
                @Index(name = "idx_koreader_annotations_user_book", columnList = "user_id,book_id")
        }
)
public class KoreaderAnnotationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    /** Stable dedupe key computed by the plugin (book + user + koreader_pos + text). */
    @Column(name = "dedupe_key", nullable = false, length = 128)
    private String dedupeKey;

    /** Raw KOReader xpointer / location string (e.g. "/body/DocFragment[3]/body/p[7]/text().42"). */
    @Column(name = "koreader_pos", length = 1000)
    private String koreaderPos;

    /** PDF/CBZ page number if applicable. */
    @Column(name = "page")
    private Integer page;

    /** Chapter title at the time the highlight was made. */
    @Column(name = "chapter", length = 500)
    private String chapter;

    /** Highlighted text. */
    @Column(name = "text", length = 5000)
    private String text;

    /** Optional user note attached to the highlight. */
    @Column(name = "note", length = 5000)
    private String note;

    /** KOReader highlight color (e.g. "yellow", "red"). */
    @Column(name = "color", length = 32)
    private String color;

    /** KOReader drawer / style (e.g. "lighten", "underscore"). */
    @Column(name = "drawer", length = 32)
    private String drawer;

    /** Always "KOREADER" for now — kept explicit for future Hardcover/Other source rows. */
    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private String source = "KOREADER";

    /** Timestamp the annotation was created in KOReader (epoch seconds), if reported by the plugin. */
    @Column(name = "koreader_created_at")
    private Long koreaderCreatedAt;

    /** Timestamp the annotation was last modified in KOReader (epoch seconds), if reported by the plugin. */
    @Column(name = "koreader_updated_at")
    private Long koreaderUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
