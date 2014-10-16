package org.mtransit.android.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.mtransit.android.R;
import org.mtransit.android.commons.CollectionUtils;
import org.mtransit.android.commons.LocationUtils.Area;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.data.AvailabilityPercent;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POIStatus;
import org.mtransit.android.commons.data.Route;
import org.mtransit.android.commons.data.Schedule;
import org.mtransit.android.commons.provider.POIFilter;
import org.mtransit.android.commons.provider.POIProvider;
import org.mtransit.android.commons.provider.StatusFilter;
import org.mtransit.android.commons.provider.StatusProvider;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

public class DataSourceProvider implements MTLog.Loggable {

	private static final String TAG = DataSourceProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static DataSourceProvider instance = null;

	private List<AgencyProperties> allAgencies = null;

	private List<DataSourceType> allAgencyTypes = null;

	private WeakHashMap<DataSourceType, List<AgencyProperties>> allAgenciesByType = null;
	private WeakHashMap<String, AgencyProperties> allAgenciesByAuthority = null;

	private WeakHashMap<String, Set<StatusProviderProperties>> statusProvidersByTargetAuthority = null;

	private WeakHashMap<String, JPaths> rtsRouteLogoByAuthority = null;

	private HashMap<String, Uri> uriMap = new HashMap<String, Uri>();

	public static DataSourceProvider get() {
		if (instance == null) {
			instance = new DataSourceProvider();
		}
		return instance;
	}

	private DataSourceProvider() {
	}

	public Uri getUri(String authority) {
		Uri uri = uriMap.get(authority);
		if (uri == null) {
			uri = UriUtils.newContentUri(authority);
			uriMap.put(authority, uri);
		}
		return uri;
	}

	public List<DataSourceType> getAvailableAgencyTypes(Context context) {
		if (this.allAgencyTypes == null) {
			init(context);
		}
		return this.allAgencyTypes;
	}

	public List<AgencyProperties> getAllDataSources(Context context) {
		if (this.allAgencies == null) {
			init(context);
		}
		return this.allAgencies;
	}

	public List<AgencyProperties> getTypeDataSources(Context context, DataSourceType type) {
		if (this.allAgenciesByType == null) {
			init(context);
		}
		return this.allAgenciesByType.get(type);
	}

	public AgencyProperties getAgency(Context context, String authority) {
		if (this.allAgenciesByAuthority == null) {
			init(context);
		}
		return this.allAgenciesByAuthority.get(authority);
	}

	public Collection<StatusProviderProperties> getTargetAuthorityStatusProviders(Context context, String targetAuthority) {
		if (this.statusProvidersByTargetAuthority == null) {
			init(context);
		}
		return this.statusProvidersByTargetAuthority.get(targetAuthority);
	}

	public JPaths getRTSRouteLogo(Context context, String authority) {
		if (this.rtsRouteLogoByAuthority == null) {
			init(context);
		}
		return this.rtsRouteLogoByAuthority.get(authority);
	}

