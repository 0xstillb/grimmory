package org.booklore.model.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.booklore.model.enums.BookFileType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadingSessionRequest {
    @NotNull
    private Long bookId;

    private BookFileType bookType;

    @Size(max = 128)
    private String bookHash;

    @Size(max = 100)
    private String device;

    @JsonAlias("device_id")
    @Size(max = 255)
    private String deviceId;

    @NotNull
    private Instant startTime;

    @NotNull
    private Instant endTime;

    @NotNull
    private Integer durationSeconds;

    private String durationFormatted;

    private Float startProgress;

    private Float endProgress;

    private Float progressDelta;

    private String startLocation;

    private String endLocation;

    private Integer currentPage;

    private Integer totalPages;
}
