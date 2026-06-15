package com.smsforward.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;

        SharedPreferences prefs =
                context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(MainActivity.KEY_ENABLED, false)) return;

        String botToken = prefs.getString(MainActivity.KEY_BOT_TOKEN, "").trim();
        String chatId   = prefs.getString(MainActivity.KEY_GROUP_CHAT_ID, "").trim();
        if (botToken.isEmpty() || chatId.isEmpty()) return;

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) return;

        StringBuilder body = new StringBuilder();
        String sender = "";
        for (SmsMessage sms : messages) {
            if (sms == null) continue;
            body.append(sms.getMessageBody());
            sender = sms.getOriginatingAddress();
        }

        if (!isAadhaarOtp(body.toString())) return;

        String text = "OTP from " + sender + ":\n" + body;

        // Enqueue into WorkManager's database immediately — this survives process death,
        // device reboot, and no-network conditions. The job will not run until the device
        // is online, and retries automatically with exponential backoff on failure.
        Data inputData = new Data.Builder()
                .putString(TelegramSendWorker.KEY_BOT_TOKEN, botToken)
                .putString(TelegramSendWorker.KEY_CHAT_ID, chatId)
                .putString(TelegramSendWorker.KEY_TEXT, text)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(TelegramSendWorker.class)
                .setInputData(inputData)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }

    // Matches Aadhaar OTP messages from UIDAI/C-DAC, e.g.:
    // "209032 is OTP for Aadhaar (XX2769) (valid for 10 mins) at C-DAC. -UIDAI"
    private boolean isAadhaarOtp(String body) {
        String lower = body.toLowerCase().replace("-", "");
        return lower.contains("otp") && lower.contains("aadhaar") && lower.contains("cdac");
    }
}
