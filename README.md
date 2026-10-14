# AutoTask Macro - Android Screen Automation APK

An Android automation app with a floating overlay widget featuring **Start Record**, **Stop**, and **Play Loop** buttons that float over your Android home screen and any target application.

## Core Features
1. **Floating Overlay Widget**:
   - Stays on top of your home screen and any app (just like screen recorder controls).
   - Draggable anywhere along screen edges.
   - 3 instant buttons: **● Rec** (Record), **■ Stop** (Halt), and **▶ Loop** (Repeat).

2. **Task Capture & Infinite Form Repeater**:
   - When you tap **● Rec**, fill any form or perform any repetitive clicks in any Android app.
   - The Accessibility Service captures every click coordinate, field focus, and text entry.
   - When you tap **▶ Loop**, it will replay that exact task sequence again and again in a continuous loop until you tap **■ Stop**!

## How to Compile into an APK

### Option A: Using Free GitHub Actions (Zero Setup - Recommended)
1. Create a new private or public GitHub repository.
2. Push or upload these files to your GitHub repository.
3. Go to the **Actions** tab on GitHub.
4. Click on the **Build Android APK** workflow and click **Run workflow**.
5. Once finished (approx. 2 minutes), download the ready `AutoTask-Macro-Debug-APK` (.apk file)!
6. Transfer to your phone and install!

### Option B: Using Android Studio
1. Open Android Studio.
2. Select **Open** and select this project folder.
3. Let Gradle sync.
4. Click **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
5. Transfer the output `app-debug.apk` to your Android device.

### Device Permissions Setup
Upon opening the app on your phone:
1. Tap **Grant Overlay Permission** -> Toggle "Allow display over other apps" for AutoTask Macro.
2. Tap **Enable Accessibility Service** -> Find "AutoTask Macro" under Installed Apps and turn it ON.
3. Tap **Launch Floating Start/Stop Overlay**.
4. The floating widget will appear on your screen. Navigate to your target form app and start recording!