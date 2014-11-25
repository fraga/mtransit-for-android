package org.mtransit.android.data;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

import org.mtransit.android.R;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.ColorUtils;
import org.mtransit.android.commons.LocationUtils.LocationPOI;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.SpanUtils;
import org.mtransit.android.commons.StoreUtils;
import org.mtransit.android.commons.TimeUtils;
import org.mtransit.android.commons.data.AppStatus;
import org.mtransit.android.commons.data.AvailabilityPercent;
import org.mtransit.android.commons.data.DefaultPOI;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POIStatus;
import org.mtransit.android.commons.data.RouteTripStop;
import org.mtransit.android.commons.data.Schedule;
import org.mtransit.android.commons.data.ServiceUpdate;
import org.mtransit.android.commons.provider.ServiceUpdateProvider;
import org.mtransit.android.commons.provider.StatusFilter;
import org.mtransit.android.provider.FavoriteManager;
import org.mtransit.android.task.ServiceUpdateLoader;
import org.mtransit.android.task.StatusLoader;
import org.mtransit.android.ui.MainActivity;
import org.mtransit.android.ui.fragment.POIFragment;
import org.mtransit.android.ui.fragment.RTSRouteFragment;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

public class POIManager implements LocationPOI, MTLog.Loggable {

	private static final String TAG = POIManager.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static Integer defaultPoiTextColor = null;

	public static int getDefaultPOITextColor(Context context) {
		if (defaultPoiTextColor == null) {
			defaultPoiTextColor = ColorUtils.getTextColorPrimary(context);
		}
		return defaultPoiTextColor;
	}

	private static ForegroundColorSpan defaultPoiTextColorSpan = null;

	public static ForegroundColorSpan getDefaultPOITextColorSpan(Context context) {
		if (defaultPoiTextColorSpan == null) {
			defaultPoiTextColorSpan = SpanUtils.getTextColor(getDefaultPOITextColor(context));
		}
		return defaultPoiTextColorSpan;
	}

	public static final POIDistanceComparator POI_DISTANCE_COMPARATOR = new POIDistanceComparator();

	public static final POIAlphaComparator POI_ALPHA_COMPATOR = new POIAlphaComparator();

	private static int defaultDistanceAndCompassColor = -1;

	public static int getDefaultDistanceAndCompassColor(Context context) {
		if (defaultDistanceAndCompassColor < 0) {
			defaultDistanceAndCompassColor = ColorUtils.getTextColorTertiary(context);
		}
		return defaultDistanceAndCompassColor;
	}

	public POI poi;

	private CharSequence distanceString = null;

	private float distance = -1;

	private POIStatus status;
	private ArrayList<ServiceUpdate> serviceUpdates;

	private long lastFindStatusTimestampMs = -1;

	private WeakReference<StatusLoader.StatusLoaderListener> statusLoaderListenerWR;

	private int scheduleMaxDataRequests = Schedule.ScheduleStatusFilter.MAX_DATA_REQUESTS_DEFAULT;

	public POIManager(POI poi) {
		this(poi, null);
	}

	public POIManager(POI poi, POIStatus status) {
		this.poi = poi;
		this.status = status;
	}

	@Override
	public String toString() {
		return new StringBuilder(POIManager.class.getSimpleName()).append('[')//
				.append("poi:").append(this.poi) //
				.append(']').toString();
	}

	@Override
	public float getDistance() {
		return distance;
	}

	@Override
	public void setDistance(float distance) {
		this.distance = distance;
	}

	@Override
	public CharSequence getDistanceString() {
		return distanceString;
	}

	@Override
	public void setDistanceString(CharSequence distanceString) {
		this.distanceString = distanceString;
	}

	public void setStatusLoaderListener(StatusLoader.StatusLoaderListener statusLoaderListener) {
		this.statusLoaderListenerWR = new WeakReference<StatusLoader.StatusLoaderListener>(statusLoaderListener);
	}

