package com.example.instabond_fe.repository;

import android.content.Context;
import androidx.lifecycle.MutableLiveData;

import com.example.instabond_fe.model.PostSearchDTO;
import com.example.instabond_fe.model.SearchHistoryDTO;
import com.example.instabond_fe.model.SearchHistoryRequest;
import com.example.instabond_fe.model.UserSearchDTO;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchRepository {
    private final ApiService apiService;

    public SearchRepository(Context context) {
        apiService = ApiClient.getApiService(context);
    }

    public void fetchSuggestions(String query, MutableLiveData<List<UserSearchDTO>> suggestionsLiveData) {
        apiService.getSearchSuggestions("USER", query).enqueue(new Callback<List<UserSearchDTO>>() {
            @Override
            public void onResponse(Call<List<UserSearchDTO>> call, Response<List<UserSearchDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    suggestionsLiveData.postValue(response.body());
                } else {
                    suggestionsLiveData.postValue(null);
                }
            }

            @Override
            public void onFailure(Call<List<UserSearchDTO>> call, Throwable t) {
                suggestionsLiveData.postValue(null);
            }
        });
    }

    public void fetchPostResults(String query, int page, MutableLiveData<List<PostSearchDTO>> postResultsLiveData) {
        apiService.getSearchResults("POST", query, page, 20).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Gson gson = new Gson();
                    List<PostSearchDTO> posts = gson.fromJson(response.body(), new TypeToken<List<PostSearchDTO>>(){}.getType());
                    postResultsLiveData.postValue(posts);
                } else {
                    postResultsLiveData.postValue(null);
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                postResultsLiveData.postValue(null);
            }
        });
    }

    public void fetchExplorePosts(long seed, int page, MutableLiveData<List<PostSearchDTO>> liveData) {
        apiService.getExplorePosts(seed, page, 20).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Gson gson = new Gson();
                    List<PostSearchDTO> posts = gson.fromJson(response.body(), new TypeToken<List<PostSearchDTO>>(){}.getType());
                    liveData.postValue(posts);
                } else {
                    liveData.postValue(null);
                }
            }
            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                liveData.postValue(null);
            }
        });
    }

    public void fetchSearchHistory(MutableLiveData<List<SearchHistoryDTO>> liveData) {
        apiService.getSearchHistory().enqueue(new Callback<List<SearchHistoryDTO>>() {
            @Override
            public void onResponse(Call<List<SearchHistoryDTO>> call, Response<List<SearchHistoryDTO>> response) {
                if (response.isSuccessful()) {
                    liveData.postValue(response.body());
                } else {
                    liveData.postValue(null);
                }
            }

            @Override
            public void onFailure(Call<List<SearchHistoryDTO>> call, Throwable t) {
                liveData.postValue(null);
            }
        });
    }

    public void saveSearchHistory(SearchHistoryRequest request) {
        apiService.saveSearchHistory(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    public void deleteSearchHistory(String id, MutableLiveData<List<SearchHistoryDTO>> liveData) {
        apiService.deleteSearchHistory(id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    // Fetch lại danh sách sau khi xóa thành công
                    fetchSearchHistory(liveData);
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }
}