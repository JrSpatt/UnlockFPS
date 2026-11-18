package com.spatt.unlockfps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Sends Joyose-compatible refresh-rate override broadcasts to PowerKeeper and auto-detects games. */
public final class FpsApplier {
    private static final String TAG = "UnlockFPS";
    private static final String PREFS = "unlock_fps_prefs";
    private static final String KEY_PACKAGES = "packages";
    private static final String KEY_IGNORED = "ignored_games";
    private static final String KEY_AUTO_ENABLED = "auto_enabled";
    private static final String KEY_AUTO_ADD_GAMES = "auto_add_games";
    private static final String KEY_TARGET_FPS = "target_fps";
    private static final String KEY_GAME_FPS_PREFIX = "game_fps_";
    private static final String KEY_SERVICE_BEAT = "service_heartbeat_ms";
    private static final String KEY_BEAST = "beast_enabled";
    private static final String KEY_BEAST_WARN_HIDDEN = "beast_warn_hidden";
    private static final String KEY_ONBOARDED = "onboarded_v1";
    private static final String ACTION_OVERRIDE = "com.xiaomi.joyose.OVERRIDE_GAME_FRESHRATE";
    private static final String POWERKEEPER_PKG = "com.miui.powerkeeper";

    public static final int[] SUPPORTED_FPS = {60, 90, 120, 144};

    public static final String[] DEFAULT_PACKAGES = {
        "com.mobile.legends",
        "com.roblox.client",
        "com.mojang.minecraftpe"
    };

    private FpsApplier() {}

    public static Set<String> loadPackages(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(KEY_PACKAGES, null);
        if (saved == null) {
            return new LinkedHashSet<>(Arrays.asList(DEFAULT_PACKAGES));
        }
        return new LinkedHashSet<>(saved);
    }

