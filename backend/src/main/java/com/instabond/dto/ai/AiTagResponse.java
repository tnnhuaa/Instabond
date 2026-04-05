package com.instabond.dto.ai;

import com.instabond.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTagResponse {
    private List<DetectedFace> detected_faces;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedFace {
        private String matched_user_id;
        private Double confidence;
        private Post.TaggedUser.Position position;
    }
}