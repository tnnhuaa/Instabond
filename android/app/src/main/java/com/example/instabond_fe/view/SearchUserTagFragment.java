package com.example.instabond_fe.view;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.instabond_fe.databinding.FragmentSearchUserTagBinding;
import com.example.instabond_fe.viewmodel.SearchViewModel;

public class SearchUserTagFragment extends Fragment {

    private FragmentSearchUserTagBinding binding;
    private SearchViewModel searchViewModel;
    private SearchUserAdapter adapter; // Tái sử dụng Adapter của bạn

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchUserTagBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        adapter = new SearchUserAdapter();
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvSearchResults.setAdapter(adapter);

        binding.btnCancel.setOnClickListener(v -> {
            ((TagUserActivity) getActivity()).closeSearchFragment();
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchViewModel.onSearchQueryChanged(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Observe LiveData
        searchViewModel.getSuggestionsLiveData().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                adapter.setUsers(users);
            }
        });

        adapter.setOnItemClickListener(user -> {
            ((TagUserActivity) getActivity()).onUserSelected(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getAvatarUrl()
            );
        });
    }
}