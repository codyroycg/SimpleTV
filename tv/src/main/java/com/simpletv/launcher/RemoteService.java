package com.simpletv.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/** Keeps the remote server alive in the background. */
public class RemoteService extends Service {
    private static final String CHANNEL = "remote";

    static void start(Context c) {
        try {
            Intent i = new Intent(c, RemoteService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            Log.w("SimpleTV", "Couldn't start service", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Phone remote", NotificationManager.IMPORTANCE_MIN));
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        Notification n = b.setContentTitle("Simple TV")
                .setContentText("Listening for your phone remote")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
        startForeground(1, n);
        RemoteServer.ensureStarted(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        RemoteServer.ensureStarted(this);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
