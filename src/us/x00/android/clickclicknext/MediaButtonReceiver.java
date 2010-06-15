package us.x00.android.clickclicknext;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
	private static final String TAG = "MediaButtonReceiver";
	private static final int PLAYPAUSE = 0;
	private static final int STOP = 1;
	private static final int NEXT = 2;
	private static final int PREVIOUS = 3;
	private static long lastClick;
	private static SharedPreferences prefs;

	@Override
	public void onReceive(Context context, Intent intent) {
		
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean isEnabled = prefs.getBoolean("enabled", true);
		Log.d(TAG, "Enabled: "+isEnabled);
		if (!isEnabled) return;

		/**
		 * Ignore if a call is in progress or the phone is ringing
		 */
		if(((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getCallState()
				== TelephonyManager.CALL_STATE_RINGING 
				||
				((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getCallState()
				== TelephonyManager.CALL_STATE_OFFHOOK)
		{
			return;
		}

		String intentAction = intent.getAction();
		if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
			KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (event == null) {
				return;
			}

			int keycode = event.getKeyCode();
			int action = event.getAction();
			long eventtime = event.getEventTime();

			int command = -1;
			switch (keycode) {
			case KeyEvent.KEYCODE_MEDIA_STOP:
				command = STOP;
				break;
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				command = PLAYPAUSE;
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				command = NEXT;
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				command = PREVIOUS;
				break;
			}
			if (command != -1) {
				if (action == KeyEvent.ACTION_UP) {
					if ((eventtime - lastClick) < 300) {
						command = NEXT;
						lastClick = 0;
					}
					else {
						lastClick = eventtime;
					}
					Log.d(TAG, "keycode="+keycode+"; action="+action+"; command="+command);
					Intent i = new Intent(context, ConnectionService.class);
					i.putExtra("command", command);
					Log.d(TAG, "Starting service with intent="+intent);
					context.startService(i);
					abortBroadcast();
				}
			}
		}
	}

	public static class ConnectionService extends Service {

		@Override
		public void onStart(Intent intent, int startId) {
			super.onStart(intent, startId);
			Log.d(TAG, "Starting ConnectionService");
			int command = intent.getIntExtra("command", -1);
			MediaPlaybackServiceConnection conn = new MediaPlaybackServiceConnection(command, this);
			Intent i = new Intent().setClassName("com.htc.music", "com.htc.music.MediaPlaybackService");
			startService(i);
			bindService(new Intent().setClassName("com.htc.music", "com.htc.music.MediaPlaybackService"), conn, 0);
		}

		@Override
		public IBinder onBind(Intent intent) {
			return null;
		}

	}
	public static class MediaPlaybackServiceConnection implements ServiceConnection {
		private int command;
		private Service context;

		public MediaPlaybackServiceConnection(int command, Service context) {
			this.command = command;
			this.context = context;
			Log.d(TAG, "Connecting to service...");
		}

		public void onServiceConnected(ComponentName comp, IBinder binder) {
			com.htc.music.IMediaPlaybackService service = com.htc.music.IMediaPlaybackService.Stub.asInterface(binder);
			try {
				switch (command) {
				case STOP:
					Log.d(TAG, "Stoping music.");
					service.stop();
					break;
				case PLAYPAUSE:
					if (service.isPlaying()) {
						Log.d(TAG, "Pausing music.");
						service.pause();
					}
					else {
						Log.d(TAG, "Playing music.");
						service.play();
					}
					break;
				case NEXT:
					Log.d(TAG, "Next track.");
					service.next();
					service.play();
					break;
				case PREVIOUS:
					Log.d(TAG, "Previous track.");
					service.prev();
					break;
				}
			} catch (RemoteException e) {
				Log.d(TAG, e.getMessage());
			}
			context.unbindService(this);
			context.stopSelf();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "Disconnected from service.");
		}

	}


}
