package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.viewmodel.SearchViewModel;

import java.util.ArrayList;

public class SearchActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private ActivitySearchBinding binding;
    private SearchViewModel searchViewModel;
    private SearchPostAdapter searchPostAdapter;
    private SearchUserAdapter searchUserAdapter;

    private String currentSearchQuery = "";
    private String currentTab = "POST";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);

        bindActions();
        setupRecyclerViews();
        setupTabs();
        setupSearchInput();
        observeViewModel();

        resetToDefaultExploreState();
    }

    /**
     * Displaying search results and real-time user suggestions.
     */
    private void setupRecyclerViews() {
        searchPostAdapter = new SearchPostAdapter();
        searchUserAdapter = new SearchUserAdapter();

        StaggeredGridLayoutManager gridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        binding.rvSearchResults.setLayoutManager(gridLayoutManager);
        binding.rvSearchResults.setAdapter(searchPostAdapter);

        binding.rvSearchResults.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // scroll down
                if (dy > 0 && currentSearchQuery.isEmpty()) {
                    // Load Explore only when search query is empty
                    int[] lastVisibleItemPositions = gridLayoutManager.findLastVisibleItemPositions(null);
                    int lastVisibleItemPosition = Math.max(lastVisibleItemPositions[0], lastVisibleItemPositions[1]);
                    int totalItemCount = gridLayoutManager.getItemCount();

                    if (lastVisibleItemPosition + 4 >= totalItemCount) {
                        searchViewModel.loadMoreExplorePosts();
                    }
                }
            }
        });

        binding.rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchSuggestions.setAdapter(searchUserAdapter);
    }

    /**
     * Switching between different layouts and data sets seamlessly.
     */
    private void setupTabs() {
        // Click to Tab Posts
        binding.chipPosts.setOnClickListener(v -> {
            if (!currentTab.equals("POST")) {
                currentTab = "POST";
                updateTabUI();

                binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
                binding.rvSearchResults.setAdapter(searchPostAdapter);

                // Fetch API
                searchViewModel.fetchResults(currentSearchQuery, "POST", 0);
            }
        });

        // Click to Tab Users
        binding.chipUsers.setOnClickListener(v -> {
            if (!currentTab.equals("USER")) {
                currentTab = "USER";
                updateTabUI();

                binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
                binding.rvSearchResults.setAdapter(searchUserAdapter);

                // Fetch API
                searchViewModel.fetchResults(currentSearchQuery, "USER", 0);
            }
        });
    }

    /**
     * Updates the visual state of the filter tabs (background colors) to indicate which tab is currently active.
     */
    private void updateTabUI() {
        if (currentTab.equals("POST")) {
            binding.chipPosts.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
            binding.chipUsers.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
        } else {
            binding.chipPosts.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
            binding.chipUsers.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
        }
    }

    /**
     * Configures the search input field, including real-time typing detection for
     * suggestions (debounce) and keyboard action handling for submitting the search.
     */
    private void setupSearchInput() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                searchViewModel.onSearchQueryChanged(s.toString());

                // Hide tabs while typing
                binding.chipsScroll.setVisibility(View.GONE);

                if (query.length() > 0) {
                    binding.rvSearchResults.setVisibility(View.GONE);
                    binding.rvSearchSuggestions.setVisibility(View.VISIBLE);
                } else {
                    resetToDefaultExploreState();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentSearchQuery = binding.etSearch.getText().toString().trim();
                hideKeyboard();

                if (currentSearchQuery.isEmpty()) {
                    resetToDefaultExploreState();
                    return true;
                }

                // Enter => Visible Tab, Show Results, Hide Suggestions
                binding.chipsScroll.setVisibility(View.VISIBLE);
                binding.rvSearchSuggestions.setVisibility(View.GONE);
                binding.rvSearchResults.setVisibility(View.VISIBLE);

                updateTabUI();

                if (currentTab.equals("POST")) {
                    binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
                    binding.rvSearchResults.setAdapter(searchPostAdapter);
                } else {
                    binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
                    binding.rvSearchResults.setAdapter(searchUserAdapter);
                }

                // Fetch API based on current tab
                searchViewModel.fetchResults(currentSearchQuery, currentTab, 0);

                return true;
            }
            return false;
        });
    }

    /**
     * Resets the UI to the default Explore state (empty search query), hiding suggestions and tabs while showing the default post grid.
     */
    private void resetToDefaultExploreState() {
        binding.chipsScroll.setVisibility(View.GONE);
        binding.rvSearchSuggestions.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);

        binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.rvSearchResults.setAdapter(searchPostAdapter);

        // Reset list
        searchPostAdapter.setPosts(new ArrayList<>());

        // Fetch initial explore posts with new random seed
        searchViewModel.fetchInitialExplorePosts();
    }

    /**
     * Subscribes to LiveData emitted by the ViewModel to automatically update the RecyclerView adapters when new search suggestions or results arrive.
     */
    private void observeViewModel() {
        searchViewModel.getSuggestionsLiveData().observe(this, users -> {
            if (users != null) {
                searchUserAdapter.setUsers(users);
            }
        });

        searchViewModel.getPostResultsLiveData().observe(this, posts -> {
            if (posts != null) {
                searchPostAdapter.setPosts(posts);
            }
        });

        searchViewModel.getExploreResultsLiveData().observe(this, posts -> {
            if (posts != null && currentSearchQuery.isEmpty()) {
                searchPostAdapter.setPosts(posts);
            }
        });
    }

    /**
     * Binds click events to the top toolbar actions.
     */
    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
    }

    /**
     * Utility method to forcibly hide the software keyboard after a search is submitted or when the user navigates away.
     */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}