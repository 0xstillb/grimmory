package org.booklore.controller;

import org.booklore.model.dto.request.ReadingSessionBatchRequest;
import org.booklore.model.dto.request.ReadingSessionItemRequest;
import org.booklore.model.dto.request.ReadingSessionRequest;
import org.booklore.model.dto.response.ReadingSessionBatchResponse;
import org.booklore.model.dto.response.ReadingSessionResponse;
import org.booklore.model.enums.BookFileType;
import org.booklore.service.ReadingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class ReadingSessionControllerTest {

    @Mock
    private ReadingSessionService readingSessionService;

    @InjectMocks
    private ReadingSessionController controller;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void recordReadingSession_returnsAccepted() {
        ReadingSessionRequest request = new ReadingSessionRequest(
                1L,
                "hash",
                BookFileType.EPUB,
                Instant.now(),
                Instant.now().plusSeconds(60),
                60,
                "1m",
                10.0f,
                12.0f,
                2.0f,
                "KOReader",
                "device-1",
                "start",
                "end"
        );
        doNothing().when(readingSessionService).recordSession(request);

        ResponseEntity<Void> response = controller.recordReadingSession(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void recordBatchSessions_returnsOk() {
        ReadingSessionBatchRequest request = new ReadingSessionBatchRequest(
                1L,
                "hash",
                BookFileType.EPUB,
                "KOReader",
                "device-1",
                List.of(new ReadingSessionItemRequest(
                        Instant.now(),
                        Instant.now().plusSeconds(60),
                        60,
                        "1m",
                        10.0f,
                        12.0f,
                        2.0f,
                        "start",
                        "end"
                ))
        );

        ReadingSessionBatchResponse batchResponse = ReadingSessionBatchResponse.builder()
                .totalRequested(1)
                .successCount(1)
                .build();
        when(readingSessionService.recordSessionsBatch(request)).thenReturn(batchResponse);

        ResponseEntity<ReadingSessionBatchResponse> response = controller.recordBatchSessions(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(batchResponse, response.getBody());
    }

    @Test
    void getReadingSessionsForBook_returnsPage() {
        Page<ReadingSessionResponse> page = new PageImpl<>(List.of(
                ReadingSessionResponse.builder().id(1L).bookId(2L).build()
        ));
        when(readingSessionService.getReadingSessionsForBook(2L, 0, 5)).thenReturn(page);

        ResponseEntity<Page<ReadingSessionResponse>> response = controller.getReadingSessionsForBook(2L, 0, 5);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(page, response.getBody());
    }
}
