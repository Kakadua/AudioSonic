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


package github.awsomefox.audiosonic.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.TextView;

import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.util.FileUtil;
import github.awsomefox.audiosonic.util.ImageLoader;
import github.awsomefox.audiosonic.R;
import github.awsomefox.audiosonic.util.Util;

import java.io.File;

public class AlbumView extends UpdateView2<MusicDirectory.Entry, ImageLoader> {
	private static final String TAG = AlbumView.class.getSimpleName();

	private File file;
	private TextView titleView;
	private TextView artistView;
	private boolean showArtist = true;
	private String coverArtId;

	public AlbumView(Context context, boolean cell) {
		super(context);

		if(cell) {
			LayoutInflater.from(context).inflate(R.layout.album_cell_item, this, true);
		} else {
			LayoutInflater.from(context).inflate(R.layout.album_list_item, this, true);
		}

		coverArtView = findViewById(R.id.album_coverart);
		titleView = findViewById(R.id.album_title);
		artistView = findViewById(R.id.album_artist);
		starButton = findViewById(R.id.album_star);
		starButton.setFocusable(false);
		moreButton = findViewById(R.id.item_more);
		cacheButton = findViewById(R.id.song_cached);

		checkable = true;
	}

	public void setShowArtist(boolean showArtist) {
		this.showArtist = showArtist;
	}

	protected void setObjectImpl(MusicDirectory.Entry album, ImageLoader imageLoader) {

        titleView.setText(album.getAlbumDisplay());
		String artist = "";
		if(showArtist) {
			artist = album.getArtist();
			if (artist == null) {
				artist = "";
			}
			if (album.getYear() != null) {
				artist += " - " + album.getYear();
			}
		} else if(album.getYear() != null) {
			artist += album.getYear();
		}
		artistView.setText(album.getArtist() == null ? "" : artist);

		onUpdateImageView();
		file = null;
	}

	public void onUpdateImageView() {
		imageTask = item2.loadImage(coverArtView, item, false, true);
		coverArtId = item.getCoverArt();
	}

	@Override
	protected void updateBackground() {
		if(file == null) {
			file = FileUtil.getAlbumDirectory(context, item);
		}

		exists = file.exists();
		isStarred = item.isStarred();
	}

	@Override
	public void update() {
		super.update();

		if(!Util.equals(item.getCoverArt(), coverArtId)) {
			onUpdateImageView();
		}
	}

	public MusicDirectory.Entry getEntry() {
		return item;
	}

	public File getFile() {
		return file;
	}
}
