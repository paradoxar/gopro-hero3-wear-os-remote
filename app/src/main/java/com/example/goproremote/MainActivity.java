package com.example.goproremote;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "GoProController";
    private static final String GOPRO_PASSWORD = "a2222222";
    private static final String BASE_URL = "http://10.5.5.9";
    private Network goproNetwork = null;
    private boolean isWearableAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Layout inflation failed", e);
            return;
        }

        // Safely register network callback
        try {
            registerGoProNetworkCallback();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize network routing safely", e);
        }

        // Test if Wearable API is present on this device build
        try {
            Wearable.getMessageClient(this);
            isWearableAvailable = true;
        } catch (Exception e) {
            Log.e(TAG, "Wearable Services unavailable on this device", e);
            isWearableAvailable = false;
        }

        // Set up click handlers with explicit null-pointer safety checks
        if (findViewById(R.id.btn_power_on) != null) {
            findViewById(R.id.btn_power_on).setOnClickListener(v -> sendGoProCommand("/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%01"));
            findViewById(R.id.btn_power_on).setOnLongClickListener(v -> {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startActivity(new Intent(android.provider.Settings.Panel.ACTION_WIFI));
                    } else {
                        startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to open Wi-Fi panel settings", e);
                }
                return true;
            });
        }

        if (findViewById(R.id.btn_power_off) != null) {
            findViewById(R.id.btn_power_off).setOnClickListener(v -> sendGoProCommand("/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%00"));
        }
        if (findViewById(R.id.btn_start_rec) != null) {
            findViewById(R.id.btn_start_rec).setOnClickListener(v -> sendGoProCommand("/camera/SH?t=" + GOPRO_PASSWORD + "&p=%01"));
        }
        if (findViewById(R.id.btn_stop_rec) != null) {
            findViewById(R.id.btn_stop_rec).setOnClickListener(v -> sendGoProCommand("/camera/SH?t=" + GOPRO_PASSWORD + "&p=%00"));
        }
    }

    private void registerGoProNetworkCallback() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        connectivityManager.requestNetwork(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                Log.d(TAG, "Bound app network channel directly to GoPro Wi-Fi hardware.");
                goproNetwork = network;
            }
            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                goproNetwork = null;
            }
        });
    }

    private void sendGoProCommand(final String commandUrl) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BASE_URL + commandUrl);
                if (goproNetwork != null) {
                    connection = (HttpURLConnection) goproNetwork.openConnection(url);
                } else {
                    connection = (HttpURLConnection) url.openConnection();
                }
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(4000);
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "GoPro response code: " + responseCode);
            } catch (Exception e) {
                Log.e(TAG, "Network transmission error: ", e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isWearableAvailable) {
            try {
                Wearable.getMessageClient(this).addListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Failed to bind Wearable listener on resume", e);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isWearableAvailable) {
            try {
                Wearable.getMessageClient(this).removeListener(this);
            } catch (Exception e) {
                Log.e(TAG, "Failed to strip Wearable listener on pause", e);
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Inbound watch signal processed: " + path);
        switch (path) {
            case "/power_on": sendGoProCommand("/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%01"); break;
            case "/power_off": sendGoProCommand("/bacpac/PW?t=" + GOPRO_PASSWORD + "&p=%00"); break;
            case "/start_rec": sendGoProCommand("/camera/SH?t=" + GOPRO_PASSWORD + "&p=%01"); break;
            case "/stop_rec": sendGoProCommand("/camera/SH?t=" + GOPRO_PASSWORD + "&p=%00"); break;
        }
    }
}