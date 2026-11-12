package com.liskovsoft.smartyoutubetv2.tv.phone;

import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.disposables.Disposable;

/**
 * The You tab's front page, as in the YouTube app: under the account card a list of places (History, Playlists,
 * Watch later, Liked videos, Downloads, Your videos, Settings), then the most recent History and the Playlists as rows
 * of cards with "View all". Shown as the header of the (empty) feed list; a place opens in the same tab.
 */
final class PhoneYouDashboard {
    private static final int SHELF_SIZE = 12;
    private static final String WATCH_LATER = "WL";
    private static final String LIKED = "LL";

    interface Callback {
        /** A SmartTube section of the You tab (MediaGroup.TYPE_...); false when it's switched off */
        boolean hasSection(int sectionId);

        String sectionTitle(int sectionId);

        void openSection(int sectionId);

        void openDownloads();

        boolean isSignedIn();

        FeedAdapter.Listener listener();
    }

    private final PhoneBrowseActivity mActivity;
    private final Callback mCallback;
    private final LinearLayout mView;
    private final LinearLayout mRows;
    private final LinearLayout mShelves;
    private final List<Video> mHistory = new ArrayList<>();
    private final List<Video> mPlaylists = new ArrayList<>();
    private ShelfAdapter mHistoryAdapter;
    private ShelfAdapter mPlaylistsAdapter;
    private View mHistoryShelf;
    private View mPlaylistsShelf;
    private Disposable mHistoryAction;
    private Disposable mPlaylistsAction;
    private final float mDensity;

    PhoneYouDashboard(PhoneBrowseActivity activity, Callback callback) {
        mActivity = activity;
        mCallback = callback;
        mDensity = activity.getResources().getDisplayMetrics().density;
        mView = new LinearLayout(activity);
        mView.setOrientation(LinearLayout.VERTICAL);
        mView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mView.setPadding(0, 0, 0, dp(16));
        mRows = new LinearLayout(activity);
        mRows.setOrientation(LinearLayout.VERTICAL);
        mView.addView(mRows);
        mShelves = new LinearLayout(activity);
        mShelves.setOrientation(LinearLayout.VERTICAL);
        mView.addView(mShelves);
    }

    View getView() {
        return mView;
    }

    /** The tab is shown: the rows for the sections that exist now, and fresh History and Playlists */
    void show() {
        buildRows();
        buildShelves();
        if (!mCallback.isSignedIn()) {
            return; // History and Playlists belong to an account
        }
        RxHelper.disposeActions(mHistoryAction, mPlaylistsAction);
        if (mHistoryShelf != null) {
            View shelf = mHistoryShelf;
            ShelfAdapter adapter = mHistoryAdapter;
            mHistoryAction = RxHelper.execute(YouTubeServiceManager.instance().getContentService().getHistoryObserve(),
                    group -> fill(mHistory, adapter, shelf, group), error -> { });
        }
        if (mPlaylistsShelf != null) {
            View shelf = mPlaylistsShelf;
            ShelfAdapter adapter = mPlaylistsAdapter;
            mPlaylistsAction = RxHelper.execute(YouTubeServiceManager.instance().getContentService().getPlaylistsObserve(),
                    group -> fill(mPlaylists, adapter, shelf, group), error -> { });
        }
    }

    void release() {
        RxHelper.disposeActions(mHistoryAction, mPlaylistsAction);
    }

