package com.busuu.app.services.notification;


import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.responses.NotificationResponse;
import com.busuu.app.entities.User;
import com.busuu.app.entities.Correction;
import com.busuu.app.entities.Notification;
import com.busuu.app.entities.enums.FriendshipStatus;
import com.busuu.app.entities.enums.NotificationStatus;
import com.busuu.app.entities.enums.NotificationType;
import com.busuu.app.entities.Post;
import com.busuu.app.entities.enums.ReactionType;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.repositories.CorrectionRepository;
import com.busuu.app.repositories.NotificationRepository;
import com.busuu.app.repositories.PostRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.publisher.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService implements INotificationService
{

    private final NotificationRepository notificationRepository;

    private final ModelMapper modelMapper;

    private final UserRepository userRepository;

    private final PostRepository postRepository;

    private final CorrectionRepository correctionRepository;

    private final NotificationEventPublisher notificationEventPublisher;


    @Override
    @Transactional
    public void addNotification(String destinationId, NotificationType notificationType, ReactionType reactionType)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User userActor = (User) auth.getPrincipal();

            boolean giveNotification = true;
            String message = "";
            List<String> toUserList = new ArrayList<>();

            switch (notificationType)
            {
                //DestinationId: User
                case FRIEND_REQUESTED:
                {
                    User destinationUser = userRepository.findById(destinationId)
                            .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID"));

                    message = " has sent you a friend request";
                    toUserList.add(destinationUser.getId());

                    break;
                }
                //DestinationId: User
                case FRIEND_ACCEPTED:
                {
                    User destinationUser = userRepository.findById(destinationId)
                            .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID"));

                    message = " has accepted your friend request";
                    toUserList.add(destinationUser.getId());

                    break;
                }
                //DestinationId: Post
                case NEW_POST:
                {
                    
                    boolean existingPost = postRepository.existsById(destinationId);
                    if (!existingPost) throw new DataNotFoundException("Cannot find post with ID");

                    message = " has posted a new post";

                    List<String> friendList = userRepository.findFriendIds(userActor.getId(), FriendshipStatus.ACCEPT);
                    toUserList.addAll(friendList);

                    break;
                }
                //DestinationId: Post
                case POST_CORRECTED:
                {
                    Post destinationPost = postRepository.findById(destinationId)
                            .orElseThrow( ()-> new DataNotFoundException("Cannot find post with ID"));

                    message = " has corrected your post";
                    toUserList.add(destinationPost.getUser().getId());

                    break;
                }
                //DestinationId: Correction -> FE navigate to Post
                case CORRECTION_REACTION:
                {
                    Correction destinationCorrection = correctionRepository.findById(destinationId)
                            .orElseThrow( ()-> new DataNotFoundException("Cannot find correction with ID"));

                    if (destinationCorrection.getPost() != null) destinationId = destinationCorrection.getPost().getId();
                    else destinationId = destinationCorrection.getCorrection().getPost().getId();

                    //More correction level
//                    else if (destinationCorrection.getCorrection().getPost() != null) destinationId = destinationCorrection.getCorrection().getPost().getId();
//                    else if (destinationCorrection.getCorrection().getCorrection().getPost() != null) destinationId = destinationCorrection.getCorrection().getCorrection().getPost().getId();
//                    else if (destinationCorrection.getCorrection().getCorrection().getCorrection().getPost() != null)  destinationId = destinationCorrection.getCorrection().getCorrection().getCorrection().getPost().getId();
//                    else if (destinationCorrection.getCorrection().getCorrection().getCorrection().getCorrection().getPost() != null) destinationId = destinationCorrection.getCorrection().getCorrection().getCorrection().getCorrection().getPost().getId();
//                    else destinationId = destinationCorrection.getCorrection().getCorrection().getCorrection().getCorrection().getCorrection().getPost().getId();

                    message = " has " + reactionType.name().toLowerCase() +  "d your correction";
                    toUserList.add(destinationCorrection.getUser().getId());

                    break;
                }
                //DestinationId: Correction -> FE navigate to Post
                case CORRECTION_REPLIED:
                {
                    Correction destinationCorrection = correctionRepository.findById(destinationId)
                             .orElseThrow( ()-> new DataNotFoundException("Cannot find correction with ID"));

                    if (destinationCorrection.getPost() != null) destinationId = destinationCorrection.getPost().getId();
                    else destinationId = destinationCorrection.getCorrection().getPost().getId();

                    message = " has replied your correction";

                    if (destinationCorrection.getTaggedUser() != null ) toUserList.add(destinationCorrection.getTaggedUser().getId());

                    break;
                }

                default: break;

            }

            if (giveNotification)
            {

                for (String toUser : toUserList)
                {

                    User existingUser = userRepository.findById(toUser)
                            .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID: " + toUser));

                    UUID uuid = UUID.randomUUID();
                    Notification newNotification = Notification.builder()
                            .id(uuid.toString())
                            .user(existingUser)
                            .destinationId(destinationId)
                            .actor(userActor)
                            .message(message)
                            .type(notificationType)
                            .build();

                    notificationRepository.save(newNotification);

                    //Socket
                    notificationEventPublisher.publishNotification(newNotification);

                }

            }

        } catch (Exception e) {
            log.error("Failed to add notification, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_NOTIFICATION, "Internal request");
        }
    }


    @Override
    public Page<NotificationResponse> getSelfNotification(String requestId, int page, int size)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();
            String userId = user.getId();

            Sort sort = Sort.by(
                    Sort.Order.desc("status"),
                    Sort.Order.desc("createdAt")
            );

            return notificationRepository.findByUserId(userId, PageRequest.of(page, size, sort)).map(
                    notification ->
                    {
                        NotificationResponse response = modelMapper.map(notification, NotificationResponse.class);
                        response.setActorId(notification.getActor().getId());
                        response.setUserId(notification.getUser().getId());
                        return response;
                    }

            );

        } catch (Exception e) {
        log.error("Failed to get notification, err="+e.getMessage());
        throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                Constants.ERROR_CODE.ERR_GET_NOTIFICATION, requestId);
        }
    }

    @Override
    @Transactional
    public void deleteNotification(String requestId, String notificationId)
    {

        try {

            Notification existsNotification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find notification with ID = " + notificationId));

            notificationRepository.delete(existsNotification);

        } catch (Exception e) {
            log.error("Failed to add notification, err=" + e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_DELETE_NOTIFICATION, requestId);
        }
    }

    @Override
    @Transactional
    public NotificationResponse changeStatus(String requestId, String notificationId)
    {
        try {

            Notification existsNotification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find notification with ID = " + notificationId));

            if (existsNotification.getStatus().equals(NotificationStatus.UNREAD)) existsNotification.setStatus(NotificationStatus.READ);
            else existsNotification.setStatus(NotificationStatus.UNREAD);

            NotificationResponse response = modelMapper.map(notificationRepository.save(existsNotification), NotificationResponse.class);
            response.setActorId(existsNotification.getActor().getId());
            response.setUserId(existsNotification.getUser().getId());

            return response;

        } catch (Exception e) {
            log.error("Failed to change notification status, err=" + e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CHANGE_STATUS_NOTIFICATION, requestId);
        }
    }

    @Override
    @Transactional
    public void changeAllStatus(String requestId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();
            String userId = user.getId();

            List<Notification> unreadList = notificationRepository.findByUserIdAndStatus(userId, NotificationStatus.UNREAD);

            for (Notification notification : unreadList)
            {
                notification.setStatus(NotificationStatus.READ);
                notificationRepository.save(notification);
            }

        } catch (Exception e) {
            log.error("Failed to change notification status, err=" + e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CHANGE_STATUS_NOTIFICATION, requestId);
        }
    }

    @Override
    public Long countUnreadNotifications()
    {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();
        String userId = user.getId();

        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);

    }
}
