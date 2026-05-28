package org.booklore.controller;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.koreader.KoreaderReadStatusRequest;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.service.koreader.KoreaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader")
@Tag(name = "KoReader", description = "Endpoints for KoReader device integration and progress sync")
public class KoreaderController {

    private final KoreaderService koreaderService;

    @Operation(summary = "Authorize KoReader user", description = "Authorize a user for KoReader sync.")
    @ApiResponse(responseCode = "200", description = "User authorized successfully")
    @GetMapping("/users/auth")
    public ResponseEntity<Map<String, Object>> authorizeUser() {
        return koreaderService.authorizeUser();
    }

    @Operation(summary = "Create KoReader user (disabled)", description = "Attempt to register a user via KoReader (always forbidden).")
    @ApiResponse(responseCode = "403", description = "User registration forbidden")
    @PostMapping("/users/create")
    public ResponseEntity<?> createUser(@Parameter(description = "User data") @RequestBody Map<String, Object> userData) {
        log.warn("Attempt to register user via Koreader blocked: {}", userData);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "User registration via Koreader is disabled"));
    }

    @Operation(summary = "Get KoReader progress", description = "Retrieve reading progress for a book by its hash.")
    @ApiResponse(responseCode = "200", description = "Progress returned successfully")
    @GetMapping("/syncs/progress/{bookHash}")
    public ResponseEntity<KoreaderProgress> getProgress(@Parameter(description = "Book hash") @PathVariable String bookHash) {
        KoreaderProgress progress = koreaderService.getProgress(bookHash);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(progress);
    }

    @Operation(summary = "Get book by hash", description = "Retrieve a book by its file hash for KOReader matching.")
    @ApiResponse(responseCode = "200", description = "Book returned successfully")
    @GetMapping("/books/by-hash/{bookHash}")
    public ResponseEntity<Book> getBookByHash(@Parameter(description = "Book hash") @PathVariable String bookHash) {
        return koreaderService.getBookByHash(bookHash);
    }

    @Operation(summary = "Update KoReader progress", description = "Update reading progress for a book.")
    @ApiResponse(responseCode = "200", description = "Progress updated successfully")
    @PutMapping("/syncs/progress")
    public ResponseEntity<?> updateProgress(@Parameter(description = "KoReader progress object") @Valid @RequestBody KoreaderProgress koreaderProgress) {
        koreaderService.saveProgress(koreaderProgress);
        return ResponseEntity.ok(Map.of("status", "progress updated"));
    }

    @Operation(summary = "Get supported manual read statuses", description = "Returns manual read status values supported by KOReader status endpoint.")
    @ApiResponse(responseCode = "200", description = "Supported statuses returned successfully")
    @GetMapping("/books/read-statuses")
    public ResponseEntity<Map<String, List<String>>> getSupportedReadStatuses() {
        return ResponseEntity.ok(Map.of("statuses", koreaderService.getSupportedReadStatuses()));
    }

    @Operation(summary = "Update manual read status", description = "Update read status for a book using KOReader authentication.")
    @ApiResponse(responseCode = "200", description = "Read status updated successfully")
    @PutMapping("/books/{bookId}/status")
    public ResponseEntity<Map<String, Object>> updateReadStatus(
            @Parameter(description = "Book id") @PathVariable Long bookId,
            @RequestBody KoreaderReadStatusRequest request) {
        return ResponseEntity.ok(koreaderService.updateReadStatus(bookId, request != null ? request.getStatus() : null));
    }
}
