package org.booklore.controller;

import org.booklore.exception.ApiError;
import org.booklore.model.dto.koreader.KoreaderAnnotationDto;
import org.booklore.model.dto.koreader.KoreaderBatchResultDto;
import org.booklore.model.dto.koreader.KoreaderBookmarkDto;
import org.booklore.model.dto.koreader.KoreaderRatingDto;
import org.booklore.service.koreader.KoreaderAnnotationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class KoreaderAnnotationControllerTest {

    @Mock
    private KoreaderAnnotationService annotationService;

    @InjectMocks
    private KoreaderAnnotationController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- Annotations ----------

    @Test
    void listAnnotations_returnsList() {
        List<KoreaderAnnotationDto> items = List.of(
                KoreaderAnnotationDto.builder().id(1L).dedupeKey("k1").text("hi").build()
        );
        when(annotationService.listAnnotations(42L, null)).thenReturn(items);

        ResponseEntity<List<KoreaderAnnotationDto>> resp = controller.listAnnotations(42L, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(1, resp.getBody().size());
        assertEquals("hi", resp.getBody().get(0).getText());
    }

    @Test
    void listAnnotations_forbiddenPropagates() {
        when(annotationService.listAnnotations(anyLong(), any()))
                .thenThrow(ApiError.FORBIDDEN.createException("nope"));
        assertThrows(RuntimeException.class, () -> controller.listAnnotations(42L, null));
    }

    @Test
    void upsertAnnotations_returnsBatchResult() {
        KoreaderBatchResultDto result = KoreaderBatchResultDto.builder()
                .received(2).inserted(1).updated(1).build();
        when(annotationService.upsertAnnotations(eq(42L), any())).thenReturn(result);

        List<KoreaderAnnotationDto> input = List.of(
                KoreaderAnnotationDto.builder().dedupeKey("k1").text("a").build(),
                KoreaderAnnotationDto.builder().dedupeKey("k2").text("b").build()
        );
        ResponseEntity<KoreaderBatchResultDto> resp = controller.upsertAnnotations(42L, input);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().getReceived());
        assertEquals(1, resp.getBody().getInserted());
        assertEquals(1, resp.getBody().getUpdated());
    }

    // ---------- Bookmarks ----------

    @Test
    void listBookmarks_returnsList() {
        List<KoreaderBookmarkDto> items = List.of(
                KoreaderBookmarkDto.builder().id(1L).dedupeKey("b1").chapter("Ch 1").build()
        );
        when(annotationService.listBookmarks(7L, null)).thenReturn(items);

        ResponseEntity<List<KoreaderBookmarkDto>> resp = controller.listBookmarks(7L, null);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("Ch 1", resp.getBody().get(0).getChapter());
    }

    @Test
    void upsertBookmarks_returnsBatchResult() {
        KoreaderBatchResultDto result = KoreaderBatchResultDto.builder()
                .received(1).inserted(1).build();
        when(annotationService.upsertBookmarks(eq(7L), any())).thenReturn(result);

        List<KoreaderBookmarkDto> input = List.of(
                KoreaderBookmarkDto.builder().dedupeKey("b1").build()
        );
        ResponseEntity<KoreaderBatchResultDto> resp = controller.upsertBookmarks(7L, input);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().getInserted());
    }

    @Test
    void upsertBookmarks_bookNotFoundPropagates() {
        when(annotationService.upsertBookmarks(anyLong(), any()))
                .thenThrow(ApiError.BOOK_NOT_FOUND.createException(7L));
        assertThrows(RuntimeException.class, () -> controller.upsertBookmarks(7L, List.of()));
    }

    // ---------- Rating ----------

    @Test
    void getRating_returnsCurrentValue() {
        when(annotationService.getRating(11L))
                .thenReturn(KoreaderRatingDto.builder().bookId(11L).rating(8).build());

        ResponseEntity<KoreaderRatingDto> resp = controller.getRating(11L);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(11L, resp.getBody().getBookId());
        assertEquals(8, resp.getBody().getRating());
    }

    @Test
    void updateRating_returnsUpdatedValue() {
        KoreaderRatingDto request = KoreaderRatingDto.builder().bookId(11L).rating(9).build();
        when(annotationService.updateRating(eq(11L), any()))
                .thenReturn(KoreaderRatingDto.builder().bookId(11L).rating(9).build());

        ResponseEntity<KoreaderRatingDto> resp = controller.updateRating(11L, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(9, resp.getBody().getRating());
    }

    @Test
    void updateRating_invalidValuePropagates() {
        when(annotationService.updateRating(anyLong(), any()))
                .thenThrow(ApiError.GENERIC_BAD_REQUEST.createException("Rating out of range"));
        assertThrows(RuntimeException.class,
                () -> controller.updateRating(11L, KoreaderRatingDto.builder().rating(99).build()));
    }
}
