package org.booklore.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.booklore.model.dto.koreader.MetadataSyncRequest;
import org.booklore.model.dto.koreader.MetadataSyncResponse;
import org.booklore.service.koreader.KoreaderMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/koreader/syncs")
@Tag(name = "KoReader Metadata", description = "Endpoints for KoReader metadata sync")
public class KoreaderMetadataController {

    private final KoreaderMetadataService koreaderMetadataService;

    @Operation(summary = "Sync KOReader metadata batch", description = "Upload rating, annotations, notes, and bookmarks to Grimmory.")
    @ApiResponse(responseCode = "200", description = "Metadata batch processed")
    @PostMapping("/metadata")
    public ResponseEntity<MetadataSyncResponse> syncMetadata(@RequestBody(required = false) MetadataSyncRequest request) {
        MetadataSyncResponse response = koreaderMetadataService.syncMetadata(request);
        return ResponseEntity.ok(response);
    }
}
