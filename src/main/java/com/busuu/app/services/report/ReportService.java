package com.busuu.app.services.report;


import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.requests.report.ReportDTO;
import com.busuu.app.dtos.responses.ReportResponse;
import com.busuu.app.entities.Report;
import com.busuu.app.entities.User;
import com.busuu.app.entities.enums.ReportType;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.exceptions.ExistDataException;
import com.busuu.app.repositories.CorrectionRepository;
import com.busuu.app.repositories.PostRepository;
import com.busuu.app.repositories.ReportRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService implements IReportService
{

    private final ReportRepository reportRepository;

    private final PostRepository postRepository;

    private final CorrectionRepository correctionRepository;

    private final UserService userService;

    private final ModelMapper modelMapper;


    @Override
    @Transactional
    public ReportResponse addReport(String requestId, ReportDTO reportDTO)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Report newReport = modelMapper.map(reportDTO, Report.class);
            newReport.setId(UUID.randomUUID().toString());
            newReport.setUser(user);
            newReport.setType(ReportType.valueOf(reportDTO.getReportType().toUpperCase()));

            //Exception
            if (newReport.getType().equals(ReportType.POST))
            {
                if (!postRepository.existsById(newReport.getDestinationId())) throw new DataNotFoundException("Post with id " + newReport.getDestinationId() + " does not exist");
                if (reportRepository.existsByUserIdAndDestinationIdAndType(user.getId(), newReport.getDestinationId(), ReportType.POST)) throw new ExistDataException("You have already report this post!");
            }
            else
            {
                if (!correctionRepository.existsById(newReport.getDestinationId())) throw new  DataNotFoundException("Correction with id " + newReport.getDestinationId() + " does not exist");
                if (reportRepository.existsByUserIdAndDestinationIdAndType(user.getId(), newReport.getDestinationId(), ReportType.CORRECTION)) throw new ExistDataException("You have already report this correction!");
            }

            //Save and map return
            newReport = reportRepository.save(newReport);
            ReportResponse response = modelMapper.map(newReport, ReportResponse.class);
            response.setUserInfo(userService.getUserInfoById("Internal request", newReport.getUser().getId()));

            return response;

        } catch (Exception e) {
            log.error("requestId="+requestId+", failed to create new report, err= "+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_NEW_REPORT, requestId);
        }
    }
}
