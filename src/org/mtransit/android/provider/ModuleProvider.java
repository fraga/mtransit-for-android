package org.mtransit.android.provider;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mtransit.android.R;
import org.mtransit.android.commons.ArrayUtils;
import org.mtransit.android.commons.FileUtils;
import org.mtransit.android.commons.LocationUtils.Area;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.PackageManagerUtils;
import org.mtransit.android.commons.PreferenceUtils;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.TimeUtils;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.data.AppStatus;
import org.mtransit.android.commons.data.DefaultPOI;
import org.mtransit.android.commons.data.POI;
import org.mtransit.android.commons.data.POI.POIUtils;
import org.mtransit.android.commons.data.POIStatus;
import org.mtransit.android.commons.provider.AgencyProvider;
import org.mtransit.android.commons.provider.POIDbHelper;
import org.mtransit.android.commons.provider.POIFilter;
import org.mtransit.android.commons.provider.POIProvider;
import org.mtransit.android.commons.provider.POIProvider.POIColumns;
import org.mtransit.android.commons.provider.POIProviderContract;
import org.mtransit.android.commons.provider.StatusFilter;
import org.mtransit.android.commons.provider.StatusProvider;
import org.mtransit.android.commons.provider.StatusProviderContract;
import org.mtransit.android.data.Module;

