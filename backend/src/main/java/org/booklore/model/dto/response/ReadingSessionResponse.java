package org.booklore.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReadingSessionResponse {
    private Long id;
    private Long bookId;
    private String bookHash;
    private String bookTitle;
    private String bookType;
    private Instant startTime;
    private Instant endTime;
    private Integer durationSeconds;
    private String durationFormatted;
    private Float startProgress;
    private Float endProgress;
    private Float progressDelta;
    private String startLocation;
    private String endLocation;
    private Integer currentPage;
    private Integer totalPages;
    private String device;
    private String deviceId;
    private LocalDateTime createdAt;
}

