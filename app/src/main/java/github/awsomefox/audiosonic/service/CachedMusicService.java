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
package github.awsomefox.audiosonic.service;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import github.awsomefox.audiosonic.domain.Artist;
import github.awsomefox.audiosonic.domain.ArtistInfo;
import github.awsomefox.audiosonic.domain.Bookmark;
import github.awsomefox.audiosonic.domain.Genre;
import github.awsomefox.audiosonic.domain.Indexes;
import github.awsomefox.audiosonic.domain.Lyrics;
import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.domain.MusicFolder;
import github.awsomefox.audiosonic.domain.PlayerQueue;
import github.awsomefox.audiosonic.domain.SearchCritera;
import github.awsomefox.audiosonic.domain.SearchResult;
import github.awsomefox.audiosonic.domain.User;
import github.awsomefox.audiosonic.util.FileUtil;
import github.awsomefox.audiosonic.util.ProgressListener;
import github.awsomefox.audiosonic.util.SilentBackgroundTask;
import github.awsomefox.audiosonic.util.SongDBHandler;
import github.awsomefox.audiosonic.util.SyncUtil;
import github.awsomefox.audiosonic.util.TimeLimitedCache;
import github.awsomefox.audiosonic.util.Util;

/**
 * @author Sindre Mehus
 */
public class CachedMusicService implements MusicService {
	private static final String TAG = CachedMusicService.class.getSimpleName();

    private static final int MUSIC_DIR_CACHE_SIZE = 20;
    private static final int TTL_MUSIC_DIR = 5 * 60; // Five minutes
	public static final int CACHE_UPDATE_LIST = 1;
	public static final int CACHE_UPDATE_METADATA = 2;
	private static final int CACHED_LAST_FM = 24 * 60;

	private final RESTMusicService musicService;
    private final TimeLimitedCache<Boolean> cachedLicenseValid = new TimeLimitedCache<Boolean>(120, TimeUnit.SECONDS);
    private final TimeLimitedCache<Indexes> cachedIndexes = new TimeLimitedCache<Indexes>(60 * 60, TimeUnit.SECONDS);
    private final TimeLimitedCache<List<MusicFolder>> cachedMusicFolders = new TimeLimitedCache<List<MusicFolder>>(10 * 3600, TimeUnit.SECONDS);
    private String restUrl;
	private String musicFolderId;
	private boolean isTagBrowsing = false;

    public CachedMusicService(RESTMusicService musicService) {
        this.musicService = musicService;
    }

    @Override
    public void ping(Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        musicService.ping(context, progressListener);
    }

