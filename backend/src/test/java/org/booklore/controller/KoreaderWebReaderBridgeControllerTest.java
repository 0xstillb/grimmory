package org.booklore.controller;

import org.booklore.model.dto.koreader.KoreaderCfiResolveRequest;
import org.booklore.model.dto.koreader.KoreaderCfiResolveResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressResponse;
import org.booklore.model.dto.koreader.KoreaderWebProgressUpdateRequest;
import org.booklore.service.koreader.KoreaderWebReaderBridgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class KoreaderWebReaderBridgeControllerTest {

    @Mock
    private KoreaderWebReaderBridgeService bridgeService;

    @InjectMocks
    private KoreaderWebReaderBridgeController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getWebProgress_returnsBridgePayload() {
        KoreaderWebProgressResponse body = KoreaderWebProgressResponse.builder()
                .bookId(42L)
                .percentage(55.4f)
                .epubCfi("epubcfi(/6/2!/4/2)")
                .conversionStatus("cfi_available")
                .build();
        when(bridgeService.getWebProgress(42L)).thenReturn(body);

        ResponseEntity<KoreaderWebProgressResponse> response = controller.getWebProgress(42L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(42L, response.getBody().getBookId());
        assertEquals("cfi_available", response.getBody().getConversionStatus());
    }

    @Test
    void updateWebProgress_returnsConflictMetadata() {
        KoreaderWebProgressResponse body = KoreaderWebProgressResponse.builder()
                .bookId(42L)
                .conflictDetected(true)
                .updated(false)
                .message("remote newer")
                .build();
        KoreaderWebProgressUpdateRequest request = KoreaderWebProgressUpdateRequest.builder()
                .percentage(44.0f)
                .build();
        when(bridgeService.updateWebProgress(42L, request)).thenReturn(body);

        ResponseEntity<KoreaderWebProgressResponse> response = controller.updateWebProgress(42L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getConflictDetected());
        assertFalse(response.getBody().getUpdated());
    }

    @Test
    void resolveCfi_returnsBestEffortPayload() {
        KoreaderCfiResolveResponse body = KoreaderCfiResolveResponse.builder()
                .bookId(42L)
                .converted(true)
                .conversionStatus("xpointer_to_cfi")
                .epubCfi("epubcfi(/6/2!/4/2)")
                .build();
        KoreaderCfiResolveRequest request = KoreaderCfiResolveRequest.builder()
                .rawKoreaderXPointer("/body/DocFragment[1]/body/div[1]")
                .build();
        when(bridgeService.resolveCfi(42L, request)).thenReturn(body);

        ResponseEntity<KoreaderCfiResolveResponse> response = controller.resolveCfi(42L, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isConverted());
        assertEquals("xpointer_to_cfi", response.getBody().getConversionStatus());
    }
}