    private void fill(List<Video> target, ShelfAdapter adapter, View shelf, MediaGroup group) {
        List<MediaItem> items = group != null ? group.getMediaItems() : null;
        target.clear();
        if (items != null) {
            for (MediaItem item : items) {
                if (item != null && target.size() < SHELF_SIZE) {
                    target.add(Video.from(item));
                }
            }
        }
        adapter.onDataChanged(0);
        shelf.setVisibility(target.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // region Rows

    private void buildRows() {
        mRows.removeAllViews();
        addSectionRow(MediaGroup.TYPE_HISTORY, R.drawable.ic_phone_history);
        addSectionRow(MediaGroup.TYPE_USER_PLAYLISTS, R.drawable.ic_phone_library);
        if (mCallback.isSignedIn() && mCallback.hasSection(MediaGroup.TYPE_USER_PLAYLISTS)) {
            addRow(mActivity.getString(R.string.phone_you_watch_later), R.drawable.ic_phone_save, () -> openPlaylist(WATCH_LATER));
            addRow(mActivity.getString(R.string.phone_you_liked), R.drawable.ic_phone_like, () -> openPlaylist(LIKED));
        }
        addRow(mActivity.getString(R.string.phone_downloads), R.drawable.ic_phone_download, mCallback::openDownloads);
        addSectionRow(MediaGroup.TYPE_MY_VIDEOS, R.drawable.ic_phone_play);
        addSectionRow(MediaGroup.TYPE_SETTINGS, R.drawable.ic_phone_settings);
    }

    private void addSectionRow(int sectionId, int icon) {
        if (mCallback.hasSection(sectionId)) {
            addRow(mCallback.sectionTitle(sectionId), icon, () -> mCallback.openSection(sectionId));
        }
    }

    private void addRow(String title, int icon, Runnable onClick) {
        View row = LayoutInflater.from(mActivity).inflate(R.layout.phone_item_setting, mRows, false);
        ((ImageView) row.findViewById(R.id.phone_thumb)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.phone_title)).setText(title);
        row.findViewById(R.id.phone_setting_chevron).setVisibility(View.GONE);
        row.setOnClickListener(v -> onClick.run());
        mRows.addView(row);
    }

    /** Watch later and Liked videos are two of the account's playlists: the one with this id, else all playlists */
    private void openPlaylist(String playlistId) {
        for (Video playlist : mPlaylists) {
            if (playlistId.equals(playlist.playlistId)) {
                mCallback.listener().onVideoClicked(playlist);
                return;
            }
        }
        Video playlist = new Video();
        playlist.playlistId = playlistId;
        playlist.title = mActivity.getString(WATCH_LATER.equals(playlistId) ? R.string.phone_you_watch_later : R.string.phone_you_liked);
        com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelUploadsPresenter.instance(mActivity).openChannel(playlist);
    }

    // endregion

    // region Shelves

    private void buildShelves() {
        mShelves.removeAllViews();
        mHistoryShelf = null;
        mPlaylistsShelf = null;
        if (!mCallback.isSignedIn()) {
            return;
        }
        if (mCallback.hasSection(MediaGroup.TYPE_HISTORY)) {
            mHistoryAdapter = new ShelfAdapter(mHistory, ShelfAdapter.STYLE_CARD, mCallback.listener(), null);
            mHistoryShelf = addShelf(MediaGroup.TYPE_HISTORY, mHistoryAdapter, mHistory.isEmpty());
        }
        if (mCallback.hasSection(MediaGroup.TYPE_USER_PLAYLISTS)) {
            mPlaylistsAdapter = new ShelfAdapter(mPlaylists, ShelfAdapter.STYLE_CARD, mCallback.listener(), null);
            mPlaylistsShelf = addShelf(MediaGroup.TYPE_USER_PLAYLISTS, mPlaylistsAdapter, mPlaylists.isEmpty());
        }
    }

    /** A title with "View all" and a row of cards under it; hidden until there is something to show */
    private View addShelf(int sectionId, ShelfAdapter adapter, boolean empty) {
        LinearLayout shelf = new LinearLayout(mActivity);
        shelf.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(mActivity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(18), dp(12), dp(10));
        TextView title = new TextView(mActivity);
        title.setText(mCallback.sectionTitle(sectionId));
        title.setTextColor(mActivity.getResources().getColor(R.color.phone_text));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = new TextView(mActivity);
        all.setText(R.string.phone_you_view_all);
        all.setTextColor(mActivity.getResources().getColor(R.color.phone_text));
        all.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        all.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        all.setGravity(Gravity.CENTER);
        all.setPadding(dp(14), 0, dp(14), 0);
        all.setBackgroundResource(R.drawable.phone_outline_pill);
        all.setOnClickListener(v -> mCallback.openSection(sectionId));
        header.addView(all, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        shelf.addView(header);

        RecyclerView cards = new RecyclerView(mActivity);
        cards.setLayoutManager(new LinearLayoutManager(mActivity, LinearLayoutManager.HORIZONTAL, false));
        cards.setAdapter(adapter);
        cards.setClipToPadding(false);
        cards.setPadding(dp(8), 0, dp(8), 0);
        cards.setNestedScrollingEnabled(false);
        cards.setItemAnimator(null);
        shelf.addView(cards, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        shelf.setVisibility(empty ? View.GONE : View.VISIBLE);
        mShelves.addView(shelf);
        return shelf;
    }

    // endregion

    private int dp(float value) {
        return Math.round(value * mDensity);
    }
}
