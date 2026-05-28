package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetadataSyncRequest {

    @Builder.Default
    private Integer schemaVersion = 1;

    @Builder.Default
    private String syncMode = "incremental";

    private Long bookId;
    private String bookHash;
    private Long bookFileId;
    private String fileFormat;
    private String device;
    private String deviceId;
    private Instant timestamp;
    private RatingPayload rating;

    @Builder.Default
    private List<AnnotationPayload> annotations = new ArrayList<>();

    @Builder.Default
    private List<BookmarkPayload> bookmarks = new ArrayList<>();
}
