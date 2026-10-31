package com.simpletv.launcher;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny HTTP server the phone remote talks to.
 *   GET /ping?code=1234
 *   GET /launch?code=1234&app=netflix
 *   GET /key?code=1234&k=back|home|playpause|rewind|forward|volup|voldown|mute
 *   GET /apps?code=1234
 */
final class RemoteServer extends Thread {
    static final int PORT = 8765;
    private static final String TAG = "SimpleTV";
    private static RemoteServer running;

    private final Context app;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private int badCodes;

    private RemoteServer(Context app) {
        super("SimpleTV-server");
        this.app = app;
    }

    static synchronized void ensureStarted(Context c) {
        if (running == null || !running.isAlive()) {
            running = new RemoteServer(c.getApplicationContext());
            running.start();
        }
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(PORT));
            Log.i(TAG, "Remote server listening on " + PORT);
            while (!isInterrupted()) {
                final Socket s = ss.accept();
                pool.execute(() -> handle(s));
            }
        } catch (IOException e) {
            Log.e(TAG, "Remote server stopped", e);
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String requestLine = in.readLine();
            if (requestLine == null) return;
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) {
                // skip headers
            }
            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String target = parts.length > 1 ? parts[1] : "/";
            Uri uri = Uri.parse("http://tv" + target);
            String path = uri.getPath() == null ? "/" : uri.getPath();

            if ("OPTIONS".equals(method)) {
                respond(sock, 204, "");
                return;
            }

            JSONObject out = new JSONObject();
            int status = 200;
            if (!Prefs.code(app).equals(uri.getQueryParameter("code"))) {
                synchronized (this) {
                    badCodes++;
                    if (badCodes > 10) Thread.sleep(2000); // slow down guessing
                }
                status = 403;
                out.put("ok", false).put("error", "bad-code");
            } else {
                synchronized (this) { badCodes = 0; }
                switch (path) {
                    case "/ping":
                        out.put("ok", true).put("name", "Simple TV");
                        break;
                    case "/launch": {
                        boolean ok = AppCatalog.launch(Actions.launchContext(app),
                                uri.getQueryParameter("app"));
                        out.put("ok", ok);
                        if (!ok) out.put("error", "not-installed");
                        break;
                    }
                    case "/key": {
                        String err = Actions.run(app, uri.getQueryParameter("k"));
                        out.put("ok", err == null);
                        if (err != null) out.put("error", err);
                        break;
                    }
                    case "/apps": {
                        JSONArray arr = new JSONArray();
                        for (AppCatalog.Entry e : AppCatalog.APPS) {
                            arr.put(new JSONObject()
                                    .put("id", e.id)
                                    .put("label", e.label)
                                    .put("installed", AppCatalog.resolvePackage(app, e) != null));
                        }
                        out.put("ok", true).put("apps", arr);
                        break;
                    }
                    default:
                        status = 404;
                        out.put("ok", false).put("error", "not-found");
                }
            }
            respond(sock, status, out.toString());
        } catch (Exception e) {
            Log.w(TAG, "Request failed", e);
        }
    }

    private static void respond(Socket s, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 204 ? "No Content"
                : status == 403 ? "Forbidden" : "Not Found";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream os = s.getOutputStream();
        os.write(head.getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
        os.flush();
    }
}
