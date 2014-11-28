package org.mtransit.android.ui.view;

import java.lang.ref.WeakReference;

import org.mtransit.android.R;
import org.mtransit.android.commons.KeyboardUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.ui.MainActivity;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SearchView;

public class MTSearchView extends SearchView implements MTLog.Loggable, View.OnFocusChangeListener, SearchView.OnCloseListener, SearchView.OnQueryTextListener {

	private static final String TAG = MTSearchView.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	public MTSearchView(MainActivity mainActivity, Context context) {
		super(context);
		init(mainActivity);
	}

	public MTSearchView(MainActivity mainActivity, Context context, AttributeSet attrs) {
		super(context, attrs);
		init(mainActivity);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public MTSearchView(MainActivity mainActivity, Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(mainActivity);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public MTSearchView(MainActivity mainActivity, Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(mainActivity);
	}

	private WeakReference<MainActivity> mainActivityWR;

	private void init(MainActivity mainActivity) {
		this.mainActivityWR = new WeakReference<MainActivity>(mainActivity);
		setQueryHint(getContext().getString(R.string.search_hint));
		setIconifiedByDefault(true);
		setOnQueryTextListener(this);
		setOnQueryTextFocusChangeListener(this);
		setOnCloseListener(this);
		setIconified(false);
	}

	@Override
	public boolean onQueryTextChange(String newText) {
		MainActivity mainActivity = this.mainActivityWR == null ? null : this.mainActivityWR.get();
		if (mainActivity != null) {
			mainActivity.onSearchRequested(newText);
		}
		return true; // handled
	}

	@Override
	public boolean onQueryTextSubmit(String query) {
		MainActivity mainActivity = this.mainActivityWR == null ? null : this.mainActivityWR.get();
		if (mainActivity != null) {
			mainActivity.onSearchRequested(query);
			KeyboardUtils.hideKeyboard(mainActivity, this);
		}
		return true; // handled
	}

	@Override
	public void onFocusChange(View v, boolean hasFocus) {
		if (!hasFocus) {
			MainActivity mainActivity = this.mainActivityWR == null ? null : this.mainActivityWR.get();
			if (mainActivity != null) {
				KeyboardUtils.hideKeyboard(mainActivity, this);
			}
		}
	}

	@Override
	public boolean onClose() {
		return true; // do not close
	}

}
