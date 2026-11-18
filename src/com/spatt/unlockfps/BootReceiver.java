package com.spatt.unlockfps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-applies saved 120Hz game overrides and starts Auto120Service after reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            FpsApplier.reapply(context);
            if (FpsApplier.isAutoEnabled(context)) {
                Auto120Service.ensureRunning(context);
            }
        }
    }
}
