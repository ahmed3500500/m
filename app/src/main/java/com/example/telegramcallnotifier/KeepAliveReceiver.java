package com.example.telegramcallnotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

public class KeepAliveReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        DebugLogger.log(context, "KeepAliveReceiver", "onReceive action=" + (intent != null ? intent.getAction() : "null"));
        DebugLogger.logState(context, "KeepAliveReceiver", "keep alive fired");

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;

        try {
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "TelegramCallNotifier:KeepAliveWakeLock"
                );
                wakeLock.acquire(20_000L);
                DebugLogger.log(context, "KeepAliveReceiver", "WakeLock acquired");
            }

            Intent wakeIntent = new Intent(context, WakeActivity.class);
            wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(wakeIntent);
            DebugLogger.log(context, "KeepAliveReceiver", "WakeActivity started");

            playSilentAudio(context);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent reportIntent = new Intent(context, ReportService.class);
                    reportIntent.setAction("KEEP_ALIVE_REPORT");
                    reportIntent.putExtra("reportType", "keep_alive");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(reportIntent);
                    } else {
                        context.startService(reportIntent);
                    }

                    DebugLogger.log(context, "KeepAliveReceiver", "Keep alive report requested after 10 seconds");
                } catch (Exception e) {
                    DebugLogger.logError(context, "KeepAliveReceiver", e);
                }
            }, 10_000L);

        } catch (Exception e) {
            DebugLogger.logError(context, "KeepAliveReceiver", e);
        } finally {
            try {
                KeepAliveScheduler.scheduleNext(context, KeepAliveScheduler.KEEP_ALIVE_INTERVAL_MS);
                DebugLogger.log(context, "KeepAliveReceiver", "Next keep alive scheduled");
            } catch (Exception e) {
                DebugLogger.logError(context, "KeepAliveReceiver", e);
            }

            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                    DebugLogger.log(context, "KeepAliveReceiver", "WakeLock released");
                } catch (Exception e) {
                    DebugLogger.logError(context, "KeepAliveReceiver", e);
                }
            }
        }
    }

    private void playSilentAudio(Context context) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                );
            }

            mediaPlayer.setVolume(0f, 0f);

            android.content.res.AssetFileDescriptor afd =
                    context.getResources().openRawResourceFd(R.raw.silent_keepalive);

            if (afd == null) {
                DebugLogger.log(context, "KeepAliveReceiver", "silent_keepalive resource not found");
                return;
            }

            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            mediaPlayer.setOnPreparedListener(mp -> {
                DebugLogger.log(context, "KeepAliveReceiver", "Silent audio prepared, starting");
                mp.start();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                DebugLogger.log(context, "KeepAliveReceiver", "Silent audio completed");
                try {
                    mp.stop();
                } catch (Exception ignored) {
                }
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                DebugLogger.log(context, "KeepAliveReceiver", "Silent audio error what=" + what + " extra=" + extra);
                try {
                    mp.release();
                } catch (Exception ignored) {
                }
                return true;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            DebugLogger.logError(context, "KeepAliveReceiver", e);
        }
    }
}
