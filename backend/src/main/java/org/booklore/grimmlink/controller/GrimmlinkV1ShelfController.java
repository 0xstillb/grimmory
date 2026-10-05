package org.booklore.grimmlink.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.exception.APIException;
import org.booklore.grimmlink.GrimmlinkRoutes;
import org.booklore.grimmlink.dto.GrimmlinkBookSummary;
import org.booklore.grimmlink.dto.GrimmlinkShelfRemovalResponse;
import org.booklore.grimmlink.dto.GrimmlinkShelfSummary;
import org.booklore.grimmlink.service.GrimmlinkShelfService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(GrimmlinkRoutes.API_PREFIX + "/shelves")
public class GrimmlinkV1ShelfController {

    private final GrimmlinkShelfService shelfService;

    @GetMapping(value = "/crosspoint.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> crosspointRootCatalog() {
        return ResponseEntity.ok("<?xml version=\"1.0\" encoding=\"UTF-8\"?><catalog>"
                + "<entry><title>Regular Shelves</title><url>/api/grimmlink/v1/shelves/crosspoint/regular.xml</url><collection/></entry>"
                + "<entry><title>Magic Shelves</title><url>/api/grimmlink/v1/shelves/crosspoint/magic.xml</url><collection/></entry>"
                + "</catalog>");
    }

    @GetMapping(value = "/crosspoint/{shelfType}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> crosspointShelfCatalog(@PathVariable String shelfType) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><catalog>");
        for (GrimmlinkShelfSummary shelf : shelfService.listShelves(shelfType)) {
            xml.append("<entry><title>").append(xmlEscape(shelf.getName())).append("</title>")
                    .append("<id>").append(shelf.getId()).append("</id>")
                    .append("<url>/api/grimmlink/v1/shelves/crosspoint/")
                    .append(xmlEscape(shelfType)).append("/").append(shelf.getId()).append(".xml</url>")
                    .append("<collection/></entry>");
        }
        return ResponseEntity.ok(xml.append("</catalog>").toString());
    }

    @GetMapping(value = "/crosspoint/{shelfType}/{shelfId}.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> crosspointBookCatalog(@PathVariable String shelfType, @PathVariable Long shelfId) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><catalog>");
        for (GrimmlinkBookSummary book : shelfService.listShelfBooks(shelfType, shelfId, 100, 0, null, "EPUB", null, null)) {
            xml.append("<entry><title>").append(xmlEscape(book.getTitle())).append("</title>")
                    .append("<author>").append(xmlEscape(book.getAuthor())).append("</author>")
                    .append("<id>").append(book.getBookId()).append("</id>")
                    .append("<url>/api/grimmlink/v1/books/").append(book.getBookId()).append("/download</url>")
                    .append("</entry>");
        }
        return ResponseEntity.ok(xml.append("</catalog>").toString());
    }

    private String xmlEscape(Object value) {
        if (value == null) return "";
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    @GetMapping
    public ResponseEntity<List<GrimmlinkShelfSummary>> listShelves(@RequestParam(required = false) String type) {
        return ResponseEntity.ok(shelfService.listShelves(type));
    }

    @GetMapping("/{shelfId}/books")
    public ResponseEntity<List<GrimmlinkBookSummary>> listShelfBooks(@PathVariable Long shelfId,
                                                                     @RequestParam(required = false) Integer limit,
                                                                     @RequestParam(required = false) Integer offset,
                                                                     @RequestParam(required = false) String cursor,
                                                                     @RequestParam(required = false) String format,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer pageSize) {
        return ResponseEntity.ok(
                shelfService.listShelfBooks("regular", shelfId, limit, offset, cursor, format, page, pageSize));
    }

    @GetMapping("/{shelfType}/{shelfId}/books")
    public ResponseEntity<List<GrimmlinkBookSummary>> listShelfBooksByType(@PathVariable String shelfType,
                                                                          @PathVariable Long shelfId,
                                                                          @RequestParam(required = false) Integer limit,
                                                                          @RequestParam(required = false) Integer offset,
                                                                          @RequestParam(required = false) String cursor,
                                                                          @RequestParam(required = false) String format,
                                                                          @RequestParam(required = false) Integer page,
                                                                          @RequestParam(required = false) Integer pageSize) {
        String debugId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            return ResponseEntity.ok(
                    shelfService.listShelfBooks(shelfType, shelfId, limit, offset, cursor, format, page, pageSize));
        } catch (APIException ex) {
            log.error("GrimmLink shelf fetch failed debugId={} shelfType={} shelfId={} status={} message={}",
                    debugId, shelfType, shelfId, ex.getStatus(), ex.getMessage(), ex);
            throw new APIException(ex.getMessage() + " [debugId=" + debugId + "]", ex.getStatus());
        } catch (Exception ex) {
            log.error("GrimmLink shelf fetch crashed debugId={} shelfType={} shelfId={}: {}",
                    debugId, shelfType, shelfId, ex.getMessage(), ex);
            throw new APIException("Failed to fetch shelf books (debugId=" + debugId + ")", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<GrimmlinkShelfRemovalResponse> removeBookFromShelf(@PathVariable Long shelfId,
                                                                             @PathVariable Long bookId) {
        return ResponseEntity.ok(shelfService.removeBookFromShelf("regular", shelfId, bookId));
    }

    @PostMapping("/{shelfType}/{shelfId}/books/{bookId}/remove")
    public ResponseEntity<GrimmlinkShelfRemovalResponse> removeBookFromShelfByType(@PathVariable String shelfType,
                                                                                   @PathVariable Long shelfId,
                                                                                   @PathVariable Long bookId) {
        return ResponseEntity.ok(shelfService.removeBookFromShelf(shelfType, shelfId, bookId));
    }
}
