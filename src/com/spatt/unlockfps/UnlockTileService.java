package com.spatt.unlockfps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import java.util.Set;

/** Quick Settings tile to toggle the background override and show the current rate. */
public class UnlockTileService extends TileService {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }


    public static void requestTileSync(Context ctx) {
        try {
            TileService.requestListeningState(
                ctx, new ComponentName(ctx, UnlockTileService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean next = !FpsApplier.isAutoEnabled(this);
        FpsApplier.setAutoEnabled(this, next);
        Intent svc = new Intent(this, Auto120Service.class);
        if (next) {
            Auto120Service.ensureRunning(this);
            FpsApplier.reapply(this);
        } else {
            stopService(svc);
        }
        UnlockWidget.updateAll(this);
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean active = FpsApplier.isAutoEnabled(this);
        int fps = FpsApplier.effectiveFps(this);
        Set<String> pkgs = FpsApplier.loadPackages(this);
        tile.setState(active ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.app_name));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(!FpsApplier.hasAppliedProfile(this) ? getString(R.string.apply_first)
                : fps <= 0 ? getString(R.string.panel_unknown)
                : active ? getString(R.string.tile_sub, fps, pkgs.size()) : getString(R.string.tile_off));
        }
        tile.updateTile();
    }
}
