package com.busuu.app.dtos.requests.correction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 5000, message = "Invalid correction length (maximum length is 5000 words)!")
    private String correctionText;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("correction_id")
    private String correctionId;

    @JsonProperty("comment")
    @Size(max = 5000, message = "Invalid comment length (maximum length is 5000 words)!")
    @NotBlank(message = "Comment cannot be blank")
    private String comment;

    @JsonProperty("tag_id")
    private String tagId;

}