    @Override
    public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        Boolean result = cachedLicenseValid.get();
        if (result == null) {
			result = FileUtil.deserialize(context, getCacheName(context, "license"), Boolean.class);

			if(result == null) {
            	result = musicService.isLicenseValid(context, progressListener);

				// Only save a copy license is valid
				if(result) {
					FileUtil.serialize(context, result, getCacheName(context, "license"));
				}
			}
            cachedLicenseValid.set(result, result ? 30L * 60L : 2L * 60L, TimeUnit.SECONDS);
        }
        return result;
    }

    @Override
    public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedMusicFolders.clear();
        }
        List<MusicFolder> result = cachedMusicFolders.get();
        if (result == null) {
        	if(!refresh) {
        		result = FileUtil.deserialize(context, getCacheName(context, "musicFolders"), ArrayList.class);
        	}

        	if(result == null) {
            	result = musicService.getMusicFolders(refresh, context, progressListener);
            	FileUtil.serialize(context, new ArrayList<MusicFolder>(result), getCacheName(context, "musicFolders"));
        	}

			MusicFolder.sort(result);
            cachedMusicFolders.set(result);
        }
        return result;
    }

	@Override
	public void startRescan(Context context, ProgressListener listener) throws Exception {
		musicService.startRescan(context, listener);
	}

	@Override
    public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
        checkSettingsChanged(context);
        if (refresh) {
            cachedIndexes.clear();
            cachedMusicFolders.clear();
        }
        Indexes result = cachedIndexes.get();
        if (result == null) {
			String name = Util.isTagBrowsing(context, musicService.getInstance(context)) ? "artists" : "indexes";
			name = getCacheName(context, name, musicFolderId);
			if(!refresh) {
				result = FileUtil.deserialize(context, name, Indexes.class);
			}

        	if(result == null) {
            	result = musicService.getIndexes(musicFolderId, refresh, context, progressListener);
            	FileUtil.serialize(context, result, name);
        	}
            cachedIndexes.set(result);
        }
        return result;
    }

    @Override
    public MusicDirectory getMusicDirectory(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;
		final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "directory", id), MusicDirectory.class);
		if(!refresh && cached != null) {
			dir = cached;

			new SilentBackgroundTask<Void>(context) {
				MusicDirectory refreshed;
				private boolean metadataUpdated;

				@Override
				protected Void doInBackground() throws Throwable {
					refreshed = musicService.getMusicDirectory(id, name, true, context, null);
					updateAllSongs(context, refreshed);

					metadataUpdated = cached.updateMetadata(refreshed);
					deleteRemovedEntries(context, refreshed, cached);
					FileUtil.serialize(context, refreshed, getCacheName(context, "directory", id));
					return null;
				}

				// Update which entries exist
				@Override
				public void done(Void result) {
					if(progressListener != null) {
						if(cached.updateEntriesList(context, musicService.getInstance(context), refreshed)) {

							progressListener.updateCache(CACHE_UPDATE_LIST);
						}
						if(metadataUpdated) {
							progressListener.updateCache(CACHE_UPDATE_METADATA);
						}
					}
				}

				@Override
				public void error(Throwable error) {
					Log.e(TAG, "Failed to refresh music directory", error);
				}
			}.execute();
		}

		if(dir == null) {
			dir = musicService.getMusicDirectory(id, name, refresh, context, progressListener);
			updateAllSongs(context, dir);
			FileUtil.serialize(context, dir, getCacheName(context, "directory", id));


			// If a cached copy exists to check against, look for removes
			deleteRemovedEntries(context, dir, cached);
		}
		dir.sortChildren(context, musicService.getInstance(context));

		return dir;
    }

	@Override
	public MusicDirectory getArtist(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;
		final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "artist", id), MusicDirectory.class);
		if(!refresh && cached != null) {
			dir = cached;

			new SilentBackgroundTask<Void>(context) {
				MusicDirectory refreshed;

				@Override
				protected Void doInBackground() throws Throwable {
					refreshed = musicService.getArtist(id, name, refresh, context, null);
					cached.updateMetadata(refreshed);
					deleteRemovedEntries(context, refreshed, cached);
					FileUtil.serialize(context, refreshed, getCacheName(context, "artist", id));
					return null;
				}

				// Update which entries exist
				@Override
				public void done(Void result) {
					if(progressListener != null) {
						if(cached.updateEntriesList(context, musicService.getInstance(context), refreshed)) {

							progressListener.updateCache(CACHE_UPDATE_LIST);
						}
					}
				}

				@Override
				public void error(Throwable error) {
					Log.e(TAG, "Failed to refresh getArtist", error);
				}
			}.execute();
		}

		if(dir == null) {
			dir = musicService.getArtist(id, name, refresh, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "artist", id));

			// If a cached copy exists to check against, look for removes
			deleteRemovedEntries(context, dir, cached);
		}
		dir.sortChildren(context, musicService.getInstance(context));

		return dir;
	}

	@Override
	public MusicDirectory getAlbum(final String id, final String name, final boolean refresh, final Context context, final ProgressListener progressListener) throws Exception {
		MusicDirectory dir = null;
		final MusicDirectory cached = FileUtil.deserialize(context, getCacheName(context, "album", id), MusicDirectory.class);
		if(!refresh && cached != null) {
			dir = cached;

			new SilentBackgroundTask<Void>(context) {
				MusicDirectory refreshed;
				private boolean metadataUpdated;

				@Override
				protected Void doInBackground() throws Throwable {
					refreshed = musicService.getAlbum(id, name, refresh, context, null);
					updateAllSongs(context, refreshed);

					metadataUpdated = cached.updateMetadata(refreshed);
					deleteRemovedEntries(context, refreshed, cached);
					FileUtil.serialize(context, refreshed, getCacheName(context, "album", id));
					return null;
				}

				// Update which entries exist
				@Override
				public void done(Void result) {
					if(progressListener != null) {
						if(cached.updateEntriesList(context, musicService.getInstance(context), refreshed)) {

							progressListener.updateCache(CACHE_UPDATE_LIST);
						}
						if(metadataUpdated) {
							progressListener.updateCache(CACHE_UPDATE_METADATA);
						}
					}
				}

				@Override
				public void error(Throwable error) {
					Log.e(TAG, "Failed to refresh getAlbum", error);
				}
			}.execute();
		}

		if(dir == null) {
			dir = musicService.getAlbum(id, name, refresh, context, progressListener);
			updateAllSongs(context, dir);
			FileUtil.serialize(context, dir, getCacheName(context, "album", id));

			// If a cached copy exists to check against, look for removes
			deleteRemovedEntries(context, dir, cached);
		}
		dir.sortChildren(context, musicService.getInstance(context));

		return dir;
	}

	@Override
    public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
        return musicService.search(criteria, context, progressListener);
    }

    @Override
    public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getLyrics(artist, title, context, progressListener);
    }

    @Override
    public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception {
        musicService.scrobble(id, submission, context, progressListener);
    }

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		try {
			MusicDirectory dir = musicService.getAlbumList(type, size, offset, refresh, context, progressListener);

			// Do some serialization updates for changes to recently added
			if ("newest".equals(type) && offset == 0) {
				String recentlyAddedFile = getCacheName(context, type);
				ArrayList<String> recents = FileUtil.deserialize(context, recentlyAddedFile, ArrayList.class);
				if (recents == null) {
					recents = new ArrayList<String>();
				}

				// Add any new items
				final int instance = musicService.getInstance(context);
				isTagBrowsing = Util.isTagBrowsing(context, instance);
				for (final MusicDirectory.Entry album : dir.getChildren()) {
					if (!recents.contains(album.getId())) {
						recents.add(album.getId());

						String cacheName, parent;
						if (isTagBrowsing) {
							cacheName = "artist";
							parent = album.getArtistId();
						} else {
							cacheName = "directory";
							parent = album.getParent();
						}

						// Add album to artist
						if (parent != null) {
							new MusicDirectoryUpdater(context, cacheName, parent) {
								private boolean changed = false;

								@Override
								public boolean checkResult(MusicDirectory.Entry check) {
									return true;
								}

								@Override
								public void updateResult(List<MusicDirectory.Entry> objects, MusicDirectory.Entry result) {
									// Only add if it doesn't already exist in it!
									if (!objects.contains(album)) {
										objects.add(album);
										changed = true;
									}
								}

								@Override
								public void save(ArrayList<MusicDirectory.Entry> objects) {
									// Only save if actually added to artist
									if (changed) {
										musicDirectory.replaceChildren(objects);
										FileUtil.serialize(context, musicDirectory, cacheName);
									}
								}
							}.execute();
						} else {
							// If parent is null, then this is a root level album
							final Artist artist = new Artist();
							artist.setId(album.getId());
							artist.setName(album.getTitle());

							new IndexesUpdater(context, isTagBrowsing ? "artists" : "indexes") {
								private boolean changed = false;

								@Override
								public boolean checkResult(Artist check) {
									return true;
								}

								@Override
								public void updateResult(List<Artist> objects, Artist result) {
									if (!objects.contains(artist)) {
										objects.add(artist);
										changed = true;
									}
								}

								@Override
								public void save(ArrayList<Artist> objects) {
									if (changed) {
										indexes.setArtists(objects);
										FileUtil.serialize(context, indexes, cacheName);
										cachedIndexes.set(indexes);
									}
								}
							}.execute();
						}
					}
				}

				// Keep list from growing into infinity
				while (recents.size() > 0) {
					recents.remove(0);
				}
				FileUtil.serialize(context, recents, recentlyAddedFile);
			}

			FileUtil.serialize(context, dir, getCacheName(context, type, Integer.toString(offset)));
			return dir;
		} catch(IOException e) {
			Log.w(TAG, "Failed to refresh album list: ", e);
			if(refresh) {
				throw e;
			}

			MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, type, Integer.toString(offset)), MusicDirectory.class);

			if(dir == null) {
				// If we are at start and no cache, throw error higher
				if(offset == 0) {
					throw e;
				} else {
					// Otherwise just pretend we are at the end of the list
					return new MusicDirectory();
				}
			} else {
				return dir;
			}
		}
	}

	@Override
	public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		try {
			MusicDirectory dir = musicService.getAlbumList(type, extra, size, offset, refresh, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, type + extra, Integer.toString(offset)));
			return dir;
		} catch(IOException e) {
			Log.w(TAG, "Failed to refresh album list: ", e);
			if(refresh) {
				throw e;
			}

			MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, type + extra, Integer.toString(offset)), MusicDirectory.class);

			if(dir == null) {
				// If we are at start and no cache, throw error higher
				if(offset == 0) {
					throw e;
				} else {
					// Otherwise just pretend we are at the end of the list
					return new MusicDirectory();
				}
			} else {
				return dir;
			}
		}
	}

	@Override
	public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getSongList(type, size, offset, context, progressListener);
	}

	@Override
	public MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception {
		return musicService.getRandomSongs(size, artistId, context, progressListener);
	}

	@Override
    public MusicDirectory getStarredList(Context context, ProgressListener progressListener) throws Exception {
		try {
			MusicDirectory dir = musicService.getStarredList(context, progressListener);

			MusicDirectory oldDir = FileUtil.deserialize(context, "starred", MusicDirectory.class);
			if (oldDir != null) {
				final List<MusicDirectory.Entry> newList = new ArrayList<MusicDirectory.Entry>();
				newList.addAll(dir.getChildren());
				final List<MusicDirectory.Entry> oldList = oldDir.getChildren();

				for (Iterator<MusicDirectory.Entry> it = oldList.iterator(); it.hasNext(); ) {
					MusicDirectory.Entry oldEntry = it.next();

					// Remove entries from newList
					if (newList.remove(oldEntry)) {
						// If it was removed, then remove it from old list as well
						it.remove();
					} else {
						oldEntry.setStarred(false);
					}
				}

				List<MusicDirectory.Entry> totalList = new ArrayList<MusicDirectory.Entry>();
				totalList.addAll(oldList);
				totalList.addAll(newList);

				new StarUpdater(context, totalList).execute();
			}
			FileUtil.serialize(context, dir, "starred");

			return dir;
		} catch(IOException e) {
			MusicDirectory dir = FileUtil.deserialize(context, "starred", MusicDirectory.class);
			if(dir == null) {
				throw e;
			} else {
				return dir;
			}
		}
    }

    @Override
    public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
        return musicService.getRandomSongs(size, folder, genre, startYear, endYear, context, progressListener);
    }

	@Override
	public String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception {
		return musicService.getCoverArtUrl(context, entry);
	}

	@Override
    public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		Bitmap bitmap = FileUtil.getAlbumArtBitmap(context, entry, size);
		if (bitmap != null) {
			return bitmap;
		} else {
			return musicService.getCoverArt(context, entry, size, progressListener, task);
		}
    }

    @Override
    public HttpURLConnection getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {
        return musicService.getDownloadInputStream(context, song, offset, maxBitrate, task);
    }

	@Override
	public String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception {
		return musicService.getMusicUrl(context, song, maxBitrate);
	}

	@Override
    public String getVideoUrl(int maxBitrate, Context context, String id) {
        return musicService.getVideoUrl(maxBitrate, context, id);
    }
	
	@Override
    public String getVideoStreamUrl(String format, int maxBitrate, Context context, String id) throws Exception {
        return musicService.getVideoStreamUrl(format, maxBitrate, context, id);
    }
	
	@Override
	public String getHlsUrl(String id, int bitRate, Context context) throws Exception {
		return musicService.getHlsUrl(id, bitRate, context);
	}
    
	@Override
	public void setStarred(List<MusicDirectory.Entry> entries, List<MusicDirectory.Entry> artists, List<MusicDirectory.Entry> albums, final boolean starred, ProgressListener progressListener, Context context) throws Exception {
		musicService.setStarred(entries, artists, albums, starred, progressListener, context);

		// Fuzzy logic to update parents serialization
		List<MusicDirectory.Entry> allEntries = new ArrayList<MusicDirectory.Entry>();
		if(artists != null) {
			allEntries.addAll(artists);
		}
		if(albums != null) {
			allEntries.addAll(albums);
		}
		if (entries != null) {
			allEntries.addAll(entries);
		}

		new StarUpdater(context, allEntries).execute();
	}
	
	@Override
	public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		List<Genre> result = null;

		if(!refresh) {
			result = FileUtil.deserialize(context, getCacheName(context, "genre"), ArrayList.class);
		}

		if(result == null) {
			result = musicService.getGenres(refresh, context, progressListener);
			FileUtil.serialize(context, new ArrayList<Genre>(result), getCacheName(context, "genre"));
		}

		return result;
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
		try {
			MusicDirectory dir = musicService.getSongsByGenre(genre, count, offset, context, progressListener);
			FileUtil.serialize(context, dir, getCacheName(context, "genreSongs", Integer.toString(offset)));

			return dir;
		} catch(IOException e) {
			MusicDirectory dir = FileUtil.deserialize(context, getCacheName(context, "genreSongs", Integer.toString(offset)), MusicDirectory.class);

			if(dir == null) {
				// If we are at start and no cache, throw error higher
				if(offset == 0) {
					throw e;
				} else {
					// Otherwise just pretend we are at the end of the list
					return new MusicDirectory();
				}
			} else {
				return dir;
			}
		}
	}

	@Override
	public MusicDirectory getBookmarks(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		MusicDirectory bookmarks = musicService.getBookmarks(refresh, context, progressListener);
		
		MusicDirectory oldBookmarks = FileUtil.deserialize(context, "bookmarks", MusicDirectory.class);
		if(oldBookmarks != null) {
			final List<MusicDirectory.Entry> oldList = oldBookmarks.getChildren();
			final List<MusicDirectory.Entry> newList = new ArrayList<MusicDirectory.Entry>();
			newList.addAll(bookmarks.getChildren());

			for(Iterator<MusicDirectory.Entry> it = oldList.iterator(); it.hasNext(); ) {
				MusicDirectory.Entry oldEntry = it.next();
				// Remove entries from newList
				int position = newList.indexOf(oldEntry);
				if(position != -1) {
					MusicDirectory.Entry newEntry = newList.get(position);
					if(newEntry.getBookmark().getPosition() == oldEntry.getBookmark().getPosition()) {
						newList.remove(position);
					}

					// Remove from old regardless of whether position is wrong
					it.remove();
				} else {
					oldEntry.setBookmark(null);
				}
			}

			List<MusicDirectory.Entry> totalList = new ArrayList<MusicDirectory.Entry>();
			totalList.addAll(oldList);
			totalList.addAll(newList);

			new BookmarkUpdater(context, totalList).execute();
		}
		FileUtil.serialize(context, bookmarks, "bookmarks");
		
		return bookmarks;
	}

	@Override
	public void createBookmark(MusicDirectory.Entry entry, int position, String comment, Context context, ProgressListener progressListener) throws Exception {
		musicService.createBookmark(entry, position, comment, context, progressListener);

		new BookmarkUpdater(context, entry).execute();
	}

	@Override
	public void deleteBookmark(MusicDirectory.Entry entry, Context context, ProgressListener progressListener) throws Exception {
		musicService.deleteBookmark(entry, context, progressListener);

		new BookmarkUpdater(context, entry).execute();
	}

	@Override
	public User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception {
		User result = null;

		try {
			result = musicService.getUser(refresh, username, context, progressListener);
			FileUtil.serialize(context, result, getCacheName(context, "user-" + username));
		} catch(Exception e) {
			// Don't care
		}
		
		if(result == null && !refresh) {
			result = FileUtil.deserialize(context, getCacheName(context, "user-" + username), User.class);
		}

		return result;
	}

	@Override
	public List<User> getUsers(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		List<User> result = null;

		if(!refresh) {
			result = FileUtil.deserialize(context, getCacheName(context, "users"), ArrayList.class);
		}

		if(result == null) {
			result = musicService.getUsers(refresh, context, progressListener);
			FileUtil.serialize(context, new ArrayList<User>(result), getCacheName(context, "users"));
		}

		return result;
	}

	@Override
	public void createUser(final User user, Context context, ProgressListener progressListener) throws Exception {
		musicService.createUser(user, context, progressListener);
		
		new UserUpdater(context, "") {
			@Override
			public boolean checkResult(User check) {
				return true;
			}
			
			@Override
			public void updateResult(List<User> users, User result) {
				users.add(user);
			}
		}.execute();
	}

	@Override
	public void updateUser(final User user, Context context, ProgressListener progressListener) throws Exception {
		musicService.updateUser(user, context, progressListener);

		new UserUpdater(context, user.getUsername()) {
			@Override
			public void updateResult(List<User> users, User result) {
				result.setEmail(user.getEmail());
				result.setSettings(user.getSettings());
			}
		}.execute();
	}

	@Override
	public void deleteUser(String username, Context context, ProgressListener progressListener) throws Exception {
		musicService.deleteUser(username, context, progressListener);

		new UserUpdater(context, username) {
			@Override
			public void updateResult(List<User> users, User result) {
				users.remove(result);
			}
		}.execute();
	}

	@Override
	public void changeEmail(String username, final String email, Context context, ProgressListener progressListener) throws Exception {
		musicService.changeEmail(username, email, context, progressListener);
		
		// Update cached email for user
		new UserUpdater(context, username) {
			@Override
			public void updateResult(List<User> users, User result) {
				result.setEmail(email);
			}
		}.execute();
	}

	@Override
	public void changePassword(String username, String password, Context context, ProgressListener progressListener) throws Exception {
		musicService.changePassword(username, password, context, progressListener);
	}

	@Override
	public Bitmap getAvatar(String username, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		Bitmap bitmap = FileUtil.getAvatarBitmap(context, username, size);
		if(bitmap != null) {
			return bitmap;
		} else {
			return musicService.getAvatar(username, size, context, progressListener, task);
		}
	}

	@Override
	public ArtistInfo getArtistInfo(String id, boolean refresh, boolean allowNetwork, Context context, ProgressListener progressListener) throws Exception {
		String cacheName = getCacheName(context, "artistInfo", id);
		ArtistInfo info = null;
		if(!refresh) {
			info = FileUtil.deserialize(context, cacheName, ArtistInfo.class, CACHED_LAST_FM);
		}

		if(info == null && allowNetwork) {
			try {
				info = musicService.getArtistInfo(id, refresh, allowNetwork, context, progressListener);
				FileUtil.serialize(context, info, cacheName);
			} catch(Exception e) {
				Log.w(TAG, "Failed to refresh Artist Info");
				info = FileUtil.deserialize(context, cacheName, ArtistInfo.class);

				// Nothing ever cached, throw error further upstream
				if(info == null) {
					throw e;
				}
			}
		}

		return info;
	}

	@Override
	public Bitmap getBitmap(String url, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		Bitmap bitmap = FileUtil.getMiscBitmap(context, url, size);
		if(bitmap != null) {
			return bitmap;
		} else {
			return musicService.getBitmap(url, size, context, progressListener, task);
		}
	}

	@Override
	public MusicDirectory getVideos(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		try {
			MusicDirectory dir = musicService.getVideos(refresh, context, progressListener);
			FileUtil.serialize(context, dir, "videos");

			return dir;
		} catch(IOException e) {
			MusicDirectory dir = FileUtil.deserialize(context, "videos", MusicDirectory.class);
			if(dir == null) {
				throw e;
			} else {
				return dir;
			}
		}
	}

	@Override
	public void savePlayQueue(List<MusicDirectory.Entry> songs, MusicDirectory.Entry currentPlaying, int position, Context context, ProgressListener progressListener) throws Exception {
		musicService.savePlayQueue(songs, currentPlaying, position, context, progressListener);
	}

	@Override
	public PlayerQueue getPlayQueue(Context context, ProgressListener progressListener) throws Exception {
		return musicService.getPlayQueue(context, progressListener);
	}

	@Override
	public int processOfflineSyncs(final Context context, final ProgressListener progressListener) throws Exception{
		return musicService.processOfflineSyncs(context, progressListener);
	}
	
	@Override
    public void setInstance(Integer instance) throws Exception {
    	musicService.setInstance(instance);
    }
  
  	private String getCacheName(Context context, String name, String id) {
  		String s = musicService.getRestUrl(context, null, false) + id;
  		return name + "-" + s.hashCode() + ".ser";
  	}
  	private String getCacheName(Context context, String name) {
  		String s = musicService.getRestUrl(context, null, false);
  		return name + "-" + s.hashCode() + ".ser";
  	}

	private void deleteRemovedEntries(Context context, MusicDirectory dir, MusicDirectory cached) {
		if(cached != null) {
			List<MusicDirectory.Entry> oldList = new ArrayList<MusicDirectory.Entry>();
			oldList.addAll(cached.getChildren());

			// Remove all current items from old list
			for(MusicDirectory.Entry entry: dir.getChildren()) {
				oldList.remove(entry);
			}

			// Anything remaining has been removed from server
			MediaStoreService store = new MediaStoreService(context);
			for(MusicDirectory.Entry entry: oldList) {
				File file = FileUtil.getEntryFile(context, entry);
				FileUtil.recursiveDelete(file, store);
			}
		}
	}
  	
  	private abstract class SerializeUpdater<T> {
  		final Context context;
  		final String cacheName;
  		final boolean singleUpdate;
  		
  		public SerializeUpdater(Context context, String cacheName) {
  			this(context, cacheName, true);
  		}
  		public SerializeUpdater(Context context, String cacheName, boolean singleUpdate) {
  			this.context = context;
  			this.cacheName = getCacheName(context, cacheName);
  			this.singleUpdate = singleUpdate;
  		}
  		public SerializeUpdater(Context context, String cacheName, String id) {
  			this(context, cacheName, id, true);
  		}
		public SerializeUpdater(Context context, String cacheName, String id, boolean singleUpdate) {
			this.context = context;
			this.cacheName = getCacheName(context, cacheName, id);
			this.singleUpdate = singleUpdate;
		}

		public ArrayList<T> getArrayList() {
			return FileUtil.deserialize(context, cacheName, ArrayList.class);
		}
  		public abstract boolean checkResult(T check);
  		public abstract void updateResult(List<T> objects, T result);
		public void save(ArrayList<T> objects) {
			FileUtil.serialize(context, objects, cacheName);
		}
  		
  		public void execute() {
  			ArrayList<T> objects = getArrayList();
  			
  			// Only execute if something to check against
  			if(objects != null) {
  				List<T> results = new ArrayList<T>();
  				for(T check: objects) {
  					if(checkResult(check)) {
						results.add(check);
  						if(singleUpdate) {
  							break;
  						}
  					}
  				}
  				
  				// Iterate through and update each object matched
  				for(T result: results) {
  					updateResult(objects, result);
  				}
  				
  				// Only reserialize if at least one match was found
  				if(results.size() > 0) {
  					save(objects);
  				}
  			}
  		}
  	}
  	private abstract class UserUpdater extends SerializeUpdater<User> {
  		String username;
  		
  		public UserUpdater(Context context, String username) {
  			super(context, "users");
  			this.username = username;
  		}
  		
  		@Override
  		public boolean checkResult(User check) {
  			return username.equals(check.getUsername());
  		}
  	}

	private abstract class MusicDirectoryUpdater extends SerializeUpdater<MusicDirectory.Entry> {
		protected MusicDirectory musicDirectory;

		public MusicDirectoryUpdater(Context context, String cacheName, String id) {
			super(context, cacheName, id, true);
		}
		public MusicDirectoryUpdater(Context context, String cacheName, String id, boolean singleUpdate) {
			super(context, cacheName, id, singleUpdate);
		}

		@Override
		public ArrayList<MusicDirectory.Entry> getArrayList() {
			musicDirectory = FileUtil.deserialize(context, cacheName, MusicDirectory.class);
			if(musicDirectory != null) {
				return new ArrayList<>(musicDirectory.getChildren());
			} else {
				return null;
			}
		}
		public void save(ArrayList<MusicDirectory.Entry> objects) {
			musicDirectory.replaceChildren(objects);
			FileUtil.serialize(context, musicDirectory, cacheName);
		}
	}
	private abstract class PlaylistDirectoryUpdater {
		Context context;
		
		public PlaylistDirectoryUpdater(Context context) {
			this.context = context;
		}
		
		public abstract boolean checkResult(MusicDirectory.Entry check);
		public abstract void updateResult(MusicDirectory.Entry result);
		
		public void execute() {
		}
	}
	private abstract class GenericEntryUpdater {
		Context context;
		List<MusicDirectory.Entry> entries;
		
		public GenericEntryUpdater(Context context, MusicDirectory.Entry entry) {
			this.context = context;
			this.entries = Arrays.asList(entry);
		}
		public GenericEntryUpdater(Context context, List<MusicDirectory.Entry> entries) {
			this.context = context;
			this.entries = entries;
		}
		
		public boolean checkResult(MusicDirectory.Entry entry, MusicDirectory.Entry check) {
			return entry.getId().equals(check.getId());
		}
		public abstract void updateResult(MusicDirectory.Entry result);
		
		public void execute() {
			String cacheName, parent;
			// Make sure it is up to date
			isTagBrowsing = Util.isTagBrowsing(context, musicService.getInstance(context));
			
			// Run through each entry, trying to update the directory it is in
			final List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();
			for(final MusicDirectory.Entry entry: entries) {
				if(isTagBrowsing) {
					// If starring album, needs to reference artist instead
					if(entry.isDirectory()) {
						if(entry.isAlbum()) {
							cacheName = "artist";
							parent = entry.getArtistId();
						} else {
							cacheName = "artists";
							parent = null;
						}
					} else {
						cacheName = "album";
						parent = entry.getAlbumId();
					}
				} else {
					if(entry.isDirectory() && !entry.isAlbum()) {
						cacheName = "indexes";
						parent = null;
					} else {
						cacheName = "directory";
						parent = entry.getParent();
					}
				}

				// Parent is only null when it is an artist
				if(parent == null) {
					new IndexesUpdater(context, cacheName) {
						@Override
						public boolean checkResult(Artist check) {
							return GenericEntryUpdater.this.checkResult(entry, new MusicDirectory.Entry(check));
						}
						
						@Override
						public void updateResult(List<Artist> objects, Artist result) {
							// Don't try to put anything here, as the Entry update method will not be called since it's a artist!
						}
					}.execute();
				} else {
					new MusicDirectoryUpdater(context, cacheName, parent) {
						@Override
						public boolean checkResult(MusicDirectory.Entry check) {
							return GenericEntryUpdater.this.checkResult(entry, check);
						}
						
						@Override
						public void updateResult(List<MusicDirectory.Entry> objects, MusicDirectory.Entry result) {
							GenericEntryUpdater.this.updateResult(result);
						}
					}.execute();
				}
				
				if(!entry.isDirectory()) {
					songs.add(entry);
				}
			}
			
			// Only run through playlists once and check each song against it
			if(songs.size() > 0) {
				new PlaylistDirectoryUpdater(context) {
					@Override
					public boolean checkResult(MusicDirectory.Entry check) {
						for(MusicDirectory.Entry entry: songs) {
							if(GenericEntryUpdater.this.checkResult(entry, check)) {
								return true;
							}
						}
						
						return false;
					}
					
					@Override
					public void updateResult(MusicDirectory.Entry result) {
						GenericEntryUpdater.this.updateResult(result);
					}
				}.execute();
			}
		}
	}
	private class BookmarkUpdater extends GenericEntryUpdater {
		public BookmarkUpdater(Context context, MusicDirectory.Entry entry) {
			super(context, entry);
		}
		public BookmarkUpdater(Context context, List<MusicDirectory.Entry> entries) {
			super(context, entries);
		}

		@Override
		public boolean checkResult(MusicDirectory.Entry entry, MusicDirectory.Entry check) {
			if(entry.getId().equals(check.getId())) {
				int position;
				if(entry.getBookmark() == null) {
					position = -1;
				} else {
					position = entry.getBookmark().getPosition();
				}

				if(position == -1 && check.getBookmark() != null) {
					check.setBookmark(null);
					return true;
				} else if(position  >= 0 && (check.getBookmark() == null || check.getBookmark().getPosition() != position)) {
					Bookmark bookmark = check.getBookmark();

					// Create one if empty
					if(bookmark == null) {
						bookmark = new Bookmark();
						check.setBookmark(bookmark);
					}

					// Update bookmark position no matter what
					bookmark.setPosition(position);
					return true;
				}
			}

			return false;
		}

		@Override
		public void updateResult(MusicDirectory.Entry result) {

		}
	}
	private class StarUpdater extends GenericEntryUpdater {
		public StarUpdater(Context context, List<MusicDirectory.Entry> entries) {
			super(context, entries);
		}

		@Override
		public boolean checkResult(MusicDirectory.Entry entry, MusicDirectory.Entry check) {
			if (entry.getId().equals(check.getId())) {
				if(entry.isStarred() != check.isStarred()) {
					check.setStarred(entry.isStarred());
					return true;
				}
			}

			return false;
		}

		@Override
		public void updateResult(MusicDirectory.Entry result) {

		}
	}

    private abstract class IndexesUpdater extends SerializeUpdater<Artist> {
		Indexes indexes;

		IndexesUpdater(Context context, String name) {
			super(context, name, Util.getSelectedMusicFolderId(context, musicService.getInstance(context)));
		}

		@Override
		public ArrayList<Artist> getArrayList() {
			indexes = FileUtil.deserialize(context, cacheName, Indexes.class);
			if(indexes == null) {
				return null;
			}

			ArrayList<Artist> artists = new ArrayList<Artist>();
			artists.addAll(indexes.getArtists());
			artists.addAll(indexes.getShortcuts());
			return artists;
		}

		public void save(ArrayList<Artist> objects) {
			indexes.setArtists(objects);
			FileUtil.serialize(context, indexes, cacheName);
			cachedIndexes.set(indexes);
		}
	}

	protected void updateAllSongs(Context context, MusicDirectory dir) {
		List<MusicDirectory.Entry> songs = dir.getSongs();
		if(!songs.isEmpty()) {
			SongDBHandler.getHandler(context).addSongs(musicService.getInstance(context), songs);
		}
	}

    private void checkSettingsChanged(Context context) {
		int instance = musicService.getInstance(context);
        String newUrl = musicService.getRestUrl(context, null, false);
		boolean newIsTagBrowsing = Util.isTagBrowsing(context, instance);
        if (!Util.equals(newUrl, restUrl) || isTagBrowsing != newIsTagBrowsing) {
            cachedMusicFolders.clear();
            cachedLicenseValid.clear();
            cachedIndexes.clear();
            restUrl = newUrl;
			isTagBrowsing = newIsTagBrowsing;
        }

		String newMusicFolderId = Util.getSelectedMusicFolderId(context, instance);
		if(!Util.equals(newMusicFolderId, musicFolderId)) {
			cachedIndexes.clear();
			musicFolderId = newMusicFolderId;
		}
    }

	public RESTMusicService getMusicService() {
		return musicService;
	}
}
