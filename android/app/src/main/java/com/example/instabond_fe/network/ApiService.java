package com.example.instabond_fe.network;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("api/test")
    Call<ResponseBody> testConnection();
}
