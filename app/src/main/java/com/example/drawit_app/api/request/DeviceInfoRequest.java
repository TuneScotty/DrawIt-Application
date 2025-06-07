package com.example.drawit_app.network.request;

/**
 * Request model for device information
 */
public class DeviceInfoRequest {
    private String deviceId;
    private String deviceModel;
    private String osVersion;
    
    public DeviceInfoRequest(String deviceId) {
        this.deviceId = deviceId;
        this.deviceModel = android.os.Build.MODEL;
        this.osVersion = android.os.Build.VERSION.RELEASE;
    }
    
    public DeviceInfoRequest(String deviceId, String deviceModel, String osVersion) {
        this.deviceId = deviceId;
        this.deviceModel = deviceModel;
        this.osVersion = osVersion;
    }
    
    public String getDeviceId() {
        return deviceId;
    }
    
    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
    
    public String getDeviceModel() {
        return deviceModel;
    }
    
    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }
    
    public String getOsVersion() {
        return osVersion;
    }
    
    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }
}
