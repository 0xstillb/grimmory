package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.model.dto.koreader.KoreaderBookSummary;
import org.booklore.model.dto.koreader.KoreaderShelfSummary;
import org.booklore.model.dto.koreader.KoreaderShelfRemovalResponse;
import org.booklore.service.koreader.KoreaderShelfService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader")
@Tag(name = "KOReader", description = "Endpoints for KOReader device integration and progress sync")
public class KoreaderShelfController {

    private final KoreaderShelfService koreaderShelfService;

    @Operation(summary = "List shelves", description = "List shelves available to the authenticated KOReader user.")
    @ApiResponse(responseCode = "200", description = "Shelves returned successfully")
    @GetMapping("/shelves")
    public ResponseEntity<List<KoreaderShelfSummary>> listShelves() {
        return ResponseEntity.ok(koreaderShelfService.listShelves());
    }

    @Operation(summary = "List books in shelf", description = "List books in a specific shelf accessible to the authenticated KOReader user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Books returned successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Shelf not found")
    })
    @GetMapping("/shelves/{shelfId}/books")
    public ResponseEntity<List<KoreaderBookSummary>> listShelfBooks(
            @Parameter(description = "ID of the shelf") @PathVariable Long shelfId) {
        return ResponseEntity.ok(koreaderShelfService.listShelfBooks(shelfId));
    }

    @Operation(summary = "Download book", description = "Download a book file for the authenticated KOReader user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Book file streamed successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Book not found")
    })
    @GetMapping("/books/{bookId}/download")
    public ResponseEntity<Resource> downloadBook(
            @Parameter(description = "ID of the book") @PathVariable Long bookId) {
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
        return ResponseEntity.ok(koreaderShelfService.removeBookFromShelf(shelfId, bookId));
    }
}
