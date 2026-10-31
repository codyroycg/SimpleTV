package com.simpletv.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Locale;

final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("simpletv", Context.MODE_PRIVATE);
    }

    /** 4-digit pairing code the phone must send with every command. */
    static synchronized String code(Context c) {
        String s = sp(c).getString("code", null);
        if (s == null) {
            s = String.format(Locale.ROOT, "%04d", new SecureRandom().nextInt(10000));
            sp(c).edit().putString("code", s).apply();
        }
        return s;
    }

    /** Temporarily stop bouncing away from Amazon's home screen (for Settings etc.). */
    static void bypassHome(Context c, long millis) {
        sp(c).edit().putLong("bypassUntil", System.currentTimeMillis() + millis).apply();
    }

    static boolean homeBypassed(Context c) {
        return System.currentTimeMillis() < sp(c).getLong("bypassUntil", 0);
    }

    /** The TV's address on the home network, e.g. 192.168.1.50. */
    static String ipAddress() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "not connected";
    }
}
