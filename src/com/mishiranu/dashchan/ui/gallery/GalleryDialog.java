package com.mishiranu.dashchan.ui.gallery;

import android.app.ActionBar;
import android.app.Dialog;
import android.media.AudioManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.util.ViewUtils;

public class GalleryDialog extends Dialog {
	private final Fragment fragment;
	private View actionBar;

	public GalleryDialog(Fragment fragment) {
		super(fragment.requireContext(), R.style.Theme_Gallery);
		this.fragment = fragment;
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
		ViewUtils.applyToolbarStyle(getWindow(), getActionBarView());
	}

	private boolean actionBarAnimationsFixed = false;

	@Override
	public ActionBar getActionBar() {
		ActionBar actionBar = super.getActionBar();
		if (actionBar != null && !actionBarAnimationsFixed) {
			actionBarAnimationsFixed = true;
			// Action bar animations are enabled only after onStart
			// which is called first time before action bar created
			onStop();
			onStart();
		}
		return actionBar;
	}

	public View getActionBarView() {
		if (actionBar == null) {
			actionBar = getWindow().getDecorView().findViewById(fragment
					.getResources().getIdentifier("action_bar", "id", "android"));
		}
		return actionBar;
	}

	public interface OnFocusChangeListener {
		void onFocusChange(boolean hasFocus);
	}

	private OnFocusChangeListener onFocusChangeListener;

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (onFocusChangeListener != null) {
			onFocusChangeListener.onFocusChange(hasFocus);
		}
	}

	public void setOnFocusChangeListener(OnFocusChangeListener listener) {
		this.onFocusChangeListener = listener;
	}

	@Override
	public void onBackPressed() {
		if (!(fragment instanceof ActivityHandler) || !((ActivityHandler) fragment).onBackPressed()) {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(@NonNull Menu menu) {
		fragment.onCreateOptionsMenu(menu, fragment.requireActivity().getMenuInflater());
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
		fragment.onPrepareOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, @NonNull MenuItem item) {
		if (featureId == Window.FEATURE_OPTIONS_PANEL) {
			return onOptionsItemSelected(item);
		} else {
			return false;
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		return fragment.onOptionsItemSelected(item);
	}
}
