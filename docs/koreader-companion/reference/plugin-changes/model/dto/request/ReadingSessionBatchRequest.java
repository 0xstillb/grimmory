package org.booklore.model.dto.request;

import org.booklore.model.enums.BookFileType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionBatchRequest {
    @NotNull(message = "Book ID is required")
    private Long bookId;

    @NotNull(message = "Book type is required")
    private BookFileType bookType;

    @NotEmpty(message = "Sessions list cannot be empty")
    @Size(max = 500, message = "Sessions list cannot exceed 500 items")
    @Valid
    private List<ReadingSessionItemRequest> sessions;
}
