package org.booklore.controller;

import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.booklore.service.ReadingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingSessionControllerTest {

    @Mock
    private ReadingSessionService readingSessionService;

    @InjectMocks
    private ReadingSessionController controller;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    void recordReadingSession_returnsAccepted() {
        ReadingSessionRequest request = new ReadingSessionRequest();
        request.setBookId(1L);
        request.setStartTime(Instant.ofEpochSecond(10L));
        request.setEndTime(Instant.ofEpochSecond(20L));
        request.setDurationSeconds(10);
        doNothing().when(readingSessionService).recordSession(request);

        ResponseEntity<Void> response = controller.recordReadingSession(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void recordBatchSessions_returnsOk() {
        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest();
        request.setBookId(1L);
        request.setSessions(List.of());
        ReadingSessionBatchResponse batchResponse = ReadingSessionBatchResponse.builder()
                .totalRequested(1)
                .successCount(1)
                .results(List.of())
                .build();
        when(readingSessionService.recordSessionsBatch(request)).thenReturn(batchResponse);

        ResponseEntity<ReadingSessionBatchResponse> response = controller.recordBatchSessions(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(batchResponse, response.getBody());
    }

    @Test
    void getReadingSessionsForBook_returnsPage() {
        Page<ReadingSessionResponse> page = new PageImpl<>(List.of());
        when(readingSessionService.getReadingSessionsForBook(1L, 0, 5)).thenReturn(page);

        ResponseEntity<Page<ReadingSessionResponse>> response = controller.getReadingSessionsForBook(1L, 0, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody());
    }
}
