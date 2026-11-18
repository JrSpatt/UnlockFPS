package com.spatt.unlockfps;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Known frame-rate limits of games on Android.
 *
 * Unlock FPS lifts HyperOS's display cap, but a game still renders no faster than its own engine
 * allows, and many games only offer 90/120 on phones the developer has approved.
 *
 * Source of truth is data/games.json (tools/sync_games.py). The APK bundles a copy in
 * res/raw; when Config.GAMES_JSON_URL is set the app also downloads the latest list once a day,
 * so new games reach users without an app update.
 */
final class GameCaps {
    static final int MAX_60 = 60;
    static final int MAX_90 = 90;
    static final int UP_TO_120 = 120;

    private static final String CACHE_FILE = "games.json";
    private static final String PREFS = "unlock_fps_prefs";
    private static final String KEY_LAST_FETCH = "games_last_fetch_ms";
    private static final long FETCH_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    static final class Info {
        final String name;
        final int cap;
        final String note;

        Info(String name, int cap, String note) {
            this.name = name;
            this.cap = cap;
            this.note = note;
        }
    }

    private static volatile Map<String, Info> db = new HashMap<>();
    private static volatile int version = 0;
    private static boolean loaded = false;
    private static volatile String currentLang = "en";

    private GameCaps() {}

    static Info get(String pkg) {
        return db.get(pkg);
    }

    /** Re-reads the lists so tips follow a language change. */
    static synchronized void reload(Context ctx) {
        loaded = false;
        init(ctx);
    }

    static java.util.List<Info> catalog(Context ctx) {
        init(ctx);
        // Each game may have several regional packages pointing to the same entry.
        java.util.List<Info> games = new java.util.ArrayList<>(new java.util.HashSet<>(db.values()));
        games.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return games;
    }

    static int version() {
        return version;
    }

    /** Loads the bundled list, then the cached download if it is newer. Cheap; call from onCreate. */
    static synchronized void init(Context ctx) {
        if (loaded) return;
        loaded = true;
        currentLang = Lang.get(ctx);
        try (InputStream in = ctx.getResources().openRawResource(R.raw.games)) {
            parseAndUse(readAll(in));
        } catch (Exception ignored) {
        }
        File cache = new File(ctx.getFilesDir(), CACHE_FILE);
        if (cache.exists()) {
            try (InputStream in = new FileInputStream(cache)) {
                parseAndUse(readAll(in));
            } catch (Exception ignored) {
            }
        }
    }

    /** Downloads the latest list at most once a day; runs {@code onUpdated} on the UI thread if it changed. */
    static void refreshAsync(Context ctx, Runnable onUpdated) {
        if (Config.GAMES_JSON_URL.isEmpty()) return; // online list not configured: bundled list only
        Context app = ctx.getApplicationContext();
        long last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_FETCH, 0L);
        if (System.currentTimeMillis() - last < FETCH_INTERVAL_MS) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(Config.GAMES_JSON_URL).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "UnlockFPS/" + FpsApplier.appVersion(app));
                if (conn.getResponseCode() != 200) return;
                String body;
                try (InputStream in = conn.getInputStream()) {
                    body = readAll(in);
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_FETCH, System.currentTimeMillis()).apply();
                int before = version;
                if (parseAndUse(body)) {
                    try (FileOutputStream out = new FileOutputStream(new File(app.getFilesDir(), CACHE_FILE))) {
                        out.write(body.getBytes(StandardCharsets.UTF_8));
                    }
                    if (version != before && onUpdated != null) {
                        new Handler(Looper.getMainLooper()).post(onUpdated);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** Parses a list and switches to it if it is valid and not older than the current one. */
    private static boolean parseAndUse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int v = root.optInt("version", 0);
        if (v < version) return false;
        String lang = currentLang;
        JSONArray games = root.getJSONArray("games");
        Map<String, Info> next = new HashMap<>();
        for (int i = 0; i < games.length(); i++) {
            JSONObject g = games.getJSONObject(i);
            int cap = g.getInt("cap");
            if (cap != MAX_60 && cap != MAX_90 && cap != UP_TO_120) continue;
            String tip = g.optString("tip_" + lang, g.optString("tip", ""));
            Info info = new Info(g.optString("name", ""), cap, tip);
            JSONArray pkgs = g.getJSONArray("packages");
            for (int j = 0; j < pkgs.length(); j++) next.put(pkgs.getString(j), info);
        }
        if (next.isEmpty()) return false;
        db = next;
        version = v;
        return true;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
