package com.ble.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.subjects.ReplaySubject;

public class ScanRepository {

    private ReplaySubject<SearchElement> source = ReplaySubject.create();
    private boolean scanning;
    private Handler mHandler = new Handler();
    private BluetoothAdapter mBluetoothAdapter;
    private Timer scanTimer = new Timer();
    private String status = "Initial state";

    public ScanRepository(BluetoothManager bluetoothManager) {
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    public ReplaySubject<SearchElement> getSubject() {
        return this.source;
    }

    public void startScan() {
        if (!this.isInitialized()) {
            this.status = "No bluetooth adapter detected";
            throw new IllegalStateException("No bluetooth adapter detected.");
        }

        this.scanTimer = new Timer();
        this.scanTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    status = "[" + System.currentTimeMillis() + "] Scanning in progress";
                    scanLeDevice();
                } catch (Exception e) {
                    Log.e("ScanRepository", e.getMessage(), e);
                    status = "[" + System.currentTimeMillis() + "] Exception: " + e.getMessage();
                }
            }
        }, 0, 7 * 1000);
    }

    public void stopScan() {
        status = "[" + System.currentTimeMillis() + "] Stop scann requested";
        if (null != this.scanTimer) {
            this.scanTimer.cancel();
        }
    }

    private void scanLeDevice() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                scanning = false;
            }
        }, 5000/*SCAN_PERIOD*/);

        if(!scanning) {
            scanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {

            final StringBuilder stringBuilder = new StringBuilder(scanRecord.length);
            stringBuilder.append("0x");
            for (byte byteChar : scanRecord) {
                String hex =String.format("%02X", byteChar);
                stringBuilder.append(hex);
            }

            boolean device_added = false;
            SearchElement deviceDetails = new SearchElement(device.getName(), device.getAddress(), rssi,stringBuilder.toString().trim());
            deviceDetails.setDevice(device);
            source.onNext(deviceDetails);
        }
    };

    public boolean isInitialized() {
        return null != mBluetoothAdapter;
    }

    public String getStatus() {
        return this.status;
    }
}
