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
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.model.SearchHistoryDTO;
import com.example.instabond_fe.repository.NotificationCountManager;
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
    private SearchHistoryAdapter searchHistoryAdapter;

    private String currentSearchQuery = "";
    private String currentTab = "POST";
    private NotificationCountManager notificationCountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        notificationCountManager = NotificationCountManager.getInstance(this);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);

        binding.bottomNav.setOnTabReselectedListener(tab -> {
            if (tab == InstaBottomNavView.Tab.SEARCH) {
                binding.etSearch.setText("");
                binding.etSearch.clearFocus();
                hideKeyboard();

                // Scroll back to the top
                if (binding.rvSearchResults.getVisibility() == View.VISIBLE) {
                    binding.rvSearchResults.smoothScrollToPosition(0);
                }

                // Reset to default explore feed
                resetToDefaultExploreState();
            }
        });

        // Setup SwipeRefreshLayout (Loading effect)
        binding.swipeRefreshSearch.setColorSchemeResources(R.color.login_bg_start, R.color.login_bg_mid);
        binding.swipeRefreshSearch.setOnRefreshListener(() -> {
            if (currentSearchQuery.isEmpty()) {
                resetToDefaultExploreState();
            } else {
                searchViewModel.fetchResults(currentSearchQuery, currentTab, 0);
            }
        });

        bindActions();
        setupRecyclerViews();
        setupTabs();
        setupSearchInput();
        observeViewModel();

        resetToDefaultExploreState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Fetch fresh unread notification count
        notificationCountManager.fetchUnreadCount();
    }

    private void setupRecyclerViews() {
        searchPostAdapter = new SearchPostAdapter();

        searchUserAdapter = new SearchUserAdapter();
        searchUserAdapter.setOnItemClickListener(user -> {
            // Save history "profile"
            searchViewModel.saveSearchHistory("PROFILE", null, user.getId());

            Intent intent = new Intent(SearchActivity.this, ProfileActivity.class);
            intent.putExtra("targetUserId", user.getId());
            startActivity(intent);
        });

        searchHistoryAdapter = new SearchHistoryAdapter(new SearchHistoryAdapter.OnHistoryClickListener() {
            @Override
            public void onItemClick(SearchHistoryDTO history) {
                if ("PROFILE".equalsIgnoreCase(history.getType())) {
                    searchViewModel.saveSearchHistory("PROFILE", null, history.getTargetUserId());
                    Intent intent = new Intent(SearchActivity.this, ProfileActivity.class);
                    intent.putExtra("targetUserId", history.getTargetUserId());
                    startActivity(intent);
                } else {
                    binding.etSearch.setText(history.getKeyword());
                    binding.etSearch.setSelection(history.getKeyword().length());
                    performSearch(history.getKeyword());
                }
            }

            @Override
            public void onDeleteClick(SearchHistoryDTO history) {
                searchViewModel.deleteSearchHistory(history.getId());
            }
        });

        // Setup LayoutManagers
        StaggeredGridLayoutManager gridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        binding.rvSearchResults.setLayoutManager(gridLayoutManager);
        binding.rvSearchResults.setAdapter(searchPostAdapter);

        binding.rvSearchSuggestions.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchSuggestions.setAdapter(searchUserAdapter);

        binding.rvSearchHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.rvSearchHistory.setAdapter(searchHistoryAdapter);

        // Scroll listener for Explore
        binding.rvSearchResults.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0 && currentSearchQuery.isEmpty() && binding.rvSearchResults.getVisibility() == View.VISIBLE) {
                    int[] lastVisibleItemPositions = gridLayoutManager.findLastVisibleItemPositions(null);
                    int lastVisibleItemPosition = Math.max(lastVisibleItemPositions[0], lastVisibleItemPositions[1]);
                    int totalItemCount = gridLayoutManager.getItemCount();

                    if (lastVisibleItemPosition + 4 >= totalItemCount) {
                        searchViewModel.loadMoreExplorePosts();
                    }
                }
            }
        });
    }

    private void setupTabs() {
        binding.chipPosts.setOnClickListener(v -> switchTab("POST"));
        binding.chipUsers.setOnClickListener(v -> switchTab("USER"));
    }

    private void switchTab(String tabName) {
        if (!currentTab.equals(tabName)) {
            currentTab = tabName;
            updateTabUI();
            binding.swipeRefreshSearch.setRefreshing(true);

            if (currentTab.equals("POST")) {
                binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
                binding.rvSearchResults.setAdapter(searchPostAdapter);
            } else {
                binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
                binding.rvSearchResults.setAdapter(searchUserAdapter);
            }
            searchViewModel.fetchResults(currentSearchQuery, currentTab, 0);
        }
    }

    private void updateTabUI() {
        // Get text color
        int colorActive = ContextCompat.getColor(this, R.color.theme_on_primary);
        int colorInactive = ContextCompat.getColor(this, R.color.theme_on_surface_muted);

        if (currentTab.equals("POST")) {
            binding.chipPosts.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
            binding.chipPosts.setTextColor(colorActive);

            binding.chipUsers.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
            binding.chipUsers.setTextColor(colorInactive);
        } else {
            binding.chipPosts.setBackgroundResource(R.drawable.search_filter_chip_inactive_bg);
            binding.chipPosts.setTextColor(colorInactive);

            binding.chipUsers.setBackgroundResource(R.drawable.search_filter_chip_active_bg);
            binding.chipUsers.setTextColor(colorActive);
        }
    }

    private void setupSearchInput() {
        // Focus on Search Box
        binding.etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showHistoryMode();
            }
        });

        // Cancel button
        binding.tvCancel.setOnClickListener(v -> {
            binding.etSearch.setText("");
            binding.etSearch.clearFocus();
            hideKeyboard();
            resetToDefaultExploreState();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                searchViewModel.onSearchQueryChanged(query);

                binding.chipsScroll.setVisibility(View.GONE);

                binding.swipeRefreshSearch.setRefreshing(false);

                if (query.length() > 0) {
                    binding.rvSearchResults.setVisibility(View.GONE);
                    binding.rvSearchHistory.setVisibility(View.GONE);
                    binding.rvSearchSuggestions.setVisibility(View.VISIBLE);
                } else if (binding.etSearch.hasFocus()) {
                    showHistoryMode();
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = binding.etSearch.getText().toString().trim();
                performSearch(query);
                return true;
            }
            return false;
        });
    }

    private void performSearch(String query) {
        currentSearchQuery = query;
        hideKeyboard();
        binding.etSearch.clearFocus();

        if (currentSearchQuery.isEmpty()) {
            resetToDefaultExploreState();
            return;
        }

        binding.chipsScroll.setVisibility(View.VISIBLE);
        binding.rvSearchSuggestions.setVisibility(View.GONE);
        binding.rvSearchHistory.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);
        binding.tvCancel.setVisibility(View.VISIBLE);

        updateTabUI();

        binding.swipeRefreshSearch.setRefreshing(true);

        if (currentTab.equals("POST")) {
            binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
            binding.rvSearchResults.setAdapter(searchPostAdapter);
        } else {
            binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
            binding.rvSearchResults.setAdapter(searchUserAdapter);
        }

        searchViewModel.fetchResults(currentSearchQuery, currentTab, 0);
    }

    private void showHistoryMode() {
        binding.tvCancel.setVisibility(View.VISIBLE);
        binding.chipsScroll.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.GONE);
        binding.rvSearchSuggestions.setVisibility(View.GONE);
        binding.rvSearchHistory.setVisibility(View.VISIBLE);
        binding.swipeRefreshSearch.setRefreshing(false);

        searchViewModel.loadSearchHistory();
    }

    private void resetToDefaultExploreState() {
        currentSearchQuery = "";
        binding.tvCancel.setVisibility(View.GONE);
        binding.chipsScroll.setVisibility(View.GONE);
        binding.rvSearchSuggestions.setVisibility(View.GONE);
        binding.rvSearchHistory.setVisibility(View.GONE);
        binding.rvSearchResults.setVisibility(View.VISIBLE);

        binding.rvSearchResults.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.rvSearchResults.setAdapter(searchPostAdapter);

        searchPostAdapter.setPosts(new ArrayList<>());

        binding.swipeRefreshSearch.post(() -> binding.swipeRefreshSearch.setRefreshing(true));

        searchViewModel.fetchInitialExplorePosts();
    }

    private void observeViewModel() {
        searchViewModel.getSuggestionsLiveData().observe(this, users -> {
            binding.swipeRefreshSearch.setRefreshing(false);
            if (users != null) {
                searchUserAdapter.setUsers(users);
            }
        });

        searchViewModel.getPostResultsLiveData().observe(this, posts -> {
            binding.swipeRefreshSearch.setRefreshing(false);
            if (posts != null && !currentSearchQuery.isEmpty() && binding.rvSearchResults.getVisibility() == View.VISIBLE) {
                searchPostAdapter.setPosts(posts);
            }
        });

        searchViewModel.getExploreResultsLiveData().observe(this, posts -> {
            binding.swipeRefreshSearch.setRefreshing(false);
            if (posts != null && currentSearchQuery.isEmpty() && binding.rvSearchResults.getVisibility() == View.VISIBLE) {
                searchPostAdapter.setPosts(posts);
            }
        });

        searchViewModel.getSearchHistoryLiveData().observe(this, histories -> {
            if (histories != null) {
                searchHistoryAdapter.setHistoryList(histories);
            }
        });
    }

    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}