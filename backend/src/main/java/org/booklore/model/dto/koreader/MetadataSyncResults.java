package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetadataSyncResults {
    private ItemResult rating;

    @Builder.Default
    private List<ItemResult> annotations = new ArrayList<>();

    @Builder.Default
    private List<ItemResult> bookmarks = new ArrayList<>();
}
