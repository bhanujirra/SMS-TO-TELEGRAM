# SMS to Telegram

An Android app that automatically forwards every incoming SMS to a Telegram group via a bot.

## How it works

1. The app listens for incoming SMS using a `BroadcastReceiver`.
2. When an SMS arrives, it dispatches a `WorkManager` job to send the message to Telegram.
3. The Telegram Bot API (`sendMessage`) delivers it to your chosen group.

## Setup

### 1. Create a Telegram bot
1. Open Telegram and message `@BotFather`.
2. Send `/newbot` and follow the prompts to get a **bot token**.
3. Add the bot to your Telegram group and send any message in that group.

### 2. Configure the app
1. Install the APK on your Android device.
2. Open the app and paste your **bot token**.
3. Tap **Fetch Groups** — the app calls `getUpdates` on your bot to discover groups it has been added to.
4. Select the target group from the list. If no groups appear, use **Enter Manually** and paste your chat ID (get it from `@userinfobot`).
5. Tap **Grant SMS Permissions** and allow Receive SMS and Read SMS.
6. Toggle the **Enable** switch.

The status banner turns green when everything is configured and forwarding is active.

## Build

```
# Open in Android Studio, or:
./gradlew assembleRelease
```

Signing credentials are read from `local.properties` (not tracked in git). Create one with:

```properties
sdk.dir=/path/to/your/Android/sdk

KEYSTORE_PATH=app/your.keystore
KEYSTORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

## Permissions

| Permission | Why |
|---|---|
| `RECEIVE_SMS` | Detect incoming messages |
| `READ_SMS` | Read message content |

No `SEND_SMS` permission is needed — messages are forwarded over the internet via Telegram, not as SMS.

## Notes

- Battery optimization is disabled on first launch to prevent Android from delaying forwarding when the screen is off.
- Settings (bot token, chat ID, enabled state) are stored in `SharedPreferences` on-device.
- If the bot returns a permanent HTTP error (4xx), the job is marked failed and not retried. Transient errors (5xx, network) are retried by WorkManager automatically.
