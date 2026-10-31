package com.simpletv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The apps shown on the home screen. Edit APPS to change the tiles. */
public final class AppCatalog {

    public static final class Entry {
        public final String id;
        public final String label;
        /** Fallback: match any installed app whose name contains this (lowercase). */
        public final String match;
        /** Known package names, tried in order. */
        public final String[] packages;

        Entry(String id, String label, String match, String... packages) {
            this.id = id;
            this.label = label;
            this.match = match;
            this.packages = packages;
        }
    }

    public static final Entry[] APPS = {
            new Entry("netflix", "Netflix", "netflix",
                    "com.netflix.ninja", "com.netflix.mediaclient"),
            new Entry("youtube", "YouTube", "youtube",
                    "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
                    "com.amazon.firetv.youtube.tv"),
            new Entry("prime", "Prime Video", "prime video",
                    "com.amazon.avod", "com.amazon.avod.thirdpartyclient"),
            new Entry("angel", "Angel", "angel"),
    };

    private AppCatalog() {}

    public static Entry find(String id) {
        if (id == null) return null;
        for (Entry e : APPS) if (e.id.equals(id)) return e;
        return null;
    }

    /** Returns the installed package for this entry, or null if it isn't installed. */
    public static String resolvePackage(Context c, Entry e) {
        PackageManager pm = c.getPackageManager();
        for (String pkg : e.packages) {
            if (launchIntent(pm, pkg) != null) return pkg;
        }
        // Fall back to matching by app name (handles apps whose package we don't know).
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : all) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            CharSequence label = ri.loadLabel(pm);
            if (label != null && label.toString().toLowerCase(Locale.ROOT).contains(e.match)) {
                return pkg;
            }
        }
        return null;
    }

    public static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = null;
        try {
            i = pm.getLeanbackLaunchIntentForPackage(pkg);
        } catch (Exception ignored) {
        }
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        }
        return i;
    }

    /** Opens the app. Returns false if it isn't installed. */
    public static boolean launch(Context c, String id) {
        Entry e = find(id);
        if (e == null) return false;
        String pkg = resolvePackage(c, e);
        if (pkg == null) return false;
        Intent i = launchIntent(c.getPackageManager(), pkg);
        if (i == null) return false;
        c.startActivity(i);
        return true;
    }

    /** The app's TV banner if it has one, otherwise its icon. */
    public static Drawable art(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        try {
            Drawable d = pm.getApplicationBanner(pkg);
            if (d == null) d = pm.getApplicationIcon(pkg);
            return d;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
