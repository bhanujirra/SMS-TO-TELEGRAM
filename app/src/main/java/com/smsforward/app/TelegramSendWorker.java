package com.smsforward.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TelegramSendWorker extends Worker {

    static final String KEY_BOT_TOKEN = "bot_token";
    static final String KEY_CHAT_ID   = "chat_id";
    static final String KEY_TEXT      = "text";

    private static final String CHANNEL_ID  = "sms_forwarder";
    private static final int    MAX_ATTEMPTS = 5;

    public TelegramSendWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String botToken = getInputData().getString(KEY_BOT_TOKEN);
        String chatId   = getInputData().getString(KEY_CHAT_ID);
        String text     = getInputData().getString(KEY_TEXT);

        if (botToken == null || chatId == null || text == null) {
            return Result.failure(); // bad data, no point retrying
        }

        try {
            int responseCode = sendToTelegram(botToken, chatId, text);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return Result.success();
            }
            // 4xx = permanent error (bad token, wrong chat ID) — notify and stop retrying
            if (responseCode >= 400 && responseCode < 500) {
                showNotification("OTP Forward Failed",
                        "Permanent error " + responseCode + " — check bot token / chat ID");
                return Result.failure();
            }
            // 5xx or unexpected: retry with backoff
            return retryOrFail("Server error " + responseCode);

        } catch (Exception e) {
            // Network error: retry with backoff (WorkManager will wait for connectivity)
            return retryOrFail(e.getMessage());
        }
    }

    private Result retryOrFail(String reason) {
        if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
            showNotification("OTP Forward Failed",
                    "Gave up after " + MAX_ATTEMPTS + " attempts: " + reason);
            return Result.failure();
        }
        return Result.retry();
    }

    private int sendToTelegram(String botToken, String chatId, String text) throws Exception {
        URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);
        conn.setDoOutput(true);

        String json = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + escapeJson(text) + "\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void showNotification(String title, String message) {
        Context context = getApplicationContext();
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "SMS Forwarder", NotificationManager.IMPORTANCE_HIGH));

        nm.notify((int) System.currentTimeMillis(),
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build());
    }
}
