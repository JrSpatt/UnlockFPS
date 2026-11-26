package com.spatt.unlockfps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Updates a single catalog entry when Android reports a package change. */
public class PackageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent.getData() == null) return;
        boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction());
        if (removed && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
        String pkg = intent.getData().getSchemeSpecificPart();
        if (pkg != null) InstalledApps.get(context).packageChanged(pkg, removed);
    }
}
