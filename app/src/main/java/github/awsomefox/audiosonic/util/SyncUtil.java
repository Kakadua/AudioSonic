package github.awsomefox.audiosonic.util;

import android.content.Context;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Scott on 11/24/13.
 */
public final class SyncUtil {
	private static String TAG = SyncUtil.class.getSimpleName();
	
	// Starred
	public static ArrayList<String> getSyncedStarred(Context context, int instance) {
		ArrayList<String> list = FileUtil.deserializeCompressed(context, getStarredSyncFile(context, instance), ArrayList.class);
		if(list == null) {
			list = new ArrayList<String>();
		}
		return list;
	}
	public static void setSyncedStarred(ArrayList<String> syncedList, Context context, int instance) {
		FileUtil.serializeCompressed(context, syncedList, SyncUtil.getStarredSyncFile(context, instance));
	}
	public static String getStarredSyncFile(Context context, int instance) {
		return "sync-starred-" + (Util.getRestUrl(context, null, instance, false)).hashCode() + ".ser";
	}
	
	// Most Recently Added
	public static ArrayList<String> getSyncedMostRecent(Context context, int instance) {
		ArrayList<String> list = FileUtil.deserialize(context, getMostRecentSyncFile(context, instance), ArrayList.class);
		if(list == null) {
			list = new ArrayList<String>();
		}
		return list;
	}
	public static void removeMostRecentSyncFiles(Context context) {
		int total = Util.getServerCount(context);
		for(int i = 0; i < total; i++) {
			File file = new File(context.getCacheDir(), getMostRecentSyncFile(context, i));
			file.delete();
		}
	}
	public static String getMostRecentSyncFile(Context context, int instance) {
		return "sync-most_recent-" + (Util.getRestUrl(context, null, instance, false)).hashCode() + ".ser";
	}

	public static String joinNames(List<String> names) {
		StringBuilder builder = new StringBuilder();
		for (String val : names) {
			builder.append(val).append(", ");
		}
		builder.setLength(builder.length() - 2);
		return builder.toString();
	}

	public static class SyncSet implements Serializable {
		public String id;
		public List<String> synced;

		protected SyncSet() {

		}
		public SyncSet(String id) {
			this.id = id;
		}
		public SyncSet(String id, List<String> synced) {
			this.id = id;
			this.synced = synced;
		}

		@Override
		public boolean equals(Object obj) {
			if(obj instanceof SyncSet) {
				return this.id.equals(((SyncSet)obj).id);
			} else {
				return false;
			}
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}
	}
}
