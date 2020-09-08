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

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

public class DelegatingScanActivity extends AppCompatActivity {

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

    private ScanRepository scanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_root);

        //Set navigation drawer
        this.scanningText = (TextView) findViewById(R.id.scanningText);

        this.deviceListView = (ListView) findViewById(R.id.deviceList);
        this.listAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList);
        this.deviceListView.setAdapter(listAdapter);
        this.deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Toast.makeText(DelegatingScanActivity.this, "pos: " + i, Toast.LENGTH_LONG).show();
                if (deviceDetailsList.size() < (i + 1)) {
                    scanningText.append("\nCould not get device details");
                    final Intent intent = new Intent(getBaseContext(), ControllerActivity.class);
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_NAME, "Nonexistent device");
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_MAC, "Nonexistent MAC Address");
                    scanner.stopScan();
                    startActivity(intent);
                } else {
                    SearchElement deviceDetails = deviceDetailsList.get(i);
                    scanningText.append("\nAttempting to connect to device. Name: " + deviceDetails.getName() + " , MAC addr: " + deviceDetails.getMacAddress());
                    final Intent intent = new Intent(getBaseContext(), ControllerActivity.class);
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_NAME, deviceDetails.getName());
                    intent.putExtra(ControllerActivity.EXTRAS_DEVICE_MAC, deviceDetails.getMacAddress());
                    scanner.stopScan();
                    startActivity(intent);
                }
            }
        });

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_ALL);
        }

        getSupportActionBar().setTitle("BLE - Scan Device");
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            scanningText.setText("\nBluetooth LE is not supported on this device");
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            doScan = false;
            //finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        scanner = new ScanRepository(bluetoothManager);
        if (!scanner.isInitialized()) {
            scanningText.append("\nBluetooth adapter could not be found");
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            scanner.startScan();
            if (scanner.isInitialized()) {
                startScanStatusTimer();
            }
        } catch (Exception e) {
            scanningText.append("\n" + e.getMessage());
        }

        scanner.getSubject().subscribe(new Observer<SearchElement>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {

            }

            @Override
            public void onNext(@NonNull SearchElement deviceDetails) {
                if (!deviceList.contains(deviceDetails.getDevice().getName())) {
                    deviceList.add(deviceDetails.getDevice().getName());
                    deviceDetailsList.add(deviceDetails);
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {

            }

            @Override
            public void onComplete() {

            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != scanStatusTimer) {
            scanStatusTimer.cancel();
        }
        this.scanner.stopScan();
    }

    private boolean scanning = false;
    private Timer scanStatusTimer;
    android.text.format.DateFormat df = new android.text.format.DateFormat();
    private void startScanStatusTimer() {
        this.scanStatusTimer = new Timer();
        this.scanStatusTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanningText.append("\n[" + df.format("hh:mm:ss a", new Date()) + "] Checking scanner status...");
                        scanningText.append("\n" + scanner.getStatus());
                    }
                });
            }
        }, 0, 10 * 1000);
    }

    private void log(String message) {
        Log.i("DelegatingScanActivity", message);
    }
}
