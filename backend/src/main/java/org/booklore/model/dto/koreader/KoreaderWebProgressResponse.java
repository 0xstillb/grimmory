package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoreaderWebProgressResponse {
    private Long bookId;
    private Float percentage;
    private Integer currentPage;
    private Integer totalPages;
    private String epubCfi;
    private String positionHref;
    private Float contentSourceProgressPercent;
    private String rawKoreaderLocation;
    private String rawKoreaderProgress;
    private String rawKoreaderXPointer;
    private String source;
    private String device;
    private String deviceId;
    private Long timestamp;
    private Instant updatedAt;
    private String conversionStatus;
    private Float conversionConfidence;
    private Boolean updated;
    private Boolean conflictDetected;
    private String message;
}
