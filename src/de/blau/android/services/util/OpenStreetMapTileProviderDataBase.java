package de.blau.android.services.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import org.acra.ACRA;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import de.blau.android.services.exceptions.EmptyCacheException;
import de.blau.android.views.util.OpenStreetMapViewConstants;

/**
 * The OpenStreetMapTileProviderDataBase contains a table with info for the available renderers and one for
 * the available tiles in the file system cache.<br/>
 * This class was taken from OpenStreetMapViewer (original package org.andnav.osm) in 2010
 * by Marcus Wolschon to be integrated into the de.blau.androin
 * OSMEditor. 
 * @author Nicolas Gramlich
 * @author Marcus Wolschon <Marcus@Wolschon.biz
 */
class OpenStreetMapTileProviderDataBase implements OpenStreetMapViewConstants {

	private static final String DATABASE_NAME = "osmaptilefscache_db";
	private static final int DATABASE_VERSION = 4;

	private static final String T_FSCACHE = "t_fscache";	
	private static final String T_FSCACHE_RENDERER_ID = "rendererID";
	private static final String T_FSCACHE_ZOOM_LEVEL = "zoomLevel";
	private static final String T_FSCACHE_TILE_X = "tileX";
	private static final String T_FSCACHE_TILE_Y = "tileY";
//	private static final String T_FSCACHE_LINK = "link";			// TODO store link (multiple use for similar tiles)
	private static final String T_FSCACHE_TIMESTAMP = "timestamp";
	private static final String T_FSCACHE_USAGECOUNT = "countused";
	private static final String T_FSCACHE_FILESIZE = "filesize";
	
	private static final String T_RENDERER               = "t_renderer";
	private static final String T_RENDERER_ID            = "id";
	private static final String T_RENDERER_NAME          = "name";
	private static final String T_RENDERER_BASE_URL      = "base_url";
	private static final String T_RENDERER_ZOOM_MIN      = "zoom_min";
	private static final String T_RENDERER_ZOOM_MAX      = "zoom_max";
	private static final String T_RENDERER_TILE_SIZE_LOG = "tile_size_log";
	
	private static final String T_FSCACHE_CREATE_COMMAND = "CREATE TABLE IF NOT EXISTS " + T_FSCACHE
	+ " (" 
	+ T_FSCACHE_RENDERER_ID + " VARCHAR(255) NOT NULL,"
	+ T_FSCACHE_ZOOM_LEVEL + " INTEGER NOT NULL,"
	+ T_FSCACHE_TILE_X + " INTEGER NOT NULL,"
	+ T_FSCACHE_TILE_Y + " INTEGER NOT NULL,"
	+ T_FSCACHE_TIMESTAMP + " DATE NOT NULL,"
	+ T_FSCACHE_USAGECOUNT + " INTEGER NOT NULL DEFAULT 1,"
	+ T_FSCACHE_FILESIZE + " INTEGER NOT NULL,"
	+ " PRIMARY KEY(" 	+ T_FSCACHE_RENDERER_ID + ","
						+ T_FSCACHE_ZOOM_LEVEL + ","
						+ T_FSCACHE_TILE_X + ","
						+ T_FSCACHE_TILE_Y + ")"
	+ ");";
	
	private static final String T_RENDERER_CREATE_COMMAND = "CREATE TABLE IF NOT EXISTS " + T_RENDERER
	+ " ("
	+ T_RENDERER_ID + " VARCHAR(255) PRIMARY KEY,"
	+ T_RENDERER_NAME + " VARCHAR(255),"
	+ T_RENDERER_BASE_URL + " VARCHAR(255),"
	+ T_RENDERER_ZOOM_MIN + " INTEGER NOT NULL,"
	+ T_RENDERER_ZOOM_MAX + " INTEGER NOT NULL,"
	+ T_RENDERER_TILE_SIZE_LOG + " INTEGER NOT NULL"
	+ ");";

	private static final String SQL_ARG = "=?";
	private static final String AND = " AND ";

