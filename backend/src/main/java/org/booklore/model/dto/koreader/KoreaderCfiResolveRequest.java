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
public class KoreaderCfiResolveRequest {
    @JsonAlias({"book_file_id"})
    private Long bookFileId;
    @JsonAlias({"book_hash"})
    private String bookHash;
    @JsonAlias({"file_format"})
    private String fileFormat;
    @Size(max = 2000)
    private String epubCfi;
    @Size(max = 2000)
    private String rawKoreaderLocation;
    @Size(max = 2000)
    private String rawKoreaderXPointer;
    private Integer currentPage;
    private Integer totalPages;
    private Float percentage;
}
