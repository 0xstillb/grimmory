package org.booklore.controller;

import org.booklore.model.dto.koreader.MetadataSyncRequest;
import org.booklore.model.dto.koreader.MetadataSyncResponse;
import org.booklore.service.koreader.KoreaderMetadataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoreaderMetadataControllerTest {

    @Mock
    private KoreaderMetadataService koreaderMetadataService;

    @InjectMocks
    private KoreaderMetadataController controller;

    @Test
    void syncMetadata_returnsServiceResponse() {
        MetadataSyncRequest request = MetadataSyncRequest.builder().bookId(1L).build();
        MetadataSyncResponse expected = MetadataSyncResponse.builder().bookId(1L).ok(true).build();
        when(koreaderMetadataService.syncMetadata(request)).thenReturn(expected);

        ResponseEntity<MetadataSyncResponse> response = controller.syncMetadata(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
