package org.booklore.model.dto.koreader;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KoreaderBatchResultDto {

    private int received;
    private int inserted;
    private int updated;
    private int skipped;
    private int failed;

    private List<String> errors;
}
