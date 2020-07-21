package com.ble.controller;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ble.ByteQueue;
import com.ble.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ControllerActivity extends AppCompatActivity {
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_MAC = "DEVICE_MAC";

    //layout
    private SeekBar powerBar;
    private Button upButton;
    private Button downButton;
    private TextView powerText;
    private TextView debugText;
    private TextView deviceDetails;
    private TextView connectionStatus;

    //connection
    private String deviceName;
    private String deviceMac;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private boolean mConnected = false;
    private boolean mConnecting = false;

    //Store all service and Characteristic
    private ArrayList<BluetoothGattCharacteristic> mReadCharacteristics, mWriteCharacteristics, mNotifyCharacteristics;
    private ArrayList<BluetoothGattService> mReadServices, mWriteServices, mNotifyServices;
    private BluetoothGattCharacteristic mNotifyCharacteristic, mReadCharacteristic, mWriteCharacteristic;
    List<BluetoothGattCharacteristic> chars = null;
    String readuuid, writeuuid, notifyuuid;

    //params
    private boolean doSend;
    private int normalizedPower;
    private boolean up;
    private boolean down;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        //layout
        this.powerBar = (SeekBar) findViewById(R.id.powerBar);
        this.upButton = (Button) findViewById(R.id.upButton);
        this.downButton = (Button) findViewById(R.id.downButton);
        this.powerText = (TextView) findViewById(R.id.powerText);
        this.deviceDetails = (TextView) findViewById(R.id.deviceDetails);
        this.debugText = (TextView) findViewById(R.id.debugText);
        this.connectionStatus = (TextView) findViewById(R.id.connectionStatus);

        //connection
        final Intent intent = getIntent();
        deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        this.deviceDetails.setText("BLE Controller - " + deviceName);
        deviceMac  = intent.getStringExtra(EXTRAS_DEVICE_MAC);
        this.deviceDetails.append("\nDevice MAC: " + deviceMac);
        getSupportActionBar().setTitle("Controller - " + deviceName);

        this.powerBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                normalizedPower = progress - 1000;
                powerText.setText("" + normalizedPower);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        this.upButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch(motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        up = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        up = false;
                }
                return false;
            }
        });
        this.downButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch(motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        down = true;
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        down = false;
                }
                return false;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean initSuccess = initialize();
        if (initSuccess) {
            connect();
            this.doSend = true;
            startSendTimer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.doSend = false;
        if (null != sendTimer) {
            this.sendTimer.cancel();
        }
    }

    private final BluetoothGattCallback mchatbleCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mConnected = true;
                mConnecting = false;
                connectionStatus.setText("Connected");
                invalidateOptionsMenu();
                mBluetoothGatt.discoverServices();


            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                mConnected = false;
                mConnecting = false;
                connectionStatus.setText("Disconnected");
                invalidateOptionsMenu();

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                mConnected = false;
                mConnecting = true;
                connectionStatus.setText("Connecting");
                invalidateOptionsMenu();

            }
        }


        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (BluetoothGattService service : getSupportedGattServices()) {
                        //addService(service);
                        String serviceuuid = service.getUuid().toString();
                        chars = service.getCharacteristics();
                        if (chars.size() > 0) {
                            for (BluetoothGattCharacteristic characteristic : chars) {
                                if (characteristic != null) {
                                    String charuuid = characteristic.getUuid().toString();
                                    final int charaProp = characteristic.getProperties();
                                    final int read = charaProp & BluetoothGattCharacteristic.PROPERTY_READ;
                                    final int write = charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE;
                                    final int notify = charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY;

                                    if (read > 0) {
                                        // If there is an active notification on a characteristic, clear
                                        // it first so it doesn't update the data field on the user interface.
                                           /*mReadCharacteristic = characteristic;
                                                setCharacteristicNotification(mReadCharacteristic, false);
                                                readCharacteristic(characteristic);*/
                                        mReadServices.add(service);
                                        mReadCharacteristics.add(characteristic);
                                    }
                                    if (notify > 0) {/*
                                             mNotifyCharacteristic = characteristic;
                                                setCharacteristicNotification(characteristic, true);*/

                                        mNotifyServices.add(service);
                                        mNotifyCharacteristics.add(characteristic);
                                    }

                                    if (write > 0) {/*
                                            mWriteCharacteristic = characteristic;*/
                                        //addCharacteristic(characteristic);
                                        mWriteServices.add(service);
                                        mWriteCharacteristics.add(characteristic);
                                    }

                                }
                            }
                        }
                    }
                    //handle on savedinstance here
                   /* selectkey = 0;
                    SetCharacteristic(writechar);
                    selectkey = 1;
                    SetCharacteristic(readchar);
                    selectkey = 2;
                    SetCharacteristic(notifychar);*/
                }


            });
        }

        private List<BluetoothGattService> getSupportedGattServices() {
            if (mBluetoothGatt == null) return null;

            return mBluetoothGatt.getServices();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {

            write(characteristic.getValue(),characteristic.getValue().length);

            //displayData(characteristic.getValue());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //flag = false;
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

            write(characteristic.getValue(),characteristic.getValue().length);
            //displayData(characteristic.getValue());
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //if (status == BluetoothGatt.GATT_SUCCESS)
                //displayRSSI(rssi);
        }
    };

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                debugText.append("\nUnable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            debugText.setText("\nUnable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public boolean connect() {
        if (mBluetoothAdapter == null || deviceMac == null) {
            debugText.append("\nBluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Disconnect first.
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
        }


        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceMac);
        if (device == null) {
            debugText.append("\nDevice not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mchatbleCallback);
        debugText.append("\nTrying to create a new connection.");

        return true;
    }

    private byte[]  mReceiveBuffer = new byte[10 * 1024];
    private ByteQueue mByteQueue = new ByteQueue(10 * 1024);
    public static final int UPDATE = 1;
    public void write(byte[] buffer, int length) {
        try {
            mByteQueue.write(buffer, 0, length);
            debugText.append("\nWrite success");
        } catch (InterruptedException e) {
            debugText.append("\nCould not write data to ble: " + e.getMessage());
        }
        //mHandler.sendMessage( mHandler.obtainMessage(UPDATE));
    }

    private boolean sending = false;
    private Timer sendTimer;
    private void startSendTimer() {
        if (!mConnected) {
            return;
        }
        sendTimer = new Timer();
        sendTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            writeData();
                        } catch (Exception e) {
                            debugText.append("\n" + e.getMessage());
                        }
                    }
                });
            }
        }, 0, 100);
    }

    private void writeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("up", this.up);
        data.put("down", this.down);
        data.put("power", this.normalizedPower);
        JSONObject dataJson = new JSONObject(data);
        String dataString = dataJson.toString();
        debugText.setText("Will attempt to write to ble: " + dataString);
        write(dataString.getBytes(), dataString.length());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
