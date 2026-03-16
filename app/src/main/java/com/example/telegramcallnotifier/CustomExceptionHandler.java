package com.example.telegramcallnotifier;

import android.content.Context;

import java.io.File;

public class CustomExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CustomExceptionHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        DebugLogger.log(context, "CrashHandler", "uncaughtException thread=" + (thread != null ? thread.getName() : "null"));
        DebugLogger.logError(context, "CrashHandler", throwable);

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            System.exit(1);
        }
    }

    public static void logError(Context context, Throwable throwable) {
        DebugLogger.logError(context, "CrashHandler", throwable);
    }

    public static void log(Context context, String message) {
        DebugLogger.log(context, "AppLog", message);
    }

    public static File getLogFile(Context context) {
        return DebugLogger.getLogFile(context);
    }

    public static String getLogContent(Context context) {
        return DebugLogger.getLogContent(context);
    }
}
