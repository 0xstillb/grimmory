package org.booklore.model.dto.koreader;

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
