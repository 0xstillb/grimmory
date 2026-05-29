package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.service.koreader.KoreaderShelfService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader")
@Tag(name = "KoReader", description = "Shelf sync endpoints for KOReader companions")
public class KoreaderShelfController {

    private final KoreaderShelfService koreaderShelfService;

    @Operation(summary = "List shelves", description = "List shelves available to the authenticated KOReader user.")
    @ApiResponse(responseCode = "200", description = "Shelves returned successfully")
    @GetMapping("/shelves")
    public ResponseEntity<List<KoreaderShelfSummary>> listShelves(
            @Parameter(description = "Optional shelf type filter: regular or magic")
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(koreaderShelfService.listShelves(type));
    }

    @Operation(summary = "List books in shelf", description = "List books in a specific shelf accessible to the authenticated KOReader user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books returned successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Shelf not found")
    })
    @GetMapping("/shelves/{shelfId}/books")
    public ResponseEntity<List<KoreaderBookSummary>> listShelfBooks(@Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        return ResponseEntity.ok(koreaderShelfService.listShelfBooks("regular", shelfId));
    }

    @Operation(summary = "List books in shelf", description = "List books in a specific regular or magic shelf accessible to the authenticated KOReader user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books returned successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Shelf not found")
    })
    @GetMapping("/shelves/{shelfType}/{shelfId}/books")
    public ResponseEntity<List<KoreaderBookSummary>> listShelfBooksByType(
            @Parameter(description = "Shelf type: regular or magic") @PathVariable String shelfType,
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        String debugId = newDebugId();
        try {
            return ResponseEntity.ok(koreaderShelfService.listShelfBooks(shelfType, shelfId));
        } catch (APIException ex) {
            // Keep original HTTP status while attaching a debug id for faster field troubleshooting.
            log.error("KOReader shelf fetch failed debugId={} shelfType={} shelfId={} status={} message={}",
                    debugId, shelfType, shelfId, ex.getStatus(), ex.getMessage(), ex);
            throw new APIException(ex.getMessage() + " [debugId=" + debugId + "]", ex.getStatus());
        } catch (Exception ex) {
            String summary = summarizeException(ex);
            log.error("KOReader shelf fetch crashed debugId={} shelfType={} shelfId={} cause={}",
                    debugId, shelfType, shelfId, summary, ex);
            throw new APIException(
                    "Failed to fetch shelf books (debugId=" + debugId + "): " + summary,
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Operation(summary = "Download book", description = "Download a book file for the authenticated KOReader user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book file streamed successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/books/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(@Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return koreaderShelfService.downloadBook(bookId);
    }

    @Operation(summary = "Remove book from shelf", description = "Remove a book from a shelf without deleting the book record or library file.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Shelf membership removed successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Shelf or book not found")
    })
    @PostMapping("/shelves/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<KoreaderShelfRemovalResponse> removeBookFromShelf(
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId,
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(koreaderShelfService.removeBookFromShelf("regular", shelfId, bookId));
    }

    @Operation(summary = "Remove book from shelf", description = "Remove a book from a regular shelf, or return unsupported for a magic shelf.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Remove action handled"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Shelf or book not found")
    })
    @PostMapping("/shelves/{shelfType}/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<KoreaderShelfRemovalResponse> removeBookFromShelfByType(
            @Parameter(description = "Shelf type: regular or magic") @PathVariable String shelfType,
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId,
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
        return ResponseEntity.ok(koreaderShelfService.removeBookFromShelf(shelfType, shelfId, bookId));
    }

    private static String newDebugId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String summarizeException(Throwable throwable) {
        if (throwable == null) {
            return "unknown_error";
        }
        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage() == null ? "" : throwable.getMessage().replaceAll("\\s+", " ").trim();
        if (message.length() > 220) {
            message = message.substring(0, 220) + "...";
        }
        return message.isEmpty() ? type : (type + ": " + message);
    }
}
