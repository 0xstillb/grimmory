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
public class LocationPayload {

    @Builder.Default
    private String kind = "koreader";

    private String pos0;
    private String pos1;
    private String cfi;
    private Integer pageno;
    private String raw;
}
