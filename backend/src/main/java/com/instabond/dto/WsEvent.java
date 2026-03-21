package com.instabond.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WsEvent<T> {

    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_PRESENCE = "PRESENCE";
    public static final String TYPE_ERROR = "ERROR";

    private String type;
    private T payload;

    public static <T> WsEvent<T> of(String type, T payload) {
        return WsEvent.<T>builder()
                .type(type)
                .payload(payload)
                .build();
    }
}
