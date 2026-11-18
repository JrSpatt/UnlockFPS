package com.spatt.unlockfps;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Home-screen widget: last applied target and one-tap reapply. */
public class UnlockWidget extends AppWidgetProvider {
    private static final String ACTION_REAPPLY = "com.spatt.unlockfps.widget.REAPPLY";

    /** Redraws every placed widget. Safe to call from anywhere state changes. */
    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, UnlockWidget.class));
        if (ids == null || ids.length == 0) return;
        mgr.updateAppWidget(ids, build(ctx));
    }

    /** Asks the launcher to place the widget (shows the system preview dialog). */
    public static boolean requestPin(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        if (!mgr.isRequestPinAppWidgetSupported()) return false;
        return mgr.requestPinAppWidget(new ComponentName(ctx, UnlockWidget.class), null, null);
    }

    /**
     * HyperOS / MIUI gate widget and shortcut pinning behind the per-app "Home screen shortcuts"
     * permission (MIUI app-op 10017). When it is off, pin requests are dropped without any UI.
     * Returns true if the permission is granted or the ROM has no such permission.
     */
    public static boolean isMiuiShortcutAllowed(Context ctx) {
        try {
            android.app.AppOpsManager ops = (android.app.AppOpsManager) ctx.getSystemService(Context.APP_OPS_SERVICE);
            java.lang.reflect.Method m = android.app.AppOpsManager.class.getMethod(
                "checkOpNoThrow", int.class, int.class, String.class);
            int mode = (Integer) m.invoke(ops, 10017, android.os.Process.myUid(), ctx.getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Throwable t) {
            return true;
        }
    }

    /** HyperOS / MIUI per-app permission screen; falls back to App info. */
    public static void openMiuiPermissions(android.app.Activity act) {
        try {
            Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
            i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            i.putExtra("extra_pkgname", act.getPackageName());
            act.startActivity(i);
        } catch (Exception e) {
            UiHelper.openAppInfo(act);
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        mgr.updateAppWidget(ids, build(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (!ACTION_REAPPLY.equals(intent.getAction())) return;
        Context local = Lang.wrap(ctx);
        FpsApplier.ApplyResult result = FpsApplier.reapply(ctx);
        int message;
        if (!FpsApplier.hasAppliedProfile(ctx)) message = R.string.apply_first;
        else if (!result.powerKeeperFound) message = R.string.pk_not_found_long;
        else if (!FpsApplier.panelDetected(ctx)) message = R.string.panel_unknown;
        else if (result.count == 0) message = R.string.widget_no_apps;
        else message = R.string.widget_sent;
        android.widget.Toast.makeText(local, local.getString(message), android.widget.Toast.LENGTH_SHORT).show();
        updateAll(ctx);
    }

    private static RemoteViews build(Context base) {
        Context ctx = Lang.wrap(base);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_unlock);
        int fps = FpsApplier.hasAppliedProfile(ctx) ? FpsApplier.effectiveFps(ctx) : 0;
        v.setTextViewText(R.id.widget_target, ctx.getString(R.string.widget_target));
        v.setTextViewText(R.id.widget_hz, fps > 0 ? ctx.getString(R.string.widget_rate_value, fps) : "—");
        v.setTextColor(R.id.widget_hz, fps > 0 ? UiHelper.fpsColor(fps) : 0xffaeb7c2);
        v.setContentDescription(R.id.widget_reapply, ctx.getString(R.string.widget_reapply));
        v.setOnClickPendingIntent(R.id.widget_reapply, broadcast(ctx, ACTION_REAPPLY, 4));
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 3,
            new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        return v;
    }

    private static PendingIntent broadcast(Context ctx, String action, int code) {
        Intent i = new Intent(ctx, UnlockWidget.class).setAction(action);
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
