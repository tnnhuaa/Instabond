package com.example.instabond_fe.network;

import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.model.CreatePostRequest;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UpdateProfileRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.google.gson.JsonElement;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @GET("api/users/me")
    Call<UserProfileResponse> getMe();

    @GET("api/posts/feed")
    Call<JsonElement> getFeed(@Query("page") int page,
                              @Query("size") int size);

    @GET("api/posts/feed")
    Call<JsonElement> getFeed();

    @GET("api/posts/user/{userId}")
    Call<JsonElement> getPostsByUserId(@Path("userId") String userId);

    @GET("api/test")
    Call<ResponseBody> testConnection();

    @POST("api/posts")
    Call<PostResponse> createPost(@Body CreatePostRequest request);

    @PUT("api/users/{id}")
    Call<UserProfileResponse> updateProfile(@Path("id") String userId, @Body UpdateProfileRequest request);

    @Multipart
    @PUT("api/users/{id}/avatar")
    Call<UserProfileResponse> uploadAvatar(@Path("id") String userId, @Part MultipartBody.Part file);

    @POST("api/conversations/direct")
    Call<Conversation> getOrCreateDirectConversation(@Query("partnerId") String partnerId);

    @GET("api/messages/conversation/{conversationId}/history")
    Call<JsonElement> getHistory(
            @Path("conversationId") String convId,
            @Query("page") int page,
            @Query("size") int size
    );

    @Multipart
    @POST("api/messages/conversation/{conversationId}/images")
    Call<ChatMessageResponse> uploadChatImage(
            @Path("conversationId") String convId,
            @Part MultipartBody.Part file
    );

    @POST("api/messages/conversation/{conversationId}/read")
    Call<Void> markMessagesAsRead(@Path("conversationId") String convId);

    @GET("api/conversations")
    Call<ConversationPageResponse> getUserConversations(
            @Query("cursor") String cursor,
            @Query("limit") int limit
    );
}
