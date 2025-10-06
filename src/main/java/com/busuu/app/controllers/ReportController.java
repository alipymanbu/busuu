package com.busuu.app.controllers;

import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.requests.report.ReportDTO;
import com.busuu.app.dtos.responses.ReportResponse;
import com.busuu.app.dtos.responses.Response;
import com.busuu.app.entities.Report;
import com.busuu.app.services.report.IReportService;
import com.busuu.app.utils.LocalizationUtils;
import com.busuu.app.utils.MessagesKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping(Constants.REPORT)
@RequiredArgsConstructor
@Slf4j
public class ReportController
{

    private final IReportService reportService;

    private final LocalizationUtils localizationUtils;

    @PostMapping()
    @Operation(security = {@SecurityRequirement(name = "bearer-key")})
    @PreAuthorize("hasRole('ROLE_USER')")
    public ResponseEntity<Response> addReport(@RequestParam(value = "req-id", required = false) String requestId,
                                              @Valid @RequestBody ReportDTO reportDTO)
    {

        try {

            if (requestId == null || requestId.isEmpty()) {
                requestId = UUID.randomUUID().toString();
            }

            //Call add report service
            ReportResponse addedReport = reportService.addReport(requestId, reportDTO);

            //Return response
            return ResponseEntity.ok().body(
                    Response.builder()
                            .message(localizationUtils.getLocalizedMessage(MessagesKey.INSERT_DATA_SUCCESSFULLY))
                            .status(HttpStatus.CREATED.value())
                            .data(addedReport)
                            .build()
            );

        } catch (Exception e) {
            log.error("Error when adding new report: " + e.getMessage());
            return ResponseEntity.badRequest().body(
                    Response.builder()
                            .message(localizationUtils.getLocalizedMessage(MessagesKey.INSERT_DATA_FAILED) + ": " + e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build()
            );
        }
    }
}
