package org.yaxim.androidclient.map;

import java.util.ArrayList;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.data.RosterProvider;

import us.jsy.map.CrossOverlay;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.ReticleDrawMode;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;

public class MapWindow extends MapActivity {
	private static final String TAG = "yaxim.MapWindow";

	private ContentObserver mContactObserver = new ContactObserver();
	
	private MapView mapView;
	private ImageItemizedOverlay itemizedoverlay;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
	
		setContentView(R.layout.map_act);
		
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		mapView.setTraffic(true);
		mapView.setReticleDrawMode(ReticleDrawMode.DRAW_RETICLE_OVER);
		mapView.displayZoomControls(true);
		// mZoom = (ZoomControls)mapView.getZoomControls();
		// ll.addView(mZoom);
		
		{
			Drawable drawable = this.getResources().getDrawable(R.drawable.ic_status_available);
			itemizedoverlay = new ImageItemizedOverlay(drawable, this);
			mapView.getOverlays().add(itemizedoverlay);
		}
		
		final MyLocationOverlay myLocationOverlay = new MyLocationOverlay(this, mapView) {
			@Override
			public synchronized void onLocationChanged(Location loc) {
				super.onLocationChanged(loc);
//				mapView.getController().animateTo(
//						new GeoPoint((int)(loc.getLatitude()*1e6), (int)(loc.getLongitude()*1e6))
//						);
			}			
		};
		
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.enableCompass();
		
		mapView.getOverlays().add(myLocationOverlay);
		mapView.getOverlays().add(new CrossOverlay());

		myLocationOverlay.runOnFirstFix(new Runnable() {
			@Override
			public void run() {
				mapView.getController().animateTo(myLocationOverlay.getMyLocation());
			}
		});
		
		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mContactObserver);
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateContactStatus();
	}

	private void updateContactStatus() {
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI, null,
					null, null, null);
//		int MODE_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MODE);
//		int MSG_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MESSAGE);
		int ALIAS_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.ALIAS);
		int JID_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.JID);
		
		int LAT_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.LAT);
		int LON_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.LON);
		
		ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();;
		for (int i = 0; i < cursor.getCount(); i++) {
			cursor.moveToPosition(i);
			
//			int status_mode = cursor.getInt(MODE_IDX);
//			String status_message = cursor.getString(MSG_IDX);
			String alias = cursor.getString(ALIAS_IDX);
			String jid = cursor.getString(JID_IDX);
			
			int lat = cursor.getInt(LAT_IDX);
			int lon = cursor.getInt(LON_IDX);
			
			boolean set = ! cursor.isNull(LAT_IDX);
			
			Log.d(TAG, i + " contact: " + alias + " " + jid + " " + lat + "," + lon + "," + set);
			if (set || i == 0) {
				OverlayItem item = new OverlayItem(new GeoPoint(lat, lon), alias, jid);
//				item.setMarker(StatusMode.values()[status_mode].getDrawableId());
				items.add(item);
			}
		}
		cursor.close();
		
		this.itemizedoverlay.setOverlay(items);
		mapView.invalidate();
	}

	private class ContactObserver extends ContentObserver {
		public ContactObserver() {
			super(new Handler());
		}

		public void onChange(boolean selfChange) {
			Log.d(TAG, "ContactObserver.onChange: " + selfChange);
			updateContactStatus();
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}
}
