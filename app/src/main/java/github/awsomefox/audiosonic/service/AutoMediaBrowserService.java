/*
	This file is part of Subsonic.

	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.

	Copyright 2015 (C) Scott Jackson
*/
package github.awsomefox.audiosonic.service;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;

import java.util.ArrayList;
import java.util.List;

import github.awsomefox.audiosonic.domain.Artist;
import github.awsomefox.audiosonic.domain.Indexes;
import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.domain.MusicFolder;
import github.awsomefox.audiosonic.util.Constants;
import github.awsomefox.audiosonic.util.SilentServiceTask;
import github.awsomefox.audiosonic.util.Util;
import github.awsomefox.audiosonic.util.compat.RemoteControlClientLP;
import github.awsomefox.audiosonic.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class AutoMediaBrowserService extends MediaBrowserServiceCompat {
	private static final String TAG = AutoMediaBrowserService.class.getSimpleName();
	private static final String BROWSER_ROOT = "root";
	private static final String BROWSER_ALBUM_LISTS = "albumLists";
	private static final String BROWSER_LIBRARY = "library";
	private static final String BROWSER_BOOKMARKS = "bookmarks";
	private static final String ALBUM_TYPE_PREFIX = "ty-";
	private static final String MUSIC_DIRECTORY_PREFIX = "md-";
	private static final String MUSIC_FOLDER_PREFIX = "mf-";
	private static final String MUSIC_DIRECTORY_CONTENTS_PREFIX = "mdc-";

	private DownloadService downloadService;
	private Handler handler = new Handler();

	@Override
	public void onCreate() {
		super.onCreate();
		getDownloadService();
	}

	@Nullable
	@Override
	public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
		BrowserRoot root = new BrowserRoot(BROWSER_ROOT, null);
		return root;
	}

	@Override
	public void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result) {
		if(BROWSER_ROOT.equals(parentId)) {
			getRootFolders(result);
		} else if(BROWSER_ALBUM_LISTS.equals(parentId)) {
			getAlbumLists(result);
		} else if(parentId.startsWith(ALBUM_TYPE_PREFIX)) {
			int id = Integer.valueOf(parentId.substring(ALBUM_TYPE_PREFIX.length()));
			getAlbumList(result, id);
		}  else if(parentId.startsWith(MUSIC_DIRECTORY_PREFIX)) {
			String id = parentId.substring(MUSIC_DIRECTORY_PREFIX.length());
			getPlayOptions(result, id, Constants.INTENT_EXTRA_NAME_ID);
		} else if(BROWSER_LIBRARY.equals(parentId)) {
			getLibrary(result);
		}  else if(parentId.startsWith(MUSIC_FOLDER_PREFIX)) {
			String id = parentId.substring(MUSIC_FOLDER_PREFIX.length());
			getIndexes(result, id);
		}  else if(parentId.startsWith(MUSIC_DIRECTORY_CONTENTS_PREFIX)) {
			String id = parentId.substring(MUSIC_DIRECTORY_CONTENTS_PREFIX.length());
			getMusicDirectory(result, id);
		} else if(BROWSER_BOOKMARKS.equals(parentId)) {
			getBookmarks(result);
		} else {
			// No idea what it is, send empty result
			result.sendResult(new ArrayList<MediaBrowserCompat.MediaItem>());
		}
	}

	private void getRootFolders(Result<List<MediaBrowserCompat.MediaItem>> result) {
		List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();


		MediaDescriptionCompat.Builder albumLists = new MediaDescriptionCompat.Builder();
		albumLists.setTitle(downloadService.getString(R.string.main_albums_title))
				.setMediaId(BROWSER_ALBUM_LISTS);
		mediaItems.add(new MediaBrowserCompat.MediaItem(albumLists.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

		MediaDescriptionCompat.Builder library = new MediaDescriptionCompat.Builder();
		library.setTitle(downloadService.getString(R.string.button_bar_browse))
			.setMediaId(BROWSER_LIBRARY);
		mediaItems.add(new MediaBrowserCompat.MediaItem(library.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

		if(Util.getPreferences(downloadService).getBoolean(Constants.PREFERENCES_KEY_BOOKMARKS_ENABLED, true)) {
			MediaDescriptionCompat.Builder bookmarks = new MediaDescriptionCompat.Builder();
			bookmarks.setTitle(downloadService.getString(R.string.button_bar_bookmarks))
					.setMediaId(BROWSER_BOOKMARKS);
			mediaItems.add(new MediaBrowserCompat.MediaItem(bookmarks.build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
		}

		result.sendResult(mediaItems);
	}

	private void getAlbumLists(Result<List<MediaBrowserCompat.MediaItem>> result) {
		List<Integer> albums = new ArrayList<>();
		albums.add(R.string.main_albums_newest);
		albums.add(R.string.main_albums_random);
		albums.add(R.string.main_albums_starred);
		albums.add(R.string.main_albums_recent);

		List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

		for(Integer id: albums) {
			MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
					.setTitle(downloadService.getResources().getString(id))
					.setMediaId(ALBUM_TYPE_PREFIX + id)
					.build();

			mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
		}

		result.sendResult(mediaItems);
	}
	private void getAlbumList(final Result<List<MediaBrowserCompat.MediaItem>> result, final int id) {
		new SilentServiceTask<MusicDirectory>(downloadService) {
			@Override
			protected MusicDirectory doInBackground(MusicService musicService) throws Throwable {
				String albumListType;
				switch(id) {
					case R.string.main_albums_newest:
						albumListType = "newest";
						break;
					case R.string.main_albums_random:
						albumListType = "random";
						break;
					case R.string.main_albums_starred:
						albumListType = "starred";
						break;
					case R.string.main_albums_recent:
						albumListType = "recent";
						break;
					default:
						albumListType = "newest";
				}

				return musicService.getAlbumList(albumListType, 20, 0, true, downloadService, null);
			}

			@Override
			protected void done(MusicDirectory albumSet) {
				List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

				for(MusicDirectory.Entry album: albumSet.getChildren(true, false)) {
					MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
							.setTitle(album.getAlbumDisplay())
							.setSubtitle(album.getArtist())
							.setMediaId(MUSIC_DIRECTORY_PREFIX + album.getId())
							.build();

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
				}

				result.sendResult(mediaItems);
			}
		}.execute();

		result.detach();
	}

	private void getLibrary(final Result<List<MediaBrowserCompat.MediaItem>> result) {
		new SilentServiceTask<List<MusicFolder>>(downloadService) {
			@Override
			protected List<MusicFolder> doInBackground(MusicService musicService) throws Throwable {
				return musicService.getMusicFolders(false, downloadService, null);
			}

			@Override
			protected void done(List<MusicFolder> folders) {
				List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

				for(MusicFolder folder: folders) {
					MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
							.setTitle(folder.getName())
							.setMediaId(MUSIC_FOLDER_PREFIX + folder.getId())
							.build();

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
				}

				result.sendResult(mediaItems);
			}
		}.execute();

		result.detach();
	}
	private void getIndexes(final Result<List<MediaBrowserCompat.MediaItem>> result, final String musicFolderId) {
		new SilentServiceTask<Indexes>(downloadService) {
			@Override
			protected Indexes doInBackground(MusicService musicService) throws Throwable {
				return musicService.getIndexes(musicFolderId, false, downloadService, null);
			}

			@Override
			protected void done(Indexes indexes) {
				List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

				// music directories
				for(Artist artist : indexes.getArtists()) {
					MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
							.setTitle(artist.getName())
							.setMediaId(MUSIC_DIRECTORY_CONTENTS_PREFIX + artist.getId())
							.build();

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
				}

				// music files
				for(MusicDirectory.Entry entry: indexes.getEntries()) {
					MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
							.setTitle(entry.getTitle())
							.setMediaId(MUSIC_DIRECTORY_PREFIX + entry.getId())
							.build();

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
				}

				result.sendResult(mediaItems);
			}
		}.execute();

		result.detach();
	}

	private void getMusicDirectory(final Result<List<MediaBrowserCompat.MediaItem>> result, final String musicDirectoryId) {
		new SilentServiceTask<MusicDirectory>(downloadService) {
			@Override
			protected MusicDirectory doInBackground(MusicService musicService) throws Throwable {
				return musicService.getMusicDirectory(musicDirectoryId, "", false, downloadService, null);
			}

			@Override
			protected void done(MusicDirectory directory) {
				List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

				addPlayOptions(mediaItems, musicDirectoryId, Constants.INTENT_EXTRA_NAME_ID);

				for(MusicDirectory.Entry entry : directory.getChildren()) {
					MediaDescriptionCompat description;
					if (entry.isDirectory()) {
						// browse deeper
						description = new MediaDescriptionCompat.Builder()
								.setTitle(entry.getTitle())
								.setSubtitle("Chapter "+ entry.getTrack())
								.setMediaId(MUSIC_DIRECTORY_CONTENTS_PREFIX + entry.getId())
								.build();
					} else {
						// playback options for a single item
						description = new MediaDescriptionCompat.Builder()
								.setTitle(entry.getTitle())
								.setSubtitle("Chapter "+ entry.getTrack())
								.setMediaId(MUSIC_DIRECTORY_PREFIX + entry.getId())
								.build();
					}

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
				}
				result.sendResult(mediaItems);
			}
		}.execute();

		result.detach();
	}

	private void getBookmarks(final Result<List<MediaBrowserCompat.MediaItem>> result) {
		new SilentServiceTask<MusicDirectory>(downloadService) {
			@Override
			protected MusicDirectory doInBackground(MusicService musicService) throws Throwable {
				return musicService.getBookmarks(false, downloadService, null);
			}

			@Override
			protected void done(MusicDirectory bookmarkList) {
				List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

				for(MusicDirectory.Entry entry: bookmarkList.getChildren(false, true)) {
					Bundle extras = new Bundle();
					extras.putString(Constants.INTENT_EXTRA_NAME_PARENT_ID, entry.getParent());

					MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
							.setTitle(entry.getTitle())
							.setSubtitle("Chapter "+ entry.getTrack() + " - " + Util.formatDuration(entry.getBookmark().getPosition() / 1000))
							.setMediaId(MUSIC_DIRECTORY_PREFIX + entry.getId())
							.setExtras(extras)
							.build();

					mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
				}

				result.sendResult(mediaItems);
			}
		}.execute();

		result.detach();
	}
	private void addPlayOptions(List<MediaBrowserCompat.MediaItem> mediaItems, String id, String idConstant) {
		Bundle playAllExtras = new Bundle();
		playAllExtras.putString(idConstant, id);

		MediaDescriptionCompat.Builder playAll = new MediaDescriptionCompat.Builder();
		playAll.setTitle(downloadService.getString(R.string.menu_play))
				.setMediaId("play-" + id)
				.setExtras(playAllExtras);
		mediaItems.add(new MediaBrowserCompat.MediaItem(playAll.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
	}

	private void getPlayOptions(Result<List<MediaBrowserCompat.MediaItem>> result, String id, String idConstant) {
		List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

		addPlayOptions(mediaItems, id, idConstant);

		result.sendResult(mediaItems);
	}

	public void getDownloadService() {
		if(DownloadService.getInstance() == null) {
			startService(new Intent(this, DownloadService.class));
		}

		waitForDownloadService();
	}
	public void waitForDownloadService() {
		downloadService = DownloadService.getInstance();
		if(downloadService == null) {
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					waitForDownloadService();
				}
			}, 100);
		} else {
			RemoteControlClientLP remoteControlClient = (RemoteControlClientLP) downloadService.getRemoteControlClient();
			setSessionToken(remoteControlClient.getMediaSession().getSessionToken());
		}
	}
}
