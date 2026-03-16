package com.example.telegramcallnotifier;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmScheduler {

    public static final long TEST_INTERVAL_MS = 60 * 1000L;
    public static final long PROD_INTERVAL_MS = 10 * 60 * 1000L;

    public static void scheduleNext(Context context, long delayMs) {
        DebugLogger.log(context, "AlarmScheduler", "scheduleNext called delayMs=" + delayMs);
        DebugLogger.logState(context, "AlarmScheduler", "before scheduleNext");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("WAKE_AND_REPORT");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = System.currentTimeMillis() + delayMs;
        DebugLogger.log(context, "AlarmScheduler", "triggerAt=" + triggerAt + " now=" + System.currentTimeMillis());

        if (alarmManager == null) {
            DebugLogger.log(context, "AlarmScheduler", "alarmManager is null");
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
                DebugLogger.log(context, "AlarmScheduler", "setExactAndAllowWhileIdle success");
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
                DebugLogger.log(context, "AlarmScheduler", "setExact success");
            }
        } catch (SecurityException e) {
            DebugLogger.logError(context, "AlarmScheduler", e);
        } catch (Exception e) {
            DebugLogger.logError(context, "AlarmScheduler", e);
        }
    }

    public static void cancel(Context context) {
        DebugLogger.log(context, "AlarmScheduler", "cancel called");

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction("WAKE_AND_REPORT");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            DebugLogger.log(context, "AlarmScheduler", "alarm canceled");
        } else {
            DebugLogger.log(context, "AlarmScheduler", "cancel skipped alarmManager is null");
        }
    }
}
