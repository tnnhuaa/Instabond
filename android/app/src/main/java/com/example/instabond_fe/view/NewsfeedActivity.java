package com.example.instabond_fe.view;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.instabond_fe.R;
import com.example.instabond_fe.databinding.ActivityMainBinding;
import com.example.instabond_fe.model.Post;
import com.example.instabond_fe.model.PostResponse;
import com.example.instabond_fe.model.StoryItem;
import com.example.instabond_fe.model.StoryResponse;
import com.example.instabond_fe.model.UserProfileResponse;
import com.example.instabond_fe.network.ApiClient;
import com.example.instabond_fe.network.ApiListParser;
import com.example.instabond_fe.network.ApiService;
import com.example.instabond_fe.network.SessionManager;
import com.example.instabond_fe.repository.ChatRepository;
import com.example.instabond_fe.repository.NotificationCountManager;
import com.example.instabond_fe.utils.LocaleManager;
import com.example.instabond_fe.view.component.InstaBottomNavView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.instabond_fe.model.FollowUserResponse;

public class NewsfeedActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleManager.setLocale(newBase));
    }

    private static final String EXTRA_REFRESH_FEED = "refresh_feed";
    private static final String FEED_MODE_FOR_YOU = "for_you";
    private static final String FEED_MODE_FOLLOWING = "following";
    private static final int PAGE_SIZE = 5;
    private static final int VISIBLE_THRESHOLD = 2;

    private ActivityMainBinding binding;
    private PostAdapter topPostsAdapter;
    private PostAdapter bottomPostsAdapter;
    private StoryFeedAdapter storyAdapter;
    private StorySectionAdapter storySectionAdapter;
    private FeedModeHeaderAdapter feedModeHeaderAdapter;
    private FriendSuggestionSectionAdapter suggestionSectionAdapter;
    private ConcatAdapter feedAdapter;
    private ApiService apiService;
    private SessionManager sessionManager;
    private final Gson gson = new Gson();
    private final Random random = new Random();
    private final List<StoryItem> storyFeedItems = new ArrayList<>();
    private final Map<String, List<StoryItem>> storiesByAuthor = new LinkedHashMap<>();
    private UserProfileResponse currentUserProfile;

    private final Set<String> loadedPostIds = new HashSet<>();
    private int currentPage;
    private boolean isRequestInFlight;
    private boolean reachedEnd;
    private boolean friendSuggestionsDismissed;
    private String currentFeedMode = FEED_MODE_FOR_YOU;
    private long forYouSeed;
    private NotificationCountManager notificationCountManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        apiService = ApiClient.getApiService(this);
        sessionManager = new SessionManager(this);
        notificationCountManager = NotificationCountManager.getInstance(this);

        if (sessionManager.isLoggedIn()) {
            ChatRepository repository = ChatRepository.getInstance(this);
            repository.connectRealtime();
            repository.subscribeGlobalChannels();
        }

        topPostsAdapter = new PostAdapter(new ArrayList<>());
        bottomPostsAdapter = new PostAdapter(new ArrayList<>());
        storyAdapter = new StoryFeedAdapter();
        storySectionAdapter = new StorySectionAdapter(storyAdapter);
        feedModeHeaderAdapter = new FeedModeHeaderAdapter();
        feedModeHeaderAdapter.setListener(this::switchFeedMode);
        suggestionSectionAdapter = new FriendSuggestionSectionAdapter(this, apiService);
        suggestionSectionAdapter.setOnDismissListener(() -> friendSuggestionsDismissed = true);

        storyAdapter.setListener(new StoryFeedAdapter.Listener() {
            @Override
            public void onCreateStoryClicked() {
                startActivity(new Intent(NewsfeedActivity.this, CreateStoryActivity.class));
            }

            @Override
            public void onStoryClicked(StoryItem item) {
                openStoryViewer(item);
            }
        });

        PostAdapter.OnPostInteractionListener postInteractionListener = new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(Post post, int position) {
                boolean isCurrentlyLiked = post.isLiked();
                post.setLiked(!isCurrentlyLiked);
                post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? -1 : 1));
                notifyPostChanged(post);

                Callback<PostResponse> cb = new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (!response.isSuccessful()) {
                            post.setLiked(isCurrentlyLiked);
                            post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? 1 : -1));
                            notifyPostChanged(post);
                        }
                    }
                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {
                        post.setLiked(isCurrentlyLiked);
                        post.setLikesCount(post.getLikesCount() + (isCurrentlyLiked ? 1 : -1));
                        notifyPostChanged(post);
                    }
                };

                if (isCurrentlyLiked) {
                    apiService.unlikePost(post.getId()).enqueue(cb);
                } else {
                    apiService.likePost(post.getId()).enqueue(cb);
                }
            }

            @Override
            public void onCommentClicked(Post post, int position) {
                Intent intent = new Intent(NewsfeedActivity.this, CommentActivity.class);
                intent.putExtra("postId", post.getId());
                intent.putExtra("postUsername", post.getUsername());
                intent.putExtra("postCaption", post.getCaption());
                intent.putExtra("postAvatarUrl", post.getAvatarUrl());
                intent.putExtra("postCreatedAt", post.getCreatedAt());
                startActivity(intent);
            }

            @Override
            public void onShareClicked(Post post, int position) {
                apiService.sharePost(post.getId()).enqueue(new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (response.isSuccessful()) {
                            post.setSharesCount(post.getSharesCount() + 1);
                            notifyPostChanged(post);
                        }
                    }
                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {}
                });
                com.example.instabond_fe.utils.ShareUtils.showShareBottomSheet(NewsfeedActivity.this, post, apiService, sessionManager.getUserId());
            }

            @Override
            public void onUserClicked(Post post, int position) {
                Intent intent = new Intent(NewsfeedActivity.this, ProfileActivity.class);
                intent.putExtra("targetUserId", post.getAuthorId());
                startActivity(intent);
            }

            @Override
            public void onBookmarkClicked(Post post, int position) {
                boolean isCurrentlyBookmarked = post.isBookmarked();
                post.setBookmarked(!isCurrentlyBookmarked);
                notifyPostChanged(post);

                Callback<PostResponse> cb = new Callback<PostResponse>() {
                    @Override
                    public void onResponse(Call<PostResponse> call, Response<PostResponse> response) {
                        if (!response.isSuccessful()) {
                            post.setBookmarked(isCurrentlyBookmarked);
                            notifyPostChanged(post);
                        }
                    }
                    @Override
                    public void onFailure(Call<PostResponse> call, Throwable t) {
                        post.setBookmarked(isCurrentlyBookmarked);
                        notifyPostChanged(post);
                    }
                };

                if (isCurrentlyBookmarked) {
                    apiService.unbookmarkPost(post.getId()).enqueue(cb);
                } else {
                    apiService.bookmarkPost(post.getId()).enqueue(cb);
                }
            }
        };

        topPostsAdapter.setListener(postInteractionListener);
        bottomPostsAdapter.setListener(postInteractionListener);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);

        binding.rvFeed.setLayoutManager(layoutManager);
        feedAdapter = new ConcatAdapter(
                storySectionAdapter,
                feedModeHeaderAdapter,
                topPostsAdapter,
                suggestionSectionAdapter,
                bottomPostsAdapter);
        binding.rvFeed.setAdapter(feedAdapter);
        binding.rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0 || reachedEnd || isRequestInFlight) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                int total = binding.rvFeed.getAdapter() == null ? 0 : binding.rvFeed.getAdapter().getItemCount();
                if (lastVisible >= total - 1 - VISIBLE_THRESHOLD) {
                    loadNextPage();
                }
            }
        });

        binding.swipeRefreshFeed.setOnRefreshListener(this::refreshFeed);
        binding.swipeRefreshFeed.setColorSchemeResources(R.color.login_bg_start, R.color.login_bg_mid);

        binding.bottomNav.bind(this, InstaBottomNavView.Tab.HOME);
        binding.btnCamera.setOnClickListener(v -> startActivity(new Intent(this, CreatePostActivity.class)));

        updateFeedModeUi();

        binding.btnInbox.setOnClickListener(v -> startActivity(new Intent(this, InboxActivity.class)));

        loadCurrentUserProfile();
        binding.swipeRefreshFeed.setRefreshing(true);
        refreshFeed();
        loadFriendSuggestions();
    }

    private void loadFriendSuggestions() {
        if (friendSuggestionsDismissed) {
            suggestionSectionAdapter.submitSuggestions(List.of());
            return;
        }
        apiService.getFriendSuggestions(10).enqueue(new Callback<List<FollowUserResponse>>() {
            @Override
            public void onResponse(Call<List<FollowUserResponse>> call, Response<List<FollowUserResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    suggestionSectionAdapter.submitSuggestions(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<FollowUserResponse>> call, Throwable t) {
                suggestionSectionAdapter.submitSuggestions(List.of());
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        if (intent != null && intent.getBooleanExtra(EXTRA_REFRESH_FEED, false)) {
            binding.swipeRefreshFeed.setRefreshing(true);
            refreshFeed();
            intent.removeExtra(EXTRA_REFRESH_FEED);
        }
        if (intent != null && intent.getBooleanExtra(CreateStoryActivity.EXTRA_REFRESH_STORIES, false)) {
            loadStories();
            intent.removeExtra(CreateStoryActivity.EXTRA_REFRESH_STORIES);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStories();
        notificationCountManager.fetchUnreadCount();
    }

    @Override
    protected void onPause() {
        PostAdapter.stopAudioPlayback();
        super.onPause();
    }

    private void refreshFeed() {
        if (isRequestInFlight) {
            return;
        }

        forYouSeed = FEED_MODE_FOR_YOU.equals(currentFeedMode) ? System.currentTimeMillis() : 0L;
        currentPage = 0;
        reachedEnd = false;
        loadedPostIds.clear();
        topPostsAdapter.setPosts(new ArrayList<>());
        bottomPostsAdapter.setPosts(new ArrayList<>());
        friendSuggestionsDismissed = false;
        suggestionSectionAdapter.submitSuggestions(List.of());
        suggestionSectionAdapter.restoreSection();
        loadStories();
        loadFriendSuggestions();
        loadPage(true);
    }

    private void loadNextPage() {
        if (isRequestInFlight || reachedEnd) {
            return;
        }
        loadPage(false);
    }

    private void loadPage(boolean fromRefresh) {
        isRequestInFlight = true;
        Long seedParam = FEED_MODE_FOR_YOU.equals(currentFeedMode) ? forYouSeed : null;
        apiService.getFeed(currentPage, PAGE_SIZE, currentFeedMode, seedParam).enqueue(new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                isRequestInFlight = false;
                if (fromRefresh) binding.swipeRefreshFeed.setRefreshing(false);

                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(NewsfeedActivity.this, "Failed to load Newsfeed", Toast.LENGTH_SHORT).show();
                    return;
                }

                List<PostResponse> pagePosts = ApiListParser.parsePostList(gson, response.body());
                List<PostResponse> uniquePosts = filterNewPosts(pagePosts);
                List<Post> mappedPosts = mapToUiPosts(uniquePosts);

                if (fromRefresh) {
                    applyFeedPostsForRefresh(mappedPosts);
                } else {
                    bottomPostsAdapter.appendPosts(mappedPosts);
                }

                if (pagePosts.size() < PAGE_SIZE || uniquePosts.isEmpty()) {
                    reachedEnd = true;
                } else {
                    currentPage++;
                }
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                isRequestInFlight = false;
                if (fromRefresh) binding.swipeRefreshFeed.setRefreshing(false);
                Toast.makeText(NewsfeedActivity.this, "Connection error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void switchFeedMode(String mode) {
        if (mode == null || mode.equals(currentFeedMode)) return;
        currentFeedMode = mode;
        updateFeedModeUi();
        binding.swipeRefreshFeed.setRefreshing(true);
        refreshFeed();
    }

    private void updateFeedModeUi() {
        feedModeHeaderAdapter.setCurrentMode(currentFeedMode);
    }

    private List<PostResponse> filterNewPosts(List<PostResponse> apiPosts) {
        List<PostResponse> result = new ArrayList<>();
        for (PostResponse post : apiPosts) {
            String key = buildPostKey(post);
            if (loadedPostIds.add(key)) result.add(post);
        }
        return result;
    }

    private String buildPostKey(PostResponse post) {
        if (post.getId() != null && !post.getId().trim().isEmpty()) return post.getId();
        String username = post.getAuthor() == null ? "" : post.getAuthor().getUsername();
        String caption = post.getCaption() == null ? "" : post.getCaption();
        String mediaUrl = (post.getMedia() != null && !post.getMedia().isEmpty()) ? post.getMedia().get(0).getUrl() : "";
        return username + "|" + caption + "|" + mediaUrl;
    }

    private List<Post> mapToUiPosts(List<PostResponse> apiPosts) {
        List<Post> result = new ArrayList<>();
        for (PostResponse postResponse : apiPosts) {
            String username = "unknown", avatarUrl = "", authorId = "", postId = postResponse.getId();

            if (postResponse.getAuthor() != null) {
                authorId = postResponse.getAuthor().getId() != null ? postResponse.getAuthor().getId() : "";
                username = postResponse.getAuthor().getUsername() != null ? postResponse.getAuthor().getUsername() : "unknown";
                avatarUrl = normalizeUrl(postResponse.getAuthor().getAvatarUrl());
            }

            String imageUrl = (postResponse.getMedia() != null && !postResponse.getMedia().isEmpty())
                    ? normalizeUrl(postResponse.getMedia().get(0).getUrl()) : "";

            int likes = 0, comments = 0, shares = 0;
            if (postResponse.getStats() != null) {
                likes = postResponse.getStats().getLikes();
                comments = postResponse.getStats().getComments();
                shares = postResponse.getStats().getShares();
            }

            String musicPreviewUrl = postResponse.getMusicPreviewUrl() != null ? postResponse.getMusicPreviewUrl() : "";
            boolean hasPlayableMusic = !musicPreviewUrl.trim().isEmpty();

            Post uiPost = new Post(
                    postId, authorId, username,
                    postResponse.getCaption() == null ? "" : postResponse.getCaption(),
                    postResponse.getCreatedAt(),
                    likes, comments, shares, avatarUrl, imageUrl,
                    postResponse.getLocationName(),
                    postResponse.getMusicDisplayText(),
                    musicPreviewUrl,
                    hasPlayableMusic,
                    postResponse.isLiked(),
                    postResponse.isBookmarked()
            );

            if (postResponse.getTaggedUsers() != null) {
                uiPost.setTaggedUsers(postResponse.getTaggedUsers());
            }

            result.add(uiPost);
        }
        return result;
    }

    private void notifyPostChanged(Post post) {
        topPostsAdapter.notifyPostChanged(post);
        bottomPostsAdapter.notifyPostChanged(post);
    }

    private void applyFeedPostsForRefresh(List<Post> mappedPosts) {
        int insertionIndex = resolveSuggestionInsertIndex(mappedPosts.size());
        topPostsAdapter.setPosts(new ArrayList<>(mappedPosts.subList(0, insertionIndex)));
        bottomPostsAdapter.setPosts(new ArrayList<>(mappedPosts.subList(insertionIndex, mappedPosts.size())));
    }

    private int resolveSuggestionInsertIndex(int postCount) {
        return postCount <= 1 ? postCount : 1 + random.nextInt(postCount - 1);
    }

    private String normalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return "";
        Uri uri = Uri.parse(rawUrl);
        if (uri.getScheme() != null) return rawUrl;

        String baseUrl = ApiClient.getBaseUrl();
        String prefix = rawUrl.startsWith("/") ? "" : "/";
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        return baseUrl + prefix + rawUrl;
    }

    private void loadCurrentUserProfile() {
        apiService.getMe().enqueue(new Callback<UserProfileResponse>() {
            @Override
            public void onResponse(@NonNull Call<UserProfileResponse> call, @NonNull Response<UserProfileResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentUserProfile = response.body();
                    loadStories();
                } else {
                    renderStories(List.of());
                }
            }
            @Override
            public void onFailure(@NonNull Call<UserProfileResponse> call, @NonNull Throwable t) {
                renderStories(List.of());
            }
        });
    }

    private void loadStories() {
        apiService.getStoriesFeed().enqueue(new Callback<List<StoryResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<StoryResponse>> call, @NonNull Response<List<StoryResponse>> response) {
                renderStories(response.isSuccessful() ? response.body() : List.of());
            }
            @Override
            public void onFailure(@NonNull Call<List<StoryResponse>> call, @NonNull Throwable t) {
                renderStories(List.of());
            }
        });
    }

    private void renderStories(List<StoryResponse> stories) {
        storyFeedItems.clear();
        storiesByAuthor.clear();
        String currentUserId = sessionManager.getUserId();
        StoryItem ownStoryPreview = null;
        LinkedHashMap<String, StoryItem> followerStoryPreviews = new LinkedHashMap<>();

        if (stories != null) {
            for (StoryResponse response : stories) {
                if (response == null || response.getAuthor() == null) continue;
                StoryItem item = new StoryItem(
                        response.getId(), response.getAuthor().getId(), response.getAuthor().getUsername(),
                        normalizeUrl(response.getAuthor().getAvatarUrl()), normalizeUrl(response.getMediaUrl()),
                        response.getCreatedAt(), false, response.isViewedByMe(), response.isLikedByMe(), response.getViewerCount());

                storiesByAuthor.computeIfAbsent(item.getAuthorId(), k -> new ArrayList<>()).add(item);
                if (currentUserId != null && currentUserId.equals(item.getAuthorId())) {
                    if (ownStoryPreview == null) ownStoryPreview = item;
                    continue;
                }
                followerStoryPreviews.putIfAbsent(item.getAuthorId(), item);
            }
        }
        storyFeedItems.add(buildCreateCard(ownStoryPreview));
        storyFeedItems.addAll(followerStoryPreviews.values());
        storySectionAdapter.submitItems(storyFeedItems);
    }

    private StoryItem buildCreateCard(StoryItem ownStoryPreview) {
        String currentUserId = sessionManager.getUserId();
        String username = (currentUserProfile != null && currentUserProfile.getUsername() != null) ? currentUserProfile.getUsername() : getString(R.string.feed_story_your_story);
        String avatarUrl = currentUserProfile != null ? normalizeUrl(currentUserProfile.getAvatarUrl()) : "";
        return new StoryItem(
                ownStoryPreview != null ? ownStoryPreview.getId() : "create-story",
                currentUserId, username, avatarUrl,
                ownStoryPreview != null ? ownStoryPreview.getMediaUrl() : "",
                ownStoryPreview != null ? ownStoryPreview.getCreatedAt() : "",
                true, ownStoryPreview != null && ownStoryPreview.isViewedByMe(),
                ownStoryPreview != null && ownStoryPreview.isLikedByMe(),
                ownStoryPreview != null ? ownStoryPreview.getViewerCount() : 0
        );
    }

    private void openStoryViewer(StoryItem selectedStory) {
        List<StoryItem> authorStories = storiesByAuthor.get(selectedStory.getAuthorId());
        if (authorStories == null || authorStories.isEmpty()) return;

        int storyIndex = 0;
        for (int i = 0; i < authorStories.size(); i++) {
            if (selectedStory.getId().equals(authorStories.get(i).getId())) {
                storyIndex = i;
                break;
            }
        }

        Intent intent = new Intent(this, StoryViewerActivity.class);
        intent.putExtra(StoryViewerActivity.EXTRA_STORIES, new ArrayList<>(authorStories));
        intent.putExtra(StoryViewerActivity.EXTRA_STORY_INDEX, storyIndex);
        startActivity(intent);
    }

    private void handleUnauthorized() {
        sessionManager.clearSession();
        Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SignInActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}