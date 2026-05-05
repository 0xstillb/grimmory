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
    private Long bookFileId;
    private String bookHash;
    private String currentHash;
    private String initialHash;
    private String fileFormat;
    private Float percentage;
    private Integer pdfCurrentPage;
    private Integer pdfTotalPages;
    private Float pdfProgressPercent;
    private Float webPercentDisplayOnly;
    private Float koreaderPercentDisplayOnly;
    private Integer currentPage;
    private Integer totalPages;
    private String epubCfi;
    private String epubHref;
    private String epubAnchor;
    private String positionHref;
    private Float contentSourceProgressPercent;
    private String rawKoreaderLocation;
    private String rawKoreaderProgress;
    private String rawKoreaderXPointer;
    private String koreaderLocation;
    private String koreaderXPointer;
    private String source;
    private String device;
    private String deviceId;
    private Long timestamp;
    private Instant updatedAt;
    private Long progressVersion;
    private String conversionStatus;
    private String locatorPrecision;
    private Float conversionConfidence;
    private Boolean updated;
    private Boolean conflictDetected;
    private String message;
}
