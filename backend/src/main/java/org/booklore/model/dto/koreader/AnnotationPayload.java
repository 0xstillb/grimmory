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
public class AnnotationPayload {
    private String dedupeKey;

    @Builder.Default
    private String type = "highlight";

    private String text;
    private String note;
    private String color;
    private String drawer;
    private String style;
    private String chapter;
    private Integer page;
    private LocationPayload location;
    private Instant createdAt;
    private Instant updatedAt;
}
