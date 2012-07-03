package org.yaxim.androidclient.map;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.yaxim.androidclient.R;
import org.yaxim.androidclient.YaximApplication;
import org.yaxim.androidclient.chat.XMPPChatServiceAdapter;
import org.yaxim.androidclient.data.ChatProvider;
import org.yaxim.androidclient.data.ChatProvider.ChatConstants;
import org.yaxim.androidclient.data.RosterProvider;
import org.yaxim.androidclient.service.IXMPPChatService;
import org.yaxim.androidclient.service.XMPPService;

import us.jsy.map.CrossOverlay;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.ReticleDrawMode;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;

@SuppressWarnings("deprecation") /* recent ClipboardManager only available since API 11 */
public class MapWindow extends MapActivity implements OnKeyListener,
		TextWatcher {

	public static final String INTENT_EXTRA_USERNAME = MapWindow.class.getName() + ".username";
	public static final String INTENT_EXTRA_MESSAGE = MapWindow.class.getName() + ".message";
	
	private static final String TAG = "yaxim.ChatWindow";
	private static final String[] PROJECTION_FROM = new String[] {
			ChatProvider.ChatConstants._ID, ChatProvider.ChatConstants.DATE,
			ChatProvider.ChatConstants.DIRECTION, ChatProvider.ChatConstants.JID,
			ChatProvider.ChatConstants.MESSAGE, ChatProvider.ChatConstants.DELIVERY_STATUS };

	private static final int[] PROJECTION_TO = new int[] { R.id.chat_date,
			R.id.chat_from, R.id.chat_message };

	private ContentObserver mContactObserver = new ContactObserver();
	private ImageView mStatusMode;
	private TextView mTitle;
	private TextView mSubTitle;
	private Button mSendButton = null;
	private EditText mChatInput = null;
	private String mWithJabberID = null;
	private String mUserScreenName = null;
	private Intent mServiceIntent;
	private ServiceConnection mServiceConnection;
	private XMPPChatServiceAdapter mServiceAdapter;
	private int mChatFontSize;
	private MapView mapView;
	private ImageItemizedOverlay itemizedoverlay;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(YaximApplication.getConfig(this).getTheme());
		super.onCreate(savedInstanceState);
	
		mChatFontSize = Integer.valueOf(YaximApplication.getConfig(this).chatFontSize);

//		requestWindowFeature(Window.FEATURE_ACTION_BAR);
		setContentView(R.layout.map_act);
		
//		ll = (LinearLayout) findViewById(R.id.zoomview);
		mapView = (MapView) findViewById(R.id.mapview);
		mapView.setBuiltInZoomControls(true);
		// mapView.setTraffic(true);
		mapView.setReticleDrawMode(ReticleDrawMode.DRAW_RETICLE_OVER);
		mapView.displayZoomControls(true);
		// mZoom = (ZoomControls)mapView.getZoomControls();
		// ll.addView(mZoom);

//		Drawable defaultMarker = this.getResources()
//			.getDrawable(android.R.drawable.ic_notification_overlay);
		
//		if (dataUri != null)
		{
			Drawable drawable = this.getResources().getDrawable(R.drawable.ic_status_available);
			itemizedoverlay = new ImageItemizedOverlay(drawable, this);
			
			GeoPoint point = new GeoPoint(19240000,-99120000);
			OverlayItem overlayitem = new OverlayItem(point, "Hola, Mundo!", "I'm in Mexico City!");
			
			itemizedoverlay.addOverlay(overlayitem);
			mapView.getOverlays().add(itemizedoverlay);
		}
		
		MyLocationOverlay myLocationOverlay = new MyLocationOverlay(this, mapView) {
			@Override
			public synchronized void onLocationChanged(Location loc) {
				super.onLocationChanged(loc);
				mapView.getController().animateTo(
						new GeoPoint((int)(loc.getLatitude()*1e6), (int)(loc.getLongitude()*1e6))
						);
			}			
		};
		
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.enableCompass();
//		
		
		mapView.getOverlays().add(myLocationOverlay);
		mapView.getOverlays().add(new CrossOverlay());

//		if (point != null)
//			mapView.getController().animateTo(new GeoPoint(0, 0));

		
		getContentResolver().registerContentObserver(RosterProvider.CONTENT_URI,
				true, mContactObserver);

//		ActionBar actionBar = getSupportActionBar();
//		actionBar.setHomeButtonEnabled(true);
//		actionBar.setDisplayHomeAsUpEnabled(true);

//		registerForContextMenu(getListView());
//		setContactFromUri();
//		registerXMPPService();
//		setSendButton();
//		setUserInput();
		
		String titleUserid;
		if (mUserScreenName != null) {
			titleUserid = mUserScreenName;
		} else {
			titleUserid = mWithJabberID;
		}

		setCustomTitle(titleUserid);

		setChatWindowAdapter();
	}

	private void setCustomTitle(String title) {
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.chat_action_title, null);
		mStatusMode = (ImageView)layout.findViewById(R.id.action_bar_status);
		mTitle = (TextView)layout.findViewById(R.id.action_bar_title);
		mSubTitle = (TextView)layout.findViewById(R.id.action_bar_subtitle);
		mTitle.setText(title);

