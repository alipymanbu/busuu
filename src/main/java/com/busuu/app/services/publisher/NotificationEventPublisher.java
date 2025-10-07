package com.busuu.app.services.publisher;

import com.busuu.app.dtos.responses.NotificationResponse;
import com.busuu.app.dtos.socket.SocketPayload;
import com.busuu.app.entities.Notification;
import com.busuu.app.entities.enums.TopicSocket;
import com.busuu.app.entities.enums.TypeSocket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    // Send events
    public void publishNotification(Notification notification) {
            SocketPayload<NotificationResponse> payload = SocketPayload.<NotificationResponse>builder()
                .messageId(UUID.randomUUID().toString())
                .data(NotificationResponse.builder()
                        .id(notification.getId())
                        .destinationId(notification.getDestinationId())
                        .actorId(notification.getActor().getId())
                        .message(notification.getMessage())
                        .type(notification.getType())
                        .build())
                .timestamp(Instant.now())
                .type(TypeSocket.NOTIFICATION)
                .build();

        payload.getData().setCreatedAt(LocalDateTime.now());

        String destination = "/topic/" + TopicSocket.NOTIFICATION.getValue() + "." + notification.getUser().getId();
        messagingTemplate.convertAndSend(destination, payload);

        log.info("Publishing [{}] notification event from user={} to user={}",
                payload.getType(), notification.getActor().getId(), notification.getUser().getId());
    }
}
