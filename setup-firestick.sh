#!/bin/sh
# Backs up the Fire TV's current settings/app list, then installs Simple TV over Wi-Fi.
# Usage: ./setup-firestick.sh 10.0.0.252 [path/to/tv-debug.apk]
set -e
IP="$1"
APK="${2:-tv/build/outputs/apk/debug/tv-debug.apk}"
PKG=com.simpletv.launcher
if [ -z "$IP" ]; then echo "Usage: $0 <firestick-ip> [apk]"; exit 1; fi
D="$IP:5555"

echo "Connecting to $IP (accept the prompt on the TV if one appears)..."
adb connect "$D"
sleep 2
adb -s "$D" get-state >/dev/null || { echo "Not connected. Accept the prompt on the TV and run again."; exit 1; }

# ---- Backup ----
B="firestick-backup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$B"
echo "Saving backup to $B ..."
adb -s "$D" shell getprop                                        > "$B/device-info.txt"
adb -s "$D" shell pm list packages -f                            > "$B/installed-packages.txt"
adb -s "$D" shell settings list secure                           > "$B/settings-secure.txt"
adb -s "$D" shell settings list global                           > "$B/settings-global.txt"
adb -s "$D" shell settings list system                           > "$B/settings-system.txt"
ACC=$(adb -s "$D" shell settings get secure enabled_accessibility_services | tr -d '\r')
ACCON=$(adb -s "$D" shell settings get secure accessibility_enabled | tr -d '\r')
# Copy sideloaded (non-Amazon-store) APKs so they can be reinstalled
mkdir -p "$B/apks"
adb -s "$D" shell pm list packages -3 -f | tr -d '\r' | sed 's/^package://' | while read -r line; do
  path="${line%=*}"; name="${line##*=}"
  adb -s "$D" pull "$path" "$B/apks/$name.apk" >/dev/null 2>&1 || true
done

cat > "$B/undo.sh" <<EOF
#!/bin/sh
# Restores the Fire TV to how it was before Simple TV was installed.
adb connect $D
adb -s $D uninstall $PKG
if [ "$ACC" = "null" ] || [ -z "$ACC" ]; then
  adb -s $D shell settings delete secure enabled_accessibility_services
else
  adb -s $D shell settings put secure enabled_accessibility_services '$ACC'
fi
if [ "$ACCON" = "null" ] || [ -z "$ACCON" ]; then
  adb -s $D shell settings delete secure accessibility_enabled
else
  adb -s $D shell settings put secure accessibility_enabled $ACCON
fi
echo "Restored."
EOF
chmod +x "$B/undo.sh"
echo "Backup done. To undo everything later: ./$B/undo.sh"

# ---- Install ----
adb -s "$D" install -r "$APK"

echo "Turning on Back button + Home-button takeover..."
if [ "$ACC" = "null" ] || [ -z "$ACC" ]; then NEWACC="$PKG/$PKG.TvAccessibilityService"
else NEWACC="$ACC:$PKG/$PKG.TvAccessibilityService"; fi   # keep existing ones (e.g. VoiceView)
adb -s "$D" shell settings put secure enabled_accessibility_services "$NEWACC"
adb -s "$D" shell settings put secure accessibility_enabled 1
adb -s "$D" shell appops set $PKG SYSTEM_ALERT_WINDOW allow

adb -s "$D" shell am start -n $PKG/.MainActivity
echo "Done. The address and code for the phone app are at the bottom of the TV screen."
