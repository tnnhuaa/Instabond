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

import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private final SearchRepository searchRepository;

    // LiveData
    private final MutableLiveData<List<UserSearchDTO>> suggestionsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<PostSearchDTO>> postResultsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Debounce Handler
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public SearchViewModel(@NonNull Application application) {
        super(application);
        searchRepository = new SearchRepository(application);
    }

    public LiveData<List<UserSearchDTO>> getSuggestionsLiveData() { return suggestionsLiveData; }
    public LiveData<List<PostSearchDTO>> getPostResultsLiveData() { return postResultsLiveData; }
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