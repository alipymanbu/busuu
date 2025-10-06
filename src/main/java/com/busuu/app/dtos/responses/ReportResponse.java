package com.busuu.app.dtos.responses;

import com.busuu.app.entities.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ReportResponse extends BaseResponse
{

    @JsonProperty("id")
    private String id;

    @JsonProperty("user")
    private UserInfoResponse userInfo;

    @JsonProperty("destination_id")
    private String destinationId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("type")
    private ReportType type;

}
