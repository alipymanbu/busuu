package com.busuu.app.services.report;

import com.busuu.app.dtos.requests.report.ReportDTO;
import com.busuu.app.dtos.responses.ReportResponse;
import com.busuu.app.entities.Report;

public interface IReportService
{

    ReportResponse addReport(String requestId, ReportDTO reportDTO);

}
