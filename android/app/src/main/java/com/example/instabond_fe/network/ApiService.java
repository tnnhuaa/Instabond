package com.example.instabond_fe.network;

import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.model.CreatePostRequest;
import com.example.instabond_fe.model.ForgotPasswordRequest;
import com.example.instabond_fe.model.Notification;
import com.example.instabond_fe.model.NotificationPageResponse;
import com.example.instabond_fe.model.ProfileShareResponse;
import com.example.instabond_fe.model.PostSuggestionResponse;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.SearchHistoryDTO;
import com.example.instabond_fe.model.SearchHistoryRequest;
import com.example.instabond_fe.model.StoryResponse;
import com.example.instabond_fe.model.ResetPasswordRequest;
import com.example.instabond_fe.model.UpdateAllowTaggingResponse;
import com.example.instabond_fe.model.StoryViewersResponse;
import com.example.instabond_fe.model.UpdateProfileRequest;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.model.ChatMessageResponse;
import com.example.instabond_fe.model.Conversation;
import com.example.instabond_fe.model.ConversationPageResponse;
import com.example.instabond_fe.model.ChangePasswordRequest;
import com.example.instabond_fe.model.UserSearchDTO;
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
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;
import com.example.instabond_fe.model.CreateCommentRequest;
import com.example.instabond_fe.model.CommentResponse;
import com.example.instabond_fe.model.FollowUserResponse;
import com.example.instabond_fe.model.ChatMessageRequest;

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

    @GET("api/posts/{postId}")
    Call<PostResponse> getPost(@Path("postId") String postId);

    @GET("api/stories/feed")
    Call<List<StoryResponse>> getStoriesFeed();

    @Multipart
    @POST("api/stories/images")
    Call<StoryResponse> createStory(@Part MultipartBody.Part file);

    @POST("api/stories/{storyId}/view")
    Call<StoryResponse> markStoryViewed(@Path("storyId") String storyId);

    @POST("api/stories/{storyId}/like")
    Call<StoryResponse> likeStory(@Path("storyId") String storyId);

    @DELETE("api/stories/{storyId}/like")
    Call<StoryResponse> unlikeStory(@Path("storyId") String storyId);

    @DELETE("api/stories/{storyId}")
    Call<Void> deleteStory(@Path("storyId") String storyId);

    @GET("api/stories/{storyId}/viewers")
    Call<StoryViewersResponse> getStoryViewers(@Path("storyId") String storyId);

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

    @Multipart
    @POST("api/posts/suggestions")
    Call<PostSuggestionResponse> getPostSuggestions(@Part MultipartBody.Part image);

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

    @Headers("Content-Type: application/json")
    @POST("api/posts/{postId}/comments/{commentId}/like")
    Call<Void> likeComment(@Path("postId") String postId, @Path("commentId") String commentId);

    @DELETE("api/posts/{postId}/comments/{commentId}/like")
    Call<Void> unlikeComment(@Path("postId") String postId, @Path("commentId") String commentId);

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

    @GET("api/notifications")
    Call<NotificationPageResponse> getNotifications(
            @Query("page") int page,
            @Query("size") int size
    );

    @PUT("api/notifications/{id}/read")
    Call<Notification> markNotificationAsRead(@Path("id") String notificationId);
    @GET("api/users/follow-requests/incoming")
    Call<List<FollowUserResponse>> getIncomingFollowRequests();

    @POST("api/users/follow-requests/{id}/accept")
    Call<FollowUserResponse> acceptFollowRequest(@Path("id") String requesterId);

    @POST("api/users/follow-requests/{id}/reject")
    Call<Void> rejectFollowRequest(@Path("id") String requesterId);

    @GET("api/v1/search/suggestions")
    Call<List<UserSearchDTO>> getSearchSuggestions(
            @Query("type") String type,
            @Query("q") String query
    );

    // Returning JsonElement to allow dynamic parsing based on "type" (users, posts, audios)
    @GET("api/v1/search/results")
    Call<JsonElement> getSearchResults(
            @Query("type") String type,
            @Query("q") String query,
            @Query("page") int page,
            @Query("size") int size
    );

    @GET("api/v1/search/explore")
    Call<JsonElement> getExplorePosts(
            @Query("seed") long seed,
            @Query("page") int page,
            @Query("size") int size
    );

    // Search History
    @GET("api/v1/search/history")
    Call<List<SearchHistoryDTO>> getSearchHistory();

    @POST("api/v1/search/history")
    Call<Void> saveSearchHistory(@Body SearchHistoryRequest request);

    @DELETE("api/v1/search/history/{id}")
    Call<Void> deleteSearchHistory(@Path("id") String id);

    @POST("api/messages/text")
    Call<ChatMessageResponse> sendTextMessage(@Body ChatMessageRequest request);

    @POST("/api/auth/forgot-password")
    Call<ResponseBody> forgotPassword(@Body ForgotPasswordRequest request);

    @POST("/api/auth/reset-password")
    Call<ResponseBody> resetPassword(@Body ResetPasswordRequest request);

    // Bookmark
    @Headers("Content-Type: application/json")
    @POST("api/posts/{postId}/bookmark")
    Call<PostResponse> bookmarkPost(@Path("postId") String postId);

    @DELETE("api/posts/{postId}/bookmark")
    Call<PostResponse> unbookmarkPost(@Path("postId") String postId);

    @GET("api/posts/bookmarks")
    Call<JsonElement> getBookmarkedPosts();

    // Block User
    @Headers("Content-Type: application/json")
    @POST("api/users/{id}/block")
    Call<Void> blockUser(@Path("id") String userId);

    @DELETE("api/users/{id}/block")
    Call<Void> unblockUser(@Path("id") String userId);

    @GET("api/users/blocked")
    Call<List<FollowUserResponse>> getBlockedUsers();

        @GET("api/users/me/share-profile")
        Call<ProfileShareResponse> getMyShareProfile();

        @GET("api/users/{id}/share-profile")
        Call<ProfileShareResponse> getShareProfile(@Path("id") String userId);

    // Friend Suggestions
    @GET("api/users/suggestions")
    Call<List<FollowUserResponse>> getFriendSuggestions(@Query("limit") int limit);

    // QR Resolve
    @GET("api/users/resolve")
        Call<UserProfileResponse> resolveProfile(@Query("payload") String payload);

    @PATCH("api/users/me/allow-tagging")
    Call<UpdateAllowTaggingResponse> updateAllowTagging(@Query("value") String value);

    @PATCH("api/users/me/password")
    Call<ResponseBody> changePassword(@Body ChangePasswordRequest request);

    @Multipart
    @POST("/api/users/register-face")
    Call<ResponseBody> registerFace(@Part List<MultipartBody.Part> images);
}
