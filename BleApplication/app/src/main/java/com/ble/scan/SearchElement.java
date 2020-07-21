package com.ble.scan;


public class SearchElement {
    private String name;
    private String macAddress;
    private int rssiValue;
    private String rawData;
    public SearchElement(String name, String macAddress, int rssiValue, String rawData) {
        this.name = name;
        this.macAddress = macAddress;
        this.rssiValue = rssiValue;
        this.rawData = rawData;
    }

    public String getRawData() {
        return rawData;
    }

    public void setRawData(String rawData) {
        this.rawData = rawData;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public int getRssiValue() {
        return rssiValue;
    }

    public void setRssiValue(int rssiValue) {
        this.rssiValue = rssiValue;
    }
}
