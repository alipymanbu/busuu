package com.busuu.app.services.friendship;

import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.responses.FriendshipResponse;
import com.busuu.app.dtos.responses.UserInfoResponse;
import com.busuu.app.dtos.responses.UserLanguageResponse;
import com.busuu.app.entities.Friendship;
import com.busuu.app.entities.User;
import com.busuu.app.entities.UserLanguage;
import com.busuu.app.entities.enums.FriendshipStatus;
import com.busuu.app.entities.enums.LearningStatus;
import com.busuu.app.entities.enums.SpeakingStatus;
import com.busuu.app.entities.enums.NotificationType;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.exceptions.ExistDataException;
import com.busuu.app.repositories.FriendshipRepository;
import com.busuu.app.repositories.UserLanguageRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.notification.NotificationService;
import com.busuu.app.services.publisher.FriendRequestEventPublisher;
import com.busuu.app.services.user.UserPresenceService;
import com.busuu.app.specification.UserLanguageSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FriendshipService implements IFriendshipService
{

    // Service
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

    // Repositories
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserLanguageRepository userLanguageRepository;

    // Mapper
    private final ModelMapper modelMapper;

    // Publisher
    private final FriendRequestEventPublisher friendRequestEventPublisher;



    @Override
    @Transactional
    public String addFriendRequest(String requestId, String userId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Check valid userId, already friend/pending/rejected, add friend to self
            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            if (user.getId().equals(existingUser.getId())) throw new IllegalArgumentException("Cannot add friend to yourself!");

            Friendship friendship;
            Friendship friendshipDirect = friendshipRepository.findByFromUserAndToUser(user, existingUser);
            Friendship friendshipReverse = friendshipRepository.findByFromUserAndToUser(existingUser, user);

            if ( friendshipDirect != null ) friendship = friendshipDirect;
            else friendship = friendshipReverse;

            if ( friendship == null)
            {
                //Adding relationship if no friendship exist
                Friendship newFriendship = Friendship.builder()
                        .id(UUID.randomUUID().toString())
                        .fromUser(user)
                        .toUser(existingUser)
                        .build();

                friendshipRepository.save(newFriendship);
                notificationService.addNotification(userId, NotificationType.FRIEND_REQUESTED, null);
                friendRequestEventPublisher.publishFriendRequest(userId);

                return "Send friend request to user " + existingUser.getFullName() + " successfully!";
            }
            else
            {
                switch (friendship.getStatus())
                {
                    //Is already friend
                    case ACCEPT:
                    {
                        throw new ExistDataException("You and that user is already friend!");
                    }
                    //Is having pending request
                    case PENDING:
                    {
                        if ( friendship.getFromUser().getId().equals(user.getId())) throw new ExistDataException("You have already sent request to that user!");
                        else throw new ExistDataException("Please respond the friend request from that user!");
                    }
                    //Is having reject request
                    case REJECT:
                    {

                        if ( friendship.getFromUser().getId().equals(user.getId()))
                        {
                            friendship.setStatus(FriendshipStatus.PENDING);
                            friendshipRepository.save(friendship);
                            notificationService.addNotification(userId, NotificationType.FRIEND_REQUESTED, null);
                            friendRequestEventPublisher.publishFriendRequest(userId);
                            return "Send friend request to user " + existingUser.getFullName() + " successfully!";
                        }
                        else
                        {
                            User tempToUser = friendship.getFromUser();
                            friendship.setStatus(FriendshipStatus.PENDING);
                            friendship.setFromUser(friendship.getToUser());
                            friendship.setToUser(tempToUser);

                            friendshipRepository.save(friendship);
                            notificationService.addNotification(userId, NotificationType.FRIEND_REQUESTED, null);
                            friendRequestEventPublisher.publishFriendRequest(userId);

                            return "Send friend request to user " + existingUser.getFullName() + " successfully!";
                        }

                    }

                    default: return "Invalid status!";

                }
            }

        } catch (Exception e) {
            log.error("requestId="+requestId+", failed to add relationship, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_FRIENDSHIP, requestId);
        }
    }


    @Override
    @Transactional
    public String respondRequest(String requestId, String userId, String respond)
    {

        try {

            if ( !respond.equalsIgnoreCase("accept") && !respond.equalsIgnoreCase("reject")) throw new IllegalArgumentException("Invalid respond! Must be \"accept\" or \"reject\"");

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Check valid userId, already friend/pending/rejected, add friend to self
            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            if (user.getId().equals(existingUser.getId())) throw new IllegalArgumentException("Cannot add friend to yourself!");

            Friendship friendship = friendshipRepository.findByFromUserAndToUser(existingUser, user);
            if ( friendship == null ) throw new DataNotFoundException("That user doesn't send you request to respond");
            else
            {
                FriendshipStatus friendshipStatus = friendship.getStatus();
                if ( friendshipStatus.equals(FriendshipStatus.ACCEPT) || friendshipStatus.equals(FriendshipStatus.REJECT) ) throw new IllegalArgumentException("You have already respond the friend invitation from that user!");
                else
                {
                    friendship.setStatus(FriendshipStatus.valueOf(respond.toUpperCase()));
                    friendshipRepository.save(friendship);
                    if ( FriendshipStatus.valueOf(respond.toUpperCase()).equals(FriendshipStatus.ACCEPT) ) notificationService.addNotification(userId, NotificationType.FRIEND_ACCEPTED, null);
                    return "Respond friend request to user " + existingUser.getFullName() + " successfully!";
                }
            }


        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to respond invitation, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_FRIENDSHIP, requestId);
        }
    }

    @Override
    public List<UserInfoResponse> getFriends(String requestId, List<String> sortBy, List<String> sortDirection, String searchValue, String languageId)
    {
        try {
            return userPresenceService.getFriendsStatus(requestId);

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get friend list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_FRIENDSHIP, requestId);
        }
    }


    @Override
    public FriendshipResponse getFriendsByUserId(String userId)
    {
        try {

            List<String> friendIds = userRepository.findFriendIds(userId, FriendshipStatus.ACCEPT);

           return FriendshipResponse.builder()
                    .userId(userId)
                    .friendIds(friendIds)
                    .build();

        } catch (Exception e) {
            log.error("request failed to get friend list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_FRIENDSHIP, "Internal request" );
        }
    }

    @Override
    public FriendshipResponse getPendingRequest(String requestId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            List<Friendship> friendList = friendshipRepository.findByToUserAndAndStatus(user, FriendshipStatus.PENDING);

            List<String> userIdList = new ArrayList<>();
            for (Friendship friendship : friendList) userIdList.add(friendship.getFromUser().getId());

            return FriendshipResponse.builder()
                    .userId(user.getId())
                    .friendIds(userIdList)
                    .build();

        } catch (Exception e) {
            log.error("request failed to get pending friend list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_FRIENDSHIP, "Internal request" );
        }
    }

    @Override
    public List<String> getRandomList(String requestId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Get speaking and learning language of self
            List<String> learning = new ArrayList<>();
            List<String> speaking = new ArrayList<>();

            List<UserLanguage> userLanguages = userLanguageRepository.findByUserId(user.getId());

            for (UserLanguage userLanguage : userLanguages)
            {
                if ( userLanguage.getLearningStatus() != null && userLanguage.getLearningStatus().equals(LearningStatus.IN_PROGRESS)) learning.add(userLanguage.getLanguage().getName());
                if ( userLanguage.getSpeakingStatus() != null && !userLanguage.getSpeakingStatus().equals(SpeakingStatus.NO_PROFICIENCY) ) speaking.add(userLanguage.getLanguage().getName());
            }

            Specification<UserLanguage> spec = UserLanguageSpecification.getSpecification(learning, speaking);
            List<UserLanguageResponse> responseList = userLanguageRepository.findAll(spec).stream().map(
                    userLanguage ->  {

                        UserLanguageResponse res = modelMapper.map(userLanguage, UserLanguageResponse.class);
                        res.setLanguageId(userLanguage.getLanguage().getId());
                        res.setUserId(userLanguage.getUser().getId());

                        return res;
                    }
            ).toList(); //This list is unmodifiable

            //Get user id from list
            List<String> userIdList = new ArrayList<>();
            for (UserLanguageResponse userLanguage : responseList) userIdList.add(userLanguage.getUserId());

            //Remove duplicate and friends
            Set<String> set = new HashSet<>(userIdList);
            List<String> response = new ArrayList<>(set);
            response.remove(user.getId());

            List<String> friendIds = userRepository.findFriendIds(user.getId(), FriendshipStatus.ACCEPT);
            for (String friendId : friendIds) response.remove(friendId);

            return randomize(response);

        } catch (Exception e) {
            log.error("requestId=" + requestId + ",failed to get friend list, err=" + e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_FRIENDSHIP, requestId);
        }
    }


    @Override
    public String getFriendshipStatus(String requestId, String userId)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Check valid userId, already friend/pending/rejected, add friend to self
            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            if (user.getId().equals(existingUser.getId())) throw new IllegalArgumentException("Cannot get status to yourself!");

            //Is already friend
            boolean alreadyFrom = friendshipRepository.existsByFromUserAndToUserAndStatus(user, existingUser, FriendshipStatus.ACCEPT);
            boolean alreadyTo = friendshipRepository.existsByFromUserAndToUserAndStatus(existingUser, user, FriendshipStatus.ACCEPT);
            if (alreadyFrom || alreadyTo) return "ACCEPTED";

            //Is having a pending invitation
            boolean pendingFrom = friendshipRepository.existsByFromUserAndToUserAndStatus(user, existingUser, FriendshipStatus.PENDING);
            if ( pendingFrom ) return "INVITATION_SENT";
            boolean pendingTo = friendshipRepository.existsByFromUserAndToUserAndStatus(existingUser, user, FriendshipStatus.PENDING);
            if ( pendingTo ) return "PENDING_RESPOND";

            return "NONE";

        } catch (Exception e) {
            log.error("requestId=" + requestId + ",failed to get friend status, err=" + e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_FRIENDSHIP, requestId);
        }
    }


    @Override
    @Transactional
    public void deleteFriend(String requestId, String userId)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Check valid userId, not friend, delete to self
            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            if (user.getId().equals(existingUser.getId())) throw new IllegalArgumentException("Cannot process to yourself!");

            //Is not friend
            Friendship alreadyFrom = friendshipRepository.findByFromUserAndToUserAndStatus(user, existingUser, FriendshipStatus.ACCEPT);
            Friendship alreadyTo = friendshipRepository.findByFromUserAndToUserAndStatus(existingUser, user, FriendshipStatus.ACCEPT);
            if (alreadyFrom == null && alreadyTo == null) throw new ExistDataException("You and that user is not friend!");

            if (alreadyFrom != null) friendshipRepository.delete(alreadyFrom);
            else friendshipRepository.delete(alreadyTo);

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to delete friend, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_DELETE_FRIENDSHIP, requestId);
        }
    }

    public List<String> randomize(List<String> list)
    {

        if (list.size() <= 5) {
            return list;
        }

        //Response list
        List<String> resultList = new ArrayList<>(5);

        //Shuffle the copy list
        List<String> copy = new ArrayList<>(list);
        Collections.shuffle(copy);

        //Add the first 5 elements of the shuffled list to result list
        for (int i = 0; i < 5; i++) resultList.add(copy.get(i));

        return resultList;

    }

}
