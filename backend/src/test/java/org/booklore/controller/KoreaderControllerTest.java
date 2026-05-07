package org.booklore.controller;

import org.booklore.model.dto.Book;
import org.booklore.model.dto.progress.KoreaderProgress;
import org.booklore.service.koreader.KoreaderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KoreaderControllerTest {

    @Mock
    private KoreaderService koreaderService;

    @InjectMocks
    private KoreaderController controller;

    @Test
    void authorizeUser_returnsServiceResponse() {
        Map<String, Object> expected = Map.of("username", "test", "status", "ok");
        when(koreaderService.authorizeUser()).thenReturn(ResponseEntity.ok(expected));

        ResponseEntity<Map<String, Object>> resp = controller.authorizeUser();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(expected, resp.getBody());
    }

    @Test
    void getBookByHash_returnsBook() {
        Book book = Book.builder().id(1L).title("Title").build();
        when(koreaderService.getBookByHash("hash")).thenReturn(ResponseEntity.ok(book));

        ResponseEntity<Book> resp = controller.getBookByHash("hash");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(book, resp.getBody());
    }

    @Test
    void getProgress_returnsProgress() {
        KoreaderProgress progress = KoreaderProgress.builder()
                .bookHash("hash")
                .progress("progress")
                .percentage(50.0F)
                .device("dev")
                .deviceId("id")
                .build();
        when(koreaderService.getProgress("hash")).thenReturn(progress);

        ResponseEntity<KoreaderProgress> resp = controller.getProgress("hash");

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, resp.getHeaders().getContentType());
        assertEquals(progress, resp.getBody());
    }

    @Test
    void updateProgress_delegatesToService() {
        KoreaderProgress progress = KoreaderProgress.builder()
                .bookHash("hash")
                .progress("progress")
                .percentage(50.0F)
                .device("dev")
                .deviceId("id")
                .build();

        doNothing().when(koreaderService).saveProgress(progress);

        ResponseEntity<?> resp = controller.updateProgress(progress);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("progress updated", ((Map<?, ?>) resp.getBody()).get("status"));
        verify(koreaderService).saveProgress(progress);
    }
}
