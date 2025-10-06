package com.busuu.app.services.post;

import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.requests.post.PostDTO;
import com.busuu.app.dtos.responses.CloudinaryResponse;
import com.busuu.app.dtos.responses.FriendshipResponse;
import com.busuu.app.dtos.responses.PostResponse;
import com.busuu.app.entities.Language;
import com.busuu.app.entities.User;
import com.busuu.app.entities.UserLanguage;
import com.busuu.app.entities.Correction;
import com.busuu.app.entities.Post;
import com.busuu.app.entities.enums.NotificationType;
import com.busuu.app.entities.enums.PostType;
import com.busuu.app.entities.topics.Topic;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.exceptions.InvalidFileException;
import com.busuu.app.repositories.CorrectionRepository;
import com.busuu.app.repositories.PostRepository;
import com.busuu.app.repositories.TopicRepository;
import com.busuu.app.repositories.UserLanguageRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.cloudinary.IUploadCloudinaryService;
import com.busuu.app.services.friendship.IFriendshipService;
import com.busuu.app.services.notification.INotificationService;
import com.busuu.app.specification.PostSpecification;
import com.busuu.app.utils.UploadCloudinaryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService implements IPostService
{

    private final ModelMapper modelMapper;

    private final IUploadCloudinaryService uploadCloudinaryService;

    private final IFriendshipService friendService;

    private final PostRepository postRepository;

    private final UserRepository userRepository;

    private final CorrectionRepository correctionRepository;

    private final TopicRepository topicRepository;

    private final UserLanguageRepository userLanguageRepository;

    private final INotificationService notificationService;



    @Override
    @Transactional
    public PostResponse insertPost(String requestId, PostDTO postDTO)
    {
        try {

            Post newPost = modelMapper.map(postDTO, Post.class);
            newPost.setId(UUID.randomUUID().toString());
            newPost.setPostType(PostType.valueOf(postDTO.getPostTypes().toUpperCase()));

            //Exception
            if (newPost.getPostType() == PostType.TEXT && ( postDTO.getPostText() == null || postDTO.getPostText().isEmpty()) )
                throw new IllegalArgumentException("Post with TEXT type cannot have null posts text");
            if (newPost.getPostType() == PostType.AUDIO && postDTO.getPostAudio() == null)
                throw new IllegalArgumentException("Post with AUDIO type cannot have null posts audio");
            if (postDTO.getPostAudio() != null && postDTO.getPostText() != null ) throw new IllegalArgumentException("Both post audio and Correction text cannot exist at the same time");
            Topic topic = topicRepository.findById(postDTO.getTopicId())
                    .orElseThrow(() -> new DataNotFoundException("Cannot find topic with ID = " + postDTO.getTopicId()));

            if (postDTO.getPostAudio() != null) checkFile(postDTO.getPostAudio(), "audio");

            //MultipartFile process
            CloudinaryResponse cloudinaryResponse = null;

            //Post audio
            if (postDTO.getPostAudio() != null) cloudinaryResponse = uploadFile(postDTO.getPostAudio());
            if (cloudinaryResponse != null) {
                newPost.setPostAudioUrl(cloudinaryResponse.getUrl());
                newPost.setPostAudioName(cloudinaryResponse.getPublicId());
            }

            //Get and set post
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Language language = userLanguageRepository.findByUserIdAndIsLearning(user.getId(), true).getLanguage();

            newPost.setUser(user);
            newPost.setLanguage(language);
            newPost.setTopic(topic);

            //Save and map return
            newPost = postRepository.save(newPost);
            PostResponse postResponse = modelMapper.map(newPost, PostResponse.class);
            postResponse.setUserId(user.getId());
            postResponse.setLanguageId(language.getId());
            postResponse.setCorrectionCount(0);
            postResponse.setTopicId(newPost.getTopic().getId());

            //Send notification to friends
            notificationService.addNotification(newPost.getId(), NotificationType.NEW_POST, null);

            return postResponse;

        } catch (Exception e) {
            log.error("requestId="+requestId+", failed to create new posts, err= "+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_NEW_POST, requestId);
        }
    }


    @Override
    public Page<PostResponse> getPosts(String requestId, int page, int size, List<String> sortBy, List<String> sortDirection, String searchValue, String postType, String language)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Pageable pageable = PageRequest.of(page, size);

            if (language == null || language.isEmpty())
            {
                Language languageLearning = userLanguageRepository.findByUserIdAndIsLearning(user.getId(), true).getLanguage();
                language = languageLearning.getName();

            }

            Page<Post> posts = postRepository.findAll(PostSpecification.getSpecification(postType, language, sortBy, sortDirection, null), pageable);

            return posts.map(
                    post -> {

                        PostResponse postResponse = modelMapper.map(post, PostResponse.class);
                        postResponse.setUserId(post.getUser().getId());
                        postResponse.setLanguageId(post.getLanguage().getId());

                        long correctionCount = correctionRepository.countByPostId(post.getId());
                        postResponse.setCorrectionCount(correctionCount);
                        postResponse.setTopicId(post.getTopic().getId());

                        return postResponse;

                    });

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }
    }


    @Override
    public PostResponse getPost(String requestId, String postId)
    {
        try {

            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find Post with ID = " + postId));

            PostResponse postResponse = modelMapper.map(post, PostResponse.class);
            postResponse.setUserId(post.getUser().getId());
            postResponse.setLanguageId(post.getLanguage().getId());

            long correctionCount = correctionRepository.countByPostId(postId);
            postResponse.setCorrectionCount(correctionCount);
            postResponse.setTopicId(post.getTopic().getId());

            return postResponse;

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }
    }


    @Override
    public Page<PostResponse> getByUserId(String requestId, String userId, int page, int size, List<String> sortBy, List<String> sortDirection, String searchValue, String postType, String language)
    {

        try {

            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            Pageable pageable = PageRequest.of(page, size);

            if (language == null || language.isEmpty())
            {
                UserLanguage languageLearning = userLanguageRepository.findByUserIdAndIsLearning(existingUser.getId(), true);
                if ( languageLearning == null ) language = "English";
                else language = languageLearning.getLanguage().getName();

            }

            Page<Post> posts = postRepository.findAll(PostSpecification.getSpecification(postType, language, sortBy, sortDirection, userId), pageable);

            return posts.map(post ->
            {
                PostResponse postResponse = modelMapper.map(post, PostResponse.class);
                postResponse.setUserId(post.getUser().getId());
                postResponse.setLanguageId(post.getLanguage().getId());

                long correctionCount = correctionRepository.countByPostId(post.getId());
                postResponse.setCorrectionCount(correctionCount);
                postResponse.setTopicId(post.getTopic().getId());

                return postResponse;

            });

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }

    }


    @Override
    public Page<PostResponse> getByFriendlist(String requestId, int page, int size)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Get friend list
            FriendshipResponse friendList = friendService.getFriendsByUserId(user.getId());

            //Get friend's id list
            List<String> idList = friendList.getFriendIds();

            //Loop through the list to get posts of each friend then apply to response
            List<PostResponse> response = new ArrayList<>();

            for (String id : idList)
            {
                List<PostResponse> friendPost = getByUserId(id);
                response.addAll(friendPost);
            }

            //Pageable
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
            int totalElement = response.size();
            int startAt = (int) pageable.getOffset();
            int endAt = Math.min(startAt + pageable.getPageSize(), totalElement);

            List<PostResponse> resultList = (startAt <= endAt)
                    ? response.subList(startAt, endAt)
                    : Collections.emptyList();

            return new PageImpl<>(resultList, pageable, totalElement);

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }

    }


    public Page<PostResponse> getSelfPost(String requestId, int page, int size, List<String> sortBy, List<String> sortDirection, String searchValue, String postType, String language)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Pageable pageable = PageRequest.of(page, size);

            if (language == null || language.isEmpty())
            {
                UserLanguage languageLearning = userLanguageRepository.findByUserIdAndIsLearning(user.getId(), true);
                if ( languageLearning == null ) language = "English";
                else language = languageLearning.getLanguage().getName();

            }

            Page<Post> posts = postRepository.findAll(PostSpecification.getSpecification(postType, language, sortBy, sortDirection, user.getId()), pageable);

            return posts.map(
                    post -> {

                        PostResponse postResponse = modelMapper.map(post, PostResponse.class);
                        postResponse.setUserId(post.getUser().getId());
                        postResponse.setLanguageId(post.getLanguage().getId());

                        long correctionCount = correctionRepository.countByPostId(post.getId());
                        postResponse.setCorrectionCount(correctionCount);
                        postResponse.setTopicId(post.getTopic().getId());

                        return postResponse;

                    });

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }
    }

    public Page<PostResponse> getPostsContainUserCorrection(String requestId, int page, int size, String userId)
    {

        try {

            if (userId == null || userId.isEmpty())
            {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                User user = (User) auth.getPrincipal();
                userId = user.getId();

            }

            UserLanguage language = userLanguageRepository.findByUserIdAndIsLearning(userId, true);
            String languageName;
            if (language == null) languageName = "English";
            else languageName = language.getLanguage().getName();

            //Temp value
            List<Post> responseTemp = new ArrayList<>();

            //Get list of self correction
            List<Correction> selfCorrections = correctionRepository.findByUserIdAndPostLanguageName(userId, languageName);

            //Get list of post that contain these correction (may duplicate due to multiple correction in 1 post)
            for (Correction correction : selfCorrections)
            {

                Post post = correction.getPost();
                if (!post.getUser().getId().equals(userId)) responseTemp.add(post);

            }

            //Remove duplicate
            Set<Post> set = new HashSet<>(responseTemp);
            List<Post> response = new ArrayList<>(set);

            List<PostResponse> res = response.stream().map(
                    post -> {

                        PostResponse postResponse = modelMapper.map(post, PostResponse.class);
                        postResponse.setUserId(post.getUser().getId());
                        postResponse.setLanguageId(post.getLanguage().getId());

                        long correctionCount = correctionRepository.countByPostId(post.getId());
                        postResponse.setCorrectionCount(correctionCount);
                        postResponse.setTopicId(post.getTopic().getId());

                        return postResponse;

                    }).toList();

            //Pageable
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
            int totalElement = response.size();
            int startAt = (int) pageable.getOffset();
            int endAt = Math.min(startAt + pageable.getPageSize(), totalElement);

            List<PostResponse> resultList = (startAt <= endAt)
                    ? res.subList(startAt, endAt)
                    : Collections.emptyList();

            return new PageImpl<>(resultList, pageable, totalElement);

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get posts, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, requestId);
        }
    }


    private CloudinaryResponse uploadFile(MultipartFile file) throws Exception
    {

        //Get file type
        String contentType = file.getContentType();

        String resourceType;

        if (contentType != null)
            resourceType = contentType.split("/")[0];
        else
            throw new IllegalArgumentException("ContentType of file must not be null");

        //Upload
        UploadCloudinaryUtil.assertAllowed(file, resourceType);
        String fileName = UploadCloudinaryUtil.getFileName(file.getOriginalFilename());
        return uploadCloudinaryService.uploadFile(file, fileName, resourceType);

    }

    private void checkFile(MultipartFile file, String requiredType)
    {

        //Get file type
        String contentType = file.getContentType(); //Return like e.g. "image/png", "video/mp4", ...
        if (contentType == null || !contentType.contains("/")) {
            throw new InvalidFileException("Unsupported file type: " + contentType);
        }

        String resourceType = contentType.split("/")[0]; //e.g. "image", "video", "application"

        if (resourceType.equals("application") || resourceType.equals("text")) throw new InvalidFileException("Unsupported file type: " + resourceType);
        if (!resourceType.equals(requiredType)) throw new InvalidFileException("Invalid file type: required: " + requiredType + " but get: " + resourceType);

    }

    public List<PostResponse> getByUserId(String userId)
    {

        try {

            User existingUser = userRepository.findById(userId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find user with ID " + userId ));

            List<Post> posts = postRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"));

            return posts.stream().map(post ->
            {
                PostResponse postResponse = modelMapper.map(post, PostResponse.class);
                postResponse.setUserId(post.getUser().getId());
                postResponse.setLanguageId(post.getLanguage().getId());

                long correctionCount = correctionRepository.countByPostId(post.getId());
                postResponse.setCorrectionCount(correctionCount);
                postResponse.setTopicId(post.getTopic().getId());

                return postResponse;

            }).toList();

        } catch (Exception e) {
            log.error("failed to get posts list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_POST, "Internal request");
        }

    }

}
