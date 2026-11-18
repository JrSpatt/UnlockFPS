package com.spatt.unlockfps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Automatically detects newly installed games and adds them to the 120Hz override list. */
public class PackageReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
            FpsApplier.invalidateBeastCache();
            // New games are suggested in the editor. Only Apply changes the active profile.
        }
    }
}
