package com.simpletv.remote;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen (TV address + code) with the same remote buttons as the widget. */
public class MainActivity extends Activity {

    private EditText host, code;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        host = findViewById(R.id.host);
        code = findViewById(R.id.code);
        status = findViewById(R.id.status);
        host.setText(TvClient.host(this));
        code.setText(TvClient.code(this));

        Button save = findViewById(R.id.save);
        save.setOnClickListener(v -> {
            TvClient.save(this, host.getText().toString(), code.getText().toString());
            status.setText("Testing…");
            new Thread(() -> {
                String result;
                try {
                    TvClient.call(this, "/ping", null);
                    result = "✓ Connected to your TV";
                } catch (Exception e) {
                    result = TvClient.message(e);
                }
                final String r = result;
                runOnUiThread(() -> status.setText(r));
            }).start();
        });

        for (int i = 0; i < RemoteWidget.BUTTONS.length; i++) {
            final String cmd = RemoteWidget.COMMANDS[i];
            findViewById(RemoteWidget.BUTTONS[i]).setOnClickListener(v -> send(cmd));
        }

        Button pin = findViewById(R.id.pin);
        pin.setOnClickListener(v -> {
            AppWidgetManager mgr = getSystemService(AppWidgetManager.class);
            if (mgr != null && mgr.isRequestPinAppWidgetSupported()) {
                mgr.requestPinAppWidget(new ComponentName(this, RemoteWidget.class), null, null);
            } else {
                Toast.makeText(this,
                        "Long-press your home screen → Widgets → Simple TV",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void send(String cmd) {
        new Thread(() -> {
            try {
                TvClient.command(this, cmd);
            } catch (Exception e) {
                final String msg = TvClient.message(e);
                runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
