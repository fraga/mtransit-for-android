package org.mtransit.android.ui;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import org.mtransit.android.R;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.ui.fragment.ABFragment;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public class ActionBarController implements MTLog.Loggable {

	private static final String TAG = ActionBarController.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}


	private WeakReference<MainActivity> mainActivityWR;

	private CharSequence drawerTitle;
	private CharSequence fragmentTitle;

	private CharSequence drawerSubtitle;
	private CharSequence fragmentSubtitle;

	private int drawerIcon;
	private int fragmentIcon;

	private Integer fragmentBgColor;
	private Integer drawerBgColor;

	private View fragmentCustomView;
	private View drawerCustomView;

	private boolean fragmentThemeDarkInsteadOfThemeLight;

	private boolean fragmentDisplayHomeAsUpEnabled;
	private boolean drawerDisplayHomeAsUpEnabled;

	private boolean fragmentShowSearchMenuItem;


	private UpOnClickListener upOnClickListener;

	public ActionBarController(MainActivity mainActivity) {
		setMainActivity(mainActivity);
		init();
	}

	public void setMainActivity(MainActivity mainActivity) {
		this.mainActivityWR = new WeakReference<MainActivity>(mainActivity);
	}

	private Context getContextOrNull() {
		return getMainActivityOrNull();
	}

	private MainActivity getMainActivityOrNull() {
		return this.mainActivityWR == null ? null : this.mainActivityWR.get();
	}

	private ActionBar getABOrNull() {
		final MainActivity mainActivity = getMainActivityOrNull();
		return mainActivity == null ? null : mainActivity.getActionBar();
	}
	private void init() {
		final MainActivity mainActivity = getMainActivityOrNull();
		if (mainActivity != null) {
			final ActionBar ab = getABOrNull();
			this.fragmentTitle = this.drawerTitle = mainActivity.getTitle();
			this.fragmentSubtitle = this.drawerSubtitle = ab.getSubtitle();
			this.fragmentIcon = this.drawerIcon = R.mipmap.ic_launcher;
			this.fragmentBgColor = this.drawerBgColor = ABFragment.NO_BG_COLOR;
			this.fragmentCustomView = this.drawerCustomView = ABFragment.NO_CUSTOM_VIEW;
			this.fragmentThemeDarkInsteadOfThemeLight = ABFragment.DEFAULT_THEME_DARK_INSTEAD_OF_LIGHT;
			this.fragmentDisplayHomeAsUpEnabled = this.drawerDisplayHomeAsUpEnabled = ABFragment.DEFAULT_DISPLAY_HOME_AS_UP_ENABLED;
			this.fragmentShowSearchMenuItem = ABFragment.DEFAULT_SHOW_SEARCH_MENU_ITEM;
			ab.setDisplayHomeAsUpEnabled(this.fragmentDisplayHomeAsUpEnabled);
			ab.setHomeButtonEnabled(true);
		}
	}

	public void setAB(ABFragment abf) {
		final Context context = getContextOrNull();
		if (context != null) {
			setAB(abf.getABTitle(context), abf.getABSubtitle(context), abf.getABIconDrawableResId(), abf.getABBgColor(), abf.getABCustomView(),
					abf.isABThemeDarkInsteadOfThemeLight(), abf.isABDisplayHomeAsUpEnabled(), abf.isABShowSearchMenuItem());
		}
	}

	private void setAB(CharSequence title, CharSequence subtitle, int iconResId, Integer bgColor, View customView, boolean themeDarkInsteadOfThemeLight,
			boolean displayHomeAsUpEnabled, boolean showSearchMenuItem) {
		fragmentTitle = title;
		fragmentSubtitle = subtitle;
		fragmentIcon = iconResId;
		fragmentBgColor = bgColor;
		fragmentCustomView = customView;
		fragmentThemeDarkInsteadOfThemeLight = themeDarkInsteadOfThemeLight;
		fragmentDisplayHomeAsUpEnabled = displayHomeAsUpEnabled;
		fragmentShowSearchMenuItem = showSearchMenuItem;
	}

	private boolean isCurrentFragmentVisible(Fragment source) {
		final MainActivity mainActivity = getMainActivityOrNull();
		return mainActivity == null ? false : mainActivity.isCurrentFragmentVisible(source);
	}

	private boolean isDrawerOpen() {
		final MainActivity mainActivity = getMainActivityOrNull();
		return mainActivity == null ? false : mainActivity.isDrawerOpen();
	}

	public void setABTitle(Fragment source, CharSequence title, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentTitle = title;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABSubtitle(Fragment source, CharSequence subtitle, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentSubtitle = subtitle;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABIcon(Fragment source, int resId, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentIcon = resId;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABBgColor(Fragment source, int bgColor, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentBgColor = bgColor;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABCustomView(Fragment source, View customView, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentCustomView = customView;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABDisplayHomeAsUpEnabled(Fragment source, boolean displayHomeAsUpEnabled, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentDisplayHomeAsUpEnabled = displayHomeAsUpEnabled;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABThemeDarkInsteadOfThemeLight(Fragment source, boolean themeDarkInsteadOfThemeLight, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentThemeDarkInsteadOfThemeLight = themeDarkInsteadOfThemeLight;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void setABShowSearchMenuItem(Fragment source, boolean showSearchMenuItem, boolean update) {
		if (!isCurrentFragmentVisible(source)) {
			return;
		}
		fragmentShowSearchMenuItem = showSearchMenuItem;
		if (update && !isDrawerOpen()) {
			updateAB();
		}
	}

	public void updateAB() {
		if (isDrawerOpen()) {
			updateABDrawerOpened();
		} else {
			updateABDrawerClosed();
		}
	}


	private void updateABDrawerClosed() {
		final MainActivity mainActivity = getMainActivityOrNull();
		final ActionBar ab = getABOrNull();
		if (fragmentCustomView != null) {
			ab.setCustomView(fragmentCustomView);
			if (!fragmentDisplayHomeAsUpEnabled) {
				ab.getCustomView().setOnClickListener(getUpOnClickListener(getMainActivityOrNull()));
			}
			ab.setDisplayShowCustomEnabled(true);
		} else {
			ab.setDisplayShowCustomEnabled(false);
		}
		ab.setDisplayHomeAsUpEnabled(fragmentDisplayHomeAsUpEnabled);
		if (TextUtils.isEmpty(fragmentTitle)) {
			ab.setDisplayShowTitleEnabled(false);
		} else {
			ab.setTitle(fragmentTitle);
			ab.setSubtitle(fragmentSubtitle);
			ab.setDisplayShowTitleEnabled(true);
		}
		if (fragmentIcon > 0) {
			ab.setIcon(fragmentIcon);
			ab.setDisplayShowHomeEnabled(true);
		} else {
			ab.setDisplayShowHomeEnabled(false);
		}
		if (fragmentBgColor != null) {
			ab.setBackgroundDrawable(new ColorDrawable(fragmentBgColor.intValue()));
			mainActivity.getWindow().setStatusBarColor(fragmentBgColor.intValue());
		} else {
			mainActivity.getWindow().setStatusBarColor(mainActivity.getResources().getColor(R.color.platform_primary_dark_material_light));
			ab.setBackgroundDrawable(null);
		}
		mainActivity.updateNavigationDrawerToggleIndicator();
		updateAllMenuItems(); // action bar icons are options menu items
	}

	private void updateABDrawerOpened() {
		final MainActivity mainActivity = getMainActivityOrNull();
		final ActionBar ab = getABOrNull();
		if (drawerCustomView != null) {
			ab.setCustomView(drawerCustomView);
			if (!drawerDisplayHomeAsUpEnabled) {
				ab.getCustomView().setOnClickListener(getUpOnClickListener(mainActivity));
			}
			ab.setDisplayShowCustomEnabled(true);
		} else {
			ab.setDisplayShowCustomEnabled(false);
		}
		ab.setDisplayHomeAsUpEnabled(drawerDisplayHomeAsUpEnabled);
		if (TextUtils.isEmpty(drawerTitle)) {
			ab.setDisplayShowTitleEnabled(false);
		} else {
			ab.setTitle(drawerTitle);
			ab.setSubtitle(drawerSubtitle);
			ab.setDisplayShowTitleEnabled(true);
		}
		if (drawerIcon > 0) {
			ab.setIcon(drawerIcon);
			ab.setDisplayShowHomeEnabled(true);
		} else {
			ab.setDisplayShowHomeEnabled(false);
		}
		if (drawerBgColor != null) {
			ab.setBackgroundDrawable(new ColorDrawable(drawerBgColor));
		} else {
			ab.setBackgroundDrawable(null);
		}
		mainActivity.enableNavigationDrawerToggleIndicator();
		updateAllMenuItems(); // action bar icons are options menu items
	}

	private UpOnClickListener getUpOnClickListener(MainActivity mainActivity) {
		if (this.upOnClickListener == null) {
			if (mainActivity == null) {
				mainActivity = getMainActivityOrNull();
			}
			if (mainActivity != null) {
				this.upOnClickListener = new UpOnClickListener(mainActivity);
			}
		}
		return this.upOnClickListener;
	}

	private HashMap<Integer, MenuItem> allMenuItems = new HashMap<Integer, MenuItem>();

	public void addMenuItem(int resId, MenuItem menuItem) {
		this.allMenuItems.put(resId, menuItem);
	}

	public MenuItem getMenuItem(int resId) {
		return this.allMenuItems.get(resId);
	}


	public void destroy() {
		if (this.mainActivityWR != null) {
			this.mainActivityWR.clear();
			this.mainActivityWR = null;
		}
		if (this.allMenuItems != null) {
			this.allMenuItems.clear();
		}
		this.fragmentCustomView = null;
		this.drawerCustomView = null;
	}

	public boolean onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.main_activity, menu);
		initMenuItems(menu);
		return true;
	}

	public void initMenuItems(Menu menu) {
		this.allMenuItems.clear();
		this.allMenuItems.put(R.id.menu_search, menu.findItem(R.id.menu_search));
		updateAllMenuItems();
	}

	public void updateAllMenuItems() {
		final boolean drawerOpen = isDrawerOpen();
		final boolean showABIcons = !drawerOpen;

		if (this.allMenuItems != null) {
			for (HashMap.Entry<Integer, MenuItem> menuItemEntry : this.allMenuItems.entrySet()) {
				if (menuItemEntry.getKey().intValue() == R.id.menu_search) {
					menuItemEntry.getValue().setVisible(this.fragmentShowSearchMenuItem && showABIcons);
					this.allMenuItems.get(R.id.menu_search).setIcon(
							fragmentThemeDarkInsteadOfThemeLight ? R.drawable.ic_menu_action_search_holo_dark : R.drawable.ic_menu_action_search_holo_light);
					continue;
				}
				menuItemEntry.getValue().setVisible(showABIcons);
			}
		}

	}

	public boolean onOptionsItemSelected(MenuItem item) {
		final MainActivity mainActivity = getMainActivityOrNull();
		if (mainActivity != null) {
			if (item.getItemId() == android.R.id.home) {
				if (mainActivity.onUpIconClick()) {
					return true; // handled
				}
			}
			if (item.getItemId() == R.id.menu_search) {
				mainActivity.onSearchRequested();
				return true; // handled
			}
		}
		return false; // not handled
	}

	private static class UpOnClickListener implements View.OnClickListener {

		private WeakReference<MainActivity> mainActivityWR;

		public UpOnClickListener(MainActivity mainActivity) {
			this.mainActivityWR = new WeakReference<MainActivity>(mainActivity);
		}

		@Override
		public void onClick(View v) {
			final MainActivity mainActivity = this.mainActivityWR == null ? null : this.mainActivityWR.get();
			if (mainActivity != null) {
				mainActivity.onUpIconClick();
			}

		}
	}

}
