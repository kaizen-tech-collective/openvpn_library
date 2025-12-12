package de.blinkt.openvpn.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Manages storage and retrieval of device lock/unlock events
 * Stores events in JSON format, keeping only today's events
 */
public class LockEventsManager {
    private static final String TAG = "LockEventsManager";
    private static final String EVENTS_FILE_NAME = "vpn-lock-events.json";
    private static final String PREFS_NAME = "lock_state_prefs";
    private static final String KEY_CURRENT_LOCK_STATE = "current_lock_state";
    private static final String KEY_LOCK_STATE_TIMESTAMP = "lock_state_timestamp";
    private static final String KEY_LOCK_START_TIME = "lock_start_time";
    
    private final Context context;
    private final File eventsFile;
    private final Gson gson;
    private final SharedPreferences prefs;
    
    public LockEventsManager(Context context) {
        this.context = context.getApplicationContext();
        this.eventsFile = new File(this.context.getFilesDir(), EVENTS_FILE_NAME);
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .create();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Record a lock/unlock event
     * Only records if VPN is connected (caller should verify)
     * @return true if notification should be shown (unlock after >= 5 seconds locked)
     */
    public boolean recordLockEvent(boolean isLocked) {
        try {
            long timestamp = System.currentTimeMillis();
            boolean shouldShowNotification = false;
            
            // Check lock duration before recording unlock event
            if (!isLocked) {
                // Device is unlocking - check if we should show notification
                long lockStart = prefs.getLong(KEY_LOCK_START_TIME, 0);
                if (lockStart > 0) {
                    long duration = timestamp - lockStart;
                    if (duration >= 5000) { // 5 seconds
                        shouldShowNotification = true;
                    }
                }
            }
            
            // Load existing events
            List<LockEvent> events = loadEvents();
            
            // Filter to keep only today's events
            Calendar startOfToday = Calendar.getInstance();
            startOfToday.set(Calendar.HOUR_OF_DAY, 0);
            startOfToday.set(Calendar.MINUTE, 0);
            startOfToday.set(Calendar.SECOND, 0);
            startOfToday.set(Calendar.MILLISECOND, 0);
            long startOfTodayMillis = startOfToday.getTimeInMillis();
            
            List<LockEvent> todayEvents = new ArrayList<>();
            for (LockEvent event : events) {
                if (event.getTimestamp() >= startOfTodayMillis) {
                    todayEvents.add(event);
                }
            }
            
            // Add new event
            LockEvent newEvent = new LockEvent(timestamp, isLocked);
            todayEvents.add(newEvent);
            
            // Save to file
            saveEvents(todayEvents);
            
            // Update current state in SharedPreferences
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean(KEY_CURRENT_LOCK_STATE, isLocked)
                    .putLong(KEY_LOCK_STATE_TIMESTAMP, timestamp);
            
            // Track lock start time
            if (isLocked) {
                editor.putLong(KEY_LOCK_START_TIME, timestamp);
            } else {
                editor.remove(KEY_LOCK_START_TIME);
            }
            editor.apply();
            
            Log.d(TAG, "Recorded lock event: " + (isLocked ? "LOCKED" : "UNLOCKED") + 
                  " at " + timestamp + (shouldShowNotification ? " (notification will be shown)" : ""));
            
            return shouldShowNotification;
            
        } catch (Exception e) {
            Log.e(TAG, "Error recording lock event: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get today's lock events
     */
    public List<LockEvent> getTodayLockEvents() {
        try {
            List<LockEvent> allEvents = loadEvents();
            
            // Filter to keep only today's events
            Calendar startOfToday = Calendar.getInstance();
            startOfToday.set(Calendar.HOUR_OF_DAY, 0);
            startOfToday.set(Calendar.MINUTE, 0);
            startOfToday.set(Calendar.SECOND, 0);
            startOfToday.set(Calendar.MILLISECOND, 0);
            long startOfTodayMillis = startOfToday.getTimeInMillis();
            
            List<LockEvent> todayEvents = new ArrayList<>();
            for (LockEvent event : allEvents) {
                if (event.getTimestamp() >= startOfTodayMillis) {
                    todayEvents.add(event);
                }
            }
            
            return todayEvents;
        } catch (Exception e) {
            Log.e(TAG, "Error loading today's events: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Static utility method to get today's lock events from any context
     * @param context Application context
     * @return List of LockEvent objects for today
     */
    public static List<LockEvent> getTodayLockEvents(Context context) {
        LockEventsManager manager = new LockEventsManager(context);
        return manager.getTodayLockEvents();
    }
    
    /**
     * Get lock duration if device is currently locked
     * @return duration in milliseconds, or null if not locked
     */
    public Long getLockDuration() {
        long lockStart = prefs.getLong(KEY_LOCK_START_TIME, 0);
        if (lockStart == 0) {
            return null;
        }
        return System.currentTimeMillis() - lockStart;
    }
    
    /**
     * Load events from file
     */
    private List<LockEvent> loadEvents() {
        if (!eventsFile.exists()) {
            return new ArrayList<>();
        }
        
        try (FileInputStream fis = new FileInputStream(eventsFile)) {
            byte[] buffer = new byte[(int) eventsFile.length()];
            fis.read(buffer);
            String json = new String(buffer, "UTF-8");
            
            Type listType = new TypeToken<List<LockEvent>>(){}.getType();
            List<LockEvent> events = gson.fromJson(json, listType);
            
            return events != null ? events : new ArrayList<>();
        } catch (IOException e) {
            Log.e(TAG, "Error loading events: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Save events to file
     */
    private void saveEvents(List<LockEvent> events) {
        try {
            String json = gson.toJson(events);
            
            try (FileOutputStream fos = new FileOutputStream(eventsFile)) {
                fos.write(json.getBytes("UTF-8"));
                fos.flush();
            }
            
            Log.d(TAG, "Saved " + events.size() + " events to file");
        } catch (IOException e) {
            Log.e(TAG, "Error saving events: " + e.getMessage(), e);
        }
    }
}

