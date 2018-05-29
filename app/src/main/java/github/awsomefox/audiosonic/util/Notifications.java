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
	Copyright 2014 (C) Scott Jackson
*/

package github.awsomefox.audiosonic.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.app.NotificationCompat.MediaStyle;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.KeyEvent;

import github.awsomefox.audiosonic.domain.MusicDirectory;
import github.awsomefox.audiosonic.domain.PlayerState;
import github.awsomefox.audiosonic.provider.DSubWidgetProvider;
import github.awsomefox.audiosonic.service.DownloadFile;
import github.awsomefox.audiosonic.util.compat.RemoteControlClientBase;
import github.awsomefox.audiosonic.util.compat.RemoteControlClientLP;
import github.awsomefox.audiosonic.R;
import github.awsomefox.audiosonic.activity.SubsonicActivity;
import github.awsomefox.audiosonic.activity.SubsonicFragmentActivity;
import github.awsomefox.audiosonic.service.DownloadService;

public final class Notifications {
    private static final String TAG = Notifications.class.getSimpleName();

    // Notification IDs.
    private static final int NOTIFICATION_ID_PLAYING = 100;
    private static final int NOTIFICATION_ID_DOWNLOADING = 102;
    private static final String CHANNEL_ID = "github.awsomefox.audiosonic";

    private static boolean playShowing = false;
    private static boolean downloadShowing = false;
    private static boolean downloadForeground = false;
    private static boolean persistentPlayingShowing = false;

