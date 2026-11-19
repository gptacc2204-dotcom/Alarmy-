# ScanWake Alarm (Anti-Cheat Object-Scanning Alarm App)

## Features Included
- **Strict Object Scanning (100% Offline):** Uses CameraX + Google ML Kit Image Labeling. Alarm only stops when the selected object (e.g., Sink, Bottle, Chair, Cup, etc.) is scanned.
- **Volume Lock:** Continuously forces Alarm Volume back to 100% and blocks hardware Volume Up/Down keys.
- **Anti-Escape Lockdown:** Runs as a Foreground Service. Blocks Back button and auto-relaunches the camera screen if you press Home or switch apps.
- **Lock Screen Bypass:** Wakes up the screen and opens directly over the lock screen.
- **Emergency Exit:** Tap the red `SCAN: ...` title 25 times in a row in case of total darkness/power outage.

## APK Kaise Banayein (3 Simple Steps)
1. Is ZIP file ko extract karein.
2. **Android Studio** open karein -> **Open** par click karein -> `ScanWakeAlarm` folder select karein (Gradle sync hone dein).
3. Top menu mein **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** par click karein.
4. APK file `app/build/outputs/apk/debug/app-debug.apk` mein ban jayegi—usay apne Android phone mein copy kar ke install kar lein!