	private static final String T_FSCACHE_WHERE = T_FSCACHE_RENDERER_ID + SQL_ARG + AND
												+ T_FSCACHE_ZOOM_LEVEL + SQL_ARG + AND
												+ T_FSCACHE_TILE_X + SQL_ARG + AND
												+ T_FSCACHE_TILE_Y + SQL_ARG;
	
	private static final String T_FSCACHE_WHERE_INVALID = T_FSCACHE_RENDERER_ID + SQL_ARG + AND
														+ T_FSCACHE_ZOOM_LEVEL + SQL_ARG + AND
														+ T_FSCACHE_TILE_X + SQL_ARG + AND
														+ T_FSCACHE_TILE_Y + SQL_ARG + AND
														+ T_FSCACHE_FILESIZE + "=0";
	
	private static final String T_FSCACHE_SELECT_LEAST_USED = "SELECT " + T_FSCACHE_RENDERER_ID  + "," + T_FSCACHE_ZOOM_LEVEL + "," + T_FSCACHE_TILE_X + "," + T_FSCACHE_TILE_Y + "," + T_FSCACHE_FILESIZE + " FROM " + T_FSCACHE + " WHERE "  + T_FSCACHE_USAGECOUNT + " = (SELECT MIN(" + T_FSCACHE_USAGECOUNT + ") FROM "  + T_FSCACHE + ")";
	private static final String T_FSCACHE_SELECT_OLDEST = "SELECT " + T_FSCACHE_RENDERER_ID  + "," + T_FSCACHE_ZOOM_LEVEL + "," + T_FSCACHE_TILE_X + "," + T_FSCACHE_TILE_Y + "," + T_FSCACHE_FILESIZE + " FROM " + T_FSCACHE + " WHERE " + T_FSCACHE_FILESIZE + " > 0 ORDER BY " + T_FSCACHE_TIMESTAMP + " ASC";
	
	// ===========================================================
	// Fields
	// ===========================================================

