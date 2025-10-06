package com.busuu.app.entities;

import com.busuu.app.entities.enums.NotificationStatus;
import com.busuu.app.entities.enums.ReportType;
import com.busuu.app.entities.progresses.LevelProgress;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nimbusds.openid.connect.sdk.claims.UserInfo;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Report extends BaseEntity
{

    @Id
    @Column(name = "report_id")
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "destination_id")
    private String destinationId;

    @Column(name = "message")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private ReportType type;

}
