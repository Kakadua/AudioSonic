package github.awsomefox.audiosonic.view.compat;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.MediaRouteChooserDialog;
import android.support.v7.app.MediaRouteChooserDialogFragment;

import github.awsomefox.audiosonic.util.ThemeUtil;

public class CustomMediaRouteChooserDialogFragment extends MediaRouteChooserDialogFragment {
	@Override
	public MediaRouteChooserDialog onCreateChooserDialog(Context context, Bundle savedInstanceState) {
		return new MediaRouteChooserDialog(context, ThemeUtil.getThemeRes(context));
	}
}