	protected final Context mCtx;
	protected final OpenStreetMapTileFilesystemProvider mFSProvider;
	protected final SQLiteDatabase mDatabase;
	protected final SimpleDateFormat DATE_FORMAT_ISO8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US);

	// ===========================================================
	// Constructors
	// ===========================================================

	public OpenStreetMapTileProviderDataBase(final Context context, OpenStreetMapTileFilesystemProvider openStreetMapTileFilesystemProvider) {
		Log.i("OSMTileProviderDB", "creating database instance");
		mCtx = context;
		mFSProvider = openStreetMapTileFilesystemProvider;
		mDatabase = new AndNavDatabaseHelper(context).getWritableDatabase();
	}

	public boolean hasTile(final OpenStreetMapTile aTile) {
		boolean existed = false;
		if (mDatabase.isOpen()) {
			final String[] args = new String[]{"" + aTile.rendererID, "" + aTile.zoomLevel, "" + aTile.x, "" + aTile.y};
			final Cursor c = mDatabase.query(T_FSCACHE, new String[]{T_FSCACHE_RENDERER_ID}, T_FSCACHE_WHERE, args, null, null, null);
			existed = c.getCount() > 0;
			c.close();
		}
		return existed;
	}
	
	public boolean isInvalid(final OpenStreetMapTile aTile) {
		boolean existed = false;
		if (mDatabase.isOpen()) {
			final String[] args = new String[]{"" + aTile.rendererID, "" + aTile.zoomLevel, "" + aTile.x, "" + aTile.y};
			final Cursor c = mDatabase.query(T_FSCACHE, new String[]{T_FSCACHE_RENDERER_ID}, T_FSCACHE_WHERE_INVALID, args, null, null, null);
			existed = c.getCount() > 0;
			c.close();
		}
		return existed;
	}
	
	public boolean incrementUse(final OpenStreetMapTile aTile) {
		boolean ret = false;
		if (mDatabase.isOpen()) {
			final String[] args = new String[]{"" + aTile.rendererID, "" + aTile.zoomLevel, "" + aTile.x, "" + aTile.y};
			ContentValues cv = new ContentValues();
			cv.put(T_FSCACHE_USAGECOUNT, T_FSCACHE_USAGECOUNT + " + 1");
			cv.put(T_FSCACHE_TIMESTAMP, getNowAsIso8601());
			
			try {
				ret = mDatabase.update(T_FSCACHE, cv, T_FSCACHE_WHERE, args) > 0;
			} catch (Exception e) {
				if (e instanceof NullPointerException) {
					// just log ... likely these are really spurious
					Log.e(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "NPE in incrementUse");
				} else {
					ACRA.getErrorReporter().putCustomData("STATUS","NOCRASH");
					ACRA.getErrorReporter().handleException(e);	
				}
			}
		}
		return ret;
	}

	public synchronized int addTileOrIncrement(final OpenStreetMapTile aTile, final int aByteFilesize) { 
		// there seems to be danger for  a race condition here
		if (incrementUse(aTile)) {
			if(DEBUGMODE)
				Log.d(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Tile existed");
			return 0;
		} else {
			insertNewTileInfo(aTile, aByteFilesize);
			return aByteFilesize;
		}
	}

	private void insertNewTileInfo(final OpenStreetMapTile aTile, final int aByteFilesize) {
		if (mDatabase.isOpen()) {
			final ContentValues cv = new ContentValues();
			cv.put(T_FSCACHE_RENDERER_ID, aTile.rendererID);
			cv.put(T_FSCACHE_ZOOM_LEVEL, aTile.zoomLevel);
			cv.put(T_FSCACHE_TILE_X, aTile.x);
			cv.put(T_FSCACHE_TILE_Y, aTile.y);
			cv.put(T_FSCACHE_TIMESTAMP, getNowAsIso8601());
			cv.put(T_FSCACHE_FILESIZE, aByteFilesize);
			mDatabase.insert(T_FSCACHE, null, cv);
		}
	}
	
	long deleteOldest(final int pSizeNeeded) throws EmptyCacheException {
		if (!mDatabase.isOpen()) { // this seems to happen, protect against crashing
			Log.e(OpenStreetMapTileFilesystemProvider.DEBUGTAG,"deleteOldest called on closed DB");
			return 0;
		}
		final Cursor c = mDatabase.rawQuery(T_FSCACHE_SELECT_OLDEST, null);
		final ArrayList<OpenStreetMapTile> deleteFromDB = new ArrayList<OpenStreetMapTile>();
		long sizeGained = 0;
		if(c != null){
			OpenStreetMapTile tileToBeDeleted; 
			if(c.moveToFirst()){
				do{
					final int sizeItem = c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_FILESIZE));
					sizeGained += sizeItem;
					
					tileToBeDeleted = new OpenStreetMapTile(c.getString(c.getColumnIndexOrThrow(T_FSCACHE_RENDERER_ID)),c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_ZOOM_LEVEL)),
							c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_TILE_X)),c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_TILE_Y)));

					deleteFromDB.add(tileToBeDeleted);
					Log.d("OpenStreetMapTileProvierDatabase","deleteOldest " + tileToBeDeleted.toString());
					// mCtx.deleteFile(mFSProvider.buildPath(tileToBeDeleted));
					(new File(mFSProvider.buildPath(tileToBeDeleted))).delete();
					
					if(DEBUGMODE)
						Log.i(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Deleted from FS: " + mFSProvider.buildPath(tileToBeDeleted) + " for " + sizeItem + " Bytes");
				}while(c.moveToNext() && sizeGained < pSizeNeeded);
			}else{
				c.close();
				throw new EmptyCacheException("Cache seems to be empty.");
			}
			c.close();

			for(OpenStreetMapTile t : deleteFromDB) {
				final String[] args = new String[]{"" + t.rendererID, "" + t.zoomLevel, "" + t.x, "" + t.y};
				try {
					if (mDatabase.isOpen()) { // note we have already deleted the on disks tiles so it is not really an issue if we don't delete everything from the DB
						mDatabase.delete(T_FSCACHE, T_FSCACHE_WHERE, args);
					}
				} catch (Exception e) {
					if (e instanceof NullPointerException) {
						// just log ... likely these are really spurious
						Log.e(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "NPE in deleteOldest");
					} else {
						ACRA.getErrorReporter().putCustomData("STATUS","NOCRASH");
						ACRA.getErrorReporter().handleException(e);	
					}
				}
			}
		}
		return sizeGained;
	}
	
	/**
	 * Delete all tiles from cache for a specific renderer
	 * @param rendererID
	 * @throws EmptyCacheException
	 */
	public void flushCache(String rendererID) throws EmptyCacheException {
		Log.d(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Flushing cache for " + rendererID); 
		final Cursor c = mDatabase.rawQuery("SELECT " + T_FSCACHE_ZOOM_LEVEL + "," + T_FSCACHE_TILE_X + "," + T_FSCACHE_TILE_Y + "," + T_FSCACHE_FILESIZE + " FROM " + T_FSCACHE + " WHERE " + T_FSCACHE_RENDERER_ID + "='" + rendererID + "' ORDER BY " + T_FSCACHE_TIMESTAMP + " ASC", null);
		final ArrayList<OpenStreetMapTile> deleteFromDB = new ArrayList<OpenStreetMapTile>();
		long sizeGained = 0;
		if(c != null){
			OpenStreetMapTile tileToBeDeleted; 
			if(c.moveToFirst()){
				do{
					final int sizeItem = c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_FILESIZE));
					sizeGained += sizeItem;
				
					
					tileToBeDeleted = new OpenStreetMapTile(rendererID,c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_ZOOM_LEVEL)),
							c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_TILE_X)),c.getInt(c.getColumnIndexOrThrow(T_FSCACHE_TILE_Y)));
					Log.d("OpenStreetMapTileProvierDatabase","deleteOldest " + tileToBeDeleted.toString());
			
					deleteFromDB.add(tileToBeDeleted);
					
					if (sizeItem > 0) { // 0 size marker for invalid tiles
						// mCtx.deleteFile(mFSProvider.buildPath(tileToBeDeleted));
						(new File(mFSProvider.buildPath(tileToBeDeleted))).delete();
					}
					if(DEBUGMODE)
						Log.i(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Deleted from FS: " + mFSProvider.buildPath(tileToBeDeleted) + " for " + sizeItem + " Bytes");
				}while(c.moveToNext());
			}else{
				c.close();
				throw new EmptyCacheException("Cache seems to be empty.");
			}
			c.close();

			for(OpenStreetMapTile t : deleteFromDB) {
				final String[] args = new String[]{"" + t.rendererID, "" + t.zoomLevel, "" + t.x, "" + t.y};
				mDatabase.delete(T_FSCACHE, T_FSCACHE_WHERE, args);
			}
		}
	}

	// ===========================================================
	// Methods
	// ===========================================================
	private String TMP_COLUMN = "tmp"; 
	public int getCurrentFSCacheByteSize() {
		int ret = 0;
		if (mDatabase.isOpen()) {
			final Cursor c = mDatabase.rawQuery("SELECT SUM(" + T_FSCACHE_FILESIZE + ") AS " + TMP_COLUMN + " FROM " + T_FSCACHE, null);
			if(c != null){
				if(c.moveToFirst()){
					ret = c.getInt(c.getColumnIndexOrThrow(TMP_COLUMN));
				}
				c.close();
			}
		}

		return ret;
	}

	/**
	 * Get at the moment within ISO8601 format.
	 * @return
	 * Date and time in ISO8601 format.
	 */
	private String getNowAsIso8601() {
		return DATE_FORMAT_ISO8601.format(new Date(System.currentTimeMillis()));
	} 

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================

	private class AndNavDatabaseHelper extends SQLiteOpenHelper {
		AndNavDatabaseHelper(final Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			try {
				db.execSQL(T_RENDERER_CREATE_COMMAND);
				db.execSQL(T_FSCACHE_CREATE_COMMAND);
			} catch (SQLException e) {
				Log.w(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Problem creating database", e);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if(DEBUGMODE)
				Log.w(OpenStreetMapTileFilesystemProvider.DEBUGTAG, "Upgrading database from version " + oldVersion + " to " + newVersion + ", which will destroy all old data");

			db.execSQL("DROP TABLE IF EXISTS " + T_FSCACHE);

			onCreate(db);
		}
	}

	/**
	 * Close the DB handle
	 */
	public void close() {
		mDatabase.close();
	}


}