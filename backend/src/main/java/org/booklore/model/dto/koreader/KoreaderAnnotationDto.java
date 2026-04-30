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
public class KoreaderAnnotationDto {

    private Long id;
    private Long bookId;
    private String type;

    /** Stable dedupe key computed by the plugin. Required for batch upserts. */
    private String dedupeKey;

    /** Raw KOReader xpointer / location. */
    private String koreaderPos;

    private Integer page;
    private String chapter;
    private String text;
    private String note;
    private String color;
    private String drawer;
    private String source;

    private Long koreaderCreatedAt;
    private Long koreaderUpdatedAt;
    private Long createdAt;
    private Long updatedAt;
}
