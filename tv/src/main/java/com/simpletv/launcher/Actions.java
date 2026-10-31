package com.simpletv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

/** Remote-control commands that the phone can send. */
final class Actions {
    private Actions() {}

    /** Context to start activities from. The accessibility service is allowed to start
     *  activities while another app is in front, so prefer it when it's running. */
    static Context launchContext(Context fallback) {
        TvAccessibilityService s = TvAccessibilityService.instance;
        return s != null ? s : fallback;
    }

    /** Runs a key command. Returns null on success, or an error code. */
    static String run(Context c, String key) {
        if (key == null) return "missing-key";
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        switch (key) {
            case "back": {
                TvAccessibilityService s = TvAccessibilityService.instance;
                if (s == null) return "needs-accessibility";
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                return null;
            }
            case "home": {
                Intent i = new Intent(c, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                launchContext(c).startActivity(i);
                return null;
            }
            case "playpause":
                media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                return null;
            case "rewind":
                media(am, KeyEvent.KEYCODE_MEDIA_REWIND);
                return null;
            case "forward":
                media(am, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
                return null;
            case "volup":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
                return null;
            case "voldown":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
                return null;
            case "mute":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI);
                return null;
            default:
                return "unknown-key";
        }
    }

    private static void media(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }
}
