package org.mtransit.android.provider;

import java.util.HashMap;

import org.mtransit.android.R;
import org.mtransit.android.commons.MTLog;
import org.mtransit.android.commons.SqlUtils;
import org.mtransit.android.commons.UriUtils;
import org.mtransit.android.commons.provider.MTContentProvider;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

public class FavoriteProvider extends MTContentProvider {

	private static final String TAG = FavoriteProvider.class.getSimpleName();

	@Override
	public String getLogTag() {
		return TAG;
	}

	private static final int FAVORITE = 100;
	private static final int FAVORITE_ID = 101;

	private static final HashMap<String, String> FAVORITE_PROJECTION_MAP;
	static {
		HashMap<String, String> map;
		map = new HashMap<String, String>();
		map.put(FavoriteColumns.T_FAVORITE_K_ID, FavoriteDbHelper.T_FAVORITE + "." + FavoriteDbHelper.T_FAVORITE_K_ID + " AS "
				+ FavoriteColumns.T_FAVORITE_K_ID);
		map.put(FavoriteColumns.T_FAVORITE_K_TYPE, FavoriteDbHelper.T_FAVORITE + "." + FavoriteDbHelper.T_FAVORITE_K_TYPE + " AS "
				+ FavoriteColumns.T_FAVORITE_K_TYPE);
		map.put(FavoriteColumns.T_FAVORITE_K_FK_ID, FavoriteDbHelper.T_FAVORITE + "." + FavoriteDbHelper.T_FAVORITE_K_FK_ID + " AS "
				+ FavoriteColumns.T_FAVORITE_K_FK_ID);
		FAVORITE_PROJECTION_MAP = map;
	}

	public static final String[] PROJECTION_FAVORITE = new String[] { FavoriteColumns.T_FAVORITE_K_ID, FavoriteColumns.T_FAVORITE_K_TYPE,
			FavoriteColumns.T_FAVORITE_K_FK_ID };

	public static UriMatcher getNewUriMatcher(String authority) {
		UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(authority, "favorite", FAVORITE);
		URI_MATCHER.addURI(authority, "favorite/#", FAVORITE_ID);
		return URI_MATCHER;
	}

	private static String authority;

	private static Uri authorityUri;

	private static UriMatcher uriMatcher;

	private static FavoriteDbHelper dbHelper;

	private static int currentDbVersion = -1;

	@Override
	public boolean onCreateMT() {
		return true;
	}

	private FavoriteDbHelper getDBHelper(Context context) {
		if (dbHelper == null) { // initialize
			dbHelper = getNewDbHelper(context);
			currentDbVersion = getCurrentDbVersion();
		} else { // reset
			try {
				if (currentDbVersion != getCurrentDbVersion()) {
					dbHelper.close();
					dbHelper = null;
					return getDBHelper(context);
				}
			} catch (Exception e) { // fail if locked, will try again later
				MTLog.w(this, e, "Can't check DB version!");
			}
		}
		return dbHelper;
	}

	public FavoriteDbHelper getNewDbHelper(Context context) {
		return new FavoriteDbHelper(context.getApplicationContext());
	}

	public int getCurrentDbVersion() {
		return FavoriteDbHelper.getDbVersion();
	}

	private static Uri getAuthorityUri(Context context) {
		if (authorityUri == null) {
			authorityUri = UriUtils.newContentUri(getAUTHORITY(context));
		}
		return authorityUri;
	}

	public static UriMatcher getURIMATCHER(Context context) {
		if (uriMatcher == null) {
			uriMatcher = getNewUriMatcher(getAUTHORITY(context));
		}
		return uriMatcher;
	}

	/**
	 * Override if multiple {@link FavoriteProvider} implementations in same app.
	 */
	public static String getAUTHORITY(Context context) {
		if (authority == null) {
			authority = context.getResources().getString(R.string.favorite_authority);
		}
		return authority;
	}

