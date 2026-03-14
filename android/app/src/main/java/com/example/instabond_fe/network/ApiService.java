package com.example.instabond_fe.network;

import com.example.instabond_fe.model.AuthRequest;
import com.example.instabond_fe.model.AuthResponse;
import com.example.instabond_fe.model.CreatePostRequest;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.google.gson.JsonElement;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    @POST("api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body AuthRequest request);

    @GET("api/users/me")
    Call<UserProfileResponse> getMe();

    @GET("api/posts/feed")
    Call<JsonElement> getFeed();

    @GET("api/posts/user/{userId}")
    Call<JsonElement> getPostsByUserId(@Path("userId") String userId);

    @GET("api/test")
    Call<ResponseBody> testConnection();

    @POST("api/posts")
    Call<PostResponse> createPost(@Body CreatePostRequest request);
}
