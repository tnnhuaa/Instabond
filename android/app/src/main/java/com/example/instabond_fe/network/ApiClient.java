package com.example.instabond_fe.network;

import android.content.Context;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // OpenAPI server is localhost:8080; Android emulator must use 10.0.2.2.
    private static final String BASE_URL = "http://10.0.2.2:8080/";

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static ApiService getApiService(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            SessionManager sessionManager = new SessionManager(appContext);

            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                String token = sessionManager.getAccessToken();
                if (token == null || token.isEmpty()) {
                    return chain.proceed(original);
                }

                Request request = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .build();
                return chain.proceed(request);
            };

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(ApiService.class);
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }
}
