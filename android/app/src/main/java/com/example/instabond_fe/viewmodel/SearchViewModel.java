package com.example.instabond_fe.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.instabond_fe.model.PostSearchDTO;
import com.example.instabond_fe.model.UserSearchDTO;
import com.example.instabond_fe.repository.SearchRepository;

import java.util.ArrayList;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private final SearchRepository searchRepository;

    // LiveData
    private final MutableLiveData<List<UserSearchDTO>> suggestionsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<PostSearchDTO>> postResultsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Explore
    private long currentExploreSeed = 0L;
    private int explorePage = 0;
    private boolean isExploreLoading = false;
    private final List<PostSearchDTO> currentExplorePosts = new ArrayList<>();
    private final MutableLiveData<List<PostSearchDTO>> exploreResultsLiveData = new MutableLiveData<>();

    // Debounce Handler
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        searchRepository = new SearchRepository(application);
    }

    public LiveData<List<UserSearchDTO>> getSuggestionsLiveData() { return suggestionsLiveData; }
    public LiveData<List<PostSearchDTO>> getPostResultsLiveData() { return postResultsLiveData; }
    public LiveData<List<PostSearchDTO>> getExploreResultsLiveData() {
        return exploreResultsLiveData;
    }

    public void fetchInitialExplorePosts() {
        currentExploreSeed = System.currentTimeMillis();
        explorePage = 0;
        currentExplorePosts.clear();
        exploreResultsLiveData.setValue(new ArrayList<>());
        loadMoreExplorePosts();
    }

    public void loadMoreExplorePosts() {
        if (isExploreLoading) return;
        isExploreLoading = true;

        searchRepository.fetchExplorePosts(currentExploreSeed, explorePage, new MutableLiveData<List<PostSearchDTO>>() {
            @Override
            public void setValue(List<PostSearchDTO> newPosts) {
                super.setValue(newPosts);
                if (newPosts != null && !newPosts.isEmpty()) {
                    currentExplorePosts.addAll(newPosts);
                    exploreResultsLiveData.postValue(currentExplorePosts);
                    explorePage++;
                }
                isExploreLoading = false;
            }
        });
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }

    // WHEN TYPING IN SEARCH BAR
    public void onSearchQueryChanged(String query) {
        // Cancel pending API call
        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
        }

        if (query == null || query.trim().isEmpty()) {
            suggestionsLiveData.setValue(null);
            return;
        }

        // Debounce: schedule new call (500ms)
        searchRunnable = () -> {
            searchRepository.fetchSuggestions(query, suggestionsLiveData);
        };
        handler.postDelayed(searchRunnable, 500);
    }

    // WHEN USER SUBMITS SEARCH (PRESS ENTER | CHANGE TAB)
    public void fetchResults(String query, String type, int page) {
        if (query == null || query.trim().isEmpty()) return;

        isLoading.setValue(true);
        if (type.equalsIgnoreCase("POST")) {
            searchRepository.fetchPostResults(query, page, postResultsLiveData);
            // @TODO: LiveData for user results if needed
        }
    }
}