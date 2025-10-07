package com.busuu.app.services.correction;

import com.busuu.app.configs.constant.Constants;
import com.busuu.app.dtos.requests.correction.CorrectionDTO;
import com.busuu.app.dtos.responses.CloudinaryResponse;
import com.busuu.app.dtos.responses.CorrectionResponse;
import com.busuu.app.entities.Correction;
import com.busuu.app.entities.User;
import com.busuu.app.entities.enums.NotificationType;
import com.busuu.app.entities.Post;
import com.busuu.app.entities.enums.PostType;
import com.busuu.app.entities.Reaction;
import com.busuu.app.entities.enums.ReactionType;
import com.busuu.app.exceptions.DataNotFoundException;
import com.busuu.app.exceptions.ErrorHandleException;
import com.busuu.app.exceptions.InvalidFileException;
import com.busuu.app.repositories.CorrectionRepository;
import com.busuu.app.repositories.PostRepository;
import com.busuu.app.repositories.ReactionRepository;
import com.busuu.app.repositories.UserRepository;
import com.busuu.app.services.cloudinary.IUploadCloudinaryService;
import com.busuu.app.services.notification.NotificationService;
import com.busuu.app.specification.CorrectionSpecification;
import com.busuu.app.utils.UploadCloudinaryUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorrectionService implements ICorrectionService
{
    
    private final CorrectionRepository correctionRepository;

    private final PostRepository postRepository;

    private final ReactionRepository reactionRepository;

    private final UserRepository userRepository;

    private final ModelMapper modelMapper;

    private final IUploadCloudinaryService uploadCloudinaryService;

    private final NotificationService notificationService;

    
    @Override
    @Transactional
    public CorrectionResponse insertCorrection(String requestId, CorrectionDTO correctionDTO) 
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Correction newCorrection = modelMapper.map(correctionDTO, Correction.class);
            newCorrection.setId(UUID.randomUUID().toString());

            //Valid postId, correctionId, tagged
            Post existingPost = null;
            Correction existingCorrection = null;
            User taggedUser = null;

            if (correctionDTO.getTagId() != null)
            {
                taggedUser = userRepository.findById(correctionDTO.getTagId())
                        .orElseThrow(() -> new DataNotFoundException("Tagged user not found"));

            }

            if ( correctionDTO.getCorrectionId() != null  && correctionDTO.getPostId() != null  ) throw new IllegalArgumentException("A correction cannot both belong to a post and another correction!");
            if ( correctionDTO.getCorrectionId() == null  && correctionDTO.getPostId() == null  ) throw new IllegalArgumentException("A correction must belong to a post or another correction!");

            if (correctionDTO.getCorrectionId() != null)
            {

                existingCorrection = correctionRepository.findById(correctionDTO.getCorrectionId())
                        .orElseThrow(() -> new DataNotFoundException("Cannot find correction with ID " + correctionDTO.getCorrectionId()));

//               if (existingCorrection.getCorrection() != null) throw new IllegalArgumentException("Correction cannot belong to a correction having father is another correction! Only allow post - correction - reply correction hierarchy!");

//                if (user.getId().equals(existingCorrection.getUser().getId()))
//                    throw new IllegalArgumentException("Cannot reply to your correction!");

            }
            else
            {

                existingPost = postRepository.findById(correctionDTO.getPostId())
                        .orElseThrow(() -> new DataNotFoundException("Cannot find post with ID " + correctionDTO.getPostId()));

                if (user.getId().equals(existingPost.getUser().getId()))
                    throw new IllegalArgumentException("Cannot correct to yourself!");

                if (existingPost.getPostType() == PostType.TEXT && correctionDTO.getCorrectionAudio() != null)
                    throw new IllegalArgumentException("Post with TEXT type cannot have correction audio");

//                if (existingPost.getPostType() == PostType.TEXT && ( correctionDTO.getCorrectionText() == null || correctionDTO.getCorrectionText().isEmpty() ))
//                    throw new IllegalArgumentException("Post with TEXT type cannot have null Correction text");

            }

            if (correctionDTO.getCorrectionAudio() != null && correctionDTO.getCorrectionText() != null ) throw new IllegalArgumentException("Both Correction audio and Correction text cannot exist at the same time");
            //if (correctionDTO.getCorrectionAudio() == null && correctionDTO.getCorrectionText() == null ) throw new IllegalArgumentException("Either Correction audio and Correction must be existed");

            //Check valid file
            if (correctionDTO.getCorrectionAudio() != null) checkFile(correctionDTO.getCorrectionAudio(), "audio");

            //MultipartFile process
            CloudinaryResponse cloudinaryResponse = null;

            //Correction audio
            if (correctionDTO.getCorrectionAudio() != null) cloudinaryResponse = uploadFile(correctionDTO.getCorrectionAudio());
            if (cloudinaryResponse != null) {
                newCorrection.setCorrectionAudioUrl(cloudinaryResponse.getUrl());
                newCorrection.setCorrectionAudioName(cloudinaryResponse.getPublicId());
            }

            //Get and set user/posts/tag
            newCorrection.setUser(user);
            newCorrection.setPost(existingPost);
            newCorrection.setCorrection(existingCorrection);
            newCorrection.setTaggedUser(taggedUser);

            //Save and map return
            newCorrection = correctionRepository.save(newCorrection);
            CorrectionResponse correctionResponse = modelMapper.map(newCorrection, CorrectionResponse.class);
            correctionResponse.setUserId(user.getId());
            correctionResponse.setPostId(newCorrection.getPost() != null ? newCorrection.getPost().getId() : null);
            correctionResponse.setCorrectionId(newCorrection.getCorrection() != null ? newCorrection.getCorrection().getId() : null);
            correctionResponse.setLikeCount(0);
            correctionResponse.setDislikeCount(0);
            if (newCorrection.getTaggedUser() != null) correctionResponse.setTagId(newCorrection.getTaggedUser().getId());

            if ( existingPost != null ) notificationService.addNotification(existingPost.getId(), NotificationType.POST_CORRECTED, null);
            else notificationService.addNotification(newCorrection.getId(), NotificationType.CORRECTION_REPLIED, null);

            return correctionResponse;

        } catch (Exception e) {
            log.error("requestId="+requestId+", failed to create new correction, err= "+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_CREATE_NEW_CORRECTION, requestId);
        }
    }


    @Override
    public CorrectionResponse getCorrection(String requestId, String correctionId)
    {

        try {

            Correction correction = correctionRepository.findById(correctionId)
                    .orElseThrow(() -> new DataNotFoundException("Cannot find Correction with ID = " + correctionId));

            CorrectionResponse correctionResponse = modelMapper.map(correction, CorrectionResponse.class);
            correctionResponse.setUserId(correction.getUser().getId());
            correctionResponse.setPostId(correction.getPost() != null ? correction.getPost().getId() : null);
            correctionResponse.setCorrectionId(correction.getCorrection() != null ? correction.getCorrection().getId() : null);

            //Get reaction of self account to the correction
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();
            Reaction reaction = reactionRepository.findByUserIdAndCorrectionId(user.getId(), correctionId);
            if (reaction != null) correctionResponse.setReaction(reaction.getReactionType().name());
            else correctionResponse.setReaction(null);

            long likeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.LIKE, correction.getId());
            long dislikeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.DISLIKE, correction.getId());
            correctionResponse.setLikeCount(likeCount);
            correctionResponse.setDislikeCount(dislikeCount);
            correctionResponse.setReplyIds(getReplyList(correctionResponse.getId()));
            if (correction.getTaggedUser() != null) correctionResponse.setTagId(correction.getTaggedUser().getId());

            return correctionResponse;

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get Correction, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_CORRECTION, requestId);
        }
    }


    @Override
    public List<CorrectionResponse> getByUserId(String requestId, String userId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            List<Correction> corrections = correctionRepository.findByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"));

            return corrections.stream().map(correction ->
            {

                CorrectionResponse correctionResponse = modelMapper.map(correction, CorrectionResponse.class);
                correctionResponse.setUserId(correction.getUser().getId());
                correctionResponse.setPostId(correction.getPost() != null ? correction.getPost().getId() : null);
                correctionResponse.setCorrectionId(correction.getCorrection() != null ? correction.getCorrection().getId() : null);

                Reaction reaction = reactionRepository.findByUserIdAndCorrectionId(user.getId(), correction.getId());
                if (reaction != null) correctionResponse.setReaction(reaction.getReactionType().name());
                else correctionResponse.setReaction(null);

                long likeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.LIKE, correction.getId());
                long dislikeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.DISLIKE, correction.getId());
                correctionResponse.setLikeCount(likeCount);
                correctionResponse.setDislikeCount(dislikeCount);
                correctionResponse.setReplyIds(getReplyList(correctionResponse.getId()));
                if (correction.getTaggedUser() != null) correctionResponse.setTagId(correction.getTaggedUser().getId());

                return correctionResponse;

            }).toList();

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get Correction list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_CORRECTION, requestId);
        }
    }


    @Override
    public List<CorrectionResponse> getByPostId(String requestId, String postId)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            List<Correction> corrections = correctionRepository.findByPostId(postId, Sort.by(Sort.Direction.DESC, "createdAt"));

            return corrections.stream().map(correction ->
            {
                CorrectionResponse correctionResponse = modelMapper.map(correction, CorrectionResponse.class);
                correctionResponse.setUserId(correction.getUser().getId());
                correctionResponse.setPostId(correction.getPost() != null ? correction.getPost().getId() : null);
                correctionResponse.setCorrectionId(correction.getCorrection() != null ? correction.getCorrection().getId() : null);

                Reaction reaction = reactionRepository.findByUserIdAndCorrectionId(user.getId(), correction.getId());
                if (reaction != null) correctionResponse.setReaction(reaction.getReactionType().name());
                else correctionResponse.setReaction(null);

                long likeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.LIKE, correction.getId());
                long dislikeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.DISLIKE, correction.getId());
                correctionResponse.setLikeCount(likeCount);
                correctionResponse.setDislikeCount(dislikeCount);
                correctionResponse.setReplyIds(getReplyList(correctionResponse.getId()));
                if (correction.getTaggedUser() != null) correctionResponse.setTagId(correction.getTaggedUser().getId());

                return correctionResponse;

            }).toList();

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get Correction list, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_CORRECTION, requestId);
        }
    }


    @Override
    public Page<CorrectionResponse> getSelfCorrection(String requestId, int page, int size, List<String> sortBy, List<String> sortDirection, String searchValue, String language)
    {
        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            Pageable pageable = PageRequest.of(page, size);

            Page<Correction> corrections = correctionRepository.findAll(CorrectionSpecification.getSpecification(language, sortBy, sortDirection, user.getId()), pageable);

            return corrections.map(correction ->
            {

                CorrectionResponse correctionResponse = modelMapper.map(correction, CorrectionResponse.class);
                correctionResponse.setUserId(correction.getUser().getId());
                correctionResponse.setPostId(correction.getPost() != null ? correction.getPost().getId() : null);
                correctionResponse.setCorrectionId(correction.getCorrection() != null ? correction.getCorrection().getId() : null);
                correctionResponse.setReaction(null);

                long likeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.LIKE, correction.getId());
                long dislikeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.DISLIKE, correction.getId());
                correctionResponse.setLikeCount(likeCount);
                correctionResponse.setDislikeCount(dislikeCount);
                correctionResponse.setReplyIds(getReplyList(correctionResponse.getId()));
                if (correction.getTaggedUser() != null) correctionResponse.setTagId(correction.getTaggedUser().getId());

                return correctionResponse;

            });

        } catch (Exception e) {
            log.error("requestId="+requestId+",failed to get corrections, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_GET_CORRECTION, requestId);
        }
    }

    @Override
    @Transactional
    public CorrectionResponse reaction(String requestId, String correctionId, String reaction)
    {

        try {

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User user = (User) auth.getPrincipal();

            //Valid correctionId, exist reaction, react to self
            Correction existingCorrection = correctionRepository.findById(correctionId)
                    .orElseThrow( ()-> new DataNotFoundException("Cannot find correction with ID " + correctionId) );

            if (user.getId().equals(existingCorrection.getUser().getId())) throw new IllegalArgumentException("Cannot react to yourself!");

            //Valid reaction
            ReactionType reactionType;
            try {

                reactionType = ReactionType.valueOf(reaction.toUpperCase());

            } catch (Exception e) {

                throw new IllegalArgumentException("Illegal reaction! Must be \"LIKE\" or \"DISLIKE\" (ignore case) ");

            }

            Reaction newReaction;

            Reaction existingReaction = reactionRepository.findByUserIdAndCorrectionId(user.getId(), correctionId);
            if (existingReaction != null)
            {
                existingReaction.setReactionType(reactionType);
                newReaction = reactionRepository.save(existingReaction);
            }
            else
            {
                //Add reaction
                newReaction = Reaction.builder()
                        .id(UUID.randomUUID().toString())
                        .user(user)
                        .correction(existingCorrection)
                        .reactionType(reactionType)
                        .build();

                newReaction = reactionRepository.save(newReaction);

                notificationService.addNotification(newReaction.getCorrection().getId(), NotificationType.CORRECTION_REACTION, newReaction.getReactionType());

            }

            CorrectionResponse correctionResponse = modelMapper.map(existingCorrection, CorrectionResponse.class);
            correctionResponse.setUserId(user.getId());
            correctionResponse.setPostId(existingCorrection.getPost() != null ? existingCorrection.getPost().getId() : null);
            correctionResponse.setCorrectionId(existingCorrection.getCorrection() != null ? existingCorrection.getCorrection().getId() : null);
            correctionResponse.setReaction(newReaction.getReactionType().name());

            long likeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.LIKE, existingCorrection.getId());
            long dislikeCount = reactionRepository.countByReactionTypeAndCorrectionId(ReactionType.DISLIKE, existingCorrection.getId());
            correctionResponse.setLikeCount(likeCount);
            correctionResponse.setDislikeCount(dislikeCount);
            correctionResponse.setReplyIds(getReplyList(correctionResponse.getId()));
            if (existingCorrection.getTaggedUser() != null) correctionResponse.setTagId(existingCorrection.getTaggedUser().getId());

            return correctionResponse;

        } catch (DataNotFoundException e) {
            log.error("requestId="+requestId+",failed to add reaction, err="+e.getMessage());
            throw new ErrorHandleException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR,
                    Constants.ERROR_CODE.ERR_ADD_REACTION, requestId);
        }

    }


    private List<String> getReplyList(String correctionId)
    {

        return correctionRepository.findByCorrectionId(correctionId).stream().map(Correction::getId).toList();

    }

    private CloudinaryResponse uploadFile(MultipartFile file) throws Exception
    {

        //Get file type
        String contentType = file.getContentType();

        String resourceType;

        if (contentType != null) resourceType = contentType.split("/")[0];
        else throw new IllegalArgumentException("ContentType pf file must not be null");

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
}
