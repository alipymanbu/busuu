package com.busuu.app.dtos.requests.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class CorrectionDTO
{

    @JsonProperty("correction_audio")
    private MultipartFile correctionAudio;

    @JsonProperty("correction_text")
    private String correctionText;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("correction_id")
    private String correctionId;

    @JsonProperty("comment")
    @NotBlank(message = "Comment cannot be blank")
    private String comment;

    @JsonProperty("tag_id")
    private String tagId;

}
