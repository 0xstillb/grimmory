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
public class KoreaderBookSummary {
    private Long bookId;
    private Long bookFileId;
    private String title;
    private String author;
    private String fileName;
    private String originalFileName;
    private String extension;
    private String fileFormat;
    private Long fileSizeKb;
    private Long fileSize;
    private String bookHash;
    private String seriesName;
    private Float seriesNumber;
}
