/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.awsomefox.audiosonic.fragments;
import android.app.Activity;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.media.MediaMetadataRetriever;
import android.support.annotation.NonNull;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.StatFs;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.service.MediaStoreService;
import github.awsomefox.audiosonic.service.MusicServiceFactory;
import github.awsomefox.audiosonic.util.ImageLoader;
import github.awsomefox.audiosonic.util.LoadingTask;
import github.awsomefox.audiosonic.util.SongDBHandler;
import github.awsomefox.audiosonic.util.UpdateHelper;
import github.awsomefox.audiosonic.R;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import github.awsomefox.audiosonic.activity.SubsonicActivity;
import github.awsomefox.audiosonic.activity.SubsonicFragmentActivity;
import github.awsomefox.audiosonic.adapter.SectionAdapter;
import github.awsomefox.audiosonic.domain.Artist;
import github.awsomefox.audiosonic.domain.Bookmark;
import github.awsomefox.audiosonic.domain.Genre;
import github.awsomefox.audiosonic.domain.ServerInfo;
import github.awsomefox.audiosonic.service.DownloadFile;
import github.awsomefox.audiosonic.service.DownloadService;
import github.awsomefox.audiosonic.service.MusicService;
import github.awsomefox.audiosonic.service.OfflineException;
import github.awsomefox.audiosonic.service.ServerTooOldException;
import github.awsomefox.audiosonic.util.Constants;
import github.awsomefox.audiosonic.util.FileChooser;
import github.awsomefox.audiosonic.util.FileUtil;
import github.awsomefox.audiosonic.util.MenuUtil;
import github.awsomefox.audiosonic.util.ProgressListener;
import github.awsomefox.audiosonic.util.SilentBackgroundTask;
import github.awsomefox.audiosonic.util.Util;
import github.awsomefox.audiosonic.util.importExport;
import github.awsomefox.audiosonic.view.GridSpacingDecoration;
import github.awsomefox.audiosonic.view.UpdateView;

