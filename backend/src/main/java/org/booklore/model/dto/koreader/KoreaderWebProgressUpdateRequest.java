package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KoreaderWebProgressUpdateRequest {
    @JsonAlias({"book_file_id"})
    private Long bookFileId;
    @JsonAlias({"book_hash"})
    private String bookHash;
    @JsonAlias({"file_format"})
    private String fileFormat;
    private Float percentage;
    private Integer currentPage;
    private Integer totalPages;
    @Size(max = 2000)
    private String epubCfi;
    @Size(max = 1000)
    private String positionHref;
    private Float contentSourceProgressPercent;
    @Size(max = 2000)
    private String rawKoreaderLocation;
    @Size(max = 2000)
    private String rawKoreaderProgress;
    @Size(max = 2000)
    private String rawKoreaderXPointer;
    @Size(max = 100)
    private String source;
    @Size(max = 100)
    private String device;
    @JsonAlias("deviceId")
    @Size(max = 255)
    private String deviceId;
    private Long timestamp;
    private Long expectedUpdatedAt;
    @Builder.Default
    private boolean force = false;
}
