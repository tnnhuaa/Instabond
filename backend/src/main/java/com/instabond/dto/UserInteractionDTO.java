package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserInteractionDTO {
    private String senderId;
    private String receiverId;
    private String actionType; // "LIKE", "UNLIKE", "COMMENT", "DELETE_COMMENT", "TAG", "CHAT"
}
