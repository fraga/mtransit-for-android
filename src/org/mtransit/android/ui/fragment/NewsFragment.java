package org.mtransit.android.ui.fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import org.mtransit.android.R;
import org.mtransit.android.commons.BundleUtils;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.ColorUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.ThemeUtils;
import org.mtransit.android.commons.TimeUtils;
import org.mtransit.android.commons.data.News;
import org.mtransit.android.commons.ui.widget.MTArrayAdapter;
import org.mtransit.android.task.NewsLoader;
import org.mtransit.android.ui.MainActivity;
import org.mtransit.android.ui.widget.ListViewSwipeRefreshLayout;
import org.mtransit.android.util.LoaderUtils;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.TextView;

public class NewsFragment extends ABFragment implements LoaderManager.LoaderCallbacks<ArrayList<News>>, SwipeRefreshLayout.OnRefreshListener {

	private static final String TAG = NewsFragment.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static final String TRACKING_SCREEN_NAME = "News";

	@Override
	public String getScreenName() {
		return TRACKING_SCREEN_NAME;
	}

	private static final String EXTRA_FILTER_TARGET_UUID = "extra_filter_target_uuid";

	public static NewsFragment newInstance(String optFilterTargetUUID) {
		NewsFragment f = new NewsFragment();
		Bundle args = new Bundle();
		if (!TextUtils.isEmpty(optFilterTargetUUID)) {
			args.putString(EXTRA_FILTER_TARGET_UUID, optFilterTargetUUID);
			f.targetUUID = optFilterTargetUUID;
		}
		f.setArguments(args);
		return f;
	}

