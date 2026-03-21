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
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;
import com.example.instabond_fe.model.CreateCommentRequest;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.model.FollowUserResponse;

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

    @Multipart
    @POST("api/posts")
    Call<PostResponse> createPost(
            @Part("request") RequestBody request,
            @Part List<MultipartBody.Part> files
    );

    @PUT("api/users/{id}")
    Call<UserProfileResponse> updateProfile(@Path("id") String userId, @Body UpdateProfileRequest request);

    @POST("api/users/me/private")
    Call<UserProfileResponse> enablePrivateMode();

    @DELETE("api/users/me/private")
    Call<UserProfileResponse> disablePrivateMode();

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

    @Headers("Content-Type: application/json")
    @POST("api/posts/{postId}/like")
    Call<PostResponse> likePost(@Path("postId") String postId);

    @DELETE("api/posts/{postId}/like")
    Call<PostResponse> unlikePost(@Path("postId") String postId);

    @Headers("Content-Type: application/json")
    @POST("api/posts/{postId}/share")
    Call<PostResponse> sharePost(@Path("postId") String postId);

    @DELETE("api/posts/{postId}/share")
    Call<PostResponse> unsharePost(@Path("postId") String postId);

    @GET("api/posts/{postId}/comments")
    Call<List<CommentResponse>> getComments(@Path("postId") String postId);

    @POST("api/posts/{postId}/comments")
    Call<CommentResponse> addComment(@Path("postId") String postId, @Body CreateCommentRequest request);

    @GET("api/users/{id}")
    Call<UserProfileResponse> getUserProfile(@Path("id") String userId);

    @Headers("Content-Type: application/json")
    @POST("api/users/{id}/follow")
    Call<FollowUserResponse> followUser(@Path("id") String userId);

    @DELETE("api/users/{id}/follow")
    Call<Void> unfollowUser(@Path("id") String userId);

    @GET("api/users/{id}/followers")
    Call<List<FollowUserResponse>> getFollowers(@Path("id") String userId);

    @GET("api/users/{id}/following")
    Call<List<FollowUserResponse>> getFollowing(@Path("id") String userId);

    @POST("api/users/{id}/close-friend")
    Call<FollowUserResponse> setCloseFriend(
            @Path("id") String userId,
            @Query("isCloseFriend") boolean isCloseFriend
    );
}
