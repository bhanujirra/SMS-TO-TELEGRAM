# SMS Forwarder

A minimal Android app that watches incoming SMS messages, checks if the message
text contains any of your configured keywords, and if so forwards the full
message as a new SMS to a target phone number.

## Project size
- No Material Components, no Jetpack Compose, no extra libraries beyond AppCompat.
- Resulting APK is typically 1-3 MB (well under the 10 MB target).

## How to open
1. Open Android Studio.
2. File -> Open -> select the `SmsForwarder` folder.
3. Let Gradle sync (it will download the Gradle wrapper / AGP if missing).
4. Build -> Make Project, or just hit Run on a connected device/emulator.

> Note: This project does not include the Gradle wrapper jar. Android Studio
> will offer to generate it automatically on first open ("Gradle wrapper not
> found" prompt) - accept it.

## How to use
1. Install the app on your phone.
2. Open the app.
3. Tap **Grant SMS Permissions** and allow Receive SMS, Read SMS, Send SMS.
4. Enter:
   - **Keywords**: comma-separated words/phrases to match (case-insensitive),
     e.g. `OTP, debited, credited`
   - **Target number**: full international format, e.g. `+919876543210`
5. Tap **Save Settings**.

From now on, any incoming SMS containing one of your keywords will be
automatically forwarded as a new SMS to the target number, in the background,
with no further interaction needed.

## Important notes
- `SEND_SMS`, `READ_SMS`, and `RECEIVE_SMS` are "dangerous" permissions.
  This is fine for personal sideloaded use, but if you ever publish to the
  Play Store, Google requires the app to be a default SMS handler for most
  SMS-permission use cases.
- Forwarded messages count as normal SMS against your carrier plan/charges.
- The receiver only forwards once per message even if multiple keywords match.
- Keywords and target number are stored locally in SharedPreferences
  (unencrypted) - don't put highly sensitive data there if the phone could be
  accessed by others.

## Possible extensions
- Multiple target numbers (comma-separated, loop and send to each).
- A toggle switch to pause/resume forwarding without clearing settings.
- Logging forwarded messages to a local list/history screen.
- Per-keyword target numbers (route different alerts to different people).
