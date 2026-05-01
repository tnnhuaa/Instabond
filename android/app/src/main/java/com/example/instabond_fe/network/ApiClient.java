package com.example.instabond_fe.network;

import com.example.instabond_fe.BuildConfig;
import com.example.instabond_fe.InstabondApplication;
import com.example.instabond_fe.model.AuthResponse;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static final String BASE_URL = BuildConfig.BASE_URL;

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static ApiService getApiService(Context context) {
        if (retrofit == null) {
            Context appContext = context.getApplicationContext();
            SessionManager sessionManager = new SessionManager(appContext);

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(createAuthInterceptor(sessionManager))
                    .authenticator(new TokenAuthenticator(sessionManager, appContext))
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

    private static Interceptor createAuthInterceptor(SessionManager sessionManager) {
        return chain -> {
            Request originalRequest = chain.request();
            String accessToken = sessionManager.getAccessToken();

            if (accessToken != null && !accessToken.isEmpty()) {
                Request modifiedRequest = originalRequest.newBuilder()
                        .header("Authorization", "Bearer " + accessToken)
                        .build();
                return chain.proceed(modifiedRequest);
            }

            return chain.proceed(originalRequest);
        };
    }

    private static class TokenAuthenticator implements Authenticator {
        private final SessionManager sessionManager;
        private final Context appContext;

        public TokenAuthenticator(SessionManager sessionManager, Context appContext) {
            this.sessionManager = sessionManager;
            this.appContext = appContext.getApplicationContext();
        }

        @Nullable
        @Override
        public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
            if (getRetryCount(response) >= 2) {
                return null;
            }

            String refreshToken = sessionManager.getRefreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                notifySessionExpired(appContext);
                return null;
            }

            // Synchronized block prevents multiple threads from calling refresh simultaneously
            synchronized (this) {
                String currentToken = sessionManager.getAccessToken();
                if (currentToken != null && !currentToken.equals(getAuthorizationHeader(response))) {
                    return retryWithNewToken(response.request(), currentToken);
                }

                // Call API to get a new token
                String newAccessToken = fetchNewTokenFromServer(refreshToken);

                if (newAccessToken != null) {
                    return retryWithNewToken(response.request(), newAccessToken);
                } else {
                    notifySessionExpired(appContext);
                    return null;
                }
            }
        }

        private String fetchNewTokenFromServer(String refreshToken) {
            try {
                ApiService authService = new Retrofit.Builder()
                        .baseUrl(BASE_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(ApiService.class);

                String headerValue = "Bearer " + refreshToken;

                Call<AuthResponse> call = authService.refreshToken(headerValue);
                retrofit2.Response<AuthResponse> result = call.execute();
                if (result.isSuccessful() && result.body() != null) {
                    sessionManager.saveSession(result.body());
                    return result.body().getAccessToken();
                }

                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        private Request retryWithNewToken(Request request, String newToken) {
            return request.newBuilder()
                    .header("Authorization", "Bearer " + newToken)
                    .build();
        }

        private int getRetryCount(Response response) {
            int result = 1;
            while ((response = response.priorResponse()) != null) {
                result++;
            }
            return result;
        }

        private String getAuthorizationHeader(Response response) {
            String header = response.request().header("Authorization");
            return header != null ? header.replace("Bearer ", "") : "";
        }
    }

    private static void notifySessionExpired(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof InstabondApplication) {
            ((InstabondApplication) appContext).notifySessionExpired();
        }
    }
}
