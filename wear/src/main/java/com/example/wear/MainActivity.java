package com.example.wear;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.wear.ambient.AmbientLifecycleObserver;
import androidx.wear.ambient.AmbientLifecycleObserverKt;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends androidx.activity.ComponentActivity {

    private static final String TAG = "GoProWatchStandalone";
    private static final String GOPRO_PASSWORD = "a2222222";
    private static final String BASE_URL = "http://10.5.5.9";

    private volatile Network goproNetwork = null;
    private volatile boolean waitingForRecordConfirmation = false;

    private Button btnStartRec;
    private Button btnStopRec;
    private Button btnPowerOn;

    private ImageView connectionDot;

    private View batteryBar1;
    private View batteryBar2;
    private View batteryBar3;

    private final ExecutorService networkExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable cameraStatusPollRunnable = new Runnable() {
        @Override
        public void run() {

            fetchBatteryStatus();

            mainHandler.postDelayed(this, 10000);
        }
    };

    @SuppressLint("WearRecents")
    @SuppressWarnings("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        AmbientLifecycleObserver observer =
                AmbientLifecycleObserverKt.AmbientLifecycleObserver(
                        this,
                        new AmbientLifecycleObserver.AmbientLifecycleCallback() {
                            @Override
                            public void onEnterAmbient(
                                    @NonNull AmbientLifecycleObserver.AmbientDetails details) {
                            }

                            @Override
                            public void onExitAmbient() {
                            }

                            @Override
                            public void onUpdateAmbient() {
                            }
                        });

        getLifecycle().addObserver(observer);

        btnStartRec = findViewById(R.id.watch_start);
        btnStopRec = findViewById(R.id.watch_stop);
        btnPowerOn = findViewById(R.id.watch_on);

        View btnPowerOff = findViewById(R.id.watch_off);

        batteryBar1 = findViewById(R.id.battery_bar_1);
        batteryBar2 = findViewById(R.id.battery_bar_2);
        batteryBar3 = findViewById(R.id.battery_bar_3);

        View batteryContainer = findViewById(R.id.battery_container);
        connectionDot = findViewById(R.id.connection_dot);

        clearBatteryIndicator();
        updateConnectionIndicator(false);

        if (batteryContainer != null) {
            batteryContainer.setOnClickListener(v -> {
                vibrateClick();
                checkHero3WifiConnection();
                fetchBatteryStatus();
            });

            applyPressEffect(batteryContainer);
        }

        applyPressEffect(btnPowerOn);
        applyPressEffect(btnPowerOff);
        applyPressEffect(btnStartRec);
        applyPressEffect(btnStopRec);

        registerGoProNetworkCallback();

        if (btnPowerOn != null && btnStartRec != null && btnStopRec != null) {

            btnPowerOn.setOnClickListener(v -> {
                vibrateClick();
                sendGoProCommand(
                        "/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%01",
                        "POWER_ON");
            });

            btnPowerOn.setOnLongClickListener(v -> {
                try {
                    android.content.Intent wifiIntent =
                            new android.content.Intent(
                                    android.provider.Settings.ACTION_WIFI_SETTINGS);

                    wifiIntent.addFlags(
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(wifiIntent);

                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch Wi-Fi settings", e);
                }

                return true;
            });

            if (btnPowerOff != null) {

                btnPowerOff.setOnClickListener(v -> {
                    // Short press intentionally does nothing.
                    Log.d(TAG, "Pwr Off short press ignored. Use long press to power off.");
                });

                btnPowerOff.setOnLongClickListener(v -> {
                    vibrateConfirm();

                    waitingForRecordConfirmation = false;

                    sendGoProCommand(
                            "/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%00",
                            "POWER_OFF");

                    return true;
                });
            }

            btnStartRec.setOnClickListener(v -> {
                // Short press intentionally does nothing.
                Log.d(TAG, "REC short press ignored. Use long press to start recording.");
            });

            btnStartRec.setOnLongClickListener(v -> {
                vibrateClick();

                waitingForRecordConfirmation = true;

                mainHandler.postDelayed(() -> waitingForRecordConfirmation = false, 12000);

                sendGoProCommand(
                        "/camera/SH?t=" + GOPRO_PASSWORD + "&p=%01",
                        "RECORD_START");

                return true;
            });

            btnStopRec.setOnClickListener(v -> {
                vibrateClick();

                waitingForRecordConfirmation = false;

                sendGoProCommand(
                        "/camera/SH?t=" + GOPRO_PASSWORD + "&p=%00",
                        "RECORD_STOP");
            });
        }
    }

    private void fetchBatteryStatus() {

        if (goproNetwork == null) {
            Log.w(TAG, "Camera status skipped: HERO3 WiFi not connected");

            mainHandler.post(() -> {
                clearBatteryIndicator();
                updateConnectionIndicator(false);
                updateUiButtonStates("POWER_OFF");
                updateUiButtonStates("RECORD_STOP");
            });

            return;
        }

        networkExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(BASE_URL + "/camera/sx?t=" + GOPRO_PASSWORD);
                connection = (HttpURLConnection) goproNetwork.openConnection(url);

                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");

                int response = connection.getResponseCode();

                if (response == 200) {

                    InputStream is = connection.getInputStream();

                    byte[] data = new byte[80];
                    int bytesRead = is.read(data);

                    is.close();

                    if (bytesRead <= 29) {
                        mainHandler.post(() -> {
                            clearBatteryIndicator();
                            updateUiButtonStates("POWER_OFF");
                            updateUiButtonStates("RECORD_STOP");
                        });

                        checkHero3WifiConnection();
                        return;
                    }

                    int batteryBars = data[19] & 0xFF;
                    int recordingStatus = data[29] & 0xFF;

                    boolean cameraIsRecording = recordingStatus != 0;

                    if (batteryBars < 1 || batteryBars > 3) {
                        mainHandler.post(() -> {
                            clearBatteryIndicator();
                            updateUiButtonStates("POWER_OFF");
                            updateUiButtonStates("RECORD_STOP");
                        });

                        checkHero3WifiConnection();
                        return;
                    }

                    mainHandler.post(() -> {
                        updateConnectionIndicator(true);
                        updateUiButtonStates("POWER_ON");
                        updateBatteryIndicator(batteryBars);

                        if (cameraIsRecording) {
                            updateUiButtonStates("RECORD_START");

                            if (waitingForRecordConfirmation) {
                                waitingForRecordConfirmation = false;
                                vibrateConfirm();
                            }

                        } else {
                            updateUiButtonStates("RECORD_STOP");
                        }
                    });

                } else {

                    mainHandler.post(() -> {
                        clearBatteryIndicator();
                        updateUiButtonStates("POWER_OFF");
                        updateUiButtonStates("RECORD_STOP");
                    });

                    checkHero3WifiConnection();
                }

            } catch (Exception e) {

                Log.e(TAG, "Camera status read failed", e);

                mainHandler.post(() -> {
                    clearBatteryIndicator();
                    updateUiButtonStates("POWER_OFF");
                    updateUiButtonStates("RECORD_STOP");
                });

                checkHero3WifiConnection();

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void checkHero3WifiConnection() {

        if (goproNetwork == null) {
            mainHandler.post(() ->
                    updateConnectionIndicator(false));
            return;
        }

        networkExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(BASE_URL + "/bacpac/se?t=" + GOPRO_PASSWORD);
                connection = (HttpURLConnection) goproNetwork.openConnection(url);

                connection.setConnectTimeout(1500);
                connection.setReadTimeout(1500);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");

                int response = connection.getResponseCode();

                mainHandler.post(() ->
                        updateConnectionIndicator(response == 200));

            } catch (Exception e) {

                mainHandler.post(() ->
                        updateConnectionIndicator(false));

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void registerGoProNetworkCallback() {

        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            return;
        }

        NetworkRequest request =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .build();

        connectivityManager.requestNetwork(
                request,
                new ConnectivityManager.NetworkCallback() {

                    @Override
                    public void onAvailable(@NonNull Network network) {

                        Log.d(TAG, "WiFi network available: " + network);

                        goproNetwork = network;

                        try {
                            connectivityManager.bindProcessToNetwork(network);

                            mainHandler.postDelayed(
                                    MainActivity.this::checkHero3WifiConnection,
                                    1000);

                            mainHandler.postDelayed(
                                    MainActivity.this::checkHero3WifiConnection,
                                    3000);

                            mainHandler.postDelayed(
                                    MainActivity.this::fetchBatteryStatus,
                                    3000);

                            mainHandler.postDelayed(
                                    MainActivity.this::fetchBatteryStatus,
                                    7000);

                        } catch (Exception e) {

                            Log.e(TAG, "Network bind failed", e);
                        }
                    }

                    @Override
                    public void onLost(@NonNull Network network) {

                        if (network.equals(goproNetwork)) {
                            goproNetwork = null;
                        }

                        clearProcessBinding();

                        mainHandler.post(() -> {
                            updateConnectionIndicator(false);
                            updateUiButtonStates("POWER_OFF");
                            updateUiButtonStates("RECORD_STOP");
                        });

                        Log.d(TAG, "WiFi network disconnected");
                    }
                });
    }

    private void sendGoProCommand(
            final String commandUrl,
            final String actionType) {

        if (goproNetwork == null) {
            Log.w(TAG, "GoPro network not connected");

            waitingForRecordConfirmation = false;

            mainHandler.post(() ->
                    updateConnectionIndicator(false));

            return;
        }

        networkExecutor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                URL url = new URL(BASE_URL + commandUrl);

                connection =
                        (HttpURLConnection)
                                goproNetwork.openConnection(url);

                connection.setConnectTimeout(2000);
                connection.setReadTimeout(3000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");

                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {

                    mainHandler.post(() ->
                            updateConnectionIndicator(true));

                    if ("POWER_ON".equals(actionType)) {

                        mainHandler.post(() ->
                                updateUiButtonStates("POWER_ON"));

                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ignored) {
                        }

                        fetchBatteryStatus();
                    }

                    else if ("POWER_OFF".equals(actionType)) {

                        waitingForRecordConfirmation = false;

                        mainHandler.post(() -> {
                            clearBatteryIndicator();
                            updateUiButtonStates("POWER_OFF");
                            updateUiButtonStates("RECORD_STOP");
                            updateConnectionIndicator(true);
                        });
                    }

                    else if ("RECORD_START".equals(actionType)) {

                        mainHandler.postDelayed(
                                MainActivity.this::fetchBatteryStatus,
                                1500);

                        mainHandler.postDelayed(
                                MainActivity.this::fetchBatteryStatus,
                                3000);
                    }

                    else if ("RECORD_STOP".equals(actionType)) {

                        waitingForRecordConfirmation = false;

                        mainHandler.post(() ->
                                updateUiButtonStates("RECORD_STOP"));

                        mainHandler.postDelayed(
                                MainActivity.this::fetchBatteryStatus,
                                1500);
                    }

                } else {

                    Log.w(TAG,
                            "Camera returned code "
                                    + responseCode);

                    waitingForRecordConfirmation = false;

                    checkHero3WifiConnection();
                }

            } catch (Exception e) {

                Log.e(TAG,
                        "Command failed: " + actionType,
                        e);

                waitingForRecordConfirmation = false;

                checkHero3WifiConnection();

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void updateUiButtonStates(String actionType) {

        if ("POWER_ON".equals(actionType)
                && btnPowerOn != null) {

            btnPowerOn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#4CAF50")));

            btnPowerOn.setTextColor(Color.WHITE);
        }

        else if ("POWER_OFF".equals(actionType)
                && btnPowerOn != null) {

            btnPowerOn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#333333")));

            btnPowerOn.setTextColor(Color.LTGRAY);

            clearBatteryIndicator();
        }

        else if ("RECORD_START".equals(actionType)
                && btnStartRec != null) {

            btnStartRec.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#FF1744")));

            btnStartRec.setTextColor(Color.WHITE);
        }

        else if ("RECORD_STOP".equals(actionType)
                && btnStartRec != null) {

            btnStartRec.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#333333")));

            btnStartRec.setTextColor(Color.LTGRAY);
        }

        if (btnStopRec != null) {

            btnStopRec.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.parseColor("#333333")));

            btnStopRec.setTextColor(Color.LTGRAY);
        }
    }

    private void updateBatteryIndicator(int batteryBars) {

        if (batteryBar1 == null ||
                batteryBar2 == null ||
                batteryBar3 == null) {
            return;
        }

        int inactiveColor = Color.parseColor("#333333");
        int activeColor;

        if (batteryBars >= 3) {
            activeColor = Color.parseColor("#4CAF50");
        } else if (batteryBars == 2) {
            activeColor = Color.parseColor("#FFC107");
        } else {
            activeColor = Color.parseColor("#FF1744");
        }

        batteryBar1.setBackgroundColor(inactiveColor);
        batteryBar2.setBackgroundColor(inactiveColor);
        batteryBar3.setBackgroundColor(inactiveColor);

        if (batteryBars >= 1) {
            batteryBar1.setBackgroundColor(activeColor);
        }

        if (batteryBars >= 2) {
            batteryBar2.setBackgroundColor(activeColor);
        }

        if (batteryBars >= 3) {
            batteryBar3.setBackgroundColor(activeColor);
        }
    }

    private void clearBatteryIndicator() {

        int inactiveColor = Color.parseColor("#333333");

        if (batteryBar1 != null) {
            batteryBar1.setBackgroundColor(inactiveColor);
        }

        if (batteryBar2 != null) {
            batteryBar2.setBackgroundColor(inactiveColor);
        }

        if (batteryBar3 != null) {
            batteryBar3.setBackgroundColor(inactiveColor);
        }
    }

    private void updateConnectionIndicator(boolean connected) {

        if (connectionDot == null) {
            return;
        }

        if (connected) {
            connectionDot.setColorFilter(Color.parseColor("#FFC107"));
        } else {
            connectionDot.setColorFilter(Color.parseColor("#555555"));
        }
    }

    private void vibrateClick() {

        try {

            Vibrator vibrator =
                    (Vibrator) getSystemService(
                            Context.VIBRATOR_SERVICE);

            if (vibrator != null
                    && vibrator.hasVibrator()) {

                vibrator.vibrate(
                        VibrationEffect.createOneShot(
                                70,
                                VibrationEffect.DEFAULT_AMPLITUDE));
            }

        } catch (Exception e) {

            Log.e(TAG,
                    "Vibration failed",
                    e);
        }
    }

    private void vibrateConfirm() {

        try {

            Vibrator vibrator =
                    (Vibrator) getSystemService(
                            Context.VIBRATOR_SERVICE);

            if (vibrator != null
                    && vibrator.hasVibrator()) {

                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                new long[]{0, 90, 80, 90},
                                -1));
            }

        } catch (Exception e) {

            Log.e(TAG,
                    "Confirmation vibration failed",
                    e);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void applyPressEffect(View view) {

        if (view == null) {
            return;
        }

        view.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:
                    v.animate()
                            .alpha(0.6f)
                            .setDuration(50)
                            .start();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate()
                            .alpha(1.0f)
                            .setDuration(100)
                            .start();
                    break;
            }

            return false;
        });
    }

    private void clearProcessBinding() {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                            getSystemService(
                                    Context.CONNECTIVITY_SERVICE);

            if (cm != null) {
                cm.bindProcessToNetwork(null);
            }

        } catch (Exception e) {

            Log.e(TAG,
                    "Error cleaning network binding",
                    e);
        }
    }

    @Override
    protected void onPause() {

        super.onPause();

        mainHandler.removeCallbacks(cameraStatusPollRunnable);
    }

    @Override
    protected void onResume() {

        super.onResume();

        mainHandler.removeCallbacks(cameraStatusPollRunnable);

        mainHandler.postDelayed(cameraStatusPollRunnable, 1000);
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        mainHandler.removeCallbacksAndMessages(null);

        networkExecutor.shutdownNow();

        clearProcessBinding();
    }
}