	private String targetUUID;
	private CharSequence emptyText = null;
	private NewsAdapter adapter;
	private ListViewSwipeRefreshLayout swipeRefreshLayout;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		initAdapters(activity);
	}

	private void initAdapters(Activity activity) {
		this.adapter = new NewsAdapter(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		restoreInstanceState(savedInstanceState, getArguments());
	}

	private void restoreInstanceState(Bundle... bundles) {
		String newTargetUUID = BundleUtils.getString(EXTRA_FILTER_TARGET_UUID, bundles);
		if (!TextUtils.isEmpty(newTargetUUID) && !newTargetUUID.equals(this.targetUUID)) {
			this.targetUUID = newTargetUUID;
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (!TextUtils.isEmpty(this.targetUUID)) {
			outState.putString(EXTRA_FILTER_TARGET_UUID, this.targetUUID);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		View view = inflater.inflate(R.layout.fragment_news, container, false);
		setupView(view);
		return view;
	}

	@Override
	public void onResume() {
		super.onResume();
		View view = getView();
		if (this.modulesUpdated) {
			view.post(new Runnable() {
				@Override
				public void run() {
					if (NewsFragment.this.modulesUpdated) {
						onModulesUpdated();
					}
				}
			});
		}
		if (!this.adapter.isInitialized()) {
			LoaderUtils.restartLoader(this, NEWS_LOADER, null, this);
		}
		this.adapter.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();
		this.adapter.onPause();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		this.adapter.onDestroy();
	}

	private boolean modulesUpdated = false;

	@Override
	public void onModulesUpdated() {
		this.modulesUpdated = true;
		if (!isResumed()) {
			return;
		}
		LoaderUtils.restartLoader(this, NEWS_LOADER, null, this);
		this.modulesUpdated = false; // processed
	}

	private static final int NEWS_LOADER = 0;

	@Override
	public Loader<ArrayList<News>> onCreateLoader(int id, Bundle args) {
		switch (id) {
		case NEWS_LOADER:
			return new NewsLoader(getActivity(), this.targetUUID);
		default:
			MTLog.w(this, "Loader id '%s' unknown!", id);
			return null;
		}
	}

	@Override
	public void onLoaderReset(Loader<ArrayList<News>> loader) {
		if (this.adapter != null) {
			this.adapter.clear();
		}
	}

	@Override
	public void onLoadFinished(Loader<ArrayList<News>> loader, ArrayList<News> data) {
		this.emptyText = getString(R.string.no_news);
		this.adapter.setNews(data);
		switchView(getView());
	}

	@Override
	public void onRefresh() {
		initiateRefresh();
	}

	private boolean initiateRefresh() {
		if (this.adapter != null) {
			this.adapter.clear();
		}
		switchView(getView());
		LoaderUtils.restartLoader(this, NEWS_LOADER, null, this);
		setSwipeRefreshLayoutRefreshing(false);
		return true;
	}

	public void setSwipeRefreshLayoutRefreshing(boolean refreshing) {
		if (this.swipeRefreshLayout != null) {
			if (refreshing) {
				if (!this.swipeRefreshLayout.isRefreshing()) {
					this.swipeRefreshLayout.setRefreshing(true);
				}
			} else {
				this.swipeRefreshLayout.setRefreshing(false);
			}
		}
	}

	private void setupView(View view) {
		if (view == null) {
			return;
		}
		this.swipeRefreshLayout = (ListViewSwipeRefreshLayout) view.findViewById(R.id.swiperefresh);
		this.swipeRefreshLayout.setColorSchemeColors(ThemeUtils.resolveColorAttribute(getActivity(), R.attr.colorAccent));
		this.swipeRefreshLayout.setOnRefreshListener(this);
		inflateList(view);
		switchView(view);
		linkAdapterWithListView(view);
	}

	private void linkAdapterWithListView(View view) {
		if (view == null || this.adapter == null) {
			return;
		}
		View listView = view.findViewById(R.id.list);
		if (listView != null) {
			((AbsListView) listView).setAdapter(this.adapter);
			((AbsListView) listView).setOnItemClickListener(this.adapter);
			((AbsListView) listView).setOnItemLongClickListener(this.adapter);
		}
	}

	private void switchView(View view) {
		if (view == null) {
			return;
		}
		if (this.adapter == null || !this.adapter.isInitialized()) {
			showLoading(view);
		} else if (this.adapter.getCount() == 0) {
			showEmpty(view);
		} else {
			showList(view);
		}
	}

	private void showList(View view) {
		if (view.findViewById(R.id.loading) != null) { // IF inflated/present DO
			view.findViewById(R.id.loading).setVisibility(View.GONE); // hide
		}
		if (view.findViewById(R.id.empty) != null) { // IF inflated/present DO
			view.findViewById(R.id.empty).setVisibility(View.GONE); // hide
		}
		inflateList(view);
		view.findViewById(R.id.list).setVisibility(View.VISIBLE); // show
	}

	private void inflateList(View view) {
		if (view.findViewById(R.id.list) == null) { // IF NOT present/inflated DO
			((ViewStub) view.findViewById(R.id.list_stub)).inflate(); // inflate
			if (this.swipeRefreshLayout != null) {
				this.swipeRefreshLayout.setListViewWR((AbsListView) view.findViewById(R.id.list));
			}
		}
	}

	private void showLoading(View view) {
		if (view.findViewById(R.id.list) != null) { // IF inflated/present DO
			view.findViewById(R.id.list).setVisibility(View.GONE); // hide
		}
		if (view.findViewById(R.id.empty) != null) { // IF inflated/present DO
			view.findViewById(R.id.empty).setVisibility(View.GONE); // hide
		}
		if (this.swipeRefreshLayout != null) {
			this.swipeRefreshLayout.setLoadingViewWR(view.findViewById(R.id.loading));
		}
		view.findViewById(R.id.loading).setVisibility(View.VISIBLE); // show
	}

	private void showEmpty(View view) {
		if (view.findViewById(R.id.list) != null) { // IF inflated/present DO
			view.findViewById(R.id.list).setVisibility(View.GONE); // hide
		}
		if (view.findViewById(R.id.loading) != null) { // IF inflated/present DO
			view.findViewById(R.id.loading).setVisibility(View.GONE); // hide
		}
		if (view.findViewById(R.id.empty) == null) { // IF NOT present/inflated DO
			((ViewStub) view.findViewById(R.id.empty_stub)).inflate(); // inflate
			if (this.swipeRefreshLayout != null) {
				this.swipeRefreshLayout.setEmptyViewWR(view.findViewById(R.id.empty));
			}
		}
		if (!TextUtils.isEmpty(this.emptyText)) {
			((TextView) view.findViewById(R.id.empty_text)).setText(this.emptyText);
		}
		view.findViewById(R.id.empty).setVisibility(View.VISIBLE); // show
	}

	@Override
	public CharSequence getABTitle(Context context) {
		return context.getString(R.string.news);
	}

	private static class NewsAdapter extends MTArrayAdapter<News> implements TimeUtils.TimeChangedReceiver.TimeChangedListener,
			AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener {

		private static final String TAG = NewsAdapter.class.getSimpleName();

		@Override
		public String getLogTag() {
			return TAG;
		}

		private LayoutInflater layoutInflater;
		private WeakReference<Activity> activityWR;

		private ArrayList<News> news;

		private NewsAdapter(Activity activity) {
			super(activity, -1);
			setActivity(activity);
			this.layoutInflater = LayoutInflater.from(getContext());
		}

		public void setActivity(Activity activity) {
			this.activityWR = new WeakReference<Activity>(activity);
		}

		private Activity getActivityOrNull() {
			return this.activityWR == null ? null : this.activityWR.get();
		}

		public boolean isInitialized() {
			return this.news != null;
		}

		private void resetNowToTheMinute() {
			notifyDataSetChanged();
		}

		@Override
		public void onTimeChanged() {
			resetNowToTheMinute();
		}

		private final BroadcastReceiver timeChangedReceiver = new TimeUtils.TimeChangedReceiver(this);

		private boolean timeChangedReceiverEnabled = false;

		private void enableTimeChangedReceiver() {
			if (!this.timeChangedReceiverEnabled) {
				getContext().registerReceiver(timeChangedReceiver, TimeUtils.TIME_CHANGED_INTENT_FILTER);
				this.timeChangedReceiverEnabled = true;
			}
		}

		private void disableTimeChangeddReceiver() {
			if (this.timeChangedReceiverEnabled) {
				getContext().unregisterReceiver(this.timeChangedReceiver);
				this.timeChangedReceiverEnabled = false;
			}
		}

		public void setNews(ArrayList<News> news) {
			this.news = news;
			notifyDataSetChanged();
		}

		@Override
		public void clear() {
			if (this.news != null) {
				this.news.clear();
				this.news = null; // not initialized
			}
			super.clear();
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			News news = getItem(position);
			if (news != null) {
				Activity activity = getActivityOrNull();
				if (activity != null) {
					((MainActivity) activity).addFragmentToStack(NewsDetailsFragment.newInstance(news.getUUID(), news.getAuthority(), news));
				}
			}
		}

		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			return false;
		}

		@Override
		public int getCount() {
			return CollectionUtils.getSize(this.news);
		}

		public void onPause() {
			disableTimeChangeddReceiver();
		}

		public void onResume() {
			enableTimeChangedReceiver();
		}

		public void onDestroy() {
			disableTimeChangeddReceiver();
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = this.layoutInflater.inflate(R.layout.layout_news_base, parent, false);
				NewsViewHolder holder = new NewsViewHolder();
				holder.newsTv = (TextView) convertView.findViewById(R.id.newsText);
				holder.authorTv = (TextView) convertView.findViewById(R.id.author);
				holder.dateTv = (TextView) convertView.findViewById(R.id.date);
				convertView.setTag(holder);
			}
			NewsViewHolder holder = (NewsViewHolder) convertView.getTag();
			News news = getItem(position);
			holder.authorTv.setText(getContext().getString(R.string.news_shared_on_and_author_and_source, news.getAuthorOneLine(), news.getSourceLabel()));
			if (news.hasColor()) {
				holder.authorTv.setTextColor(news.getColorInt());
			} else {
				holder.authorTv.setTextColor(ColorUtils.getTextColorSecondary(getContext()));
			}
			holder.dateTv.setText(TimeUtils.formatRelativeTime(getContext(), news.getCreatedAtInMs()));
			holder.newsTv.setText(Html.fromHtml(news.getTextHTML()));
			if (news.hasColor()) {
				holder.newsTv.setLinkTextColor(news.getColorInt());
			} else {
				holder.newsTv.setLinkTextColor(ColorUtils.getTextColorPrimary(getContext()));
			}
			return convertView;
		}

		@Override
		public News getItem(int position) {
			return this.news == null ? null : this.news.get(position);
		}

		private static final class NewsViewHolder {
			private TextView newsTv;
			private TextView authorTv;
			private TextView dateTv;
		}
	}
}
