package com.example.instabond_fe.network;

import com.example.instabond_fe.model.PostResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ApiListParser {
    private static final Type POST_LIST_TYPE = new TypeToken<List<PostResponse>>() {}.getType();

    private ApiListParser() {
    }

    public static List<PostResponse> parsePostList(Gson gson, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new ArrayList<>();
        }

        if (element.isJsonArray()) {
            return gson.fromJson(element, POST_LIST_TYPE);
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonArray array = findArray(object, "data", "posts", "items", "content");
            if (array != null) {
                return gson.fromJson(array, POST_LIST_TYPE);
            }
        }

        return new ArrayList<>();
    }

    private static JsonArray findArray(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonArray()) {
                return object.getAsJsonArray(key);
            }
        }
        return null;
    }
}

