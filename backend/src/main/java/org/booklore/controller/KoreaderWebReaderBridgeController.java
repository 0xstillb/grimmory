package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.service.koreader.KoreaderService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/koreader/books/{bookId}")
@Tag(name = "KOReader Web Reader Bridge", description = "Stable PDF-only bridge endpoints for KOReader companions.")
public class KoreaderWebReaderBridgeController {

    private final KoreaderService koreaderService;

    @Operation(summary = "Get PDF bridge progress", description = "Returns PDF-only progress for KOReader bridge compatibility.")
    @ApiResponse(responseCode = "200", description = "Bridge progress returned successfully")
    @GetMapping(value = {"/pdf-progress", "/web-progress"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderProgress> getPdfProgress(@Parameter(description = "Book id") @PathVariable Long bookId) {
        return ResponseEntity.ok(koreaderService.getPdfProgress(bookId));
    }

    @Operation(summary = "Update PDF bridge progress", description = "Updates PDF-only progress for KOReader bridge compatibility.")
    @ApiResponse(responseCode = "200", description = "Bridge progress processed successfully")
    @PutMapping(value = {"/pdf-progress", "/web-progress"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderProgress> updatePdfProgress(
            @Parameter(description = "Book id") @PathVariable Long bookId,
            @Valid @RequestBody KoreaderProgress request) {
        return ResponseEntity.ok(koreaderService.updatePdfProgress(bookId, request));
    }
}
