package com.busuu.app.dtos.requests.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportDTO
{

    @JsonProperty("message")
    @Size(max = 5000, message = "Invalid message text length (maximum length is 5000 words)!")
    private String message;

    @JsonProperty("destination_id")
    @NotBlank(message = "Destination id cannot be null")
    private String destinationId;

    @JsonProperty("report_type")
    @NotBlank(message = "Post type cannot be null")
    @Pattern(regexp = "POST|CORRECTION", message = "Invalid report type. Must be 'POST' or 'CORRECTION' (uppercase required).")
    private String reportType;

}
