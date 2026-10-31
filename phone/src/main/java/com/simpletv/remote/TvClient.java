package com.simpletv.remote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sends commands to the Simple TV app on the Firestick. */
final class TvClient {
    private TvClient() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("remote", Context.MODE_PRIVATE);
    }

    static String host(Context c) { return sp(c).getString("host", ""); }
    static String code(Context c) { return sp(c).getString("code", ""); }

    static void save(Context c, String host, String code) {
        sp(c).edit().putString("host", host.trim()).putString("code", code.trim()).apply();
    }

    /** cmd is "app:netflix" or a key like "back", "playpause", "volup". */
    static void command(Context c, String cmd) throws Exception {
        if (cmd.startsWith("app:")) call(c, "/launch", "app=" + enc(cmd.substring(4)));
        else call(c, "/key", "k=" + enc(cmd));
    }

    static JSONObject call(Context c, String path, String query) throws Exception {
        String host = host(c);
        if (host.isEmpty()) throw new Exception("Open Simple TV Remote and set up your TV first");
        String hostPort = host.contains(":") ? host : host + ":8765";
        String url = "http://" + hostPort + path + "?code=" + enc(code(c))
                + (query != null ? "&" + query : "");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(2500);
        conn.setReadTimeout(4000);
        try {
            int status = conn.getResponseCode();
            InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = is == null ? "{}" : read(is);
            if (status == 403) throw new Exception("Wrong code. Check the code at the bottom of the TV screen.");
            JSONObject j = new JSONObject(body);
            if (!j.optBoolean("ok")) throw new Exception(friendly(j.optString("error")));
            return j;
        } finally {
            conn.disconnect();
        }
    }

    static String message(Exception e) {
        if (e instanceof SocketTimeoutException || e instanceof ConnectException
                || e instanceof NoRouteToHostException) {
            return "Can't reach the TV. Is it on, and is your phone on the same Wi-Fi?";
        }
        return e.getMessage() != null ? e.getMessage() : "Something went wrong";
    }

    private static String friendly(String err) {
        switch (err) {
            case "not-installed": return "That app isn't installed on the TV";
            case "needs-accessibility": return "Back needs TV setup step 4 (accessibility)";
            default: return "TV error: " + err;
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static String read(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
        is.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
