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
public class BookmarkPayload {
    private String dedupeKey;
    private String title;
    private String notes;
    private String chapter;
    private Integer page;
    private LocationPayload location;
    private Instant createdAt;
    private Instant updatedAt;
}
