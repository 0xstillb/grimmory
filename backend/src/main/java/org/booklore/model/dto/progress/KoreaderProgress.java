package org.booklore.model.dto.progress;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

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
    private Long bookFileId;
    private String fileFormat;
    private Float percentage;
    private String progress;
    private String location;
    private Integer currentPage;
    private Integer totalPages;
    private String device;
    @JsonAlias("device_id")
    private String deviceId;
    private Instant updatedAt;
    private Boolean updated;
    private Boolean conflictDetected;
    private String conversionStatus;
    private String message;
    private Long expectedUpdatedAt;
    private Boolean force;
    private String rawKoreaderLocation;
    private String rawKoreaderProgress;
    private String source;

    @Size(max = 128)
    private String currentHash;

    @Size(max = 128)
    private String initialHash;

    public String resolveBookHash() {
        if (bookHash != null && !bookHash.isBlank()) {
            return bookHash.trim();
        }
        return null;
    }
}
