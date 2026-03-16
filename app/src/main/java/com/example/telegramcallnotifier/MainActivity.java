package com.example.telegramcallnotifier;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 200;
    private static final int EXACT_ALARM_REQUEST_CODE = 201;

    private TelegramSender telegramSender;
    private Button btnToggleService;
    private Button btnViewLogs;
    private TextView textStatus;
    private CustomExceptionHandler exceptionHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exceptionHandler = new CustomExceptionHandler(this);
        Thread.setDefaultUncaughtExceptionHandler(exceptionHandler);

        DebugLogger.log(this, "MainActivity", "onCreate intent=" + getIntent());
        DebugLogger.logState(this, "MainActivity", "onCreate");

        setContentView(R.layout.activity_main);

        Intent reportServiceIntent = new Intent(this, ReportService.class);
        reportServiceIntent.setAction("START_FOREGROUND_SERVICE");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(reportServiceIntent);
            } else {
                startService(reportServiceIntent);
            }
            DebugLogger.log(this, "MainActivity", "ReportService start requested from onCreate");
        } catch (Exception e) {
            DebugLogger.logError(this, "MainActivity", e);
        }

        AlarmScheduler.scheduleNext(this, AlarmScheduler.TEST_INTERVAL_MS);

        telegramSender = new TelegramSender(this);

        btnToggleService = findViewById(R.id.btnToggleService);
        btnViewLogs = findViewById(R.id.btnViewLogs);
        textStatus = findViewById(R.id.textStatus);

        btnToggleService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DebugLogger.log(MainActivity.this, "MainActivity", "btnToggleService clicked isServiceRunning=" + isServiceRunning());
                if (isServiceRunning()) {
                    stopService();
                } else {
                    checkPermissionsAndStartService();
                }
            }
        });

        btnViewLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DebugLogger.log(MainActivity.this, "MainActivity", "btnViewLogs clicked");
                openLogFile();
            }
        });

        checkPermissionsAndStartService();
        updateUI();

        DebugLogger.log(this, "MainActivity", "App Started. SDK=" + Build.VERSION.SDK_INT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        DebugLogger.log(this, "MainActivity", "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugLogger.log(this, "MainActivity", "onResume");
        DebugLogger.logState(this, "MainActivity", "onResume");
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DebugLogger.log(this, "MainActivity", "onPause");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DebugLogger.log(this, "MainActivity", "onDestroy");
    }

    private void updateUI() {
        boolean running = isServiceRunning();
        DebugLogger.log(this, "MainActivity", "updateUI running=" + running);
        if (running) {
            btnToggleService.setText("RUNNING");
            btnToggleService.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            textStatus.setText("Status: Service is Active");
        } else {
            btnToggleService.setText("START");
            btnToggleService.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#F44336")));
            textStatus.setText("Status: Service Stopped");
        }
    }

    private void showLogs() {
        String logs = CustomExceptionHandler.getLogContent(this);
        textStatus.setText(logs);
    }

    private void openLogFile() {
        File logFile = CustomExceptionHandler.getLogFile(this);
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "No logs found.", Toast.LENGTH_LONG).show();
            showLogs();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", logFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            DebugLogger.log(this, "MainActivity", "Opening log file path=" + logFile.getAbsolutePath());
            startActivity(Intent.createChooser(intent, "Open logs"));
        } catch (Exception e) {
            DebugLogger.logError(this, "MainActivity", e);
            showLogs();
        }
    }

    private boolean isServiceRunning() {
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (CallMonitorService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void stopService() {
        DebugLogger.log(this, "MainActivity", "stopService requested");
        Intent serviceIntent = new Intent(this, CallMonitorService.class);
        stopService(serviceIntent);
        updateUI();
        new android.os.Handler().postDelayed(this::updateUI, 500);
    }

    private boolean needsExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            boolean needs = alarmManager != null && !alarmManager.canScheduleExactAlarms();
            DebugLogger.log(this, "MainActivity", "needsExactAlarmPermission=" + needs);
            return needs;
        }
        return false;
    }

    private void checkExactAlarmAndStart() {
        DebugLogger.log(this, "MainActivity", "checkExactAlarmAndStart called");
        if (needsExactAlarmPermission()) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, EXACT_ALARM_REQUEST_CODE);
                Toast.makeText(this, "Please allow exact alarms so the 60-minute report works while the screen is off", Toast.LENGTH_LONG).show();
                DebugLogger.log(this, "MainActivity", "Requested exact alarm permission");
                return;
            } catch (Exception e) {
                DebugLogger.logError(this, "MainActivity", e);
            }
        }
        checkBatteryAndStart();
    }

    private void checkBatteryAndStart() {
        DebugLogger.log(this, "MainActivity", "checkBatteryAndStart called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
                    DebugLogger.log(this, "MainActivity", "Requested battery optimization ignore");
                    return;
                } catch (Exception e) {
                    DebugLogger.logError(this, "MainActivity", e);
                }
            }
        }
        startService();
    }

    private void checkPermissionsAndStartService() {
        DebugLogger.log(this, "MainActivity", "checkPermissionsAndStartService called");
        List<String> permsList = new ArrayList<>();
        permsList.add(Manifest.permission.READ_PHONE_STATE);
        permsList.add(Manifest.permission.READ_CALL_LOG);
        permsList.add(Manifest.permission.ANSWER_PHONE_CALLS);
        permsList.add(Manifest.permission.CALL_PHONE);

        if (Build.VERSION.SDK_INT >= 26) {
            permsList.add(Manifest.permission.READ_PHONE_NUMBERS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permsList.add(Manifest.permission.FOREGROUND_SERVICE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permsList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= 34) {
            permsList.add(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC);
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permsList) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        DebugLogger.log(this, "MainActivity", "Missing permissions count=" + listPermissionsNeeded.size() + " list=" + listPermissionsNeeded);

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            checkExactAlarmAndStart();
        }
    }

    private void startService() {
        Intent serviceIntent = new Intent(this, CallMonitorService.class);
        try {
            DebugLogger.log(this, "MainActivity", "startService requested");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            new android.os.Handler().postDelayed(this::updateUI, 500);
        } catch (Throwable e) {
            DebugLogger.logError(this, "MainActivity", e);
            Toast.makeText(this, "Error starting service: " + e.getMessage(), Toast.LENGTH_LONG).show();
            textStatus.setText("Error: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        DebugLogger.log(this, "MainActivity", "onRequestPermissionsResult requestCode=" + requestCode + " grantCount=" + grantResults.length);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            DebugLogger.log(this, "MainActivity", "permissions granted(firstOnly check)=" + granted);
            if (granted) {
                checkExactAlarmAndStart();
            } else {
                Toast.makeText(this, "Permissions are required for the app to work", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        DebugLogger.log(this, "MainActivity", "onActivityResult requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data);

        if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            startService();
        } else if (requestCode == EXACT_ALARM_REQUEST_CODE) {
            checkBatteryAndStart();
        }
    }
}
