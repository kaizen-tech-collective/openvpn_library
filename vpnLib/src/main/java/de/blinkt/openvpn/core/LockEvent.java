package de.blinkt.openvpn.core;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a device lock/unlock event
 */
public class LockEvent {
    @SerializedName("timestamp")
    private long timestamp;
    
    @SerializedName("isLocked")
    private boolean isLocked;
    
    public LockEvent() {
        // Default constructor for Gson
    }
    
    public LockEvent(long timestamp, boolean isLocked) {
        this.timestamp = timestamp;
        this.isLocked = isLocked;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isLocked() {
        return isLocked;
    }
    
    public void setLocked(boolean locked) {
        isLocked = locked;
    }
}