	public String getLocation() {
		if (this.poi != null && this.poi instanceof Module) {
			return ((Module) this.poi).getLocation();
		}
		return null;
	}

	public int getStatusType() {
		return this.poi.getStatusType();
	}

	public boolean hasStatus() {
		return this.status != null;
	}

	public void setStatus(POIStatus status) {
		if (status == null) {
			this.status = null;
			return;
		}
		switch (getStatusType()) {
		case POI.ITEM_STATUS_TYPE_SCHEDULE:
			if (status instanceof Schedule) {
				this.status = (Schedule) status;
			} else {
				MTLog.w(this, "Unexpected schedule status '%s'!", status);
			}
			break;
		case POI.ITEM_STATUS_TYPE_AVAILABILITY_PERCENT:
			if (status instanceof AvailabilityPercent) {
				this.status = (AvailabilityPercent) status;
			} else {
				MTLog.w(this, "Unexpected availability percent status '%s'!", status);
			}
			break;
		case POI.ITEM_STATUS_TYPE_APP:
			if (status instanceof AppStatus) {
				this.status = (AppStatus) status;
			} else {
				MTLog.w(this, "Unexpected app status '%s'!", status);
			}
			break;
		default:
			MTLog.w(this, "Unexpected status '%s'!", status);
		}
	}

	public POIStatus getStatusOrNull() {
		return this.status;
	}

	public POIStatus getStatus(Context context) {
		if (this.status == null || !status.isUseful()) {
			findStatus(context, false);
		}
		return this.status;
	}

	public boolean pingStatus(Context context) {
		if (this.status == null) {
			return findStatus(context, true);
		} else {
			return false;
		}
	}

	private boolean findStatus(Context context, boolean skipIfBusy) {
		long findStatusTimestampMs = TimeUtils.currentTimeToTheMinuteMillis();
		boolean isNotSkipped = false;
		if (this.lastFindStatusTimestampMs != findStatusTimestampMs) { // IF not same minute as last findStatus() call DO
			final StatusFilter filter = getFilter(findStatusTimestampMs);
			if (filter != null) {
				StatusLoader.StatusLoaderListener listener = this.statusLoaderListenerWR == null ? null : this.statusLoaderListenerWR.get();
				isNotSkipped = StatusLoader.get().findStatus(context, this, filter, listener, skipIfBusy);
				if (isNotSkipped) {
					this.lastFindStatusTimestampMs = findStatusTimestampMs;
				}
			}
		}
		return isNotSkipped;
	}

	public void setScheduleMaxDataRequests(int scheduleMaxDataRequests) {
		this.scheduleMaxDataRequests = scheduleMaxDataRequests;
	}

	private StatusFilter getFilter(long findStatusTimestampMs) {
		switch (getStatusType()) {
		case POI.ITEM_STATUS_TYPE_SCHEDULE:
			if (this.poi instanceof RouteTripStop) {
				RouteTripStop rts = (RouteTripStop) this.poi;
				Schedule.ScheduleStatusFilter filter = new Schedule.ScheduleStatusFilter(this.poi.getUUID(), rts);
				filter.setTimestamp(findStatusTimestampMs);
				filter.setLookBehindInMs(TimeUtils.RECENT_IN_MILLIS);
				filter.setMaxDataRequests(this.scheduleMaxDataRequests);
				return filter;
			} else {
				MTLog.w(this, "Schedule fiter w/o '%s'!", this.poi);
				return null;
			}
		case POI.ITEM_STATUS_TYPE_AVAILABILITY_PERCENT:
			return new AvailabilityPercent.AvailabilityPercentStatusFilter(this.poi.getUUID());
		case POI.ITEM_STATUS_TYPE_APP:
			if (poi instanceof Module) {
				Module module = (Module) this.poi;
				return new AppStatus.AppStatusFilter(this.poi.getUUID(), module.getPkg());
			} else {
				MTLog.w(this, "App status fiter w/o '%s'!", this.poi);
				return null;
			}
		default:
			MTLog.w(this, "Unexpected status type '%s´  for filter!", getStatusType());
			return null;
		}
	}