	@Override
	public Cursor queryMT(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteDatabase db = null;
		try {
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			switch (getURIMATCHER(getContext()).match(uri)) {
			case FAVORITE:
				MTLog.v(this, "query>FAVORITE");
				qb.setTables(FavoriteDbHelper.T_FAVORITE);
				qb.setProjectionMap(FAVORITE_PROJECTION_MAP);
				break;
			case FAVORITE_ID:
				MTLog.v(this, "query>FAVORITE_ID");
				qb.setTables(FavoriteDbHelper.T_FAVORITE);
				qb.setProjectionMap(FAVORITE_PROJECTION_MAP);
				selection = FavoriteDbHelper.T_FAVORITE + "." + FavoriteDbHelper.T_FAVORITE_K_ID + "=" + uri.getPathSegments().get(1);
				break;
			default:
				throw new IllegalArgumentException(String.format("Unknown URI (query): '%s'", uri));
			}
			if (TextUtils.isEmpty(sortOrder)) {
				sortOrder = getSortOrder(uri);
			}
			db = getDBHelper(getContext()).getReadableDatabase();
			Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, null);
			if (cursor != null) {
				cursor.setNotificationUri(getContext().getContentResolver(), uri);
			}
			return cursor;
		} catch (Exception e) {
			MTLog.w(this, e, "Error while resolving query '%s'!", uri);
			return null;
		} finally {
			SqlUtils.closeQuietly(db);
		}
	}

	public String getSortOrder(Uri uri) {
		switch (getURIMATCHER(getContext()).match(uri)) {
		case FAVORITE:
		case FAVORITE_ID:
			return null;
		default:
			throw new IllegalArgumentException(String.format("Unknown URI (order): '%s'", uri));
		}
	}

	@Override
	public String getTypeMT(Uri uri) {
		switch (getURIMATCHER(getContext()).match(uri)) {
		case FAVORITE:
		case FAVORITE_ID:
			return null;
		default:
			throw new IllegalArgumentException(String.format("Unknown URI (type): '%s'", uri));
		}
	}

	@Override
	public int deleteMT(Uri uri, String selection, String[] selectionArgs) {
		int affectedRows = 0;
		SQLiteDatabase db = null;
		try {
			switch (getURIMATCHER(getContext()).match(uri)) {
			case FAVORITE:
				db = getDBHelper(getContext()).getWritableDatabase();
				affectedRows = db.delete(FavoriteDbHelper.T_FAVORITE, selection, selectionArgs);
				break;
			case FAVORITE_ID:
				selection = FavoriteDbHelper.T_FAVORITE + "." + FavoriteDbHelper.T_FAVORITE_K_ID + "=" + uri.getPathSegments().get(1);
				db = getDBHelper(getContext()).getWritableDatabase();
				affectedRows = db.delete(FavoriteDbHelper.T_FAVORITE, selection, null);
				break;
			default:
				throw new IllegalArgumentException(String.format("Unknown URI (delete): '%s'", uri));
			}
			if (affectedRows > 0) {
				getContext().getContentResolver().notifyChange(uri, null);
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while processing delete query %s!", uri);
		} finally {
			SqlUtils.closeQuietly(db);
		}
		return affectedRows;
	}

	@Override
	public int updateMT(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		MTLog.w(this, "The update method is not available.");
		return 0; // no row affected
	}

	public static final String FAVORITE_CONTENT_DIRECTORY = "favorite";

	public static Uri getFavoriteContentUri(Context context) {
		return Uri.withAppendedPath(getAuthorityUri(context), FAVORITE_CONTENT_DIRECTORY);
	}

	@Override
	public Uri insertMT(Uri uri, ContentValues values) {
		SQLiteDatabase db = null;
		try {
			Uri insertUri = null;
			long newRowId;
			switch (getURIMATCHER(getContext()).match(uri)) {
			case FAVORITE:
				MTLog.v(this, "insert>FAVORITE");
				db = getDBHelper(getContext()).getWritableDatabase();
				newRowId = db.insert(FavoriteDbHelper.T_FAVORITE, FavoriteDbHelper.T_FAVORITE_K_FK_ID, values);
				if (newRowId > 0) {
					insertUri = ContentUris.withAppendedId(getFavoriteContentUri(getContext()), newRowId);
				}
				break;
			default:
				throw new IllegalArgumentException(String.format("Unknown URI (insert): '%s'", uri));
			}
			if (insertUri == null) {
				throw new SQLException(String.format("Failed to insert row into %s", uri));
			} else {
				getContext().getContentResolver().notifyChange(insertUri, null);
				return insertUri;
			}
		} catch (Exception e) {
			MTLog.w(this, e, "Error while resolving insert query '%s'!", uri);
			return null;
		} finally {
			SqlUtils.closeQuietly(db);
		}
	}

	public static class FavoriteColumns {
		public static final String T_FAVORITE_K_ID = BaseColumns._ID;
		public static final String T_FAVORITE_K_FK_ID = "fk_id";
		public static final String T_FAVORITE_K_TYPE = "type";
	}
}
