package com.simpletv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Two jobs:
 *  1. Lets the phone remote press Back.
 *  2. Fire OS won't let you replace its home screen, so when Amazon's home appears
 *     (Home button pressed), we immediately switch to Simple TV instead.
 */
public class TvAccessibilityService extends AccessibilityService {
    static volatile TvAccessibilityService instance;

    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private long lastBounce;

    @Override
    protected void onServiceConnected() {
        instance = this;
        RemoteService.start(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = e.getPackageName();
        if (pkg == null || !AMAZON_HOME.contentEquals(pkg)) return;
        if (Prefs.homeBypassed(this)) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastBounce < 1500) return;
        lastBounce = now;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}
