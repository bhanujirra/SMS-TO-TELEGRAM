package com.smsforward.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_NAME        = "sms_forward_prefs";
    public static final String KEY_BOT_TOKEN     = "bot_token";
    public static final String KEY_GROUP_CHAT_ID = "group_chat_id";
    public static final String KEY_GROUP_NAME    = "group_name";
    public static final String KEY_ENABLED       = "enabled";

    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextInputEditText etBotToken;
    private MaterialButton btnFetchGroups;
    private LinearLayout layoutSelectedGroup, statusBanner;
    private View statusDot;
    private TextView tvSelectedGroup, tvSelectedChatId, tvStatus;
    private SwitchMaterial switchEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etBotToken          = findViewById(R.id.etBotToken);
        btnFetchGroups      = findViewById(R.id.btnFetchGroups);
        layoutSelectedGroup = findViewById(R.id.layoutSelectedGroup);
        tvSelectedGroup     = findViewById(R.id.tvSelectedGroup);
        tvSelectedChatId    = findViewById(R.id.tvSelectedChatId);
        statusBanner        = findViewById(R.id.statusBanner);
        statusDot           = findViewById(R.id.statusDot);
        tvStatus            = findViewById(R.id.tvStatus);
        switchEnabled       = findViewById(R.id.switchEnabled);

        MaterialButton btnRequestPermissions  = findViewById(R.id.btnRequestPermissions);

        loadSettings();
        updateStatus();

        btnFetchGroups.setOnClickListener(v -> fetchGroups());
        btnRequestPermissions.setOnClickListener(v -> requestSmsPermissions());

        switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(KEY_ENABLED, isChecked).apply();
            updateStatus();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        promptBatteryOptimizationIfNeeded();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        etBotToken.setText(prefs.getString(KEY_BOT_TOKEN, ""));
        switchEnabled.setChecked(prefs.getBoolean(KEY_ENABLED, false));

        String savedName   = prefs.getString(KEY_GROUP_NAME, "");
        String savedChatId = prefs.getString(KEY_GROUP_CHAT_ID, "");
        if (!savedChatId.isEmpty()) {
            showSelectedGroup(savedName, savedChatId);
        }
    }

    // ── Fetch groups from Telegram ──────────────────────────────────────────

    private void fetchGroups() {
        String token = etBotToken.getText() != null
                ? etBotToken.getText().toString().trim() : "";

        if (token.isEmpty()) {
            Toast.makeText(this, "Paste your bot token first", Toast.LENGTH_SHORT).show();
            return;
        }

        btnFetchGroups.setEnabled(false);
        btnFetchGroups.setText("Fetching\u2026");

        new Thread(() -> {
            try {
                String response = httpGet("https://api.telegram.org/bot" + token + "/getUpdates?offset=-100&limit=100");
                JSONObject json = new JSONObject(response);

                if (!json.getBoolean("ok")) {
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                "Invalid bot token. Check and try again.",
                                Toast.LENGTH_LONG).show();
                        resetFetchButton();
                    });
                    return;
                }

                // Collect unique groups (id → title)
                Map<String, String> groups = new LinkedHashMap<>();
                JSONArray results = json.getJSONArray("result");
                for (int i = 0; i < results.length(); i++) {
                    JSONObject update = results.getJSONObject(i);
                    JSONObject chat = extractChat(update);
                    if (chat != null) {
                        String type = chat.optString("type", "");
                        if (type.equals("group") || type.equals("supergroup")) {
                            String id    = String.valueOf(chat.getLong("id"));
                            String title = chat.optString("title", "Group " + id);
                            groups.put(id, title);
                        }
                    }
                }

                runOnUiThread(() -> {
                    resetFetchButton();
                    if (groups.isEmpty()) {
                        new AlertDialog.Builder(this)
                                .setTitle("No Groups Found")
                                .setMessage("Bot has no recent messages to scan.\n\nYou can:\n" +
                                        "• Send a message in the group, then try again\n" +
                                        "• Or enter your Chat ID manually")
                                .setPositiveButton("Enter Manually", (d, w) -> showManualChatIdDialog(token))
                                .setNegativeButton("Try Again", (d, w) -> fetchGroups())
                                .show();
                    } else {
                        // Save token first
                        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                .edit().putString(KEY_BOT_TOKEN, token).apply();
                        showGroupPicker(groups);
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Network error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    resetFetchButton();
                });
            }
        }).start();
    }

    private JSONObject extractChat(JSONObject update) {
        String[] keys = {"message", "edited_message", "channel_post",
                         "my_chat_member", "chat_member"};
        for (String key : keys) {
            if (update.has(key)) {
                try {
                    JSONObject obj = update.getJSONObject(key);
                    if (obj.has("chat")) return obj.getJSONObject("chat");
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void showGroupPicker(Map<String, String> groups) {
        List<String> ids    = new ArrayList<>(groups.keySet());
        List<String> titles = new ArrayList<>(groups.values());

        String[] items = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            items[i] = titles.get(i) + "\n" + ids.get(i);
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Group")
                .setItems(items, (dialog, which) -> {
                    String chatId = ids.get(which);
                    String name   = titles.get(which);
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(KEY_GROUP_CHAT_ID, chatId)
                            .putString(KEY_GROUP_NAME, name)
                            .apply();
                    showSelectedGroup(name, chatId);
                    updateStatus();
                    Toast.makeText(this, "Group saved!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showManualChatIdDialog(String token) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("e.g. -1001234567890");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new AlertDialog.Builder(this)
                .setTitle("Enter Chat ID")
                .setMessage("Open Telegram, go to your group, forward any message to @userinfobot — it will reply with the Chat ID (a negative number like -1001234567890).")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String chatId = input.getText().toString().trim();
                    if (chatId.isEmpty()) {
                        Toast.makeText(this, "Chat ID cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putString(KEY_BOT_TOKEN, token)
                            .putString(KEY_GROUP_CHAT_ID, chatId)
                            .putString(KEY_GROUP_NAME, "")
                            .apply();
                    showSelectedGroup("", chatId);
                    updateStatus();
                    Toast.makeText(this, "Chat ID saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSelectedGroup(String name, String chatId) {
        tvSelectedGroup.setText(name.isEmpty() ? chatId : name);
        tvSelectedChatId.setText("Chat ID: " + chatId);
        layoutSelectedGroup.setVisibility(View.VISIBLE);
    }

    private void resetFetchButton() {
        btnFetchGroups.setEnabled(true);
        btnFetchGroups.setText(getString(R.string.btn_fetch));
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        java.io.InputStream errorStream = conn.getErrorStream();
        java.io.InputStream stream = (code >= 200 && code < 300)
                ? conn.getInputStream()
                : (errorStream != null ? errorStream : conn.getInputStream());
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        conn.disconnect();
        return sb.toString();
    }

    // ── Permissions ─────────────────────────────────────────────────────────

    private static final String[] SMS_PERMISSIONS = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
    };

    private void requestSmsPermissions() {
        if (hasSmsPermissions()) {
            Toast.makeText(this, "SMS permissions already granted", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if any permission was permanently denied ("Don't ask again")
        boolean permanentlyDenied = false;
        for (String perm : SMS_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
                    && !ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
                    && wasPermissionAskedBefore(perm)) {
                permanentlyDenied = true;
                break;
            }
        }

        if (permanentlyDenied) {
            openAppSettings();
            return;
        }

        // Mark that we are asking
        for (String perm : SMS_PERMISSIONS) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean("asked_" + perm, true).apply();
        }
        ActivityCompat.requestPermissions(this, SMS_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    private boolean wasPermissionAskedBefore(String perm) {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("asked_" + perm, false);
    }

    private void openAppSettings() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("SMS permission was denied. Please enable it manually:\n\nSettings → Apps → SmsForwarder → Permissions → SMS")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) return;

        boolean allGranted = true;
        boolean anyPermanentlyDenied = false;

        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                    anyPermanentlyDenied = true;
                }
            }
        }

        if (allGranted) {
            Toast.makeText(this, "SMS permissions granted", Toast.LENGTH_SHORT).show();
        } else if (anyPermanentlyDenied) {
            openAppSettings();
        } else {
            Toast.makeText(this, "Permission denied. Tap the button to try again.", Toast.LENGTH_LONG).show();
        }

        updateStatus();
    }

    // ── Battery optimization ─────────────────────────────────────────────────

    private void promptBatteryOptimizationIfNeeded() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;

        boolean alreadyAsked = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("asked_battery_opt", false);
        if (alreadyAsked) return;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean("asked_battery_opt", true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Disable Battery Optimization")
                .setMessage("Android may delay forwarding when the screen is off. " +
                        "Tap \"Disable\" to exempt this app so OTPs are sent instantly.")
                .setPositiveButton("Disable", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton("Skip", null)
                .show();
    }

    // ── Status ───────────────────────────────────────────────────────────────

    private void updateStatus() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean permissionsGranted = hasSmsPermissions();
        boolean hasToken   = !prefs.getString(KEY_BOT_TOKEN, "").isEmpty();
        boolean hasChatId  = !prefs.getString(KEY_GROUP_CHAT_ID, "").isEmpty();
        boolean enabled    = prefs.getBoolean(KEY_ENABLED, false);

        boolean ready = permissionsGranted && hasToken && hasChatId;

        if (ready && enabled) {
            statusDot.setBackgroundResource(R.drawable.dot_green);
            String name = prefs.getString(KEY_GROUP_NAME, "");
            tvStatus.setText("Active \u2192 " + (name.isEmpty() ? "group" : name));
            tvStatus.setTextColor(0xFFB3D0FF);
        } else {
            statusDot.setBackgroundResource(R.drawable.dot_red);
            if (!permissionsGranted)  tvStatus.setText("Permissions needed");
            else if (!hasToken)       tvStatus.setText("Bot token not set");
            else if (!hasChatId)      tvStatus.setText("No group selected");
            else                      tvStatus.setText("Paused");
            tvStatus.setTextColor(0xFFFFCDD2);
        }
    }

    private boolean hasSmsPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

}