	private WeakReference<ServiceUpdateLoader.ServiceUpdateLoaderListener> serviceUpdateLoaderListenerWR;

	public void setServiceUpdateLoaderListener(ServiceUpdateLoader.ServiceUpdateLoaderListener serviceUpdateLoaderListener) {
		this.serviceUpdateLoaderListenerWR = new WeakReference<ServiceUpdateLoader.ServiceUpdateLoaderListener>(serviceUpdateLoaderListener);
	}

	public boolean hasServiceUpdates() {
		return CollectionUtils.getSize(this.serviceUpdates) != 0;
	}

	public void setServiceUpdates(Collection<ServiceUpdate> newServiceUpdates) {
		if (this.serviceUpdates == null) {
			this.serviceUpdates = new ArrayList<ServiceUpdate>();
		} else {
			this.serviceUpdates.clear();
		}
		if (newServiceUpdates != null) {
			this.serviceUpdates.addAll(newServiceUpdates);
			CollectionUtils.sort(this.serviceUpdates, ServiceUpdate.HIGHER_SEVERITY_FIRST_COMPARATOR);
		}
	}

	public ArrayList<ServiceUpdate> getServiceUpdatesOrNull() {
		return this.serviceUpdates;
	}

	public Boolean isServiceUpdateWarning(Context context) {
		if (this.serviceUpdates == null || !areServiceUpdatesUseful()) {
			findServiceUpdates(context, false);
			return null;
		}
		boolean isWarning = ServiceUpdate.isSeverityWarning(this.serviceUpdates);
		return isWarning;
	}

	public ArrayList<ServiceUpdate> getServiceUpdates(Context context) {
		if (this.serviceUpdates == null || !areServiceUpdatesUseful()) {
			findServiceUpdates(context, false);
		}
		return this.serviceUpdates;
	}