	private void init(Context context) {
		this.allAgencies = new ArrayList<AgencyProperties>();
		this.allAgencyTypes = new ArrayList<DataSourceType>();
		this.allAgenciesByType = new WeakHashMap<DataSourceType, List<AgencyProperties>>();
		this.allAgenciesByAuthority = new WeakHashMap<String, AgencyProperties>();
		this.statusProvidersByTargetAuthority = new WeakHashMap<String, Set<StatusProviderProperties>>();
		this.rtsRouteLogoByAuthority = new WeakHashMap<String, JPaths>();
		String agencyProviderMetaData = context.getString(R.string.agency_provider);
		String statusProviderMetaData = context.getString(R.string.status_provider);
		String statusProviderTargetMetaData = context.getString(R.string.status_provider_target);
		for (PackageInfo packageInfo : context.getPackageManager().getInstalledPackages(PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA)) {
			ProviderInfo[] providers = packageInfo.providers;
			if (providers != null) {
				for (ProviderInfo provider : providers) {
					if (provider.metaData != null) {
						if (agencyProviderMetaData.equals(provider.metaData.getString(agencyProviderMetaData))) {
							final Uri contentUri = getUri(provider.authority);
							final String label = findAgencyLabel(context, contentUri);
							final String shortName = findAgencyShortName(context, contentUri);
							final int typeId = findTypeId(context, contentUri);
							final DataSourceType type = DataSourceType.parseId(typeId);
							final Area area = findAgencyArea(context, contentUri);
							final JPaths jPath = findAgencyRTSRouteLogo(context, contentUri);
							final boolean isRTS = rtsProviderMetaData.equals(provider.metaData.getString(rtsProviderMetaData));
							if (type != null && typeId >= 0) {
								final AgencyProperties newAgency = new AgencyProperties(provider.authority, type, shortName, label, area, isRTS);
								addNewAgency(newAgency);
								if (jPath != null) {
									this.rtsRouteLogoByAuthority.put(newAgency.getAuthority(), jPath);
								}
							} else {
								MTLog.d(this, "Invalid type, skipping agency provider.");
							}
						}
						if (statusProviderMetaData.equals(provider.metaData.getString(statusProviderMetaData))) {
							String targetAuthority = provider.metaData.getString(statusProviderTargetMetaData);
							StatusProviderProperties newStatusProvider = new StatusProviderProperties(provider.authority, targetAuthority);
							addNewStatusProvider(newStatusProvider);
						}
					}
				}
			}
		}
		CollectionUtils.sort(this.allAgencyTypes, new DataSourceType.DataSourceTypeShortNameComparator(context));
		CollectionUtils.sort(this.allAgencies, AgencyProperties.SHORT_NAME_COMPARATOR);
		if (this.allAgenciesByType != null) {
			for (DataSourceType type : this.allAgenciesByType.keySet()) {
				CollectionUtils.sort(this.allAgenciesByType.get(type), AgencyProperties.SHORT_NAME_COMPARATOR);
			}
		}
	}

	private void addNewStatusProvider(StatusProviderProperties newStatusProvider) {
		String newScheduleProviderTargetAuthority = newStatusProvider.getTargetAuthority();
		if (!this.statusProvidersByTargetAuthority.containsKey(newScheduleProviderTargetAuthority)) {
			this.statusProvidersByTargetAuthority.put(newScheduleProviderTargetAuthority, new HashSet<StatusProviderProperties>());
		}
		this.statusProvidersByTargetAuthority.get(newScheduleProviderTargetAuthority).add(newStatusProvider);
	}

