package com.spatt.unlockfps;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

/** Home-screen widget: last applied target and one-tap reapply. */
public class UnlockWidget extends AppWidgetProvider {
    private static final String ACTION_REAPPLY = "com.spatt.unlockfps.widget.REAPPLY";

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final int SPIN_MS = 420;
    private static final int RESULT_MS = 2000;
    private static boolean spinning;
    private static int feedback;

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
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            updateAll(ctx);
            return;
        }
        if (!ACTION_REAPPLY.equals(intent.getAction()) || spinning || feedback != 0) return;
        if (!hasTargets(ctx)) {
            updateAll(ctx);
            return;
        }

        Context app = ctx.getApplicationContext();
        spinning = true;
        updateAll(app);
        FpsApplier.ApplyResult result = FpsApplier.reapply(app);
        int message = result.count > 0 ? R.string.widget_sent
            : !result.powerKeeperFound ? R.string.btn_pk_not_found
            : !FpsApplier.panelDetected(app) ? R.string.panel_unknown : R.string.widget_failed;
        // Keep the receiver alive only for this tap. The launcher animates the refresh icon.
        PendingResult pending = goAsync();
        handler.postDelayed(() -> {
            spinning = false;
            feedback = message;
            try {
                updateAll(app);
            } finally {
                handler.postDelayed(() -> {
                    feedback = 0;
                    try {
                        updateAll(app);
                    } finally {
                        pending.finish();
                    }
                }, RESULT_MS);
            }
        }, SPIN_MS);
    }

    private static boolean hasTargets(Context ctx) {
        return FpsApplier.hasAppliedProfile(ctx) && !(FpsApplier.isBeastEnabled(ctx)
            ? FpsApplier.beastPackages(ctx) : FpsApplier.loadPackages(ctx)).isEmpty();
    }

    private static RemoteViews build(Context base) {
        Context ctx = Lang.wrap(base);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_unlock);
        boolean ready = hasTargets(ctx);
        boolean busy = ready && spinning;
        int message = ready ? feedback : 0;
        int fps = FpsApplier.hasAppliedProfile(ctx) ? FpsApplier.effectiveFps(ctx) : 0;
        int caption = !ready ? R.string.widget_pick : message == 0 ? R.string.widget_target : message;
        int action = ready ? R.string.widget_reapply : R.string.widget_pick;
        int icon = !ready ? R.drawable.ui_chevron : message == R.string.widget_sent ? R.drawable.widget_check
            : message != 0 ? R.drawable.ui_info : R.drawable.ui_refresh;
        int color = message == R.string.widget_sent ? 0xff83daa1 : message != 0 ? 0xffffbf72 : 0xffbac2ce;

        v.setTextViewText(R.id.widget_target, ctx.getString(caption));
        v.setTextColor(R.id.widget_target, color);
        v.setTextViewText(R.id.widget_hz, fps > 0 ? ctx.getString(R.string.widget_rate_value, fps) : "—");
        v.setTextColor(R.id.widget_hz, fps > 0 ? UiHelper.fpsColor(fps) : 0xffaeb7c2);
        v.setImageViewResource(R.id.widget_icon, icon);
        v.setViewVisibility(R.id.widget_icon, busy ? View.INVISIBLE : View.VISIBLE);
        v.setViewVisibility(R.id.widget_spin, busy ? View.VISIBLE : View.GONE);
        v.setContentDescription(R.id.widget_reapply, ctx.getString(message != 0 ? message : action));

        Intent open = new Intent(ctx, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (!ready) open.setAction(MainActivity.ACTION_PICK_PROFILE);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent launch = PendingIntent.getActivity(ctx, ready ? 3 : 5, open, flags);
        PendingIntent reapply = PendingIntent.getBroadcast(ctx, 4,
            new Intent(ctx, UnlockWidget.class).setAction(ACTION_REAPPLY), flags);
        v.setOnClickPendingIntent(R.id.widget_root, launch);
        v.setOnClickPendingIntent(R.id.widget_reapply, ready ? reapply : launch);
        return v;
    }
}
