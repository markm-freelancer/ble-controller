package com.ble.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothManager
import android.os.Handler
import android.util.Log
import io.reactivex.rxjava3.subjects.ReplaySubject
import java.util.*

class KotlinScanRepository(bluetoothManager: BluetoothManager) {
    val subject = ReplaySubject.create<SearchElement>()
    private var scanning = false
    private val mHandler = Handler()
    private val mBluetoothAdapter: BluetoothAdapter?
    private var scanTimer: Timer? = Timer()
    var status = "Initial state"

    fun startScan() {
        if (!this.isInitialized) {
            status = "No bluetooth adapter detected"
            throw IllegalStateException("No bluetooth adapter detected.")
        }
        scanTimer = Timer()
        scanTimer!!.schedule(object : TimerTask() {
            override fun run() {
                try {
                    status = "[" + System.currentTimeMillis() + "] Scanning in progress"
                    scanLeDevice()
                } catch (e: Exception) {
                    Log.e("ScanRepository", e.message, e)
                    status = "[" + System.currentTimeMillis() + "] Exception: " + e.message
                }
            }
        }, 0, 7 * 1000.toLong())
    }

    fun stopScan() {
        status = "[" + System.currentTimeMillis() + "] Stop scan requested"
        if (null != scanTimer) {
            scanTimer!!.cancel()
        }
    }

    private fun scanLeDevice() {
        mHandler.postDelayed({
            mBluetoothAdapter!!.stopLeScan(mLeScanCallback)
            scanning = false
        }, 5000 /*SCAN_PERIOD*/)
        if (!scanning) {
            scanning = true
            mBluetoothAdapter!!.startLeScan(mLeScanCallback)
        }
    }

    // Device scan callback.
    private val mLeScanCallback = LeScanCallback { device, rssi, scanRecord ->
        val stringBuilder = StringBuilder(scanRecord.size)
        stringBuilder.append("0x")
        for (byteChar in scanRecord) {
            val hex = String.format("%02X", byteChar)
            stringBuilder.append(hex)
        }
        val device_added = false
        val deviceDetails = SearchElement(device.name, device.address, rssi, stringBuilder.toString().trim { it <= ' ' })
        deviceDetails.device = device
        subject.onNext(deviceDetails)
    }

    val isInitialized: Boolean
        get() = null != mBluetoothAdapter

    init {
        mBluetoothAdapter = bluetoothManager.adapter
    }
}
