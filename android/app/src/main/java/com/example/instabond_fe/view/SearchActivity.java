package com.example.instabond_fe.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivitySearchBinding;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.example.instabond_fe.viewmodel.SearchViewModel;

public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;
    private SearchViewModel searchViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        // Initialize ViewModel
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.SEARCH);

        bindActions();
        setupSearchInput();
        observeViewModel();
    }

    private void setupSearchInput() {
        // Listen to text changes for real-time search suggestions
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Pass the query to ViewModel to handle debounce
                searchViewModel.onSearchQueryChanged(s.toString());

                // Show suggestion list when typing, hide when empty
                if (s.length() > 0) {
                    binding.rvSearchSuggestions.setVisibility(View.VISIBLE);
                } else {
                    binding.rvSearchSuggestions.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Listen to the ENTER (Search) action on the soft keyboard
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = binding.etSearch.getText().toString();

                // Trigger POST search by default when hitting Enter
                searchViewModel.fetchResults(query, "POST", 0);

                // Hide suggestion list to show results
                binding.rvSearchSuggestions.setVisibility(View.GONE);

                // Hide the soft keyboard for better UX
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        // Observe Search Suggestions (As the user types)
        searchViewModel.getSuggestionsLiveData().observe(this, users -> {
            if (users != null) {
                Log.d("SEARCH_TEST", "Received " + users.size() + " user suggestions!");
                for (int i = 0; i < users.size(); i++) {
                    Log.d("SEARCH_TEST", "Suggested user: " + users.get(i).getUsername());
                }
            }
        });

        // Observe Search Results (After the user presses Enter)
        searchViewModel.getPostResultsLiveData().observe(this, posts -> {
            if (posts != null) {
                Log.d("SEARCH_TEST", "Received " + posts.size() + " post results!");
                Toast.makeText(this, "Found " + posts.size() + " posts!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindActions() {
        binding.btnCamera.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class)));
        binding.btnInbox.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.feed_messages_coming_soon), Toast.LENGTH_SHORT).show());
    }

    /**
     * Helper method to hide the soft keyboard after executing a search.
     */
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}