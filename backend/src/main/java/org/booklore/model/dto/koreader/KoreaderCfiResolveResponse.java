package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoreaderCfiResolveResponse {
    private Long bookId;
    private Long bookFileId;
    private String bookHash;
    private String currentHash;
    private String initialHash;
    private String fileFormat;
    private boolean converted;
    private String reason;
    private String conversionStatus;
    private String locatorPrecision;
    private Float conversionConfidence;
    private String epubCfi;
    private String epubHref;
    private String epubAnchor;
    private String positionHref;
    private Float contentSourceProgressPercent;
    private String rawLocation;
    private String koreaderLocation;
    private String rawKoreaderXPointer;
    private String koreaderXPointer;
    private Integer currentPage;
    private Integer totalPages;
    private Float percentage;
    private Float webPercentDisplayOnly;
    private Float koreaderPercentDisplayOnly;
}
