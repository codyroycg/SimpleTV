package com.simpletv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

/** The simplified home screen: one row of big tiles. */
public class MainActivity extends Activity {

    private static final int BG_TOP = 0xFF0E1116;
    private static final int BG_BOTTOM = 0xFF1B2130;
    private static final int TILE = 0xFF252C3B;
    private static final int TEXT_DIM = 0xFF9AA3B5;

    private LinearLayout row;
    private TextView clock;
    private TextView footer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            clock.setText(DateFormat.getTimeFormat(MainActivity.this).format(new Date()));
            handler.postDelayed(this, 15_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(32), dp(48), dp(28));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM}));

        // Header: title + clock
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Simple TV", 30, Color.WHITE, true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        clock = text("", 30, Color.WHITE, false);
        header.addView(clock);
        root.addView(header);

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        root.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        // Footer: remote info + small buttons
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        footer = text("", 15, TEXT_DIM, false);
        bottom.addView(footer, new LinearLayout.LayoutParams(0, -2, 1));
        bottom.addView(pill("Settings", v -> {
            Prefs.bypassHome(this, 5 * 60_000);
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                toast("Couldn't open Settings");
            }
        }));
        bottom.addView(pill("Amazon Home", v -> openAmazonHome()));
        root.addView(bottom);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildTiles();
        updateFooter();
        RemoteService.start(this);
        RemoteServer.ensureStarted(this);
        handler.removeCallbacks(tick);
        tick.run();
        if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onBackPressed() {
        // This is the home screen; Back does nothing.
    }

    private void buildTiles() {
        row.removeAllViews();
        for (AppCatalog.Entry e : AppCatalog.APPS) {
            row.addView(tile(e, AppCatalog.resolvePackage(this, e)));
        }
    }

    private View tile(AppCatalog.Entry e, String pkg) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(TILE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(3), Color.TRANSPARENT);
        v.setBackground(bg);

        Drawable art = pkg != null ? AppCatalog.art(this, pkg) : null;
        if (art != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(art);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v.addView(iv, new LinearLayout.LayoutParams(dp(170), dp(96)));
        } else {
            TextView letter = text(e.label.substring(0, 1), 56, Color.WHITE, true);
            letter.setGravity(Gravity.CENTER);
            v.addView(letter, new LinearLayout.LayoutParams(dp(170), dp(96)));
        }
        TextView label = text(e.label, 20, Color.WHITE, true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(10), 0, 0);
        v.addView(label);
        if (pkg == null) {
            TextView sub = text("Not installed — tap to get it", 12, TEXT_DIM, false);
            sub.setGravity(Gravity.CENTER);
            v.addView(sub);
            v.setAlpha(0.55f);
        }

        v.setFocusable(true);
        v.setClickable(true);
        v.setOnFocusChangeListener((view, focused) -> {
            bg.setStroke(dp(3), focused ? Color.WHITE : Color.TRANSPARENT);
            view.animate().scaleX(focused ? 1.08f : 1f).scaleY(focused ? 1.08f : 1f)
                    .setDuration(120).start();
        });
        v.setOnClickListener(view -> {
            if (pkg != null) {
                if (!AppCatalog.launch(this, e.id)) toast("Couldn't open " + e.label);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("amzn://apps/android?s=" + Uri.encode(e.label))));
                } catch (Exception ex) {
                    toast(e.label + " isn't installed. Find it in the Appstore.");
                }
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(200), dp(178));
        lp.setMargins(dp(14), 0, dp(14), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private View pill(String label, View.OnClickListener onClick) {
        TextView t = text(label, 15, Color.WHITE, false);
        t.setPadding(dp(18), dp(8), dp(18), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(2), Color.TRANSPARENT);
        t.setBackground(bg);
        t.setFocusable(true);
        t.setClickable(true);
        t.setOnFocusChangeListener((v, f) -> bg.setStroke(dp(2), f ? Color.WHITE : Color.TRANSPARENT));
        t.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(12), 0, 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void openAmazonHome() {
        Prefs.bypassHome(this, 10 * 60_000);
        Intent i = AppCatalog.launchIntent(getPackageManager(), "com.amazon.tv.launcher");
        if (i == null) {
            i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage("com.amazon.tv.launcher")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            startActivity(i);
        } catch (Exception e) {
            toast("Couldn't open Amazon's home screen");
        }
    }

    private void updateFooter() {
        String s = "Phone remote:  " + Prefs.ipAddress() + "    Code  " + Prefs.code(this);
        if (TvAccessibilityService.instance == null) {
            s += "    ·  Home-button takeover is off (see setup step 4)";
        }
        footer.setText(s);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