	private boolean areServiceUpdatesUseful() {
		if (this.serviceUpdates != null) {
			for (ServiceUpdate serviceUpdate : this.serviceUpdates) {
				if (serviceUpdate.isUseful()) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean pingServiceUpdates(Context context) {
		if (this.serviceUpdates == null) {
			return findServiceUpdates(context, true);
		} else {
			return false;
		}
	}

	private long lastFindServiceUpdateTimestampMs = -1;

	private boolean findServiceUpdates(Context context, boolean skipIfBusy) {
		long findServiceUpdateTimestampMs = TimeUtils.currentTimeToTheMinuteMillis();
		boolean isNotSkipped = false;
		if (this.lastFindServiceUpdateTimestampMs != findServiceUpdateTimestampMs) { // IF not same minute as last findStatus() call DO
			final ServiceUpdateProvider.ServiceUpdateFilter filter = new ServiceUpdateProvider.ServiceUpdateFilter(this.poi);
			if (filter != null) {
				ServiceUpdateLoader.ServiceUpdateLoaderListener listener = this.serviceUpdateLoaderListenerWR == null ? null
						: this.serviceUpdateLoaderListenerWR.get();
				isNotSkipped = ServiceUpdateLoader.get().findServiceUpdate(context, this, filter, listener, skipIfBusy);
				if (isNotSkipped) {
					this.lastFindServiceUpdateTimestampMs = findServiceUpdateTimestampMs;
				}
			}
		}
		return isNotSkipped;
	}

	public CharSequence[] getActionsItems(Context context, CharSequence defaultAction) {
		switch (this.poi.getActionsType()) {
		case POI.ITEM_ACTION_TYPE_FAVORITABLE:
			return new CharSequence[] {//
			defaultAction, //
					FavoriteManager.isFavorite(context, poi.getUUID()) ? context.getString(R.string.remove_fav) : context.getString(R.string.add_fav) //
			};
		case POI.ITEM_ACTION_TYPE_ROUTE_TRIP_STOP:
			final RouteTripStop rts = (RouteTripStop) poi;
			return new CharSequence[] {//
					context.getString(R.string.view_stop), //
					TextUtils.isEmpty(rts.route.shortName) ? context.getString(R.string.view_stop_route) : context.getString(
							R.string.view_stop_route_and_route, rts.route.shortName), //
					FavoriteManager.isFavorite(context, poi.getUUID()) ? context.getString(R.string.remove_fav) : context.getString(R.string.add_fav) //
			};
		case POI.ITEM_ACTION_TYPE_APP:
			if (PackageManagerUtils.isAppInstalled(context, ((Module) poi).getPkg())) {
				return new CharSequence[] { //
				context.getString(R.string.join_leave_test_on_store), //
						context.getString(R.string.rate_on_store), //
						context.getString(R.string.uninstall), //
				};
			} else {
				return new CharSequence[] { //
				context.getString(R.string.join_leave_test_on_store), //
						context.getString(R.string.download_on_store), //
				};
			}
		default:
			MTLog.w(this, "unexpected action type '%s'!", this.poi.getActionsType());
			return new CharSequence[] { defaultAction };
		}

	}

	public boolean onActionsItemClick(Activity activity, int itemClicked, FavoriteManager.FavoriteUpdateListener listener) {
		switch (this.poi.getActionsType()) {
		case POI.ITEM_ACTION_TYPE_FAVORITABLE:
			return onActionsItemClickFavoritable(activity, itemClicked, listener);
		case POI.ITEM_ACTION_TYPE_ROUTE_TRIP_STOP:
			return onActionsItemClickRTS(activity, itemClicked, listener);
		case POI.ITEM_ACTION_TYPE_APP:
			return onActionsItemClickApp(activity, itemClicked, listener);
		default:
			MTLog.w(this, "unexpected action type '%s'!", this.poi.getActionsType());
			return false; // NOT HANDLED
		}
	}

	private boolean onActionsItemClickApp(Activity activity, int itemClicked, FavoriteManager.FavoriteUpdateListener listener) {
		switch (itemClicked) {
		case 0:
			StoreUtils.viewTestingWebPage(activity, ((Module) poi).getPkg());
			return true; // HANDLED
		case 1:
			StoreUtils.viewAppPage(activity, ((Module) poi).getPkg());
			return true; // HANDLED
		case 2:
			PackageManagerUtils.uninstallApp(activity, ((Module) poi).getPkg());
			return true; // HANDLED
		}
		return false; // NOT HANDLED
	}

	private boolean onActionsItemClickRTS(Activity activity, int itemClicked, FavoriteManager.FavoriteUpdateListener listener) {
		switch (itemClicked) {
		case 1:
			RouteTripStop rts = (RouteTripStop) poi;
			((MainActivity) activity).addFragmentToStack(RTSRouteFragment.newInstance(rts));
			return true; // HANDLED
		case 2:
			return addRemoteFavorite(activity, FavoriteManager.isFavorite(activity, poi.getUUID()), listener);
		}
		return false; // NOT HANDLED
	}

	private boolean onActionsItemClickFavoritable(Activity activity, int itemClicked, FavoriteManager.FavoriteUpdateListener listener) {
		switch (itemClicked) {
		case 1:
			return addRemoteFavorite(activity, FavoriteManager.isFavorite(activity, poi.getUUID()), listener);
		}
		return false; // NOT HANDLED
	}

	public boolean addRemoteFavorite(Activity activity, boolean isFavorite, FavoriteManager.FavoriteUpdateListener listener) {
		FavoriteManager.addOrDeleteFavorite(activity, isFavorite, this.poi.getUUID());
		if (listener != null) {
			listener.onFavoriteUpdated();
		}
		return true; // HANDLED
	}

	public boolean isFavoritable() {
		switch (this.poi.getActionsType()) {
		case POI.ITEM_ACTION_TYPE_FAVORITABLE:
		case POI.ITEM_ACTION_TYPE_ROUTE_TRIP_STOP:
			return true;
		case POI.ITEM_ACTION_TYPE_APP:
			return false;
		default:
			MTLog.w(this, "unexpected action type '%s'!", this.poi.getActionsType());
			return false;
		}
	}

	public boolean onActionItemClick(Activity activity) {
		switch (this.poi.getActionsType()) {
		case POI.ITEM_ACTION_TYPE_APP:
			return false; // show long-click menu
		}
		if (activity != null && activity instanceof MainActivity) {
			final MainActivity mainActivity = (MainActivity) activity;
			mainActivity.addFragmentToStack(POIFragment.newInstance(this, mainActivity.getUserLocation()));
			return true; // HANDLED
		}
		return false; // NOT HANDLED
	}

	public static POIManager fromCursorStatic(Cursor cursor, String authority) {
		switch (DefaultPOI.getTypeFromCursor(cursor)) {
		case POI.ITEM_VIEW_TYPE_BASIC_POI:
			return new POIManager(DefaultPOI.fromCursorStatic(cursor, authority));
		case POI.ITEM_VIEW_TYPE_ROUTE_TRIP_STOP:
			return new POIManager(RouteTripStop.fromCursorStatic(cursor, authority));
		case POI.ITEM_VIEW_TYPE_MODULE:
			return new POIManager(Module.fromCursorStatic(cursor, authority));
		default:
			MTLog.w(TAG, "Unexpected POI type '%s'! (using default)", DefaultPOI.getTypeFromCursor(cursor));
			return new POIManager(DefaultPOI.fromCursorStatic(cursor, authority));
		}
	}

	@Override
	public Double getLat() {
		return this.poi.getLat();
	}

	@Override
	public void setLat(Double lat) {
		this.poi.setLat(lat);
	}

	@Override
	public void setLng(Double lng) {
		this.poi.setLng(lng);
	}

	@Override
	public Double getLng() {
		return this.poi.getLng();
	}

	@Override
	public boolean hasLocation() {
		return this.poi.hasLocation();
	}

	private static class POIAlphaComparator implements Comparator<POIManager> {
		@Override
		public int compare(POIManager lhs, POIManager rhs) {
			final POI lhsPoi = lhs == null ? null : lhs.poi;
			final POI rhsPoi = rhs == null ? null : rhs.poi;
			if (lhsPoi == null && rhsPoi == null) {
				return 0;
			}
			if (lhsPoi == null) {
				return -1;
			} else if (rhsPoi == null) {
				return +1;
			}
			return lhsPoi.compareToAlpha(null, rhsPoi);
		}
	}

	private static class POIDistanceComparator implements Comparator<POIManager> {
		@Override
		public int compare(POIManager lhs, POIManager rhs) {
			if (lhs.poi instanceof RouteTripStop && rhs.poi instanceof RouteTripStop) {
				RouteTripStop alhs = (RouteTripStop) lhs.poi;
				RouteTripStop arhs = (RouteTripStop) rhs.poi;
				if (alhs.stop.id == arhs.stop.id) {
					if (!TextUtils.isEmpty(alhs.route.shortName) || !TextUtils.isEmpty(arhs.route.shortName)) {
						try {
							return Integer.valueOf(alhs.route.shortName) - Integer.valueOf(arhs.route.shortName);
						} catch (NumberFormatException nfe) {
							return alhs.route.shortName.compareTo(arhs.route.shortName);
						}
					}
				}
			}
			float d1 = lhs.getDistance();
			float d2 = rhs.getDistance();
			if (d1 > d2) {
				return +1;
			} else if (d1 < d2) {
				return -1;
			} else {
				return 0;
			}
		}
	}

}
