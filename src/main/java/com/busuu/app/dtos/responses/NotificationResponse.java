package com.busuu.app.dtos.responses;

import com.busuu.app.entities.enums.NotificationStatus;
import com.busuu.app.entities.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationResponse extends BaseResponse
{
    @JsonProperty("id")
    private String id;

    @JsonProperty("destination_id")
    private String destinationId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("actor_id")
    private String actorId;

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    @Builder.Default
    private NotificationStatus status = NotificationStatus.UNREAD;

    @JsonProperty("type")
    private NotificationType type;
}
