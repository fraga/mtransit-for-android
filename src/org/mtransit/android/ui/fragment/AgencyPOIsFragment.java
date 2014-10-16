package org.mtransit.android.ui.fragment;

import java.util.List;

import org.mtransit.android.R;
import org.mtransit.android.commons.BundleUtils;
import org.mtransit.android.commons.LocationUtils;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.ui.fragment.MTFragmentV4;
import org.mtransit.android.data.AgencyProperties;
import org.mtransit.android.data.DataSourceProvider;
import org.mtransit.android.data.POIArrayAdapter;
import org.mtransit.android.data.POIManager;
import org.mtransit.android.task.AgencyPOIsLoader;
import org.mtransit.android.ui.MTActivityWithLocation;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.TextView;

public class AgencyPOIsFragment extends MTFragmentV4 implements VisibilityAwareFragment, LoaderManager.LoaderCallbacks<List<POIManager>>,
		MTActivityWithLocation.UserLocationListener {

	private static final String TAG = AgencyPOIsFragment.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG + "-" + (this.agency == null ? null : this.agency.getAuthority());
	}

	private static final String EXTRA_AGENCY_AUTHORITY = "extra_agency_authority";
	private static final String EXTRA_FRAGMENT_POSITION = "extra_fragment_position";
	private static final String EXTRA_LAST_VISIBLE_FRAGMENT_POSITION = "extra_last_visible_fragment_position";
	private static final String EXTRA_USER_LOCATION = "extra_user_location";

	public static AgencyPOIsFragment newInstance(int fragmentPosition, int lastVisisbleFragmentPosition, AgencyProperties agency, Location userLocationOpt) {
		AgencyPOIsFragment f = new AgencyPOIsFragment();
		Bundle args = new Bundle();
		args.putString(EXTRA_AGENCY_AUTHORITY, agency.getAuthority());
		if (fragmentPosition >= 0) {
			args.putInt(EXTRA_FRAGMENT_POSITION, fragmentPosition);
		}
		if (lastVisisbleFragmentPosition >= 0) {
			args.putInt(EXTRA_LAST_VISIBLE_FRAGMENT_POSITION, lastVisisbleFragmentPosition);
		}
		if (userLocationOpt != null) {
			args.putParcelable(EXTRA_USER_LOCATION, userLocationOpt);
		}
		f.setArguments(args);
		return f;
	}

	private AgencyProperties agency;
	private Location userLocation;
	private int fragmentPosition = -1;
	private int lastVisisbleFragmentPosition = -1;
	private boolean fragmentVisible = false;
	private POIArrayAdapter adapter;
	private String emptyText = null;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		final View view = inflater.inflate(R.layout.fragment_agency_pois, container, false);
		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (this.userLocation != null) {
			outState.putParcelable(EXTRA_USER_LOCATION, this.userLocation);
		}
		super.onSaveInstanceState(outState);
	}

	private void restoreInstanceState(Bundle savedInstanceState) {
		final String agencyAuthority = BundleUtils.getString(EXTRA_AGENCY_AUTHORITY, savedInstanceState, getArguments());
		if (!TextUtils.isEmpty(agencyAuthority)) {
			this.agency = DataSourceProvider.get().getAgency(getActivity(), agencyAuthority);
		}
		final Integer fragmentPosition = BundleUtils.getInt(EXTRA_FRAGMENT_POSITION, savedInstanceState, getArguments());
		if (fragmentPosition != null) {
			if (fragmentPosition.intValue() >= 0) {
				this.fragmentPosition = fragmentPosition.intValue();
			} else {
				this.fragmentPosition = -1;
			}
		}
		final Integer lastVisisbleFragmentPosition = BundleUtils.getInt(EXTRA_LAST_VISIBLE_FRAGMENT_POSITION, savedInstanceState, getArguments());
		if (lastVisisbleFragmentPosition != null) {
			if (lastVisisbleFragmentPosition.intValue() >= 0) {
				this.lastVisisbleFragmentPosition = lastVisisbleFragmentPosition;
			} else {
				this.lastVisisbleFragmentPosition = -1;
			}
		}
		final Location userLocation = BundleUtils.getParcelable(EXTRA_USER_LOCATION, savedInstanceState, getArguments());
		if (userLocation != null) {
			onUserLocationChanged(userLocation);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		restoreInstanceState(savedInstanceState);
	}

	private void initAdapter() {
		this.adapter = new POIArrayAdapter(getActivity());
		this.adapter.setTag(this.agency.getAuthority());
		inflateList();
		this.adapter.setListView((AbsListView) getView().findViewById(R.id.list));
		if (!this.adapter.isInitialized()) {
			showLoading();
		} else if (this.adapter.getPoisCount() == 0) {
			showEmpty();
		} else {
			showList();
		}
	}

	@Override
	public void setFragmentVisisbleAtPosition(int visisbleFragmentPosition) {
		if (this.lastVisisbleFragmentPosition == visisbleFragmentPosition //
				&& (//
				(this.fragmentPosition == visisbleFragmentPosition && this.fragmentVisible) //
				|| //
				(this.fragmentPosition != visisbleFragmentPosition && !this.fragmentVisible) //
				) //
		) {
			return;
		}
		this.lastVisisbleFragmentPosition = visisbleFragmentPosition;
		if (this.fragmentPosition < 0) {
			return;
		}
		if (this.fragmentPosition == visisbleFragmentPosition) {
			onFragmentVisisble();
		} else {
			onFragmentInvisible();
		}
	}

	private void onFragmentInvisible() {
		if (!this.fragmentVisible) {
			return; // already invisible
		}
		this.fragmentVisible = false;
		if (this.adapter != null) {
			this.adapter.onPause();
		}
	}

	private void onFragmentVisisble() {
		if (this.fragmentVisible) {
			return; // already visible
		}
		this.fragmentVisible = true;
		if (this.adapter == null) {
			initAdapter();
			getLoaderManager().restartLoader(POIS_LOADER, null, this);
		} else {
			if (this.adapter.getPoisCount() > 0) {
				this.adapter.onResume();
				this.adapter.refreshFavorites();
			}
		}
		Activity activity = getActivity();
		if (activity != null && activity instanceof MTActivityWithLocation) {
			onUserLocationChanged(((MTActivityWithLocation) activity).getUserLocation()); // user location was unknown yet or discarded while not visible
		}
	}

	private static final int POIS_LOADER = 0;

	@Override
	public Loader<List<POIManager>> onCreateLoader(int id, Bundle args) {
		switch (id) {
		case POIS_LOADER:
			final AgencyPOIsLoader agencyPOIsLoader = new AgencyPOIsLoader(getActivity(), this.agency);
			return agencyPOIsLoader;
		default:
			MTLog.w(this, "Loader id '%s' unknown!", id);
			return null;
		}
	}

	@Override
	public void onLoaderReset(Loader<List<POIManager>> loader) {
		if (this.adapter != null) {
			this.adapter.clear();
			this.adapter.onPause();
		}
	}

	@Override
	public void onLoadFinished(Loader<List<POIManager>> loader, List<POIManager> data) {
		this.adapter.setPois(data);
		this.adapter.updateDistanceNowAsync(this.userLocation);
		if (this.adapter.getPoisCount() > 0) {
			showList();
		} else {
			showEmpty();
		}
	}

	@Override
	public void onUserLocationChanged(Location newLocation) {
		if (newLocation != null) {
			if (this.userLocation == null || LocationUtils.isMoreRelevant(getLogTag(), this.userLocation, newLocation)) {
				this.userLocation = newLocation;
				if (this.adapter != null) {
					this.adapter.setLocation(this.userLocation);
				}
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		onFragmentInvisible();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (this.fragmentPosition < 0 || this.fragmentPosition == this.lastVisisbleFragmentPosition) {
			onFragmentVisisble();
		} // ELSE would be call later
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (this.adapter != null) {
			this.adapter.onDestroy();
		}
	}

	private void showList() {
		if (getView().findViewById(R.id.loading) != null) { // IF inflated/present DO
			getView().findViewById(R.id.loading).setVisibility(View.GONE); // hide
		}
		if (getView().findViewById(R.id.empty) != null) { // IF inflated/present DO
			getView().findViewById(R.id.empty).setVisibility(View.GONE); // hide
		}
		inflateList();
		getView().findViewById(R.id.list).setVisibility(View.VISIBLE); // show
	}

	private void inflateList() {
		if (getView().findViewById(R.id.list) == null) { // IF NOT present/inflated DO
			((ViewStub) getView().findViewById(R.id.list_stub)).inflate(); // inflate
			((AbsListView) getView().findViewById(R.id.list)).setFastScrollEnabled(true);
			((AbsListView) getView().findViewById(R.id.list)).setFastScrollAlwaysVisible(true); // long list
		}
	}

	private void showLoading() {
		if (getView().findViewById(R.id.list) != null) { // IF inflated/present DO
			getView().findViewById(R.id.list).setVisibility(View.GONE); // hide
		}
		if (getView().findViewById(R.id.empty) != null) { // IF inflated/present DO
			getView().findViewById(R.id.empty).setVisibility(View.GONE); // hide
		}
		if (getView().findViewById(R.id.loading) == null) { // IF NOT present/inflated DO
			((ViewStub) getView().findViewById(R.id.loading_stub)).inflate(); // inflate
			// this.swipeRefreshLayout.setLoadingViewWR(getView().findViewById(R.id.loading));
		}
		getView().findViewById(R.id.loading).setVisibility(View.VISIBLE); // show
	}

	private void showEmpty() {
		if (getView().findViewById(R.id.list) != null) { // IF inflated/present DO
			getView().findViewById(R.id.list).setVisibility(View.GONE); // hide
		}
		if (getView().findViewById(R.id.loading) != null) { // IF inflated/present DO
			getView().findViewById(R.id.loading).setVisibility(View.GONE); // hide
		}
		if (getView().findViewById(R.id.empty) == null) { // IF NOT present/inflated DO
			((ViewStub) getView().findViewById(R.id.empty_stub)).inflate(); // inflate
		}
		if (!TextUtils.isEmpty(this.emptyText)) {
			((TextView) getView().findViewById(R.id.empty_text)).setText(this.emptyText);
		}
		getView().findViewById(R.id.empty).setVisibility(View.VISIBLE); // show
	}

}