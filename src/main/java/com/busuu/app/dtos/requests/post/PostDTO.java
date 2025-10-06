package com.busuu.app.dtos.requests.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostDTO
{

    @JsonProperty("post_type")
    @NotBlank(message = "Post type cannot be null")
    @Pattern(regexp = "TEXT|AUDIO", message = "Invalid posts type. Must be 'TEXT' or 'AUDIO' (uppercase required).")
    private String postTypes;

    @JsonProperty("post_audio")
    private MultipartFile postAudio;

    @JsonProperty("post_text")
    @Size(max = 5000, message = "Invalid post text length (maximum length is 5000 words)!")
    private String postText;

    @JsonProperty("topic_id")
    @NotBlank(message = "Topic id cannot be null")
    private String topicId;

}
