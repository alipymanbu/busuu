package com.busuu.app.repositories;

import com.busuu.app.entities.Report;
import com.busuu.app.entities.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String>
{
    boolean existsByUserIdAndDestinationIdAndType(String userId, String destinationId, ReportType type);
}
