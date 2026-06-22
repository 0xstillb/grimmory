package org.booklore.grimmlink.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GrimmlinkReadingSessionBatchResponse {
    private int totalRequested;
    private int successCount;
    private List<SessionResult> results;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SessionResult {
        private Integer index;
        private Long sessionId;
        private String status;
        private String message;
        private Instant startTime;
        private Instant endTime;
    }
}
