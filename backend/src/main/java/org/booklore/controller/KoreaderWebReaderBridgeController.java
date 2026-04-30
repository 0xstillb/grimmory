package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.booklore.model.dto.koreader.KoreaderCfiResolveRequest;
import org.booklore.model.dto.koreader.KoreaderCfiResolveResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressUpdateRequest;
import org.booklore.service.koreader.KoreaderWebReaderBridgeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/koreader/books/{bookId}")
@Tag(name = "KOReader Web Reader Bridge", description = "Optional bridge endpoints for GrimmLink to read/write Web Reader progress without disturbing KOReader-native sync.")
public class KoreaderWebReaderBridgeController {

    private final KoreaderWebReaderBridgeService bridgeService;

    @Operation(summary = "Get Web Reader bridge progress", description = "Returns the current Web Reader progress alongside preserved KOReader-native raw progress fields when available.")
    @ApiResponse(responseCode = "200", description = "Bridge progress returned successfully")
    @GetMapping(value = "/web-progress", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderWebProgressResponse> getWebProgress(
            @Parameter(description = "Book id") @PathVariable Long bookId) {
        return ResponseEntity.ok(bridgeService.getWebProgress(bookId));
    }

    @Operation(summary = "Update Web Reader bridge progress", description = "Writes Web Reader progress only after access checks and a conservative newer-remote conflict guard.")
    @ApiResponse(responseCode = "200", description = "Bridge progress processed successfully")
    @PutMapping(value = "/web-progress", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderWebProgressResponse> updateWebProgress(
            @Parameter(description = "Book id") @PathVariable Long bookId,
            @Valid @RequestBody KoreaderWebProgressUpdateRequest request) {
        return ResponseEntity.ok(bridgeService.updateWebProgress(bookId, request));
    }

    @Operation(summary = "Resolve EPUB CFI bridge data", description = "Best-effort CFI/xpointer conversion for the optional Web Reader bridge. Failed conversion never deletes or overwrites native KOReader progress.")
    @ApiResponse(responseCode = "200", description = "Bridge conversion result returned successfully")
    @PostMapping(value = "/cfi/resolve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<KoreaderCfiResolveResponse> resolveCfi(
            @Parameter(description = "Book id") @PathVariable Long bookId,
            @Valid @RequestBody KoreaderCfiResolveRequest request) {
        return ResponseEntity.ok(bridgeService.resolveCfi(bookId, request));
    }
}
