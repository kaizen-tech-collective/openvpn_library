package de.blinkt.openvpn.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import de.blinkt.openvpn.R;

/**
 * Helper class for showing unlock notifications
 */
public class LockNotificationHelper {
    private static final String TAG = "LockNotificationHelper";
    private static final String CHANNEL_ID = "lock_unlock_notifications";
    private static final String CHANNEL_NAME = "Device Lock Notifications";
    private static final int NOTIFICATION_ID = 1; // Must match main app's notification ID
    
    private final Context context;
    private final Handler mainHandler;
    
    public LockNotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH // High importance for heads-up notification
            );
            channel.setDescription("Notifications for device unlock events");
            channel.enableLights(true);
            channel.enableVibration(true);
            
            NotificationManager notificationManager = 
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Show unlock notification after 2 second delay
     * Only shows if lock duration was >= 5 seconds
     */
    public void showUnlockNotificationDelayed() {
        // Wait 2 seconds before showing notification
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showUnlockNotification();
            }
        }, 2000); // 2 seconds delay
    }
    
    /**
     * Show unlock notification immediately
     */
    private void showUnlockNotification() {
        try {
            NotificationManager notificationManager = 
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            
            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager is null");
                return;
            }
            
            // Remove any existing notification with same ID
            notificationManager.cancel(NOTIFICATION_ID);
            
            // Create notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Phone Reminder")
                    .setContentText("Please tuck away your phone and stay present!")
                    .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up notification
                    .setAutoCancel(true)
                    .setDefaults(android.app.Notification.DEFAULT_SOUND | 
                                android.app.Notification.DEFAULT_VIBRATE);
            
            // Try to create a pending intent to open the app
            try {
                Intent intent = context.getPackageManager()
                        .getLaunchIntentForPackage(context.getPackageName());
                if (intent != null) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    PendingIntent pendingIntent = PendingIntent.getActivity(
                            context, 0, intent, 
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    builder.setContentIntent(pendingIntent);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not create pending intent: " + e.getMessage());
            }
            
            // Show notification
            notificationManager.notify(NOTIFICATION_ID, builder.build());
            Log.d(TAG, "Unlock notification shown");
            
        } catch (Exception e) {
            Log.e(TAG, "Error showing unlock notification: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cancel the unlock notification
     */
    public void cancelNotification() {
        try {
            NotificationManager notificationManager = 
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(NOTIFICATION_ID);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling notification: " + e.getMessage());
        }
    }
}

