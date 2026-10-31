package com.simpletv.remote;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import android.widget.Toast;

/** Home-screen widget: each button sends one command to the TV. */
public class RemoteWidget extends AppWidgetProvider {
    static final String ACTION = "com.simpletv.remote.CMD";

    static final int[] BUTTONS = {
            R.id.b_netflix, R.id.b_youtube, R.id.b_prime, R.id.b_angel,
            R.id.b_back, R.id.b_home, R.id.b_play, R.id.b_voldown, R.id.b_volup,
    };
    static final String[] COMMANDS = {
            "app:netflix", "app:youtube", "app:prime", "app:angel",
            "back", "home", "playpause", "voldown", "volup",
    };

    @Override
    public void onUpdate(Context c, AppWidgetManager mgr, int[] ids) {
        RemoteViews rv = build(c);
        for (int id : ids) mgr.updateAppWidget(id, rv);
    }

    static RemoteViews build(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_remote);
        for (int i = 0; i < BUTTONS.length; i++) {
            Intent in = new Intent(c, RemoteWidget.class)
                    .setAction(ACTION)
                    .setData(Uri.parse("simpletv://cmd/" + COMMANDS[i]))
                    .putExtra("cmd", COMMANDS[i]);
            PendingIntent pi = PendingIntent.getBroadcast(c, i, in,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(BUTTONS[i], pi);
        }
        return rv;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (!ACTION.equals(intent.getAction())) return;
        final String cmd = intent.getStringExtra("cmd");
        if (cmd == null) return;
        final Context app = ctx.getApplicationContext();
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                TvClient.command(app, cmd);
            } catch (Exception e) {
                final String msg = TvClient.message(e);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(app, msg, Toast.LENGTH_SHORT).show());
            } finally {
                pr.finish();
            }
        }).start();
    }
}
