package de.blinkt.openvpn.core;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitors device lock/unlock state using KeyguardManager and PowerManager
 * Only tracks events when VPN is connected
 */
public class DeviceLockStateMonitor {
    private static final String TAG = "DeviceLockMonitor";
    private static final long CHECK_INTERVAL_MS = 5000; // 5 seconds (can be increased to 30s for battery)
    
    private final Context context;
    private final OnLockStateChangeListener listener;
    private final Handler mainHandler;
    private final Runnable periodicCheckRunnable;
    
    private boolean isMonitoring = false;
    private boolean lastKnownLockState = false;
    private KeyguardManager keyguardManager;
    private PowerManager powerManager;
    private BroadcastReceiver lockStateReceiver;
    
    /**
     * Callback interface for lock state changes
     */
    public interface OnLockStateChangeListener {
        void onLockStateChanged(boolean isLocked);
    }
    
    public DeviceLockStateMonitor(@NonNull Context context, @NonNull OnLockStateChangeListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize managers
        keyguardManager = (KeyguardManager) this.context.getSystemService(Context.KEYGUARD_SERVICE);
        powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        
        // Periodic check runnable
        this.periodicCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    checkLockState();
                    mainHandler.postDelayed(this, CHECK_INTERVAL_MS);
                }
            }
        };
        
        setupBroadcastReceiver();
    }
    
    /**
     * Setup broadcast receiver for immediate lock/unlock detection
     */
    private void setupBroadcastReceiver() {
        lockStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (isMonitoring) {
                    String action = intent.getAction();
                    if (Intent.ACTION_USER_PRESENT.equals(action)) {
                        // Device unlocked
                        handleLockStateChange(false);
                    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        // Screen off - check if keyguard is locked
                        checkLockState();
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        context.registerReceiver(lockStateReceiver, filter);
    }
    
    /**
     * Start monitoring lock state
     * @return initial lock state (true if locked, false if unlocked)
     */
    public boolean startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring lock state");
            return lastKnownLockState;
        }
        
        isMonitoring = true;
        lastKnownLockState = checkLockState();
        
        // Start periodic checks
        mainHandler.postDelayed(periodicCheckRunnable, CHECK_INTERVAL_MS);
        
        Log.i(TAG, "Started monitoring device lock state. Initial state: " + 
              (lastKnownLockState ? "LOCKED" : "UNLOCKED"));
        
        return lastKnownLockState;
    }
    
    /**
     * Stop monitoring lock state
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        mainHandler.removeCallbacks(periodicCheckRunnable);
        
        Log.i(TAG, "Stopped monitoring device lock state");
    }
    
    /**
     * Check current lock state using KeyguardManager and PowerManager
     * @return true if device is locked, false if unlocked
     */
    private boolean checkLockState() {
        boolean isLocked = false;
        
        try {
            // Method 1: Check if keyguard is locked
            if (keyguardManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    isLocked = keyguardManager.isDeviceLocked();
                } else {
                    // For older Android versions
                    isLocked = keyguardManager.inKeyguardRestrictedInputMode();
                }
            }
            
            // Method 2: Check if screen is off (additional check)
            if (powerManager != null && !powerManager.isInteractive()) {
                // Screen is off, likely locked
                isLocked = true;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking lock state: " + e.getMessage(), e);
            // Default to unlocked if we can't determine
            isLocked = false;
        }
        
        // Notify if state changed
        if (lastKnownLockState != isLocked) {
            handleLockStateChange(isLocked);
        }
        
        return isLocked;
    }
    
    /**
     * Handle lock state change
     */
    private void handleLockStateChange(boolean isLocked) {
        if (lastKnownLockState == isLocked) {
            return; // No change
        }
        
        lastKnownLockState = isLocked;
        Log.d(TAG, "Lock state changed: " + (isLocked ? "LOCKED" : "UNLOCKED"));
        
        // Notify listener
        if (listener != null) {
            listener.onLockStateChanged(isLocked);
        }
    }
    
    /**
     * Get current lock state without triggering callbacks
     */
    public boolean getCurrentLockState() {
        return checkLockState();
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        stopMonitoring();
        if (lockStateReceiver != null) {
            try {
                context.unregisterReceiver(lockStateReceiver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering receiver: " + e.getMessage());
            }
        }
    }
}

