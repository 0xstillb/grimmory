package org.booklore.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.booklore.model.enums.BookFileType;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionBatchRequest {

    @NotNull(message = "Book ID is required")
    private Long bookId;

    private String bookHash;

    private BookFileType bookType;

    @Size(max = 100, message = "Device must not exceed 100 characters")
    private String device;

    @JsonAlias("device_id")
    @Size(max = 255, message = "Device ID must not exceed 255 characters")
    private String deviceId;

    @NotEmpty(message = "Sessions list cannot be empty")
    @Size(max = 500, message = "Sessions list cannot exceed 500 items")
    @Valid
    private List<ReadingSessionItemRequest> sessions;
}
