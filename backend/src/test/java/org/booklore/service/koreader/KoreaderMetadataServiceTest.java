package org.booklore.service.koreader;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.booklore.model.dto.koreader.*;
import org.booklore.model.entity.BookEntity;
import org.booklore.model.entity.BookFileEntity;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.LibraryEntity;
import org.booklore.model.entity.UserBookProgressEntity;
import org.booklore.model.entity.koreader.KoreaderMetadataItemEntity;
import org.booklore.model.enums.KoreaderMetadataItemType;
import org.booklore.repository.BookFileRepository;
import org.booklore.repository.BookRepository;
import org.booklore.repository.UserBookProgressRepository;
import org.booklore.repository.koreader.KoreaderMetadataItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreaderMetadataServiceTest {

    @Mock private KoreaderSecurityContextService securityContextService;
    @Mock private BookRepository bookRepository;
    @Mock private BookFileRepository bookFileRepository;
    @Mock private UserBookProgressRepository userBookProgressRepository;
    @Mock private KoreaderMetadataItemRepository metadataItemRepository;

    private KoreaderMetadataService service;
    private BookLoreUserEntity reader;
    private BookEntity book;
    private BookFileEntity bookFile;
    private LibraryEntity library;
    private Map<String, KoreaderMetadataItemEntity> metadataStore;
    private AtomicLong metadataIds;

    @BeforeEach
    void setUp() {
        service = new KoreaderMetadataService(
                securityContextService,
                bookRepository,
                bookFileRepository,
                userBookProgressRepository,
                metadataItemRepository,
                new ObjectMapper().registerModule(new JavaTimeModule())
        );

        library = new LibraryEntity();
        library.setId(7L);

        reader = new BookLoreUserEntity();
        reader.setId(42L);
        reader.setLibraries(new HashSet<>(Set.of(library)));

        book = new BookEntity();
        book.setId(100L);
        book.setLibrary(library);

        bookFile = new BookFileEntity();
        bookFile.setId(200L);
        bookFile.setBook(book);
        bookFile.setCurrentHash("hash-1");
        book.setBookFiles(List.of(bookFile));

        metadataStore = new HashMap<>();
        metadataIds = new AtomicLong(1000L);

        when(securityContextService.requireCurrentReaderEntity(true)).thenReturn(reader);
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.of(book));
        when(bookFileRepository.findByIdWithBookAndLibraryPath(200L)).thenReturn(Optional.of(bookFile));
        when(bookRepository.findAllByBookHash("hash-1")).thenReturn(List.of(book));
        when(userBookProgressRepository.findByUserIdAndBookId(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(userBookProgressRepository.save(any(UserBookProgressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(metadataItemRepository.findByUserIdAndBookIdAndItemTypeAndDedupeKey(anyLong(), anyLong(), any(), any()))
                .thenAnswer(invocation -> {
                    KoreaderMetadataItemType itemType = invocation.getArgument(2);
                    String dedupeKey = invocation.getArgument(3);
                    return Optional.ofNullable(metadataStore.get(itemType.name() + "|" + dedupeKey));
                });
        when(metadataItemRepository.save(any(KoreaderMetadataItemEntity.class)))
                .thenAnswer(invocation -> {
                    KoreaderMetadataItemEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(metadataIds.incrementAndGet());
                    }
                    metadataStore.put(entity.getItemType().name() + "|" + entity.getDedupeKey(), entity);
                    return entity;
                });
    }

    @Test
    void validBatch_syncsRatingAnnotationAndBookmark() {
        MetadataSyncResponse response = service.syncMetadata(validRequest());
        assertTrue(response.isOk());
        assertEquals("synced", response.getResults().getRating().getStatus());
        assertEquals("synced", response.getResults().getAnnotations().get(0).getStatus());
        assertEquals("synced", response.getResults().getBookmarks().get(0).getStatus());
    }

    @Test
    void duplicateDedupeKey_returnsDuplicateWithoutExtraRows() {
        service.syncMetadata(validRequest());
        MetadataSyncResponse second = service.syncMetadata(validRequest());
        assertEquals("duplicate", second.getResults().getRating().getStatus());
        assertEquals("duplicate", second.getResults().getAnnotations().get(0).getStatus());
        assertEquals("duplicate", second.getResults().getBookmarks().get(0).getStatus());
        assertEquals(3, metadataStore.size());
    }

    @Test
    void changedContentWithSameDedupeKey_returnsUpdated() {
        service.syncMetadata(validRequest());
        MetadataSyncRequest updated = validRequest();
        updated.getAnnotations().get(0).setNote("changed note");
        MetadataSyncResponse response = service.syncMetadata(updated);
        assertEquals("updated", response.getResults().getAnnotations().get(0).getStatus());
    }

    @Test
    void invalidAnnotation_isInvalidButOthersStillSync() {
        MetadataSyncRequest request = validRequest();
        request.getAnnotations().get(0).setDedupeKey(null);
        MetadataSyncResponse response = service.syncMetadata(request);
        assertEquals("synced", response.getResults().getRating().getStatus());
        assertEquals("invalid", response.getResults().getAnnotations().get(0).getStatus());
        assertEquals("synced", response.getResults().getBookmarks().get(0).getStatus());
    }

    @Test
    void unknownBook_returnsBookNotFoundFailures() {
        when(bookRepository.findByIdWithBookFiles(100L)).thenReturn(Optional.empty());
        MetadataSyncResponse response = service.syncMetadata(validRequest());
        assertFalse(response.isOk());
        assertEquals("failed", response.getResults().getRating().getStatus());
        assertEquals("book_not_found", response.getResults().getRating().getError());
    }

    @Test
    void inaccessibleBookId_returnsBookNotFoundFailures() {
        LibraryEntity other = new LibraryEntity();
        other.setId(999L);
        book.setLibrary(other);
        MetadataSyncResponse response = service.syncMetadata(validRequest());
        assertFalse(response.isOk());
        assertEquals("book_not_found", response.getResults().getRating().getError());
    }

    @Test
    void inaccessibleBookFileId_returnsBookNotFoundFailures() {
        LibraryEntity other = new LibraryEntity();
        other.setId(999L);
        book.setLibrary(other);
        MetadataSyncRequest request = validRequest();
        request.setBookId(null);
        request.setBookFileId(200L);
        MetadataSyncResponse response = service.syncMetadata(request);
        assertFalse(response.isOk());
        assertEquals("failed", response.getResults().getRating().getStatus());
        assertEquals("book_not_found", response.getResults().getRating().getError());
    }

    @Test
    void ratingNormalization_storesExpectedScaleMappings() {
        MetadataSyncRequest scaleFive = validRequest();
        scaleFive.getRating().setScale(5);
        scaleFive.getRating().setValue(4);
        service.syncMetadata(scaleFive);
        assertEquals(8, metadataStore.get("RATING|r-1").getNormalizedRating10());

        MetadataSyncRequest scaleTen = validRequest();
        scaleTen.getRating().setScale(10);
        scaleTen.getRating().setValue(9);
        scaleTen.getRating().setDedupeKey("r-2");
        service.syncMetadata(scaleTen);
        assertEquals(9, metadataStore.get("RATING|r-2").getNormalizedRating10());
    }

    @Test
    void locationAndUnicodeAreStoredWithoutCfiRequirement() {
        MetadataSyncRequest request = validRequest();
        request.getAnnotations().get(0).setText("สวัสดี โลก");
        request.getAnnotations().get(0).setLocation(LocationPayload.builder().kind("koreader").pos0("xp-a").pos1("xp-b").build());
        MetadataSyncResponse response = service.syncMetadata(request);
        KoreaderMetadataItemEntity item = metadataStore.get("ANNOTATION|a-1");
        assertEquals("synced", response.getResults().getAnnotations().get(0).getStatus());
        assertEquals("สวัสดี โลก", item.getText());
        assertTrue(item.getLocationJson().contains("pos0"));
    }

    @Test
    void logsDoNotContainFullAnnotationTextOrNote() {
        Logger logger = (Logger) LoggerFactory.getLogger(KoreaderMetadataService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MetadataSyncRequest request = validRequest();
            request.getAnnotations().get(0).setText("VERY_SECRET_HIGHLIGHT_TEXT_123");
            request.getAnnotations().get(0).setNote("VERY_SECRET_NOTE_TEXT_456");
            service.syncMetadata(request);
            String joined = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
            assertFalse(joined.contains("VERY_SECRET_HIGHLIGHT_TEXT_123"));
            assertFalse(joined.contains("VERY_SECRET_NOTE_TEXT_456"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private MetadataSyncRequest validRequest() {
        return MetadataSyncRequest.builder()
                .schemaVersion(1)
                .syncMode("incremental")
                .bookId(100L)
                .bookFileId(200L)
                .bookHash("hash-1")
                .fileFormat("EPUB")
                .device("KOReader")
                .deviceId("device-1")
                .timestamp(Instant.parse("2026-05-27T00:00:00Z"))
                .rating(RatingPayload.builder()
                        .dedupeKey("r-1")
                        .value(4)
                        .scale(5)
                        .source("koreader")
                        .updatedAt(Instant.parse("2026-05-27T00:00:00Z"))
                        .build())
                .annotations(List.of(
                        AnnotationPayload.builder()
                                .dedupeKey("a-1")
                                .type("highlight")
                                .text("Highlight")
                                .note("Note")
                                .chapter("Chapter 1")
                                .page(12)
                                .location(LocationPayload.builder().kind("koreader").pos0("xp-1").pos1("xp-2").build())
                                .createdAt(Instant.parse("2026-05-27T00:00:00Z"))
                                .updatedAt(Instant.parse("2026-05-27T00:01:00Z"))
                                .build()
                ))
                .bookmarks(List.of(
                        BookmarkPayload.builder()
                                .dedupeKey("b-1")
                                .title("Bookmark")
                                .notes("Bookmark notes")
                                .chapter("Chapter 2")
                                .page(55)
                                .location(LocationPayload.builder().kind("koreader").pos0("bm-1").build())
                                .createdAt(Instant.parse("2026-05-27T00:02:00Z"))
                                .updatedAt(Instant.parse("2026-05-27T00:03:00Z"))
                                .build()
                ))
                .build();
    }
}
