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

import java.net.HttpURLConnection;
import java.util.List;


import android.content.Context;
import android.graphics.Bitmap;

import github.awsomefox.audiosonic.domain.Genre;
import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.domain.SearchCritera;
import github.awsomefox.audiosonic.domain.SearchResult;
import github.awsomefox.audiosonic.domain.User;
import github.awsomefox.audiosonic.domain.ArtistInfo;
import github.awsomefox.audiosonic.domain.Indexes;
import github.awsomefox.audiosonic.domain.PlayerQueue;
import github.awsomefox.audiosonic.domain.Lyrics;
import github.awsomefox.audiosonic.domain.MusicFolder;
import github.awsomefox.audiosonic.util.SilentBackgroundTask;
import github.awsomefox.audiosonic.util.ProgressListener;

/**
 * @author Sindre Mehus
 */
public interface MusicService {

    void ping(Context context, ProgressListener progressListener) throws Exception;

    boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception;

    List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void startRescan(Context context, ProgressListener listener) throws Exception;

    Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

    SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getStarredList(Context context, ProgressListener progressListener) throws Exception;

    Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception;

    void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception;

    MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception;
    MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception;

	String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception;

    Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception;

    HttpURLConnection getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception;

	String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception;

	String getVideoUrl(int maxBitrate, Context context, String id);
	
	String getVideoStreamUrl(String format, int Bitrate, Context context, String id) throws Exception;
	
	String getHlsUrl(String id, int bitRate, Context context) throws Exception;
    
    void setStarred(List<MusicDirectory.Entry> entries, List<MusicDirectory.Entry> artists, List<MusicDirectory.Entry> albums, boolean starred, ProgressListener progressListener, Context context) throws Exception;
	
	List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception;
	
	MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception;

	MusicDirectory getBookmarks(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void createBookmark(MusicDirectory.Entry entry, int position, String comment, Context context, ProgressListener progressListener) throws Exception;

	void deleteBookmark(MusicDirectory.Entry entry, Context context, ProgressListener progressListener) throws Exception;

	User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception;

	List<User> getUsers(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void createUser(User user, Context context, ProgressListener progressListener) throws Exception;

	void updateUser(User user, Context context, ProgressListener progressListener) throws Exception;

	void deleteUser(String username, Context context, ProgressListener progressListener) throws Exception;

	void changeEmail(String username, String email, Context context, ProgressListener progressListener) throws Exception;

	void changePassword(String username, String password, Context context, ProgressListener progressListener) throws Exception;

	Bitmap getAvatar(String username, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception;

	ArtistInfo getArtistInfo(String id, boolean refresh, boolean allowNetwork, Context context, ProgressListener progressListener) throws Exception;

	Bitmap getBitmap(String url, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception;

	MusicDirectory getVideos(boolean refresh, Context context, ProgressListener progressListener) throws Exception;

	void savePlayQueue(List<MusicDirectory.Entry> songs, MusicDirectory.Entry currentPlaying, int position, Context context, ProgressListener progressListener) throws Exception;

	PlayerQueue getPlayQueue(Context context, ProgressListener progressListener) throws Exception;
	
	int processOfflineSyncs(final Context context, final ProgressListener progressListener) throws Exception;
	
	void setInstance(Integer instance) throws Exception;
}
