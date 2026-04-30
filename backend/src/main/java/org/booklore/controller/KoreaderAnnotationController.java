package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.koreader.KoreaderAnnotationDto;
import org.booklore.model.dto.koreader.KoreaderBatchResultDto;
import org.booklore.model.dto.koreader.KoreaderBookmarkDto;
import org.booklore.model.dto.koreader.KoreaderRatingDto;
import org.booklore.service.koreader.KoreaderAnnotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * KOReader annotation, bookmark and rating sync endpoints.
 *
 * <p>All routes are under {@code /api/koreader/} so the existing {@code KoreaderAuthFilter}
 * (x-auth-user / x-auth-key) handles authentication. No JWT required.
 *
 * <p>None of these endpoints delete book records or library files.
 */
@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader")
@Tag(name = "KoReader", description = "Endpoints for KoReader device integration and progress sync")
public class KoreaderAnnotationController {

    private final KoreaderAnnotationService annotationService;

    // ---------- Annotations ----------

    @Operation(summary = "List annotations for a book",
            description = "Returns KOReader-native highlights/notes for a book accessible to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/books/{bookId}/annotations")
    public ResponseEntity<List<KoreaderAnnotationDto>> listAnnotations(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(annotationService.listAnnotations(bookId));
    }

    @Operation(summary = "Batch upsert annotations",
            description = "Insert or update KOReader annotations for a book. Dedupe by stable key. " +
                    "Never deletes book records or library files.")
    @PostMapping("/books/{bookId}/annotations/batch")
    public ResponseEntity<KoreaderBatchResultDto> upsertAnnotations(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @RequestBody List<KoreaderAnnotationDto> items) {
        return ResponseEntity.ok(annotationService.upsertAnnotations(bookId, items));
    }

    // ---------- Bookmarks ----------

    @Operation(summary = "List bookmarks for a book",
            description = "Returns KOReader-native bookmarks for a book accessible to the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/books/{bookId}/bookmarks")
    public ResponseEntity<List<KoreaderBookmarkDto>> listBookmarks(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(annotationService.listBookmarks(bookId));
    }

    @Operation(summary = "Batch upsert bookmarks",
            description = "Insert or update KOReader bookmarks for a book. Dedupe by stable key. " +
                    "Never deletes book records or library files.")
    @PostMapping("/books/{bookId}/bookmarks/batch")
    public ResponseEntity<KoreaderBatchResultDto> upsertBookmarks(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @RequestBody List<KoreaderBookmarkDto> items) {
        return ResponseEntity.ok(annotationService.upsertBookmarks(bookId, items));
    }

    // ---------- Rating ----------

    @Operation(summary = "Get personal rating for a book",
            description = "Returns the authenticated user's personal rating (1..10) for the book, if set.")
    @GetMapping("/books/{bookId}/rating")
    public ResponseEntity<KoreaderRatingDto> getRating(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(annotationService.getRating(bookId));
    }

    @Operation(summary = "Set personal rating for a book",
            description = "Sets the authenticated user's personal rating (1..10) for the book, or clears it with null.")
    @PutMapping("/books/{bookId}/rating")
    public ResponseEntity<KoreaderRatingDto> updateRating(
            @Parameter(description = "ID of the book") @PathVariable Long bookId,
            @RequestBody KoreaderRatingDto request) {
        return ResponseEntity.ok(annotationService.updateRating(bookId, request));
    }
}
