# Telegram API response parsing — keep JSONObject field names
-keepattributes *Annotation*
-keep class org.json.** { *; }

# Material Components
-keep class com.google.android.material.** { *; }

# AppCompat
-keep class androidx.appcompat.** { *; }

# Keep app classes
-keep class com.smsforward.app.** { *; }