    public static void savePackages(Context ctx, Set<String> pkgs) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_PACKAGES, new LinkedHashSet<>(pkgs))
            .apply();
    }

    public static Set<String> loadIgnoredPackages(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> ignored = sp.getStringSet(KEY_IGNORED, null);
        return ignored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ignored);
    }

    public static void setPackageIgnored(Context ctx, String pkg, boolean ignored) {
        Set<String> set = loadIgnoredPackages(ctx);
        if (ignored) {
            set.add(pkg);
        } else {
            set.remove(pkg);
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IGNORED, set)
            .apply();
    }

    public static boolean isAutoEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ENABLED, true);
    }

    public static void setAutoEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ENABLED, enabled)
            .apply();
    }

    public static boolean isAutoAddGamesEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_ADD_GAMES, true);
    }

    public static void setAutoAddGamesEnabled(Context ctx, boolean enabled) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_ADD_GAMES, enabled)
            .apply();
    }

    /** Refresh rates (>= 60) the panel reports, or an empty array when they cannot be read. Never guesses. */
    public static int[] readPanelRates(Context ctx) {
        TreeSet<Integer> rates = new TreeSet<>();
        try {
            DisplayManager dm = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
            if (d != null) {
                for (Display.Mode m : d.getSupportedModes()) {
                    int r = Math.round(m.getRefreshRate());
                    if (r >= 60) {
                        rates.add(r);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        int[] out = new int[rates.size()];
        int i = 0;
        for (int r : rates) {
            out[i++] = r;
        }
        return out;
    }

    /** Rates the panel really reports; EMPTY when they cannot be read. Nothing is guessed. */
    public static int[] detectSupportedFps(Context ctx) {
        return readPanelRates(ctx);
    }

    /** False when the panel's refresh modes cannot be read; then no rate is sent anywhere. */
    public static boolean panelDetected(Context ctx) {
        return readPanelRates(ctx).length > 0;
    }

    /** Highest rate the panel really reports, or 0 when modes cannot be read (Beast mode needs this). */
    public static int getVerifiedMaxFps(Context ctx) {
        int[] real = readPanelRates(ctx);
        return real.length == 0 ? 0 : real[real.length - 1];
    }

    /** Highest rate the panel reports, or 0 when unknown. */
    public static int getMaxSupportedFps(Context ctx) {
        return getVerifiedMaxFps(ctx);
    }

    /** Marketing name ("Xiaomi 14") when the ROM exposes it, else brand + model code. */
    public static String getDeviceName(Context ctx) {
        try {
            String n = android.provider.Settings.Global.getString(ctx.getContentResolver(), "device_name");
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Exception ignored) {
        }
        String brand = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        return model.toLowerCase().startsWith(brand.toLowerCase()) ? model : (brand + " " + model).trim();
    }

    public static String getDeviceSummary(Context ctx) {
        String name = getDeviceName(ctx);
        return name + " (" + Build.DEVICE + ") \u2022 Android " + Build.VERSION.RELEASE
            + " \u2022 Max " + getMaxSupportedFps(ctx) + "Hz";
    }

    public static int getTargetFps(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int saved = sp.getInt(KEY_TARGET_FPS, -1);
        if (saved > 0) {
            return snapToPanel(ctx, saved);
        }
        return getMaxSupportedFps(ctx);
    }

    /**
     * Makes a saved rate valid for the panel as it is NOW: the same rate if the panel lists it, otherwise the
     * highest listed rate below it (or the lowest listed one). Returns 0 when the panel cannot be read.
     */
    public static int snapToPanel(Context ctx, int rate) {
        int[] rates = readPanelRates(ctx);
        if (rates.length == 0) return 0;
        int best = rates[0];
        for (int r : rates) {
            if (r == rate) return r;
            if (r < rate) best = r;
        }
        return best;
    }

    public static void setTargetFps(Context ctx, int fps) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TARGET_FPS, fps)
            .apply();
    }

    public static boolean isGameApp(ApplicationInfo ai) {
        return (ai.category == ApplicationInfo.CATEGORY_GAME)
            || ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0);
    }

    /** Auto-adds all installed games (unless manually unchecked by user) when enabled. */
    public static Set<String> syncInstalledGames(Context ctx) {
        Set<String> pkgs = loadPackages(ctx);
        if (!isAutoAddGamesEnabled(ctx)) {
            return pkgs;
        }
        Set<String> ignored = loadIgnoredPackages(ctx);
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        boolean changed = false;
        String selfPkg = ctx.getPackageName();
        for (ApplicationInfo ai : apps) {
            if (selfPkg.equals(ai.packageName)) continue;
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            if (isGameApp(ai) && !ignored.contains(ai.packageName)) {
                if (pkgs.add(ai.packageName)) {
                    changed = true;
                }
            }
        }
        if (changed) {
            savePackages(ctx, pkgs);
        }
        return pkgs;
    }

    public static final class ApplyResult {
        public final int count;
        public final int fps;
        public final boolean powerKeeperFound;
        public final List<String> packages;

        ApplyResult(int count, int fps, boolean powerKeeperFound, List<String> packages) {
            this.count = count;
            this.fps = fps;
            this.powerKeeperFound = powerKeeperFound;
            this.packages = packages;
        }
    }

    public static boolean isPowerKeeperInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(POWERKEEPER_PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static ApplyResult apply120Hz(Context ctx, Set<String> pkgs) {
        return applyInternal(ctx, pkgs, getTargetFps(ctx), true);
    }

    /** Sends exactly {@code fps} to exactly {@code pkgs}. Never affected by Beast mode (used by the tester). */
    public static ApplyResult applyTargetFps(Context ctx, Set<String> pkgs, int fps) {
        return applyInternal(ctx, pkgs, fps, false);
    }

    /**
     * Rate the app is really asking for: the panel maximum while Beast mode is on,
     * otherwise the target the user picked.
     */
    public static int effectiveFps(Context ctx) {
        int verified = getVerifiedMaxFps(ctx);
        return (isBeastEnabled(ctx) && verified > 0) ? verified : getTargetFps(ctx);
    }

    private static ApplyResult applyInternal(Context ctx, Set<String> pkgs, int requestedFps, boolean allowBeast) {
        boolean pkFound = isPowerKeeperInstalled(ctx);
        int fps = snapToPanel(ctx, requestedFps); // never send a rate the panel does not list
        int maxFps = getVerifiedMaxFps(ctx);
        if (maxFps <= 0) {
            // Panel not detected: never send an unverified rate.
            Log.w(TAG, "panel modes unreadable, nothing sent");
            return new ApplyResult(0, fps, pkFound, new ArrayList<String>());
        }
        boolean beast = allowBeast && isBeastEnabled(ctx) && maxFps > 0;
        // Beast mode: every launchable app (system apps included) gets the panel maximum.
        Set<String> all = new LinkedHashSet<>(pkgs);
        if (beast) all.addAll(beastPackages(ctx));
        List<String> sent = new ArrayList<>();
        for (String pkg : all) {
            String trimmed = pkg.trim();
            if (trimmed.isEmpty()) continue;
            int own = allowBeast ? snapToPanel(ctx, getGameFps(ctx, trimmed)) : 0;
            int rate = beast ? maxFps : (own > 0 ? own : fps);
            Intent intent = new Intent(ACTION_OVERRIDE);
            intent.putExtra("override_pkg_name", trimmed);
            intent.putExtra("override_freshrate", rate);
            intent.setPackage(POWERKEEPER_PKG);
            ctx.sendBroadcast(intent);
            sent.add(trimmed);
        }
        int shown = beast ? maxFps : fps;
        Log.i(TAG, "applyTargetFps: fps=" + shown + " beast=" + beast + " sent=" + sent.size()
            + " powerKeeperFound=" + pkFound);
        return new ApplyResult(sent.size(), shown, pkFound, sent);
    }

    // ---------------------------------------------------------------- beast mode

    private static volatile Set<String> beastCache = null;
    private static volatile long beastCacheAt = 0L;
    private static final long BEAST_CACHE_MS = 5L * 60L * 1000L;

    public static boolean isBeastEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BEAST, false);
    }

    public static void setBeastEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BEAST, on).apply();
    }

    /** True once the user ticked "Don't show again" on the Beast mode warning. */
    public static boolean isBeastWarnHidden(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_BEAST_WARN_HIDDEN, false);
    }

    public static void setBeastWarnHidden(Context ctx, boolean hidden) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BEAST_WARN_HIDDEN, hidden).apply();
    }

    /** Forget the cached app list (call when apps are installed or removed). */
    public static void invalidateBeastCache() {
        beastCache = null;
    }

    /** Every app that has a launcher icon, user and system apps alike, except this app. Cached. */
    public static Set<String> beastPackages(Context ctx) {
        Set<String> cached = beastCache;
        long now = System.currentTimeMillis();
        if (cached != null && now - beastCacheAt < BEAST_CACHE_MS) return cached;
        Set<String> out = new LinkedHashSet<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    out.add(ri.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {
        }
        out.remove(ctx.getPackageName());
        beastCache = out;
        beastCacheAt = now;
        return out;
    }

    /** Preinstalled apps, including ones a store has updated since (Chrome, Gmail, most Xiaomi apps). */
    public static boolean isSystemApp(ApplicationInfo ai) {
        return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    // ---------------------------------------------------------------- per-game rate

    /** Rate chosen for one game, or 0 to follow the global target. */
    public static int getGameFps(Context ctx, String pkg) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_GAME_FPS_PREFIX + pkg, 0);
    }

    public static void setGameFps(Context ctx, String pkg, int fps) {
        SharedPreferences.Editor e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (fps > 0) e.putInt(KEY_GAME_FPS_PREFIX + pkg, fps);
        else e.remove(KEY_GAME_FPS_PREFIX + pkg);
        e.apply();
    }

    // ---------------------------------------------------------------- health

    /** Written by Auto120Service on every loop so the UI can tell if HyperOS killed it. */
    public static void markServiceAlive(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_SERVICE_BEAT, System.currentTimeMillis()).apply();
    }

    public static long lastServiceBeat(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SERVICE_BEAT, 0L);
    }

    public static boolean isOnboarded(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false);
    }

    public static void setOnboarded(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDED, true).apply();
    }

    // ---------------------------------------------------------------- system refresh-rate setting

    /**
     * The refresh rate chosen in the phone's own Settings → Display → Refresh rate, or -1 if it
     * can't be read. PowerKeeper can't raise a game above this, so 60 here means no game gets 120.
     * HyperOS keeps it in Secure "user_refresh_rate" / "miui_refresh_rate"; AOSP in System "peak_refresh_rate".
     */
    public static int getSystemRefreshSetting(Context ctx) {
        android.content.ContentResolver cr = ctx.getContentResolver();
        for (String key : new String[]{"user_refresh_rate", "miui_refresh_rate"}) {
            try {
                int v = android.provider.Settings.Secure.getInt(cr, key, -1);
                if (v > 0) return v;
            } catch (Throwable ignored) {
            }
        }
        try {
            float v = android.provider.Settings.System.getFloat(cr, "peak_refresh_rate", -1f);
            if (v > 0 && !Float.isInfinite(v)) return Math.round(v);
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /** True when the phone itself is set to a low refresh rate (e.g. 60Hz "Standard"). */
    public static boolean isSystemRefreshTooLow(Context ctx) {
        int v = getSystemRefreshSetting(ctx);
        return v > 0 && v < 90 && getMaxSupportedFps(ctx) >= 90;
    }

    // ---------------------------------------------------------------- device / ROM info

    private static String prop(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object v = sp.getMethod("get", String.class).invoke(null, key);
            return v == null ? "" : v.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** "CN", "Global", "EEA", "India"... from the ROM's build properties. */
    public static String romRegion() {
        String mod = prop("ro.product.mod_device").toLowerCase(java.util.Locale.ROOT);
        if (mod.contains("_eea")) return "EEA";
        if (mod.contains("_in_")) return "India";
        if (mod.contains("_ru")) return "Russia";
        if (mod.contains("_id_")) return "Indonesia";
        if (mod.contains("_tw")) return "Taiwan";
        if (mod.contains("_tr")) return "Turkey";
        if (mod.contains("_global")) return "Global";
        String r = prop("ro.miui.build.region");
        if (r.isEmpty()) r = prop("ro.miui.region");
        if (r.equalsIgnoreCase("cn")) return "CN";
        if (r.equalsIgnoreCase("global")) return "Global";
        return r.isEmpty() ? "Unknown" : r.toUpperCase(java.util.Locale.ROOT);
    }

    /** e.g. "HyperOS OS3.0 (OS3.0.307.0.WNCCNXM)" or "MIUI V14 (...)". */
    public static String romVersion() {
        String os = prop("ro.mi.os.version.name");
        String inc = prop("ro.mi.os.version.incremental");
        if (inc.isEmpty()) inc = prop("ro.build.version.incremental");
        if (!os.isEmpty()) return "HyperOS " + os + " (" + inc + ")";
        String miui = prop("ro.miui.ui.version.name");
        if (!miui.isEmpty()) return "MIUI " + miui + " (" + inc + ")";
        return Build.DISPLAY;
    }

    /** Multi-line device block used in GitHub reports. */
    public static String deviceReport(Context ctx) {
        return "Device: " + getDeviceName(ctx) + " (" + Build.MODEL + ", " + Build.DEVICE + ")\n"
            + "ROM: " + romVersion() + " | Region: " + romRegion() + "\n"
            + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
            + "Panel max: " + getMaxSupportedFps(ctx) + "Hz | Target: " + getTargetFps(ctx) + "Hz"
            + " | Phone refresh setting: " + (getSystemRefreshSetting(ctx) > 0 ? getSystemRefreshSetting(ctx) + "Hz" : "unknown") + "\n"
            + "PowerKeeper: " + (isPowerKeeperInstalled(ctx) ? "found" : "missing")
            + " | Unlock FPS v" + appVersion(ctx);
    }

    public static String appVersion(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }
}