    @RequiresApi(Build.VERSION_CODES.O)
    private static void createChannel(final Context context) {
        NotificationManager
                mNotificationManager =
                (NotificationManager) context
                        .getSystemService(Context.NOTIFICATION_SERVICE);
        // The id of the channel.
        String id = CHANNEL_ID;
        // The user-visible name of the channel.
        CharSequence name = "Media playback";
        // The user-visible description of the channel.
        String description = "Media playback controls";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel mChannel = new NotificationChannel(id, name, importance);
        // Configure the notification channel.
        mChannel.setDescription(description);
        mChannel.setShowBadge(false);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    private static NotificationCompat.Action generateAction(Context context, int icon, String title, String intentTitle, int intentAction ) {
        Intent pauseIntent = new Intent(intentTitle);
        pauseIntent.setComponent(new ComponentName(context, DownloadService.class));

        pauseIntent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, intentAction));
        PendingIntent pendingIntent;
        pendingIntent = PendingIntent.getService(context, 0, pauseIntent, 0);
        return new NotificationCompat.Action.Builder( icon, title, pendingIntent ).build();
    }

    public static void showPlayingNotification(final Context context, final DownloadService downloadService, final Handler handler, MusicDirectory.Entry song) {
        final boolean playing = downloadService.getPlayerState() == PlayerState.STARTED;
        boolean remote = downloadService.isRemoteEnabled();
        boolean shouldFastForward = downloadService.shouldFastForward();
        setupViews(downloadService.getRemoteControlClient(), context, song, false, downloadService.getPlayerState(), remote, shouldFastForward);

        playShowing = true;
        if (downloadForeground && downloadShowing) {
            downloadForeground = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    downloadService.stopForeground(true);
                    showDownloadingNotification(context, downloadService, handler, downloadService.getCurrentDownloading(), downloadService.getBackgroundDownloads().size());
                }
            });
        } else {
            handler.post(new Runnable() {
                @Override
                public void run() {

                    if (!playing) {
                        playShowing = false;
                        persistentPlayingShowing = true;
                        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                        downloadService.stopForeground(false);

                        try {
//                            notificationManager.notify(NOTIFICATION_ID_PLAYING, notification);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start notifications while paused");
                        }
                    }
                }
            });
        }

        // Update widget
        DSubWidgetProvider.notifyInstances(context, downloadService, playing);
    }

    private static void setupViews(RemoteControlClientBase base, Context context, MusicDirectory.Entry song, boolean expanded, PlayerState state, boolean remote, boolean shouldFastForward) {
        // Use the same text for the ticker and the expanded notification
        String title = song.getTitle();
        if(song.getTrack() != null) {
            title = "Chapter " + String.format("%02d", song.getTrack());
        }
        String arist = song.getArtist();
        String album = song.getAlbum();
        Bitmap bitmap = null;
        try {
            ImageLoader imageLoader = SubsonicActivity.getStaticImageLoader(context);

            if (imageLoader != null) {
                bitmap = imageLoader.getCachedImage(context, song, false);
            }
            if (bitmap == null) {
                bitmap  = BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_album);
            }
        } catch (Exception x) {
            bitmap  = BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_album);
            Log.w(TAG, "Failed to get notification cover art", x);
        }
        if(state == PlayerState.STOPPED) { return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context);
        }
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID);
        RemoteControlClientLP remoteControlClient = (RemoteControlClientLP) base;
        Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_VIEW_ALBUM, true);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        builder
                .setStyle(
                        new MediaStyle()
                                .setMediaSession(remoteControlClient.getMediaSession().getSessionToken())
                                .setShowActionsInCompactView(1,2))
                .setSmallIcon(R.drawable.stat_notify_playing)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setContentTitle(title)
                .setContentText(album)
                .setGroup(CHANNEL_ID)
                .setSubText(arist)
                .setLargeIcon(bitmap)
                .setContentIntent(PendingIntent.getActivity(context, 0, notificationIntent, 0));

        builder.addAction( generateAction(context, R.drawable.ic_skip_previous_black_24dp, "Back", "KEYCODE_MEDIA_PREVIOUS", KeyEvent.KEYCODE_MEDIA_PREVIOUS ) );
        builder.addAction( generateAction(context, R.drawable.ic_replay_30_black_24dp, "Skip Back", "KEYCODE_MEDIA_REWIND", KeyEvent.KEYCODE_MEDIA_REWIND ) );
        if (state == PlayerState.STARTED) {
            builder.addAction( generateAction(context, R.drawable.ic_pause_black_24dp, "Pause", "KEYCODE_MEDIA_PLAY_PAUSE", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ) );
            builder.setOngoing(true).setPriority(NotificationManagerCompat.IMPORTANCE_HIGH);
        } else {
            builder.addAction(generateAction(context, R.drawable.ic_play_arrow_black_24dp, "Play", "KEYCODE_MEDIA_PLAY_PAUSE", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
            builder.setOngoing(false).setPriority(NotificationManagerCompat.IMPORTANCE_DEFAULT);
        }
        builder.setDefaults(0);
        builder.addAction( generateAction(context, R.drawable.ic_forward_30_black_24dp, "Skip Forward", "KEYCODE_MEDIA_FAST_FORWARD", KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ) );
        builder.addAction( generateAction(context, R.drawable.ic_skip_next_black_24dp, "Next", "KEYCODE_MEDIA_NEXT", KeyEvent.KEYCODE_MEDIA_NEXT ) );

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify( 1, builder.build() );
    }

    public static void hidePlayingNotification(final Context context, final DownloadService downloadService, Handler handler) {
        playShowing = false;

        // Remove notification and remove the service from the foreground
        handler.post(new Runnable() {
            @Override
            public void run() {
                downloadService.stopForeground(true);

                if (persistentPlayingShowing) {
                    NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(NOTIFICATION_ID_PLAYING);

                    persistentPlayingShowing = false;
                }
            }
        });

        // Get downloadNotification in foreground if playing
        if (downloadShowing) {
            showDownloadingNotification(context, downloadService, handler, downloadService.getCurrentDownloading(), downloadService.getBackgroundDownloads().size());
        }

        // Update widget
        DSubWidgetProvider.notifyInstances(context, downloadService, false);
    }

    public static void showDownloadingNotification(final Context context, final DownloadService downloadService, Handler handler, DownloadFile file, int size) {
        Intent cancelIntent = new Intent(context, DownloadService.class);
        cancelIntent.setAction(DownloadService.CANCEL_DOWNLOADS);
        PendingIntent cancelPI = PendingIntent.getService(context, 0, cancelIntent, 0);

        String currentDownloading, currentSize;
        if (file != null) {
            currentDownloading = file.getSong().getTitle() + " " + file.getSong().getTrack();
            currentSize = Util.formatLocalizedBytes(file.getEstimatedSize(), context);
        } else {
            currentDownloading = "none";
            currentSize = "0";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context);
        }
        NotificationCompat.Builder builder;
        builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(context.getResources().getString(R.string.download_downloading_title, size))
                .setContentText(context.getResources().getString(R.string.download_downloading_summary, currentDownloading))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getResources().getString(R.string.download_downloading_summary_expanded, currentDownloading, currentSize)))
                .setProgress(10, 5, true)
                .setGroup(CHANNEL_ID)
                .setOngoing(true)
                .addAction(R.drawable.notification_close,
                        context.getResources().getString(R.string.common_cancel),
                        cancelPI);

        Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);
        notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_DOWNLOAD_VIEW, true);

        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        builder.setContentIntent(PendingIntent.getActivity(context, 2, notificationIntent, 0));

        final Notification notification = builder.build();
        downloadShowing = true;
        if (playShowing) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(NOTIFICATION_ID_DOWNLOADING, notification);
        } else {
            downloadForeground = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    downloadService.startForeground(NOTIFICATION_ID_DOWNLOADING, notification);
                }
            });
        }

    }

    public static void hideDownloadingNotification(final Context context, final DownloadService downloadService, Handler handler) {
        downloadShowing = false;
        if (playShowing) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(NOTIFICATION_ID_DOWNLOADING);
        } else {
            downloadForeground = false;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    downloadService.stopForeground(true);
                }
            });
        }
    }

    public static void showSyncNotification(final Context context, int stringId, String extra) {
        showSyncNotification(context, stringId, extra, null);
    }

    public static void showSyncNotification(final Context context, int stringId, String extra, String extraId) {
        if (Util.getPreferences(context).getBoolean(Constants.PREFERENCES_KEY_SYNC_NOTIFICATION, true)) {
            if (extra == null) {
                extra = "";
            }

            NotificationCompat.Builder builder;
            builder = new NotificationCompat.Builder(context, "com.mypackage.service")
                    .setSmallIcon(R.drawable.stat_notify_sync)
                    .setContentTitle(context.getResources().getString(stringId))
                    .setContentText(extra)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(extra.replace(", ", "\n")))
                    .setOngoing(false)
                    .setGroup(CHANNEL_ID)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true);

            Intent notificationIntent = new Intent(context, SubsonicFragmentActivity.class);

            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            String tab = null, type = null;
            switch (stringId) {
                case R.string.sync_new_albums:
                    type = "newest";
                    break;
                case R.string.sync_new_starred:
                    type = "starred";
                    break;
            }
            if (tab != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_FRAGMENT_TYPE, tab);
            }
            if (type != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_ALBUM_LIST_TYPE, type);
            }
            if (extraId != null) {
                notificationIntent.putExtra(Constants.INTENT_EXTRA_NAME_ID, extraId);
            }

            builder.setContentIntent(PendingIntent.getActivity(context, stringId, notificationIntent, 0));

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(stringId, builder.build());
        }
    }
}