public class SubsonicFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
	private static final String TAG = SubsonicFragment.class.getSimpleName();
	private static int TAG_INC = 10;
	private int tag;

	protected SubsonicActivity context;
	protected CharSequence title = null;
	protected CharSequence subtitle = null;
	protected View rootView;
	protected boolean primaryFragment = false;
	protected boolean secondaryFragment = false;
	protected boolean isOnlyVisible = true;
	protected boolean alwaysFullscreen = false;
	protected boolean alwaysStartFullscreen = false;
	protected boolean invalidated = false;
	protected static Random random = new Random();
	protected GestureDetector gestureScanner;
	protected boolean artist = false;
	protected boolean artistOverride = false;
	protected SwipeRefreshLayout refreshLayout;
	protected boolean firstRun;
	protected MenuItem searchItem;
	protected SearchView searchView;

	public SubsonicFragment() {
		super();
		tag = TAG_INC++;
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		if(bundle != null) {
			String name = bundle.getString(Constants.FRAGMENT_NAME);
			if(name != null) {
				title = name;
			}
		}
		firstRun = true;
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		if(title != null) {
			outState.putString(Constants.FRAGMENT_NAME, title.toString());
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if(firstRun) {
			firstRun = false;
		} else {
			UpdateView.triggerUpdate();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Context activity) {
		super.onAttach(activity);
		context = (SubsonicActivity)activity;
	}

	public void setContext(SubsonicActivity context) {
		this.context = context;
	}

	protected void onFinishSetupOptionsMenu(final Menu menu) {
		searchItem = menu.findItem(R.id.menu_global_search);
		if(searchItem != null) {
			searchView = (SearchView) searchItem.getActionView();
			SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
			SearchableInfo searchableInfo = null;
			if (searchManager != null) {
				searchableInfo = searchManager.getSearchableInfo(context.getComponentName());
			}
			if(searchableInfo == null) {
				Log.w(TAG, "Failed to get SearchableInfo");
			} else {
				searchView.setSearchableInfo(searchableInfo);
			}

			String currentQuery = getCurrentQuery();
			if(currentQuery != null) {
				searchView.setOnSearchClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						searchView.setQuery(getCurrentQuery(), false);
					}
				});
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_exit:
				exit();
				return true;
			case R.id.menu_export:
				importExport.exportData(context);
				return true;
			case R.id.menu_import:
				new FileChooser(context).setFileListener(new FileChooser.FileSelectedListener() {
					@Override
					public void fileSelected(final File file) {
						importExport.importData(context, file.getPath());
					}
				}).showDialog();
				return true;
			case R.id.menu_refresh:
				refresh();
				return true;
			case R.id.menu_play_now:
				playNow(false, false);
				return true;
			case R.id.menu_play_last:
				playNow(false, true);
				return true;
			case R.id.menu_play_next:
				playNow(false, true, true);
				return true;
			case R.id.menu_shuffle:
				playNow(true, false);
				return true;
			case R.id.menu_download:
				downloadBackground(true);
				clearSelected();
				return true;
			case R.id.menu_delete:
				delete();
				clearSelected();
				return true;
			case R.id.menu_star:case R.id.menu_unstar:
				toggleSelectedStarred();
				return true;
		}

		return false;
	}

	public void onCreateContextMenuSupport(Menu menu, MenuInflater menuInflater, UpdateView updateView, Object selected) {
		if(selected instanceof MusicDirectory.Entry) {
			MusicDirectory.Entry entry = (MusicDirectory.Entry) selected;
			if (entry.isDirectory()) {
				if(Util.isOffline(context)) {
					menuInflater.inflate(R.menu.select_album_context_offline, menu);
				}
				else {
					menuInflater.inflate(R.menu.select_album_context, menu);
				}
			
			} else if(!entry.isVideo()) {
				if(Util.isOffline(context)) {
					menuInflater.inflate(R.menu.select_song_context_offline, menu);
				}
				else {
					menuInflater.inflate(R.menu.select_song_context, menu);

					if(entry.getBookmark() == null) {
						menu.removeItem(R.id.bookmark_menu_delete);
					}


					String songPressAction = Util.getSongPressAction(context);
					if(!"next".equals(songPressAction) && !"last".equals(songPressAction)) {
						menu.setGroupVisible(R.id.hide_play_now, false);
					}
				}
			} else {
				if(Util.isOffline(context)) {
					menuInflater.inflate(R.menu.select_video_context_offline, menu);
				}
				else {
					menuInflater.inflate(R.menu.select_video_context, menu);
				}
			}

			MenuItem starMenu = menu.findItem(entry.isDirectory() ? R.id.album_menu_star : R.id.song_menu_star);
			if(starMenu != null) {
				starMenu.setTitle(entry.isStarred() ? R.string.common_unstar : R.string.common_star);
			}

			if(!isShowArtistEnabled() || (!Util.isTagBrowsing(context) && entry.getParent() == null) || (Util.isTagBrowsing(context) && entry.getArtistId() == null)) {
				menu.setGroupVisible(R.id.hide_show_artist, false);
			}
		} else if(selected instanceof Artist) {
			Artist artist = (Artist) selected;
			if(Util.isOffline(context)) {
				menuInflater.inflate(R.menu.select_artist_context_offline, menu);
			} else {
				menuInflater.inflate(R.menu.select_artist_context, menu);

				menu.findItem(R.id.artist_menu_star).setTitle(artist.isStarred() ? R.string.common_unstar : R.string.common_star);
			}
		}

		MenuUtil.hideMenuItems(context, menu, updateView);
	}

	protected void recreateContextMenu(Menu menu) {
		List<MenuItem> menuItems = new ArrayList<MenuItem>();
		for(int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if(item.isVisible()) {
				menuItems.add(item);
			}
		}
		menu.clear();
		for(int i = 0; i < menuItems.size(); i++) {
			MenuItem item = menuItems.get(i);
			menu.add(tag, item.getItemId(), Menu.NONE, item.getTitle());
		}
	}

	// For reverting specific removals: https://github.com/daneren2005/Subsonic/commit/fbd1a68042dfc3601eaa0a9e37b3957bbdd51420
	public boolean onContextItemSelected(MenuItem menuItem, Object selectedItem) {
		Artist artist = selectedItem instanceof Artist ? (Artist) selectedItem : null;
		MusicDirectory.Entry entry = selectedItem instanceof MusicDirectory.Entry ? (MusicDirectory.Entry) selectedItem : null;
		if(selectedItem instanceof DownloadFile) {
			entry = ((DownloadFile) selectedItem).getSong();
		}
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>(1);
		songs.add(entry);

		switch (menuItem.getItemId()) {
			case R.id.artist_menu_play_now:
				downloadRecursively(artist.getId(), false, false, true, false, false);
				break;
			case R.id.artist_menu_download:
				downloadRecursively(artist.getId(), false, true, false, false, true);
				break;
			case R.id.artist_menu_pin:
				downloadRecursively(artist.getId(), true, true, false, false, true);
				break;
			case R.id.artist_menu_delete:
				deleteRecursively(artist);
				break;
			case R.id.artist_menu_star:
				UpdateHelper.toggleStarred(context, artist);
				break;
			case R.id.album_menu_play_now:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, false, true, false, false);
				break;
			case R.id.album_menu_download:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, true, false, false, true);
				break;
			case R.id.album_menu_pin:
				artistOverride = true;
				downloadRecursively(entry.getId(), true, true, false, false, true);
				break;
			case R.id.album_menu_star:
				UpdateHelper.toggleStarred(context, entry);
				break;
			case R.id.album_menu_delete:
				deleteRecursively(entry);
				break;
			case R.id.album_menu_info:
				displaySongInfo(entry);
				break;
			case R.id.album_menu_show_artist:
				showAlbumArtist((MusicDirectory.Entry) selectedItem);
				break;
			case R.id.song_menu_play_now:
				playNow(songs);
				break;
			case R.id.song_menu_download:
				getDownloadService().downloadBackground(songs, false);
				break;
			case R.id.song_menu_pin:
				getDownloadService().downloadBackground(songs, true);
				break;
			case R.id.song_menu_delete:
				deleteSongs(songs);
				break;
			case R.id.song_menu_star:
				UpdateHelper.toggleStarred(context, entry);
				break;
			case R.id.song_menu_play_external:
				playExternalPlayer(entry);
				break;
			case R.id.song_menu_info:
				displaySongInfo(entry);
				break;
			case R.id.song_menu_stream_external:
				streamExternalPlayer(entry);
				break;
			case R.id.song_menu_show_album:
				showAlbum((MusicDirectory.Entry) selectedItem);
				break;
			case R.id.song_menu_show_artist:
				showArtist((MusicDirectory.Entry) selectedItem);
				break;
			case R.id.bookmark_menu_delete:
				deleteBookmark(entry, null);
				break;
			default:
				return false;
		}

		return true;
	}

	public void replaceFragment(SubsonicFragment fragment) {
		replaceFragment(fragment, true);
	}
	public void replaceFragment(SubsonicFragment fragment, boolean replaceCurrent) {
		context.replaceFragment(fragment, fragment.getSupportTag(), secondaryFragment && replaceCurrent);
	}
	public void replaceExistingFragment(SubsonicFragment fragment) {
		context.replaceExistingFragment(fragment, fragment.getSupportTag());
	}
	public void removeCurrent() {
		context.removeCurrent();
	}

	public int getRootId() {
		return rootView.getId();
	}

	public void setSupportTag(int tag) { this.tag = tag; }
	public void setSupportTag(String tag) { this.tag = Integer.parseInt(tag); }
	public int getSupportTag() {
		return tag;
	}

	public void setPrimaryFragment(boolean primary) {
		primaryFragment = primary;
		if(primary) {
			if(context != null && title != null) {
				context.setTitle(title);
				context.setSubtitle(subtitle);
			}
			if(invalidated) {
				invalidated = false;
				refresh(false);
			}
		}
	}
	public void setPrimaryFragment(boolean primary, boolean secondary) {
		setPrimaryFragment(primary);
		secondaryFragment = secondary;
	}
	public void setSecondaryFragment(boolean secondary) {
		secondaryFragment = secondary;
	}
	public void setIsOnlyVisible(boolean isOnlyVisible) {
		this.isOnlyVisible = isOnlyVisible;
	}
	public boolean isAlwaysFullscreen() {
		return alwaysFullscreen;
	}
	public boolean isAlwaysStartFullscreen() {
		return alwaysStartFullscreen;
	}

	public void invalidate() {
		if(primaryFragment) {
			refresh(true);
		} else {
			invalidated = true;
		}
	}

	public DownloadService getDownloadService() {
		return context != null ? context.getDownloadService() : null;
	}

	protected void refresh() {
		refresh(true);
	}
	protected void refresh(boolean refresh) {

	}

	@Override
	public void onRefresh() {
		refreshLayout.setRefreshing(false);
		refresh();
	}

	protected void exit() {
		if(((Object) context).getClass() != SubsonicFragmentActivity.class) {
			Intent intent = new Intent(context, SubsonicFragmentActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
			Util.startActivityWithoutTransition(context, intent);
		} else {
			context.stopService(new Intent(context, DownloadService.class));
			context.finish();
		}
	}

	public void setProgressVisible(boolean visible) {
		View view = rootView.findViewById(R.id.tab_progress);
		if (view != null) {
			view.setVisibility(visible ? View.VISIBLE : View.GONE);

			if(visible) {
				View progress = rootView.findViewById(R.id.tab_progress_spinner);
				progress.setVisibility(View.VISIBLE);
			}
		}
	}

	public void updateProgress(String message) {
		TextView view = rootView.findViewById(R.id.tab_progress_message);
		if (view != null) {
			view.setText(message);
		}
	}

	public void setEmpty(boolean empty) {
		View view = rootView.findViewById(R.id.tab_progress);
		if(empty) {
			view.setVisibility(View.VISIBLE);

			View progress = view.findViewById(R.id.tab_progress_spinner);
			progress.setVisibility(View.GONE);

			TextView text = view.findViewById(R.id.tab_progress_message);
			text.setText(R.string.common_empty);
		} else {
			view.setVisibility(View.GONE);
		}
	}

	protected synchronized ImageLoader getImageLoader() {
		return context.getImageLoader();
	}

	public void setTitle(CharSequence title) {
		this.title = title;
		context.setTitle(title);
	}
	public void setTitle(int title) {
		this.title = context.getResources().getString(title);
		context.setTitle(this.title);
	}
	public void setSubtitle(CharSequence title) {
		this.subtitle = title;
		context.setSubtitle(title);
	}
	public CharSequence getTitle() {
		return this.title;
	}

	protected void setupScrollList(final AbsListView listView) {
		if(!context.isTouchscreen()) {
			refreshLayout.setEnabled(false);
		} else {
			listView.setOnScrollListener(new AbsListView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(AbsListView view, int scrollState) {
				}

				@Override
				public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
					int topRowVerticalPosition = (listView.getChildCount() == 0) ? 0 : listView.getChildAt(0).getTop();
					refreshLayout.setEnabled(topRowVerticalPosition >= 0 && listView.getFirstVisiblePosition() == 0);
				}
			});

			refreshLayout.setColorSchemeResources(
					R.color.holo_blue_light,
					R.color.holo_orange_light,
					R.color.holo_green_light,
					R.color.holo_red_light);
		}
	}
	protected void setupScrollList(final RecyclerView recyclerView) {
		if(!context.isTouchscreen()) {
			refreshLayout.setEnabled(false);
		} else {
			recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
				@Override
				public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
					super.onScrollStateChanged(recyclerView, newState);
				}

				@Override
				public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
					refreshLayout.setEnabled(!recyclerView.canScrollVertically(-1));
				}
			});

			refreshLayout.setColorSchemeResources(
					R.color.holo_blue_light,
					R.color.holo_orange_light,
					R.color.holo_green_light,
					R.color.holo_red_light);
		}
	}

	public void setupLayoutManager(RecyclerView recyclerView, boolean largeAlbums) {
		recyclerView.setLayoutManager(getLayoutManager(recyclerView, largeAlbums));
	}
	public RecyclerView.LayoutManager getLayoutManager(RecyclerView recyclerView, boolean largeCells) {
		if(largeCells) {
			return getGridLayoutManager(recyclerView);
		} else {
			return getLinearLayoutManager();
		}
	}
	public GridLayoutManager getGridLayoutManager(RecyclerView recyclerView) {
		final int columns = getRecyclerColumnCount();
		GridLayoutManager gridLayoutManager = new GridLayoutManager(context, columns);


		GridLayoutManager.SpanSizeLookup spanSizeLookup = getSpanSizeLookup(gridLayoutManager);
		if(spanSizeLookup != null) {
			gridLayoutManager.setSpanSizeLookup(spanSizeLookup);
		}
		RecyclerView.ItemDecoration itemDecoration = getItemDecoration();
		if(itemDecoration != null) {
			recyclerView.addItemDecoration(itemDecoration);
		}
		return gridLayoutManager;
	}
	public LinearLayoutManager getLinearLayoutManager() {
		LinearLayoutManager layoutManager = new LinearLayoutManager(context);
		layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
		return layoutManager;
	}

	public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final GridLayoutManager gridLayoutManager) {
		return new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(int position) {
				SectionAdapter adapter = getCurrentAdapter();
				if(adapter != null) {

					int viewType = adapter.getItemViewType(position);
					if (viewType == SectionAdapter.VIEW_TYPE_HEADER) {

						return gridLayoutManager.getSpanCount();
					} else {
						return 1;
					}
				} else {
					return 1;
				}
			}
		};
	}
	public RecyclerView.ItemDecoration getItemDecoration() {
		return new GridSpacingDecoration();
	}
	public int getRecyclerColumnCount() {
		if(isOnlyVisible) {
			return context.getResources().getInteger(R.integer.Grid_FullScreen_Columns);
		} else {
			return context.getResources().getInteger(R.integer.Grid_Columns);
		}
	}

	protected void warnIfStorageUnavailable() {
		if (!Util.isExternalStoragePresent()) {
			Util.toast(context, R.string.select_album_no_sdcard);
		}

		try {
			StatFs stat = new StatFs(FileUtil.getMusicDirectory(context).getPath());
			long bytesAvailableFs = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
			if (bytesAvailableFs < 50000000L) {
				Util.toast(context, context.getResources().getString(R.string.select_album_no_room, Util.formatBytes(bytesAvailableFs)));
			}
		} catch(Exception e) {
			Log.w(TAG, "Error while checking storage space for music directory", e);
		}
	}

	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background);
	}
	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext);
	}
	protected void downloadPlaylist(final String id, final String name, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, name, false, save, append, autoplay, shuffle, background);
	}

	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, name, isDirectory, save, append, autoplay, shuffle, background, false);
	}

	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				musicService = MusicServiceFactory.getMusicService(context);
				MusicDirectory root;
				if(id != null) {
					root = getMusicDirectory(id, name, false, musicService, this);
				} else {
					root = musicService.getStarredList(context, this);
				}

				boolean shuffleByAlbum = Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_SHUFFLE_BY_ALBUM, true);
				if(shuffle && shuffleByAlbum) {
					Collections.shuffle(root.getChildren());
				}

				songs = new LinkedList<MusicDirectory.Entry>();
				getSongsRecursively(root, songs);

				if(shuffle && !shuffleByAlbum) {
					Collections.shuffle(songs);
				}

				DownloadService downloadService = getDownloadService();
				boolean transition = false;
				if (!songs.isEmpty() && downloadService != null) {
					// Conditions for a standard play now operation
					if(!append && !save && autoplay && !playNext && !shuffle && !background) {
						playNowOverride = true;
						return false;
					}

					if (!append && !background) {
						downloadService.clear();
					}
					if(!background) {
						downloadService.download(songs, save, autoplay, playNext, false);
						if(!append) {
							transition = true;
						}
					}
					else {
						downloadService.downloadBackground(songs, save);
					}
				}
				artistOverride = false;

				return transition;
			}
		}.execute();
	}

	protected void downloadRecursively(final List<MusicDirectory.Entry> albums, final boolean shuffle, final boolean append, final boolean playNext) {
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				musicService = MusicServiceFactory.getMusicService(context);

				if(shuffle) {
					Collections.shuffle(albums);
				}

				songs = new LinkedList<MusicDirectory.Entry>();
				MusicDirectory root = new MusicDirectory();
				root.addChildren(albums);
				getSongsRecursively(root, songs);

				DownloadService downloadService = getDownloadService();
				boolean transition = false;
				if (!songs.isEmpty() && downloadService != null) {
					// Conditions for a standard play now operation
					if(!append && !shuffle) {
						playNowOverride = true;
						return false;
					}

					if (!append) {
						downloadService.clear();
					}

					downloadService.download(songs, false, true, playNext, false);
					if(!append) {
						transition = true;
					}
				}
				artistOverride = false;

				return transition;
			}
		}.execute();
	}

	protected MusicDirectory getMusicDirectory(String id, String name, boolean refresh, MusicService service, ProgressListener listener) throws Exception {
		return getMusicDirectory(id, name, refresh, false, service, listener);
	}
	protected MusicDirectory getMusicDirectory(String id, String name, boolean refresh, boolean forceArtist, MusicService service, ProgressListener listener) throws Exception {
		if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
			if(artist && !artistOverride || forceArtist) {
				return service.getArtist(id, name, refresh, context, listener);
			} else {
				return service.getAlbum(id, name, refresh, context, listener);
			}
		} else {
			return service.getMusicDirectory(id, name, refresh, context, listener);
		}
	}

	public void displaySongInfo(final MusicDirectory.Entry song) {
		Integer duration = null;
		Integer bitrate = null;
		String format = null;
		long size = 0;
		if(!song.isDirectory()) {
			try {
				DownloadFile downloadFile = new DownloadFile(context, song, false);
				File file = downloadFile.getCompleteFile();
				if(file.exists()) {

					MediaMetadataRetriever metadata = new MediaMetadataRetriever();

					String tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
					duration = Integer.parseInt((tmp != null) ? tmp : "0") / 1000;
					format = FileUtil.getExtension(file.getName());
					size = file.length();

					// If no duration try to read bitrate tag
					if(duration == null) {
						tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
						bitrate = Integer.parseInt((tmp != null) ? tmp : "0") / 1000;
					} else {
						// Otherwise do a calculation for it
						// Divide by 1000 so in kbps
						bitrate = (int) (size / duration) / 1000 * 8;
					}

					if(Util.isOffline(context)) {
						song.setGenre(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
						String year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
						song.setYear(Integer.parseInt((year != null) ? year : "0"));
					}
				}
			} catch(Exception e) {
				Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver");
			}
		}
		if(duration == null) {
			duration = song.getDuration();
		}

		List<Integer> headers = new ArrayList<>();
		List<String> details = new ArrayList<>();

		if(!song.isDirectory()) {
			headers.add(R.string.details_title);
			details.add(song.getTitle());
		}

		if(!song.isVideo()) {
			if(song.getArtist() != null && !"".equals(song.getArtist())) {
				headers.add(R.string.details_artist);
				details.add(song.getArtist());
			}
			if(song.getAlbum() != null && !"".equals(song.getAlbum())) {
				headers.add(R.string.details_album);
				details.add(song.getAlbum());
			}
		}
		if(song.getTrack() != null && song.getTrack() != 0) {
			headers.add(R.string.details_chapter);
			details.add(Integer.toString(song.getTrack()));
		}
		if(song.getGenre() != null && !"".equals(song.getGenre())) {
			headers.add(R.string.details_genre);
			details.add(song.getGenre());
		}
		if(song.getYear() != null && song.getYear() != 0) {
			headers.add(R.string.details_year);
			details.add(Integer.toString(song.getYear()));
		}
		if(!Util.isOffline(context) && song.getSuffix() != null) {
			headers.add(R.string.details_server_format);
			details.add(song.getSuffix());

			if(song.getBitRate() != null && song.getBitRate() != 0) {
				headers.add(R.string.details_server_bitrate);
				details.add(song.getBitRate() + " kbps");
			}
		}
		if(format != null && !"".equals(format)) {
			headers.add(R.string.details_cached_format);
			details.add(format);
		}
		if(bitrate != null && bitrate != 0) {
			headers.add(R.string.details_cached_bitrate);
			details.add(bitrate + " kbps");
		}
		if(size != 0) {
			headers.add(R.string.details_size);
			details.add(Util.formatLocalizedBytes(size, context));
		}
		if(duration != null && duration != 0) {
			headers.add(R.string.details_length);
			details.add(Util.formatDuration(duration));
		}
		if(song.getBookmark() != null) {
			headers.add(R.string.details_bookmark_position);
			details.add(Util.formatDuration(song.getBookmark().getPosition() / 1000));
		}

		headers.add(R.string.details_starred);
		details.add(Util.formatBoolean(context, song.isStarred()));

		try {
			Long[] dates = SongDBHandler.getHandler(context).getLastPlayed(song);
			if(dates != null && dates[0] != null && dates[0] > 0) {
				headers.add(R.string.details_last_played);
				details.add(Util.formatDate((dates[1] != null && dates[1] > dates[0]) ? dates[1] : dates[0]));
			}
		} catch(Exception e) {
			Log.e(TAG, "Failed to get last played", e);
		}
		int title;
		if(song.isDirectory()) {
			title = R.string.details_title_album;
		} else {
			title = R.string.details_title_song;
		}
		Util.showDetailsDialog(context, title, headers, details);
	}

	protected void playVideo(MusicDirectory.Entry entry) {
		if(entryExists(entry)) {
			playExternalPlayer(entry);
		} else {
			streamExternalPlayer(entry);
		}
	}

	protected void playWebView(MusicDirectory.Entry entry) {
		int maxBitrate = Util.getMaxVideoBitrate(context);

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(MusicServiceFactory.getMusicService(context).getVideoUrl(maxBitrate, context, entry.getId())));

		startActivity(intent);
	}
	protected void playExternalPlayer(MusicDirectory.Entry entry) {
		if(!entryExists(entry)) {
			Util.toast(context, R.string.download_need_download);
		} else {
			DownloadFile check = new DownloadFile(context, entry, false);
			File file = check.getCompleteFile();

			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(file), "video/*");
			intent.putExtra(Intent.EXTRA_TITLE, entry.getTitle());

			List<ResolveInfo> intents = context.getPackageManager()
					.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			if(intents != null && intents.size() > 0) {
				startActivity(intent);
			}else {
				Util.toast(context, R.string.download_no_streaming_player);
			}
		}
	}
	protected void streamExternalPlayer(MusicDirectory.Entry entry) {
		String videoPlayerType = Util.getVideoPlayerType(context);
		if("flash".equals(videoPlayerType)) {
			playWebView(entry);
		} else if("hls".equals(videoPlayerType)) {
			streamExternalPlayer(entry, "hls");
		} else if("raw".equals(videoPlayerType)) {
			streamExternalPlayer(entry, "raw");
		} else {
			streamExternalPlayer(entry, entry.getTranscodedSuffix());
		}
	}
	protected void streamExternalPlayer(MusicDirectory.Entry entry, String format) {
		try {
			int maxBitrate = Util.getMaxVideoBitrate(context);

			Intent intent = new Intent(Intent.ACTION_VIEW);
			if("hls".equals(format)) {
				intent.setDataAndType(Uri.parse(MusicServiceFactory.getMusicService(context).getHlsUrl(entry.getId(), maxBitrate, context)), "application/x-mpegURL");
			} else {
				intent.setDataAndType(Uri.parse(MusicServiceFactory.getMusicService(context).getVideoStreamUrl(format, maxBitrate, context, entry.getId())), "video/*");
			}
			intent.putExtra("title", entry.getTitle());

			List<ResolveInfo> intents = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			if(intents != null && intents.size() > 0) {
				startActivity(intent);
			} else {
				Util.toast(context, R.string.download_no_streaming_player);
			}
		} catch(Exception error) {
			String msg;
			if (error instanceof OfflineException || error instanceof ServerTooOldException) {
				msg = error.getMessage();
			} else {
				msg = context.getResources().getString(R.string.download_no_streaming_player) + " " + error.getMessage();
			}

			Util.toast(context, msg, false);
		}
	}

	protected boolean entryExists(MusicDirectory.Entry entry) {
		DownloadFile check = new DownloadFile(context, entry, false);
		return check.isCompleteFileAvailable();
	}

	public void deleteRecursively(Artist artist) {
		deleteRecursively(artist, FileUtil.getArtistDirectory(context, artist));
	}

	public void deleteRecursively(MusicDirectory.Entry album) {
		deleteRecursively(album, FileUtil.getAlbumDirectory(context, album));
	}

	public void deleteRecursively(final Object remove, final File dir) {
		if(dir == null) {
			return;
		}

		new LoadingTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MediaStoreService mediaStore = new MediaStoreService(context);
				FileUtil.recursiveDelete(dir, mediaStore);
				return null;
			}

			@Override
			protected void done(Void result) {
				if(Util.isOffline(context)) {
					SectionAdapter adapter = getCurrentAdapter();
					if(adapter != null) {
						adapter.removeItem(remove);
					} else {
						refresh();
					}
				} else {
					UpdateView.triggerUpdate();
				}
			}
		}.execute();
	}
	public void deleteSongs(final List<MusicDirectory.Entry> songs) {
		new LoadingTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				getDownloadService().delete(songs);
				return null;
			}

			@Override
			protected void done(Void result) {
				if(Util.isOffline(context)) {
					SectionAdapter adapter = getCurrentAdapter();
					if(adapter != null) {
						for(MusicDirectory.Entry song: songs) {
							adapter.removeItem(song);
						}
					} else {
						refresh();
					}
				} else {
					UpdateView.triggerUpdate();
				}
			}
		}.execute();
	}

	public void showAlbumArtist(MusicDirectory.Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
		} else {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getParent());
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
		args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}
	public void showArtist(MusicDirectory.Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
		} else {
			if(entry.getGrandParent() == null) {
				args.putString(Constants.INTENT_EXTRA_NAME_CHILD_ID, entry.getParent());
			} else {
				args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getGrandParent());
			}
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
		args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}

	public void showAlbum(MusicDirectory.Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getAlbumId());
		} else {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getParent());
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getAlbum());
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}

	public GestureDetector getGestureDetector() {
		return gestureScanner;
	}

	protected void playBookmark(List<MusicDirectory.Entry> songs, MusicDirectory.Entry song) {
		playBookmark(songs, song, null, null);
	}

	protected void playBookmark(final List<MusicDirectory.Entry> songs, final MusicDirectory.Entry song, final String playlistName, final String playlistId) {
		final Integer position = song.getBookmark().getPosition();

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.bookmark_resume_title)
				.setMessage(getResources().getString(R.string.bookmark_resume, song.getTitle(), Util.formatDuration(position / 1000)))
				.setPositiveButton(R.string.bookmark_action_resume, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {

						playNow(songs, song, position, playlistName, playlistId);
					}
				})
				.setNegativeButton(R.string.bookmark_action_start_over, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						final Bookmark oldBookmark = song.getBookmark();
						song.setBookmark(null);

						new SilentBackgroundTask<Void>(context) {
							@Override
							protected Void doInBackground() throws Throwable {
								MusicService musicService = MusicServiceFactory.getMusicService(context);
								musicService.deleteBookmark(song, context, null);

								return null;
							}

							@Override
							protected void error(Throwable error) {
								song.setBookmark(oldBookmark);

								String msg;
								if (error instanceof OfflineException || error instanceof ServerTooOldException) {
									msg = getErrorMessage(error);
								} else {
									msg = context.getResources().getString(R.string.bookmark_deleted_error, song.getTitle()) + " " + getErrorMessage(error);
								}

								Util.toast(context, msg, false);
							}
						}.execute();


						playNow(songs, 0, playlistName, playlistId);
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	protected void onSongPress(List<MusicDirectory.Entry> entries, MusicDirectory.Entry entry) {
		onSongPress(entries, entry, 0, true);
	}
	protected void onSongPress(List<MusicDirectory.Entry> entries, MusicDirectory.Entry entry, boolean allowPlayAll) {
		onSongPress(entries, entry, 0, allowPlayAll);
	}
	protected void onSongPress(List<MusicDirectory.Entry> entries, MusicDirectory.Entry entry, int position, boolean allowPlayAll) {
		List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();

		String songPressAction = Util.getSongPressAction(context);
		if("all".equals(songPressAction) && allowPlayAll) {
			for(MusicDirectory.Entry song: entries) {
				if(!song.isDirectory() && !song.isVideo()) {
					songs.add(song);
				}
			}
			playNow(songs, entry, position);
		} else if("next".equals(songPressAction)) {
			getDownloadService().download(Arrays.asList(entry), false, false, true, false);
		}  else if("last".equals(songPressAction)) {
			getDownloadService().download(Arrays.asList(entry), false, false, false, false);
		} else {
			songs.add(entry);
			playNow(songs);
		}
	}

	protected void playNow(List<MusicDirectory.Entry> entries) {
		playNow(entries, null, null);
	}
	protected void playNow(final List<MusicDirectory.Entry> entries, final String playlistName, final String playlistId) {
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				getSongsRecursively(entries, songs);
				return null;
			}

			@Override
			protected void done(Boolean result) {
				MusicDirectory.Entry bookmark = null;
				for(MusicDirectory.Entry entry: songs) {
					if(entry.getBookmark() != null) {
						bookmark = entry;
						break;
					}
				}

				// If no bookmark found, just play from start
				if(bookmark == null) {
					playNow(songs, 0, playlistName, playlistId);
				} else {
					// If bookmark found, then give user choice to start from there or to start over
					playBookmark(songs, bookmark, playlistName, playlistId);
				}
			}
		}.execute();
	}
	protected void playNow(List<MusicDirectory.Entry> entries, int position) {
		playNow(entries, position, null, null);
	}
	protected void playNow(List<MusicDirectory.Entry> entries, int position, String playlistName, String playlistId) {
		MusicDirectory.Entry selected = entries.isEmpty() ? null : entries.get(0);
		playNow(entries, selected, position, playlistName, playlistId);
	}

	protected void playNow(List<MusicDirectory.Entry> entries, MusicDirectory.Entry song, int position) {
		playNow(entries, song, position, null, null);
	}

	protected void playNow(final List<MusicDirectory.Entry> entries, final MusicDirectory.Entry song, final int position, final String playlistName, final String playlistId) {
		new LoadingTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {

				playNowInTask(entries, song, position, playlistName, playlistId);
				return null;
			}

			@Override
			protected void done(Void result) {
				context.openNowPlaying();
			}
		}.execute();
	}
	protected void playNowInTask(final List<MusicDirectory.Entry> entries, final MusicDirectory.Entry song, final int position) {
		playNowInTask(entries, song, position, null, null);
	}
	protected void playNowInTask(final List<MusicDirectory.Entry> entries, final MusicDirectory.Entry song, final int position, final String playlistName, final String playlistId) {
		DownloadService downloadService = getDownloadService();
		if(downloadService == null) {
			return;
		}

		downloadService.clear();
		downloadService.download(entries, false, true, true, false, entries.indexOf(song), position);
	}

	protected void deleteBookmark(final MusicDirectory.Entry entry, final SectionAdapter adapter) {
		Util.confirmDialog(context, R.string.bookmark_delete_title, entry.getTitle(), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final Bookmark oldBookmark = entry.getBookmark();
				entry.setBookmark(null);

				new LoadingTask<Void>(context, false) {
					@Override
					protected Void doInBackground() throws Throwable {
						MusicService musicService = MusicServiceFactory.getMusicService(context);
						musicService.deleteBookmark(entry, context, null);

						new UpdateHelper.EntryInstanceUpdater(entry, DownloadService.METADATA_UPDATED_BOOKMARK) {
							@Override
							public void update(MusicDirectory.Entry found) {
								found.setBookmark(null);
							}
						}.execute();

						return null;
					}

					@Override
					protected void done(Void result) {
						if (adapter != null) {
							adapter.removeItem(entry);
						}
						Util.toast(context, context.getResources().getString(R.string.bookmark_deleted, entry.getTitle()));
					}

					@Override
					protected void error(Throwable error) {
						entry.setBookmark(oldBookmark);

						String msg;
						if (error instanceof OfflineException || error instanceof ServerTooOldException) {
							msg = getErrorMessage(error);
						} else {
							msg = context.getResources().getString(R.string.bookmark_deleted_error, entry.getTitle()) + " " + getErrorMessage(error);
						}

						Util.toast(context, msg, false);
					}
				}.execute();
			}
		});
	}

	public SectionAdapter getCurrentAdapter() { return null; }
	public void stopActionMode() {
		SectionAdapter adapter = getCurrentAdapter();
		if(adapter != null) {
			adapter.stopActionMode();
		}
	}
	protected void clearSelected() {
		if(getCurrentAdapter() != null) {
			getCurrentAdapter().clearSelected();
		}
	}
	protected List<MusicDirectory.Entry> getSelectedEntries() {
		return getCurrentAdapter().getSelected();
	}

	protected void playNow(final boolean shuffle, final boolean append) {
		playNow(shuffle, append, false);
	}
	protected void playNow(final boolean shuffle, final boolean append, final boolean playNext) {
		List<MusicDirectory.Entry> songs = getSelectedEntries();
		if(!songs.isEmpty()) {
			download(songs, append, false, !append, playNext, shuffle);
			clearSelected();
		}
	}
	protected void download(final List<MusicDirectory.Entry> entries, final boolean append, final boolean save, final boolean autoplay, final boolean playNext, final boolean shuffle) {
		final DownloadService downloadService = getDownloadService();
		if (downloadService == null) {
			return;
		}
		warnIfStorageUnavailable();

		// Conditions for using play now button
		if(!append && !save && autoplay && !playNext && !shuffle) {
			// Call playNow which goes through and tries to use bookmark information
			playNow(entries);
			return;
		}

		RecursiveLoader onValid = new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				if (!append) {
					getDownloadService().clear();
				}
				getSongsRecursively(entries, songs);

				downloadService.download(songs, save, autoplay, playNext, shuffle);
				return null;
			}

			@Override
			protected void done(Boolean result) {
				if (autoplay) {
					context.openNowPlaying();
				} else if (save) {
					Util.toast(context,
							context.getResources().getQuantityString(R.plurals.select_album_n_songs_downloading, songs.size(), songs.size()));
				} else if (append) {
					Util.toast(context,
							context.getResources().getQuantityString(R.plurals.select_album_n_songs_added, songs.size(), songs.size()));
				}
			}
		};

		executeOnValid(onValid);
	}
	protected void executeOnValid(RecursiveLoader onValid) {
		onValid.execute();
	}
	protected void downloadBackground(final boolean save) {
		List<MusicDirectory.Entry> songs = getSelectedEntries();
		if(!songs.isEmpty()) {
			downloadBackground(save, songs);
		}
	}

	protected void downloadBackground(final boolean save, final List<MusicDirectory.Entry> entries) {
		if (getDownloadService() == null) {
			return;
		}

		warnIfStorageUnavailable();
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				getSongsRecursively(entries, true);
				getDownloadService().downloadBackground(songs, save);
				return null;
			}

			@Override
			protected void done(Boolean result) {
				Util.toast(context, context.getResources().getQuantityString(R.plurals.select_album_n_songs_downloading, songs.size(), songs.size()));
			}
		}.execute();
	}

	protected void delete() {
		List<MusicDirectory.Entry> songs = getSelectedEntries();
		if(!songs.isEmpty()) {
			DownloadService downloadService = getDownloadService();
			if(downloadService != null) {
				downloadService.delete(songs);
			}
		}
	}

	protected void toggleSelectedStarred() {
		UpdateHelper.toggleStarred(context, getSelectedEntries());
	}

	protected boolean isShowArtistEnabled() {
		return false;
	}

	protected String getCurrentQuery() {
		return null;
	}

	public abstract class RecursiveLoader extends LoadingTask<Boolean> {
		protected MusicService musicService;
		protected static final int MAX_SONGS = 500;
		protected boolean playNowOverride = false;
		protected List<MusicDirectory.Entry> songs = new ArrayList<>();

		public RecursiveLoader(Activity context) {
			super(context);
			musicService = MusicServiceFactory.getMusicService(context);
		}

		protected void getSiblingsRecursively(MusicDirectory.Entry entry) throws Exception {
			MusicDirectory parent = new MusicDirectory();
			if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
				parent.setId(entry.getAlbumId());
			} else {
				parent.setId(entry.getParent());
			}

			if(parent.getId() == null) {
				songs.add(entry);
			} else {
				MusicDirectory.Entry dir = new MusicDirectory.Entry(parent.getId());
				dir.setDirectory(true);
				parent.addChild(dir);
				getSongsRecursively(parent, songs);
			}
		}
		protected void getSongsRecursively(List<MusicDirectory.Entry> entry) throws Exception {
			getSongsRecursively(entry, false);
		}
		protected void getSongsRecursively(List<MusicDirectory.Entry> entry, boolean allowVideo) throws Exception {
			getSongsRecursively(entry, songs, allowVideo);
		}
		protected void getSongsRecursively(List<MusicDirectory.Entry> entry, List<MusicDirectory.Entry> songs) throws Exception {
			getSongsRecursively(entry, songs, false);
		}
		protected void getSongsRecursively(List<MusicDirectory.Entry> entry, List<MusicDirectory.Entry> songs, boolean allowVideo) throws Exception {
			MusicDirectory dir = new MusicDirectory();
			dir.addChildren(entry);
			getSongsRecursively(dir, songs, allowVideo);
		}

		protected void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs) throws Exception {
			getSongsRecursively(parent, songs, false);
		}
		protected void getSongsRecursively(MusicDirectory parent, List<MusicDirectory.Entry> songs, boolean allowVideo) throws Exception {
			if (songs.size() > MAX_SONGS) {
				return;
			}

			for (MusicDirectory.Entry dir : parent.getChildren(true, false)) {

				MusicDirectory musicDirectory;
				if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
					musicDirectory = musicService.getAlbum(dir.getId(), dir.getTitle(), false, context, this);
				} else {
					musicDirectory = musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, context, this);
				}
				getSongsRecursively(musicDirectory, songs);
			}

			for (MusicDirectory.Entry song : parent.getChildren(false, true)) {
				if ((!song.isVideo() || allowVideo)) {
					songs.add(song);
				}
			}
		}

		@Override
		protected void done(Boolean result) {
			warnIfStorageUnavailable();

			if(playNowOverride) {
				playNow(songs);
				return;
			}

			if(result) {
				context.openNowPlaying();
			}
		}
	}
}
