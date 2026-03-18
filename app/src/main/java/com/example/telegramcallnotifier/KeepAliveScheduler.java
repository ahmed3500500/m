package com.example.telegramcallnotifier;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class KeepAliveScheduler {

    public static final long KEEP_ALIVE_INTERVAL_MS = 20 * 60 * 1000L;

    public static void scheduleNext(Context context, long delayMs) {
        DebugLogger.log(context, "KeepAliveScheduler", "scheduleNext called delayMs=" + delayMs);

        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) {
                DebugLogger.log(context, "KeepAliveScheduler", "alarmManager is null");
                return;
            }

            Intent intent = new Intent(context, KeepAliveReceiver.class);
            intent.setAction("KEEP_ALIVE_WAKE");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerAt = System.currentTimeMillis() + delayMs;

            boolean canExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canExact = alarmManager.canScheduleExactAlarms();
            }

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
                DebugLogger.log(context, "KeepAliveScheduler", "setExactAndAllowWhileIdle success");
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
                DebugLogger.log(context, "KeepAliveScheduler", "fallback setAndAllowWhileIdle used");
            }
        } catch (Exception e) {
            DebugLogger.logError(context, "KeepAliveScheduler", e);
        }
    }

    public static void cancel(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, KeepAliveReceiver.class);
            intent.setAction("KEEP_ALIVE_WAKE");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    2002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            alarmManager.cancel(pendingIntent);
            DebugLogger.log(context, "KeepAliveScheduler", "cancel success");
        } catch (Exception e) {
            DebugLogger.logError(context, "KeepAliveScheduler", e);
        }
    }
}