//		setTitle(null);
//		getSupportActionBar().setCustomView(layout);
//		getSupportActionBar().setDisplayShowCustomEnabled(true);
	}

	private void setChatWindowAdapter() {
		String selection = ChatConstants.JID + "='" + mWithJabberID + "'";
		Cursor cursor = managedQuery(ChatProvider.CONTENT_URI, PROJECTION_FROM,
				selection, null, null);
		ListAdapter adapter = new ChatWindowAdapter(cursor, PROJECTION_FROM,
				PROJECTION_TO, mWithJabberID, mUserScreenName);

//		setListAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateContactStatus();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
//		if (hasFocus)
//			bindXMPPService();
//		else
//			unbindXMPPService();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
//		if (hasWindowFocus()) unbindXMPPService();
//		getContentResolver().unregisterContentObserver(mContactObserver);
	}

	private void registerXMPPService() {
		Log.i(TAG, "called startXMPPService()");
		mServiceIntent = new Intent(this, XMPPService.class);
		Uri chatURI = Uri.parse(mWithJabberID);
		mServiceIntent.setData(chatURI);
		mServiceIntent.setAction("org.yaxim.androidclient.XMPPSERVICE");

		mServiceConnection = new ServiceConnection() {

			public void onServiceConnected(ComponentName name, IBinder service) {
				Log.i(TAG, "called onServiceConnected()");
				mServiceAdapter = new XMPPChatServiceAdapter(
						IXMPPChatService.Stub.asInterface(service),
						mWithJabberID);
				
				mServiceAdapter.clearNotifications(mWithJabberID);
			}

			public void onServiceDisconnected(ComponentName name) {
				Log.i(TAG, "called onServiceDisconnected()");
			}

		};
	}

	private void unbindXMPPService() {
		try {
			unbindService(mServiceConnection);
		} catch (IllegalArgumentException e) {
			Log.e(TAG, "Service wasn't bound!");
		}
	}

	private void bindXMPPService() {
		bindService(mServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	private void setSendButton() {
		mSendButton = (Button) findViewById(R.id.Chat_SendButton);
		View.OnClickListener onSend = getOnSetListener();
		mSendButton.setOnClickListener(onSend);
		mSendButton.setEnabled(false);
	}

	private void setUserInput() {
		Intent i = getIntent();
		mChatInput = (EditText) findViewById(R.id.Chat_UserInput);
		mChatInput.addTextChangedListener(this);
		if (i.hasExtra(INTENT_EXTRA_MESSAGE)) {
			mChatInput.setText(i.getExtras().getString(INTENT_EXTRA_MESSAGE));
		}
	}

	private void setContactFromUri() {
		Intent i = getIntent();
		mWithJabberID = i.getDataString().toLowerCase();
		if (i.hasExtra(INTENT_EXTRA_USERNAME)) {
			mUserScreenName = i.getExtras().getString(INTENT_EXTRA_USERNAME);
		} else {
			mUserScreenName = mWithJabberID;
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.chat_contextmenu, menu);
	}

	public boolean onContextItemSelected(MenuItem item) {
		View target = ((AdapterContextMenuInfo)item.getMenuInfo()).targetView;
		switch (item.getItemId()) {
		case R.id.chat_contextmenu_copy_text:
			TextView message = (TextView)target.findViewById(R.id.chat_message);
			ClipboardManager cm = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
			cm.setText(message.getText());
			return true;
		default:
			return super.onContextItemSelected((android.view.MenuItem) item);
		}
	}
	

	private View.OnClickListener getOnSetListener() {
		return new View.OnClickListener() {

			public void onClick(View v) {
				sendMessageIfNotNull();
			}
		};
	}

	private void sendMessageIfNotNull() {
		if (mChatInput.getText().length() >= 1) {
			sendMessage(mChatInput.getText().toString());
			if (!mServiceAdapter.isServiceAuthenticated())
				showToastNotification(R.string.toast_stored_offline);
		}
	}

	private void sendMessage(String message) {
		mChatInput.setText(null);
		mSendButton.setEnabled(false);
		mServiceAdapter.sendMessage(mWithJabberID, message);
	}

	private void markAsRead(int id) {
		Uri rowuri = Uri.parse("content://" + ChatProvider.AUTHORITY
			+ "/" + ChatProvider.TABLE_NAME + "/" + id);
		Log.d(TAG, "markAsRead: " + rowuri);
		ContentValues values = new ContentValues();
		values.put(ChatConstants.DELIVERY_STATUS, ChatConstants.DS_SENT);
		getContentResolver().update(rowuri, values, null, null);
	}

	class ChatWindowAdapter extends SimpleCursorAdapter {
		String mScreenName, mJID;

		ChatWindowAdapter(Cursor cursor, String[] from, int[] to,
				String JID, String screenName) {
			super(MapWindow.this, android.R.layout.simple_list_item_1, cursor,
					from, to);
			mScreenName = screenName;
			mJID = JID;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			ChatItemWrapper wrapper = null;
			Cursor cursor = this.getCursor();
			cursor.moveToPosition(position);

			long dateMilliseconds = cursor.getLong(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DATE));

			int _id = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants._ID));
			String date = getDateString(dateMilliseconds);
			String message = cursor.getString(cursor
					.getColumnIndex(ChatProvider.ChatConstants.MESSAGE));
			int from_me = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DIRECTION));
			String jid = cursor.getString(cursor
					.getColumnIndex(ChatProvider.ChatConstants.JID));
			int delivery_status = cursor.getInt(cursor
					.getColumnIndex(ChatProvider.ChatConstants.DELIVERY_STATUS));

			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(R.layout.chatrow, null);
				wrapper = new ChatItemWrapper(row, MapWindow.this);
				row.setTag(wrapper);
			} else {
				wrapper = (ChatItemWrapper) row.getTag();
			}

			if (from_me == 0 && delivery_status == 0) {
				markAsRead(_id);
			}

			String from = jid;
			if (jid.equals(mJID))
				from = mScreenName;
			wrapper.populateFrom(date, from_me != 0, from, message, delivery_status);

			return row;
		}
	}

	private String getDateString(long milliSeconds) {
		SimpleDateFormat dateFormater = new SimpleDateFormat("yy-MM-dd HH:mm:ss");
		Date date = new Date(milliSeconds);
		return dateFormater.format(date);
	}

	public class ChatItemWrapper {
		private TextView mDateView = null;
		private TextView mFromView = null;
		private TextView mMessageView = null;
		private ImageView mIconView = null;

		private final View mRowView;
		private MapWindow chatWindow;

		ChatItemWrapper(View row, MapWindow chatWindow) {
			this.mRowView = row;
			this.chatWindow = chatWindow;
		}

		void populateFrom(String date, boolean from_me, String from, String message,
				int delivery_status) {
//			Log.i(TAG, "populateFrom(" + from_me + ", " + from + ", " + message + ")");
			getDateView().setText(date);
			TypedValue tv = new TypedValue();
			if (from_me) {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderMeColor, tv, true);
				getDateView().setTextColor(tv.data);
				getFromView().setText(getString(R.string.chat_from_me));
				getFromView().setTextColor(tv.data);
			} else {
				getTheme().resolveAttribute(R.attr.ChatMsgHeaderYouColor, tv, true);
				getDateView().setTextColor(tv.data);
				getFromView().setText(from + ":");
				getFromView().setTextColor(tv.data);
			}
			switch (delivery_status) {
			case 0:
				ColorDrawable layers[] = new ColorDrawable[2];
				getTheme().resolveAttribute(R.attr.ChatNewMessageColor, tv, true);
				layers[0] = new ColorDrawable(tv.data);
				if (from_me) {
					// message stored for later transmission
					getTheme().resolveAttribute(R.attr.ChatStoredMessageColor, tv, true);
					layers[1] = new ColorDrawable(tv.data);
				} else {
					layers[1] = new ColorDrawable(0x00000000);
				}
				TransitionDrawable backgroundColorAnimation = new
					TransitionDrawable(layers);
				int l = mRowView.getPaddingLeft();
				int t = mRowView.getPaddingTop();
				int r = mRowView.getPaddingRight();
				int b = mRowView.getPaddingBottom();
				mRowView.setBackgroundDrawable(backgroundColorAnimation);
				mRowView.setPadding(l, t, r, b);
				backgroundColorAnimation.setCrossFadeEnabled(true);
				backgroundColorAnimation.startTransition(2000);
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_queued);
				break;
			case 1:
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_unread);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			case 2:
				getIconView().setImageResource(R.drawable.ic_chat_msg_status_ok);
				mRowView.setBackgroundColor(0x00000000); // default is transparent
				break;
			}
			getMessageView().setText(message);
			getMessageView().setTextSize(TypedValue.COMPLEX_UNIT_SP, chatWindow.mChatFontSize);
		}
        
		
		TextView getDateView() {
			if (mDateView == null) {
				mDateView = (TextView) mRowView.findViewById(R.id.chat_date);
			}
			return mDateView;
		}

		TextView getFromView() {
			if (mFromView == null) {
				mFromView = (TextView) mRowView.findViewById(R.id.chat_from);
			}
			return mFromView;
		}

		TextView getMessageView() {
			if (mMessageView == null) {
				mMessageView = (TextView) mRowView
						.findViewById(R.id.chat_message);
			}
			return mMessageView;
		}

		ImageView getIconView() {
			if (mIconView == null) {
				mIconView = (ImageView) mRowView
						.findViewById(R.id.iconView);
			}
			return mIconView;
		}

	}

	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN
				&& keyCode == KeyEvent.KEYCODE_ENTER) {
			sendMessageIfNotNull();
			return true;
		}
		return false;

	}

	public void afterTextChanged(Editable s) {
		if (mChatInput.getText().length() >= 1) {
			mChatInput.setOnKeyListener(this);
			mSendButton.setEnabled(true);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// TODO Auto-generated method stub

	}

	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	private void showToastNotification(int message) {
		Toast toastNotification = Toast.makeText(this, message,
				Toast.LENGTH_SHORT);
		toastNotification.show();
	}

//	@Override
//	public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
//		switch (item.getItemId()) {
//		case android.R.id.home:
//			Intent intent = new Intent(this, MainWindow.class);
//			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//			startActivity(intent);
//			return true;
//		default:
//			return super.onOptionsItemSelected(item);
//		}
//	}


	private void updateContactStatus() {
		
		
		Cursor cursor = getContentResolver().query(RosterProvider.CONTENT_URI, null,
					null, null, null);
		int MODE_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MODE);
		int MSG_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.STATUS_MESSAGE);
		int ALIAS_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.ALIAS);
		int JID_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.JID);
		
		int LAT_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.LAT);
		int LON_IDX = cursor.getColumnIndex(RosterProvider.RosterConstants.LON);
		
		ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();;
		for (int i = 0; i < cursor.getCount(); i++) {
			cursor.moveToPosition(i);
			
			int status_mode = cursor.getInt(MODE_IDX);
			String status_message = cursor.getString(MSG_IDX);
			String alias = cursor.getString(ALIAS_IDX);
			String jid = cursor.getString(JID_IDX);
			
			int lat = cursor.getInt(LAT_IDX);
			int lon = cursor.getInt(LON_IDX);
			
			boolean set = ! cursor.isNull(LAT_IDX);
			
			Log.d(TAG, i + " contact: " + alias + " " + jid + " " + lat + "," + lon + "," + set);
			if (set || i == 0) {
				OverlayItem item = new OverlayItem(new GeoPoint(lat, lon), jid, status_message);
				items.add(item);
			}
			
//			mSubTitle.setVisibility((status_message != null && status_message.length() != 0)?
//					View.VISIBLE : View.GONE);
//			mSubTitle.setText(status_message);
//			
//			mStatusMode.setImageResource(StatusMode.values()[status_mode].getDrawableId());
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
