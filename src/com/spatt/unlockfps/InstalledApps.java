package com.spatt.unlockfps;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A process-local launcher catalog shared by the picker and Beast Mode. */
public final class InstalledApps extends Application {
    private final Map<String, ApplicationInfo> launchable = new LinkedHashMap<>();
    private final Set<Runnable> listeners = new HashSet<>();
    private int revision;

    static InstalledApps get(Context context) {
        return (InstalledApps) context.getApplicationContext();
    }

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter events = new IntentFilter();
        events.addAction(Intent.ACTION_PACKAGE_ADDED);
        events.addAction(Intent.ACTION_PACKAGE_REMOVED);
        events.addAction(Intent.ACTION_PACKAGE_CHANGED);
        events.addDataScheme("package");
        registerReceiver(new PackageReceiver(), events);
        for (ResolveInfo result : getPackageManager().queryIntentActivities(launcherIntent(), 0)) {
            if (result.activityInfo != null) record(result.activityInfo.applicationInfo);
        }
        new ProfileDraft(this).cleanMissingPackages(launchable.keySet());
    }

    private Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }

    private void record(ApplicationInfo info) {
        if (info != null && !getPackageName().equals(info.packageName)) launchable.put(info.packageName, info);
    }

    void packageChanged(String pkg, boolean removed) {
        if (getPackageName().equals(pkg)) return;
        launchable.remove(pkg);
        if (removed) {
            new ProfileDraft(this).removePackages(Collections.singleton(pkg));
        } else {
            for (ResolveInfo item : getPackageManager().queryIntentActivities(launcherIntent().setPackage(pkg), 0)) {
                if (item.activityInfo != null) record(item.activityInfo.applicationInfo);
            }
        }
        revision++;
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
        UnlockWidget.updateAll(this);
        UnlockTileService.requestTileSync(this);
    }

    ArrayList<ApplicationInfo> entries() { return new ArrayList<>(launchable.values()); }
    Set<String> packages() { return Collections.unmodifiableSet(launchable.keySet()); }
    int revision() { return revision; }
    void addListener(Runnable listener) { listeners.add(listener); }
    void removeListener(Runnable listener) { listeners.remove(listener); }
}
