package com.busuu.app.dtos.responses;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class CorrectionResponse extends BaseResponse
{
    @JsonProperty("correction_id")
    private String id;

    @JsonProperty("correction_audio_name")
    private String correctionAudioName;

    @JsonProperty("correction_audio_url")
    private String correctionAudioUrl;

    @JsonProperty("correction_text")
    private String correctionText;

    @JsonProperty("like_count")
    private long likeCount;

    @JsonProperty("dislike_count")
    private long dislikeCount;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("reaction")
    private String reaction;

    @JsonProperty("parent_correction_id")
    private String correctionId;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("tag_id")
    private String tagId;

    @JsonProperty("reply_ids")
    private List<String> replyIds;

}
