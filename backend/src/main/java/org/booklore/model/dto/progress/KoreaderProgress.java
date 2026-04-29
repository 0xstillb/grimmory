package org.booklore.model.dto.progress;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoreaderProgress {
    private Long timestamp;
    private String document;
    private String bookHash;
    private Long bookId;
    private Float percentage;
    @Size(max = 2000)
    private String progress;
    @Size(max = 2000)
    private String location;
    private String fileFormat;
    private Integer currentPage;
    private Integer totalPages;
    @Size(max = 100)
    private String device;
    @JsonAlias("deviceId")
    @Size(max = 255)
    private String device_id;

    public String resolveBookHash() {
        if (bookHash != null && !bookHash.isBlank()) {
            return bookHash.trim();
        }
        return document != null ? document.trim() : null;
    }
}