	public static POIStatus findStatus(Context context, Uri contentUri, POI poi, StatusFilter statusFilter) {
		if (poi == null) {
			return null;
		}
		POIStatus result = null;
		Cursor cursor = null;
		try {
			String statusFilterJSONString = statusFilter == null ? null : statusFilter.toJSONStringStatic(statusFilter);
			Uri uri = Uri.withAppendedPath(contentUri, StatusProvider.STATUS_CONTENT_DIRECTORY);
			cursor = context.getContentResolver().query(uri, null, statusFilterJSONString, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					int status = POIStatus.getTypeFromCursor(cursor);
					switch (status) {
					case POI.ITEM_STATUS_TYPE_SCHEDULE:
						result = Schedule.fromCursor(cursor);
						break;
					case POI.ITEM_STATUS_TYPE_AVAILABILITY_PERCENT:
						result = AvailabilityPercent.fromCursor(cursor);
						break;
					default:
						result = null;
						break;
					}
				}
			}
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	private void addNewAgency(AgencyProperties newAgency) {
		this.allAgencies.add(newAgency);
		this.allAgenciesByAuthority.put(newAgency.getAuthority(), newAgency);
		DataSourceType newAgencyType = newAgency.getType();
		if (!this.allAgencyTypes.contains(newAgencyType)) {
			this.allAgencyTypes.add(newAgencyType);
		}
		if (!this.allAgenciesByType.containsKey(newAgencyType)) {
			this.allAgenciesByType.put(newAgencyType, new ArrayList<AgencyProperties>());
		}
		this.allAgenciesByType.get(newAgencyType).add(newAgency);
	}

	private int findTypeId(Context context, Uri contentUri) {
		int result = -1;
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(contentUri, "type");
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					result = cursor.getInt(0);
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	private String findAgencyLabel(Context context, Uri contentUri) {
		String result = null;
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(contentUri, "label");
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					result = cursor.getString(0);
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	private String findAgencyShortName(Context context, Uri contentUri) {
		String result = null;
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(contentUri, "shortName");
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					result = cursor.getString(0);
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	private Area findAgencyArea(Context context, Uri contentUri) {
		Area result = null;
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(contentUri, "area");
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					result = Area.fromCursor(cursor);
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	private JPaths findAgencyRTSRouteLogo(Context context, Uri contentUri) {
		JPaths result = null;
		Cursor cursor = null;
		try {
			Uri uri = Uri.withAppendedPath(Uri.withAppendedPath(contentUri, "route"), "logo");
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			if (cursor != null && cursor.getCount() > 0) {
				if (cursor.moveToFirst()) {
					result = JPaths.fromJSONString(cursor.getString(0));
				}
			}
		} catch (Throwable t) {
			MTLog.w(this, t, "Error!");
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return result;
	}

	public static List<Route> findAllRTSAgencyRoutes(Context context, Uri contentUri) {
		Cursor cursor = null;
		try {
			final Uri uri = getRTSRoutesUri(contentUri);
			cursor = context.getContentResolver().query(uri, null, null, null, null);
			return getRTSRoutes(cursor, contentUri.getAuthority());
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error!");
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private static List<Route> getRTSRoutes(Cursor cursor, String authority) {
		List<Route> result = new ArrayList<Route>();
		if (cursor != null && cursor.getCount() > 0) {
			if (cursor.moveToFirst()) {
				do {
					final Route fromCursor = Route.fromCursor(cursor);
					result.add(fromCursor);
				} while (cursor.moveToNext());
			}
		}
		return result;
	}

	public static List<POIManager> findPOIsWithUUIDs(Context context, Uri contentUri, Set<String> uuids) {
		Cursor cursor = null;
		try {
			POIFilter poiFilter = new POIFilter(uuids);
			String filterJsonString = POIFilter.toJSON(poiFilter).toString();
			final String sortOrder = null;
			final Uri uri = getPOIUri(contentUri);
			cursor = context.getContentResolver().query(uri, POIProvider.PROJECTION_POI_ALL_COLUMNS, filterJsonString, null, sortOrder);
			return getPOIs(cursor, contentUri.getAuthority());
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error!");
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	public static List<POIManager> findAllAgencyPOIs(Context context, Uri contentUri) {
		Cursor cursor = null;
		try {
			POIFilter poiFilter = new POIFilter();
			String filterJsonString = POIFilter.toJSON(poiFilter).toString();
			final String sortOrder = null;
			final Uri uri = getPOIUri(contentUri);
			cursor = context.getContentResolver().query(uri, POIProvider.PROJECTION_POI_ALL_COLUMNS, filterJsonString, null, sortOrder);
			return getPOIs(cursor, contentUri.getAuthority());
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error!");
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	public static List<POIManager> findPOIsWithLatLngList(Context context, Uri contentUri, double lat, double lng, double aroundDiff, boolean hideDecentOnly) {
		Cursor cursor = null;
		try {
			POIFilter poiFilter = new POIFilter(lat, lng, aroundDiff);
			if (hideDecentOnly) {
				poiFilter.addExtra("decentOnly", true);
			}
			String filterJsonString = POIFilter.toJSON(poiFilter).toString();
			final String sortOrder = null;
			final Uri uri = getPOIUri(contentUri);
			cursor = context.getContentResolver().query(uri, POIProvider.PROJECTION_POI_ALL_COLUMNS, filterJsonString, null, sortOrder);
			return getPOIs(cursor, contentUri.getAuthority());
		} catch (Throwable t) {
			MTLog.w(TAG, t, "Error!");
			return null;
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	private static List<POIManager> getPOIs(Cursor cursor, String authority) {
		List<POIManager> result = new ArrayList<POIManager>();
		if (cursor != null && cursor.getCount() > 0) {
			if (cursor.moveToFirst()) {
				do {
					POIManager fromCursor = POIManager.fromCursorStatic(cursor, authority);
					result.add(fromCursor);
				} while (cursor.moveToNext());
			}
		}
		return result;
	}

	private static Uri getPOIUri(Uri contentUri) {
		return Uri.withAppendedPath(contentUri, POIProvider.POI_CONTENT_DIRECTORY);
	}

	private static Uri getRTSRoutesUri(Uri contentUri) {
		return Uri.withAppendedPath(contentUri, "route");
	}

}
