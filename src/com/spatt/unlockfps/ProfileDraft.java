package com.spatt.unlockfps;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Pending edits share a preference file with the active profile so Apply can promote them atomically. */
final class ProfileDraft {
    private static final String PREFIX = "pending.";
    private final Context ctx;
    private final SharedPreferences prefs;

    ProfileDraft(Context context) {
        ctx = context;
        prefs = ctx.getSharedPreferences(FpsApplier.PREFS, Context.MODE_PRIVATE);
        FpsApplier.hasAppliedProfile(ctx);
    }

    int target() {
        return prefs.getInt(PREFIX + FpsApplier.KEY_TARGET_FPS, FpsApplier.getTargetFps(ctx));
    }

    void setTarget(int hz) {
        putInt(FpsApplier.KEY_TARGET_FPS, hz, FpsApplier.getTargetFps(ctx));
    }

    boolean beast() {
        return prefs.getBoolean(PREFIX + FpsApplier.KEY_BEAST, FpsApplier.isBeastEnabled(ctx));
    }

    void setBeast(boolean enabled) {
        String key = PREFIX + FpsApplier.KEY_BEAST;
        SharedPreferences.Editor edit = prefs.edit();
        if (enabled == FpsApplier.isBeastEnabled(ctx)) edit.remove(key);
        else edit.putBoolean(key, enabled);
        edit.apply();
    }

    int gameRate(String pkg) {
        return prefs.getInt(PREFIX + FpsApplier.KEY_GAME_FPS_PREFIX + pkg, FpsApplier.getGameFps(ctx, pkg));
    }

    void setGameRate(String pkg, int hz) {
        putInt(FpsApplier.KEY_GAME_FPS_PREFIX + pkg, hz, FpsApplier.getGameFps(ctx, pkg));
    }

    private void putInt(String key, int value, int current) {
        SharedPreferences.Editor edit = prefs.edit();
        if (value == current) edit.remove(PREFIX + key);
        else edit.putInt(PREFIX + key, value);
        edit.apply();
    }

    private Set<String> selection(String key, Set<String> current) {
        return new HashSet<>(prefs.getStringSet(PREFIX + key, current));
    }

    private void stageSet(SharedPreferences.Editor edit, String key, Set<String> value, Set<String> current) {
        if (value.equals(current)) edit.remove(PREFIX + key);
        else edit.putStringSet(PREFIX + key, new HashSet<>(value));
    }

    Set<String> packages() {
        return selection(FpsApplier.KEY_PACKAGES, FpsApplier.loadPackages(ctx));
    }

    void saveSelection(Set<String> packages, String pkg, boolean game) {
        SharedPreferences.Editor edit = prefs.edit();
        stageSet(edit, FpsApplier.KEY_PACKAGES, packages, FpsApplier.loadPackages(ctx));
        if (game) {
            Set<String> active = FpsApplier.loadIgnoredPackages(ctx);
            Set<String> ignored = selection(FpsApplier.KEY_IGNORED, active);
            if (packages.contains(pkg)) ignored.remove(pkg);
            else ignored.add(pkg);
            stageSet(edit, FpsApplier.KEY_IGNORED, ignored, active);
        }
        edit.apply();
    }

    Set<String> suggestInstalledGames() {
        Set<String> packages = packages();
        if (!FpsApplier.isAutoAddGamesEnabled(ctx)) return packages;
        Set<String> ignored = selection(FpsApplier.KEY_IGNORED, FpsApplier.loadIgnoredPackages(ctx));
        PackageManager pm = ctx.getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            if (FpsApplier.isGameApp(app) && !ignored.contains(app.packageName)
                    && !ctx.getPackageName().equals(app.packageName)
                    && pm.getLaunchIntentForPackage(app.packageName) != null) {
                packages.add(app.packageName);
            }
        }
        SharedPreferences.Editor edit = prefs.edit();
        stageSet(edit, FpsApplier.KEY_PACKAGES, packages, FpsApplier.loadPackages(ctx));
        edit.apply();
        return packages;
    }

    boolean hasChanges() {
        for (String key : prefs.getAll().keySet()) if (key.startsWith(PREFIX)) return true;
        return false;
    }

    void publish() {
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            String pending = entry.getKey();
            if (!pending.startsWith(PREFIX)) continue;
            String key = pending.substring(PREFIX.length());
            Object value = entry.getValue();
            if (value instanceof Integer) edit.putInt(key, (Integer) value);
            else if (value instanceof Boolean) edit.putBoolean(key, (Boolean) value);
            else edit.putStringSet(key, new HashSet<>(prefs.getStringSet(pending, java.util.Collections.emptySet())));
            edit.remove(pending);
        }
        edit.putBoolean(FpsApplier.KEY_APPLIED, true).apply();
    }
}
