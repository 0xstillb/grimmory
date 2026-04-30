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
    private boolean converted;
    private String reason;
    private String conversionStatus;
    private Float conversionConfidence;
    private String epubCfi;
    private String positionHref;
    private Float contentSourceProgressPercent;
    private String rawLocation;
    private String rawKoreaderXPointer;
    private Integer currentPage;
    private Integer totalPages;
    private Float percentage;
}
