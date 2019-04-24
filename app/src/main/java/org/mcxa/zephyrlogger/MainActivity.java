
package org.mcxa.zephyrlogger;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.mcxa.zephyrlogger.hxm.HrmReading;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

	private boolean isRecording=false;
	private String recordingTag;
	private static final String TAG = "ZephyrLogger";

	public static final String CSV_FORMAT = "timeInMs,Heart Rate,Speed";

	@BindView(R.id.status) TextView mStatus;
	@BindView(R.id.toolbar) Toolbar toolbar;
	@BindView(R.id.main_button) AppCompatButton mButton;
    @BindView(R.id.main_activity_view) RelativeLayout view;

	@BindView(R.id.heart_rate) TextView mHeartRate;
	@BindView(R.id.rri) TextView mRri;
	@BindView(R.id.battery) TextView mBattery;
	@BindView(R.id.speed) TextView mSpeed;

	private String mHxMName = null;
	private String mHxMAddress = null;

	private BluetoothAdapter mBluetoothAdapter = null;

	private HxmService mHxmService = null;

	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	public File exportData(HrmReading m) {
		File zephyrlogFolder = new File(Environment.getExternalStorageDirectory(), "ZephyrLogs");

        boolean dirExists = zephyrlogFolder.exists();
        if (!dirExists) {
            dirExists = zephyrlogFolder.mkdirs();
            if (!dirExists) {
                Log.e(TAG, "Could not create ZephyrLogs directory.");
                System.exit(1);
            }
        }
		File file = new File(zephyrlogFolder, "Zephyr_"+recordingTag+"_data.txt");
		try {
			FileWriter writer;
			if(!file.exists()){
				boolean created = file.createNewFile();
				if (!created) throw new IOException("Could not create data file");
				writer = new FileWriter(file, true);
				//if this is a new file, write the CSV format at the top
				writer.write(CSV_FORMAT + "\n");
			} else {
				writer = new FileWriter(file, true);
			}
			writer.write(System.currentTimeMillis()+","+m.heartRate+","+m.speed+"\n");
			writer.close();
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Could not create logging file");
            e.printStackTrace();
			System.exit(1);
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, "Unsupported encoding exception thrown trying to write file");
			e.printStackTrace();
            System.exit(1);
		} catch (IOException e) {
            Log.e(TAG, "IO Exception trying to write to data file");
            e.printStackTrace();
            System.exit(1);
		}
		return file;
	}
	private void connectToHxm() {
		mStatus.setText(R.string.connecting);

		if (mHxmService == null)
			mHxmService = new HxmService(this, mHandler);
		if ( getFirstConnectedHxm() ) {
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mHxMAddress);
			mHxmService.connect(device);    // Attempt to connect to the device
		} else {
			mStatus.setText(R.string.nonePaired);           
		}

	}
	private boolean getFirstConnectedHxm() {

		mHxMAddress = null;
		mHxMName = null;

		BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		Set<BluetoothDevice> bondedDevices = mBtAdapter.getBondedDevices();

		if (bondedDevices.size() > 0) {
			for (BluetoothDevice device : bondedDevices) {
				String deviceName = device.getName();
				if ( deviceName.startsWith("HXM") ) {
					mHxMAddress = device.getAddress();
					mHxMName = device.getName();
					Log.d(TAG,"getFirstConnectedHxm() found a device whose name starts with 'HXM', its name is "+mHxMName+" and its address is ++mHxMAddress");
					break;
				}
			}
		}
		return (mHxMAddress != null);
	}
    private static final int WRITE_STORAGE_PERMISSIONS_REQUEST = 7;

	public void getExternalStoragePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_STORAGE_PERMISSIONS_REQUEST);
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        if (requestCode == WRITE_STORAGE_PERMISSIONS_REQUEST) {
            if (!(grantResults.length == 1 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Snackbar.make(view,"This app cannot function without external storage permissions.",
                        Snackbar.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.app_name);
        getExternalStoragePermissions();

		mStatus.setText(R.string.initializing);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			final Snackbar snackbar = Snackbar.make(view,"Bluetooth is not available or not enabled",
					Snackbar.LENGTH_INDEFINITE);
			snackbar.setAction("Close", new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					snackbar.dismiss();
				}
			}).setActionTextColor(Color.WHITE);
			snackbar.show();
			mStatus.setText(R.string.noBluetooth);
			//disable button
			mButton.setEnabled(false);

		} else {
			if (!mBluetoothAdapter.isEnabled()) {
				mStatus.setText(R.string.btNotEnabled);
				Log.d(TAG, "onStart: Blueooth adapter detected, but it's not enabled");
			} else {
				connectToHxm();
			}
		}        
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "onStart");
		if (mBluetoothAdapter != null ) {
			if (!mBluetoothAdapter.isEnabled()) {
				mStatus.setText(R.string.btNotEnabled);
				Log.d(TAG, "onStart: Blueooth adapter detected, but it's not enabled");
			}
		} else {
			mStatus.setText(R.string.noBluetooth);
			Log.d(TAG, "onStart: No blueooth adapter detected, it needs to be present and enabled");
		}

	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		if (mHxmService != null) {
			if (mHxmService.getState() == R.string.HXM_SERVICE_RESTING) {
				mHxmService.start();
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.e(TAG, "Destroying activity. Stopping bluetooth service");
		if (mHxmService != null) mHxmService.stop();
	}

	Handler mHandler = new MessageHandler(this);
	private static class MessageHandler extends Handler {
		private final WeakReference<MainActivity> activityReference;

		MessageHandler(MainActivity activity) {
			activityReference = new WeakReference<>(activity);
		}

		@Override
		public void handleMessage(Message msg) {
			MainActivity activity = activityReference.get();
			if (activity != null) {

				switch (msg.what) {
					case R.string.HXM_SERVICE_MSG_STATE:
						Log.d(TAG, "handleMessage():  MESSAGE_STATE_CHANGE: " + msg.arg1);
						switch (msg.arg1) {
							case R.string.HXM_SERVICE_CONNECTED:
								if ((activity.mStatus != null) && (activity.mHxMName != null)) {
									activity.mStatus.setText(R.string.connectedTo);
									activity.mStatus.append(activity.mHxMName);
									activity.mButton.setText(activity.getResources()
											.getString(R.string.start_record));
									activity.mButton
											.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
								}
								break;

							case R.string.HXM_SERVICE_CONNECTING:
								activity.mStatus.setText(R.string.connecting);
								break;

							case R.string.HXM_SERVICE_RESTING:
								if (activity.mStatus != null) {
									activity.mStatus.setText(R.string.notConnected);
									//set button to connect
									activity.mButton.setText(activity.getResources()
											.getString(R.string.connect));
									activity.mButton
											.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_connect, 0, 0, 0);
									activity.mHeartRate.setText("");
									activity.mBattery.setText("");
									activity.mRri.setText("");
									activity.mSpeed.setText("");
								}
								break;
						}
						break;

					case R.string.HXM_SERVICE_MSG_READ: {
						byte[] readBuf = (byte[]) msg.obj;
						HrmReading hrm = new HrmReading(readBuf);
						activity.displayHrmReading(hrm);

						if (activity.isExternalStorageWritable() && activity.isRecording) {
							activity.exportData(hrm);
						}
						break;
					}

					case R.string.HXM_SERVICE_MSG_TOAST:
						String message = msg.getData().getString(null);
						if (message != null)
							Snackbar.make(activity.view, message, Snackbar.LENGTH_LONG).show();
						break;
				}
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.scan:
			connectToHxm();
			return true;

		case R.id.record:
			startStopRecording();
			return true;

		case R.id.about:
			Intent i = new Intent(this, AboutActivity.class);
			startActivity(i);
			return true;

		case R.id.quit:

			mHxmService.stop();
			finish();
			return true;
		}

		return false;
	}

	@OnClick(R.id.main_button)
	public void onMainButtonCLicked() {
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())
			Log.d(TAG, "Unable to connect, bluetooth unavailable or not enabled");
		else if (mHxmService == null || mHxmService.getState() != R.string.HXM_SERVICE_CONNECTED)
			connectToHxm();
		else startStopRecording();
	}

	private void startStopRecording() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				== PackageManager.PERMISSION_GRANTED) {
			isRecording = !isRecording;
			recordingTag = (isRecording ? "" + System.currentTimeMillis() : recordingTag);
			Snackbar.make(view,
					(isRecording ? R.string.recording_on : R.string.recording_off),
					Snackbar.LENGTH_SHORT).show();
			if (isRecording) {
				mButton.setText(getResources().getString(R.string.stop_record));
				mButton.setCompoundDrawablesWithIntrinsicBounds( R.drawable.ic_stop, 0, 0, 0);
			} else {
				mButton.setText(getResources().getString(R.string.start_record));
				mButton.setCompoundDrawablesWithIntrinsicBounds( R.drawable.ic_play, 0, 0, 0);
			}
		} else {
			Snackbar.make(view,"Cannot record without external storage permissions.",
					Snackbar.LENGTH_LONG).show();
		}

	}
	private Long calcRRi(HrmReading h) {
		Long interval = 0L;

		Long tmp = Math.abs(h.hbTime15 - h.hbTime14);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime14 - h.hbTime13);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime13 - h.hbTime12);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime12 - h.hbTime11);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime11 - h.hbTime10);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime10 - h.hbTime9);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime9 - h.hbTime8);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime8 - h.hbTime7);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime7 - h.hbTime6);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime6 - h.hbTime5);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime5 - h.hbTime4);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime4 - h.hbTime3);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime3 - h.hbTime2);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;
		tmp = Math.abs(h.hbTime2 - h.hbTime1);
		interval += (tmp > 10000L) ? Math.abs(tmp - 65535) : tmp;

		return interval/14;
	}

	private Double calcSpeed(HrmReading h) {
		return ((double) h.speed)/256;
	}

	private void displayHrmReading(HrmReading h){
		mHeartRate.setText(String.format(Locale.US, "%d bpm", h.heartRate));
		mBattery.setText(String.format(Locale.US, "%d %%", h.batteryIndicator));
		mRri.setText(String.format(Locale.US, "%d ms", calcRRi(h)));
		mSpeed.setText(String.format(Locale.US, "%.1f m/s", calcSpeed(h)));
	}
}
