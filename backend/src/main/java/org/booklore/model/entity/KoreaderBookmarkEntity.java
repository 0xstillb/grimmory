package org.booklore.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * KOReader-native bookmark.
 *
 * <p>Separate from {@link BookMarkEntity} which is CFI-based and used by the Web Reader.
 * This table preserves the raw KOReader location data (xpointer / page) so we never
 * have to convert into EPUB CFI for the GrimmLink companion sync.
 *
 * <p>Dedupe is performed via {@code dedupeKey} which is computed by the plugin from
 * stable fields (book_id + user_id + koreader_pos). The unique constraint
 * on {@code (user_id, book_id, dedupe_key)} prevents duplicate inserts on retry.
 */
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "koreader_bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_koreader_bookmarks_user_book_key",
                        columnNames = {"user_id", "book_id", "dedupe_key"}
                )
        },
        indexes = {
                @Index(name = "idx_koreader_bookmarks_user_book", columnList = "user_id,book_id")
        }
)
public class KoreaderBookmarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private BookLoreUserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private BookEntity book;

    /** Stable dedupe key computed by the plugin (book + user + koreader_pos). */
    @Column(name = "dedupe_key", nullable = false, length = 128)
    private String dedupeKey;

    /** Raw KOReader xpointer / location string. */
    @Column(name = "koreader_pos", length = 1000)
    private String koreaderPos;

    /** PDF/CBZ page number if applicable. */
    @Column(name = "page")
    private Integer page;

    /** Chapter title at the time the bookmark was made. */
    @Column(name = "chapter", length = 500)
    private String chapter;

    /** Selected text (or quoted line) at the bookmark, if any. */
    @Column(name = "text", length = 5000)
    private String text;

    /** Optional user note attached to the bookmark. */
    @Column(name = "note", length = 5000)
    private String note;

    /** Always "KOREADER" for now. */
    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private String source = "KOREADER";

    /** Timestamp the bookmark was created in KOReader (epoch seconds), if reported. */
    @Column(name = "koreader_created_at")
    private Long koreaderCreatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
