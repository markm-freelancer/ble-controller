package com.ble.scan;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.ble.R;
import com.ble.controller.ControllerActivity;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ScanActivity extends AppCompatActivity {

    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ENABLE_GPS = 2;
    private static final int REQUEST_ENABLE_STORAGE = 3;
    private static final int REQUEST_ALL = 4;

    //layout
    private Toolbar toolbar;
    private TextView scanningText;

    private boolean doScan = true;
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;

    private ListView deviceListView;
    private ArrayAdapter listAdapter;
    private List<String> deviceList = new ArrayList<String>();
    private List<SearchElement> deviceDetailsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root);
        //DrawerLayout drawer = (DrawerLayout) findViewById(R.id.activity_root);

        //Set navigation drawer
        this.scanningText = (TextView) findViewById(R.id.scanningText);

        this.deviceListView = (ListView) findViewById(R.id.deviceList);
        this.listAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList);
        this.deviceListView.setAdapter(listAdapter);
        this.deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Toast.makeText(ScanActivity.this, "pos: " + i, Toast.LENGTH_LONG).show();
                if (deviceDetailsList.size() < (i + 1)) {
                    scanningText.append("\nCould not get device details");
                    final Intent intent = new Intent(getBaseContext(), ControllerActivity.class);
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_NAME, "Nonexistent device");
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_MAC, "Nonexistent MAC Address");
                    if (scanning) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        scanning = false;
                    }
                    startActivity(intent);
                } else {
                    SearchElement deviceDetails = deviceDetailsList.get(i);
                    scanningText.append("\nAttempting to connect to device. Name: " + deviceDetails.getName() + " , MAC addr: " + deviceDetails.getMacAddress());
                    final Intent intent = new Intent(getBaseContext(), ControllerActivity.class);
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_NAME, deviceDetails.getName());
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_MAC, deviceDetails.getMacAddress());
                    if (scanning) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        scanning = false;
                    }
                    startActivity(intent);
                }
            }
        });

        //TODO delete me
        //this.deviceList.add("Nexus 6p");

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_ALL);
        }

        getSupportActionBar().setTitle("BLE - Scan Device");
        //getActionBar().setBackgroundDrawable(new ColorDrawable(0xff20b2aa));
        mHandler = new Handler();
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            scanningText.setText("\nBluetooth LE is not supported on this device");
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            doScan = false;
            //finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            scanningText.append("\nBluetooth adapter could not be found");
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            doScan = false;
            //finish();
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    int onResumeCounter = 0;

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else if (doScan) {
            startScanTimer();
            //scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != scanTimer) {
            scanTimer.cancel();
        }
    }

    private boolean scanning = false;
    private Timer scanTimer;
    private void startScanTimer() {
        log("Starting scan...");
        scanningText.append("Starting scan...");
        scanTimer = new Timer();
        scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            scanLeDevice();
                        } catch (Exception e) {
                            scanningText.append("\n" + e.getMessage());
                        }
                    }
                });
            }
        }, 0, 7 * 1000);

    }

    android.text.format.DateFormat df = new android.text.format.DateFormat();
    private void scanLeDevice() {
        scanningText.setText("Scanning...");
        scanningText.append("\nStarted: " + df.format("hh:mm:ss a", new Date()));
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                scanning = false;
                scanningText.append("\nStopped: " + df.format("hh:mm:ss a", new Date()));
            }
        }, 5000/*SCAN_PERIOD*/);

        if(!scanning) {
            scanning = true;
            invalidateOptionsMenu();
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final StringBuilder stringBuilder = new StringBuilder(scanRecord.length);
                    stringBuilder.append("0x");
                    for (byte byteChar : scanRecord) {
                        String hex =String.format("%02X", byteChar);
                        stringBuilder.append(hex);
                    }

                    boolean device_added = false;
                    SearchElement deviceDetails = new SearchElement(device.getName(), device.getAddress(), rssi,stringBuilder.toString().trim());
                    if (deviceList.contains(device.getName())) {
                        deviceList.add(device.getName());
                        deviceDetailsList.add(deviceDetails);
                    }
                }
            });
        }
    };

    private void log(String message) {
        Log.i("ScanActivity", message);
    }
}