import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class ModuleProvider extends AgencyProvider implements POIProviderContract, StatusProviderContract {

	private static final String TAG = ModuleProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	@Override
	public String toString() {
		return getLogTag();
	}

	private static final int AGENCY_TYPE = 999;

	private static final String PREF_KEY_LAST_UPDATE_MS = ModuleDbHelper.PREF_KEY_LAST_UPDATE_MS;

	private static final long MODULE_MAX_VALIDITY_IN_MS = 1 * 7 * 24 * 60 * 60 * 1000; // 1 week
	private static final long MODULE_VALIDITY_IN_MS = 1 * 24 * 60 * 60 * 1000; // 1 day

	private static final long MODULE_STATUS_MAX_VALIDITY_IN_MS = 10 * 60 * 1000; // 10 minutes
	private static final long MODULE_STATUS_VALIDITY_IN_MS = 30 * 1000; // 30 seconds
	private static final long MODULE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS = 10 * 1000; // 10 seconds

	private static ModuleDbHelper dbHelper;

	private static int currentDbVersion = -1;

	private static UriMatcher uriMatcher = null;

	public static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = AgencyProvider.getNewUriMatcher(authority);
		StatusProvider.append(URI_MATCHER, authority);
		POIProvider.append(URI_MATCHER, authority);
		return URI_MATCHER;
	}

	private static String authority = null;

	public static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.module_authority);
		}
		return authority;
	}

	private static Uri authorityUri = null;

	public static Uri getAUTHORITYURI(Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	@Override
	public boolean onCreateMT() {
		ping();
		return true;
	}

	@Override
	public void ping() {
	}

	private ModuleDbHelper getDBHelper(Context context) {
		if (dbHelper == null) {
			dbHelper = getNewDbHelper(context);
			currentDbVersion = getCurrentDbVersion();
		} else {
			try {
				if (currentDbVersion != getCurrentDbVersion()) {
					dbHelper.close();
					dbHelper = null;
					return getDBHelper(context);
				}
			} catch (Throwable t) {
				MTLog.d(this, t, "Can't check DB version!");
			}
		}
		return dbHelper;
	}

	@Override
	public SQLiteOpenHelper getDBHelper() {
		return getDBHelper(getContext());
	}

	@Override
	public Cursor queryMT(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		try {
			Cursor cursor = super.queryMT(uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			cursor = POIProvider.queryS(this, uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			cursor = StatusProvider.queryS(this, uri, projection, selection, selectionArgs, sortOrder);
			if (cursor != null) {
				return cursor;
			}
			throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while resolving query '%s'!", uri);
			return null;
		}
	}

	@Override
	public String getSortOrder(Uri uri) {
		String sortOrder = POIProvider.getSortOrderS(this, uri);
		if (sortOrder != null) {
			return sortOrder;
		}
		sortOrder = StatusProvider.getSortOrderS(this, uri);
		if (sortOrder != null) {
			return sortOrder;
		}
		return super.getSortOrder(uri);
	}

	@Override
	public String getTypeMT(Uri uri) {
		String type = POIProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		type = StatusProvider.getTypeS(this, uri);
		if (type != null) {
			return type;
		}
		return super.getTypeMT(uri);
	}

	@Override
	public int updateMT(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		MTLog.w(this, "The update method is not available.");
		return 0;
	}

	@Override
	public Uri insertMT(Uri uri, ContentValues values) {
		MTLog.w(this, "The insert method is not available.");
		return null;
	}

	@Override
	public int deleteMT(Uri uri, String selection, String[] selectionArgs) {
		MTLog.w(this, "The delete method is not available.");
		return 0;
	}

	@Override
	public Cursor getPOI(POIFilter poiFilter) {
		return getPOIModules(poiFilter);
	}

	public Cursor getPOIModules(POIFilter poiFilter) {
		updateModuleDataIfRequired();
		return getPOIFromDB(poiFilter);
	}

	public long getMODULE_MAX_VALIDITY_IN_MS() {
		return MODULE_MAX_VALIDITY_IN_MS;
	}

	public long getMODULE_VALIDITY_IN_MS() {
		return MODULE_VALIDITY_IN_MS;
	}

	public void updateModuleDataIfRequired() {
		long lastUpdateInMs = PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_LAST_UPDATE_MS, 0l);
		long nowInMs = TimeUtils.currentTimeMillis();
		if (lastUpdateInMs + getMODULE_MAX_VALIDITY_IN_MS() < nowInMs) {
			deleteAllModuleData();
			updateAllModuleDataFromWWW(lastUpdateInMs);
			return;
		}
		if (lastUpdateInMs + getMODULE_VALIDITY_IN_MS() < nowInMs) {
			updateAllModuleDataFromWWW(lastUpdateInMs);
		}
	}

	private int deleteAllModuleData() {
		int affectedRows = 0;
		SQLiteDatabase db = null;
		try {
			db = getDBHelper(getContext()).getWritableDatabase();
			affectedRows = db.delete(ModuleDbHelper.T_MODULE, null, null);
		} catch (Throwable t) {
			MTLog.w(this, t, "Error while deleting all module data!");
		}
		return affectedRows;
	}

	private synchronized void updateAllModuleDataFromWWW(long oldLastUpdatedInMs) {
		if (PreferenceUtils.getPrefLcl(getContext(), PREF_KEY_LAST_UPDATE_MS, 0l) > oldLastUpdatedInMs) {
			return; // too late, another thread already updated
		}
		loadDataFromWWW(0); // 0 = 1st try
	}

	private Set<Module> loadDataFromWWW(int tried) {
		try {
			long newLastUpdateInMs = TimeUtils.currentTimeMillis();
			String jsonString = FileUtils.fromFileRes(getContext(), R.raw.modules);
			Set<Module> modules = new HashSet<Module>();
			JSONArray jsonArray = new JSONArray(jsonString);
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jModule = jsonArray.getJSONObject(i);
				Module module = new Module( //
						getAUTHORITY(getContext()), //
						jModule.getString("pkg") //
				);
				module.setId(jModule.getInt("id"));
				module.setName(jModule.getString("name"));
				module.setLat(jModule.getDouble("lat"));
				module.setLng(jModule.getDouble("lng"));
				modules.add(module);
			}
			deleteAllModuleData();
			insertDefaultPOIs(this, modules);
			PreferenceUtils.savePrefLcl(getContext(), PREF_KEY_LAST_UPDATE_MS, newLastUpdateInMs, true); // sync
			return modules;
		} catch (Exception e) {
			MTLog.w(this, e, "INTERNAL ERROR: Unknown Exception");
			return null;
		}
	}

	private static synchronized int insertDefaultPOIs(POIProviderContract provider, Collection<Module> defaultPOIs) {
		int affectedRows = 0;
		SQLiteDatabase db = null;
		try {
			db = provider.getDBHelper().getWritableDatabase();
			db.beginTransaction(); // start the transaction
			if (defaultPOIs != null) {
				for (DefaultPOI defaultPOI : defaultPOIs) {
					final long rowId = db.insert(provider.getPOITable(), POIDbHelper.T_POI_K_ID, defaultPOI.toContentValues());
					if (rowId > 0) {
						affectedRows++;
					}
				}
			}
			db.setTransactionSuccessful(); // mark the transaction as successful
		} catch (Exception e) {
			MTLog.w(TAG, e, "ERROR while applying batch update to the database!");
		} finally {
			try {
				if (db != null) {
					db.endTransaction(); // end the transaction
					db.close();
				}
			} catch (Exception e) {
				MTLog.w(TAG, e, "ERROR while closing the new database!");
			}
		}
		return affectedRows;
	}

	@Override
	public Cursor getPOIFromDB(POIFilter poiFilter) {
		return POIProvider.getPOIFromDB(poiFilter, this);
	}

	@Override
	public POIStatus getNewStatus(StatusFilter filter) {
		if (!(filter instanceof AppStatus.AppStatusFilter)) {
			return null;
		}
		final AppStatus.AppStatusFilter moduleStatusFilter = (AppStatus.AppStatusFilter) filter;
		return getNewModuleStatus(moduleStatusFilter);
	}

	public POIStatus getNewModuleStatus(AppStatus.AppStatusFilter filter) {
		long newLastUpdateInMs = TimeUtils.currentTimeMillis();
		boolean appInstalled = PackageManagerUtils.isAppInstalled(getContext(), filter.getPkg());
		return new AppStatus(filter.getTargetUUID(), newLastUpdateInMs, getStatusMaxValidityInMs(), appInstalled);
	}

	@Override
	public void cacheStatus(POIStatus newStatusToCache) {
		StatusProvider.cacheStatusS(getContext(), this, newStatusToCache);
	}

	@Override
	public POIStatus getCachedStatus(String targetUUID) {
		return StatusProvider.getCachedStatusS(this, targetUUID);
	}

	@Override
	public boolean purgeUselessCachedStatuses() {
		return StatusProvider.purgeUselessCachedStatuses(getContext(), this);
	}

	@Override
	public boolean deleteCachedStatus(int cachedStatusId) {
		return StatusProvider.deleteCachedStatus(getContext(), this, cachedStatusId);
	}

	@Override
	public Uri getAuthorityUri() {
		return getAUTHORITYURI(getContext());
	}

	@Override
	public String getStatusDbTableName() {
		return ModuleDbHelper.T_MODULE_STATUS;
	}

	@Override
	public boolean isAgencyDeployed() {
		return SqlUtils.isDbExist(getContext(), getDbName());
	}

	@Override
	public boolean isAgencySetupRequired() {
		boolean setupRequired = false;
		if (currentDbVersion > 0 && currentDbVersion != getCurrentDbVersion()) {
			setupRequired = true;
		} else if (!SqlUtils.isDbExist(getContext(), getDbName())) {
			setupRequired = true;
		} else if (SqlUtils.getCurrentDbVersion(getContext(), getDbName()) != getCurrentDbVersion()) {
			setupRequired = true;
		}
		return setupRequired;
	}

	@Override
	public UriMatcher getAgencyUriMatcher() {
		return getURIMATCHER(getContext());
	}

	@Override
	public int getAgencyType() {
		return AGENCY_TYPE;
	}

	@Override
	public int getStatusType() {
		return POI.ITEM_STATUS_TYPE_APP;
	}

	@Override
	public int getAgencyVersion() {
		return getCurrentDbVersion();
	}

	@Override
	public int getAgencyLabelResId() {
		return R.string.module_label;
	}

	@Override
	public int getAgencyShortNameResId() {
		return R.string.module_short_name;
	}

	@Override
	public Area getAgencyArea(Context context) {
		return new Area(-90.0, +90.0, -180.0, +180.0); // the whole world
	}

	public String getDbName() {
		return ModuleDbHelper.DB_NAME;
	}

	@Override
	public UriMatcher getURIMATCHER() {
		return getURIMATCHER(getContext());
	}

	public int getCurrentDbVersion() {
		return ModuleDbHelper.getDbVersion(getContext());
	}

	public ModuleDbHelper getNewDbHelper(Context context) {
		return new ModuleDbHelper(context.getApplicationContext());
	}

	@Override
	public long getStatusMaxValidityInMs() {
		return MODULE_STATUS_MAX_VALIDITY_IN_MS;
	}

	@Override
	public long getStatusValidityInMs() {
		return MODULE_STATUS_VALIDITY_IN_MS;
	}

	@Override
	public long getMinDurationBetweenRefreshInMs() {
		return MODULE_STATUS_MIN_DURATION_BETWEEN_REFRESH_IN_MS;
	}

	private static Map<String, String> poiProjectionMap;

	@Override
	public Map<String, String> getPOIProjectionMap() {
		if (poiProjectionMap == null) {
			poiProjectionMap = getNewPoiProjectionMap(getAUTHORITY(getContext()));
		}
		return poiProjectionMap;
	}

	public static Map<String, String> getNewPoiProjectionMap(String authority) {
		HashMap<String, String> poiProjectionMap = new HashMap<String, String>();
		poiProjectionMap.put(POIColumns.T_POI_K_UUID_META, SqlUtils.concatenate("'" + POIUtils.UID_SEPARATOR + "'", //
				"'" + authority + "'", //
				ModuleDbHelper.T_MODULE + "." + ModuleDbHelper.T_MODULE_PKG //
		) + " AS " + POIColumns.T_POI_K_UUID_META);
		poiProjectionMap.put(POIColumns.T_POI_K_ID, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_ID + " AS " + POIColumns.T_POI_K_ID);
		poiProjectionMap.put(POIColumns.T_POI_K_NAME, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_NAME + " AS " + POIColumns.T_POI_K_NAME);
		poiProjectionMap.put(POIColumns.T_POI_K_LAT, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_LAT + " AS " + POIColumns.T_POI_K_LAT);
		poiProjectionMap.put(POIColumns.T_POI_K_LNG, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_LNG + " AS " + POIColumns.T_POI_K_LNG);
		poiProjectionMap.put(POIColumns.T_POI_K_TYPE, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_TYPE + " AS " + POIColumns.T_POI_K_TYPE);
		poiProjectionMap.put(POIColumns.T_POI_K_STATUS_TYPE, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_STATUS_TYPE + " AS "
				+ POIColumns.T_POI_K_STATUS_TYPE);
		poiProjectionMap.put(POIColumns.T_POI_K_ACTIONS_TYPE, POIDbHelper.T_POI + "." + POIDbHelper.T_POI_K_ACTIONS_TYPE + " AS "
				+ POIColumns.T_POI_K_ACTIONS_TYPE);
		poiProjectionMap.put(ModuleColumns.T_MODULE_K_PKG, ModuleDbHelper.T_MODULE + "." + ModuleDbHelper.T_MODULE_PKG + " AS " + ModuleColumns.T_MODULE_K_PKG);
		return poiProjectionMap;
	}

	public static final String[] PROJECTION_MODULE = new String[] { ModuleColumns.T_MODULE_K_PKG };

	public static final String[] PROJECTION_MODULE_POI = ArrayUtils.addAll(POIProvider.PROJECTION_POI, PROJECTION_MODULE);

	@Override
	public String[] getPOIProjection() {
		return PROJECTION_MODULE_POI;
	}

	@Override
	public String getPOITable() {
		return ModuleDbHelper.T_MODULE;
	}

	public static class ModuleColumns {
		public static final String T_MODULE_K_PKG = POIColumns.getFkColumnName("pkg");
	}

}