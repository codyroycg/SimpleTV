# Simple TV

Two small apps:

- **Simple TV** (`tv/`): a simplified home screen for your Fire TV Stick with four big tiles for Netflix, YouTube, Prime Video, and Angel. It runs a small server on your home network so your phone can control it.
- **Simple TV Remote** (`phone/`): an Android app with a home-screen widget. It has buttons to open those four apps, plus Back, Home, Play/Pause, and Volume.

```
Phone widget  ──Wi-Fi──>  Simple TV on the Firestick (port 8765, 4-digit code)
```

## 1. Build the APKs

You can build them either of two ways.

**Option A: GitHub (no installs needed)**
1. Create a new private repo on github.com and upload this folder to it.
2. Open the **Actions** tab. The "Build APKs" workflow runs automatically (or you can click **Run workflow**).
3. When it finishes, download the **SimpleTV-apks** artifact. It contains `tv-debug.apk` and `phone-debug.apk`.

**Option B: Android Studio**
1. Open this folder in Android Studio and let it sync.
2. Choose **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. Your APKs will be at `tv/build/outputs/apk/debug/tv-debug.apk` and `phone/build/outputs/apk/debug/phone-debug.apk`.

## 2. Prepare the Firestick (one time)

1. On the TV, go to **Settings → My Fire TV → About**, then click your device name 7 times until it says you're a developer.
2. Back in **My Fire TV → Developer options**, turn on **ADB debugging**.
3. Note the TV's IP address, shown under **About → Network**.

## 3. Install on the TV

You need `adb` on your computer, which comes with Android's "platform-tools":

```
# Mac/Linux
./setup-firestick.sh 10.0.0.252 path/to/tv-debug.apk
# Windows
setup-firestick.bat 10.0.0.252 path\to\tv-debug.apk
```

The first time you connect, accept the **"Allow USB debugging?"** prompt on the TV. Then run the script again if it failed.

## 4. What the script does

**It makes a backup first.** Before changing anything, it saves a `firestick-backup-<date>` folder next to the script. The folder contains the TV's settings, a list of installed apps, copies of any sideloaded APKs, and an `undo` script. The undo script uninstalls Simple TV and restores the accessibility setting to exactly what it was. Fire OS itself can't be copied without rooting, but nothing here modifies it.

The script then enables Simple TV's accessibility service. This lets the phone's **Back** button work, and it makes the TV's **Home button bring you to Simple TV**. Fire OS won't let you replace its home screen outright, so you'll see Amazon's home flash for a moment before Simple TV appears.

- Any accessibility services you already use (such as VoiceView) are kept.
- Simple TV's **Settings** and **Amazon Home** buttons pause the takeover for 5 and 10 minutes respectively, so you can reach Amazon's screens.
- To undo everything, run the `undo` script in the backup folder.

## 5. Set up the phone

1. Install `phone-debug.apk` on your Android phone. You'll need to allow "install unknown apps" for your browser or file manager.
2. Open **Simple TV Remote**. Enter the address and code shown at the bottom of the TV screen, then tap **Save and test**.
3. Tap **Add widget to home screen**, or long-press your home screen and go to **Widgets → Simple TV**.

## Notes and limits

- **No arrow keys on the phone.** Android doesn't let one app press arrow keys in another app without root access. Use the physical remote to pick shows. The phone handles opening apps, play/pause, back, home, and volume.
- **Volume** changes the Firestick's own volume. If your TV volume is controlled over HDMI by the Fire remote, the phone buttons may not move the TV's volume.
- **Angel** is matched by app name, since its Fire TV package ID isn't fixed in the code. If it shows "Not installed," install Angel Studios from the Appstore.
- **Changing the tiles:** edit `APPS` in `tv/src/main/java/com/simpletv/launcher/AppCatalog.java`. Also edit the buttons in `phone/src/main/res/layout/widget_remote.xml` and `COMMANDS` in `RemoteWidget.java`.
- **Security:** the phone must send the TV's 4-digit code with every command, and the server only listens on your home network. To get a new code, clear Simple TV's data on the TV.
