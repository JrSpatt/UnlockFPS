package com.spatt.unlockfps;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/** Lightweight background service that keeps PowerKeeper's refresh-rate overrides active. */
public class Auto120Service extends Service {
    private static final String CHANNEL_ID = "unlockfps_bg";
    private static final int NOTIF_ID = 120;
    private static final long REFRESH_INTERVAL_MS = 15000L;

    /** In Beast mode the long app list is re-sent every Nth tick only (N x 15 s) to stay light. */
    private static final int BEAST_EVERY_N_TICKS = 4;
    private int tick = 1;
    private static Auto120Service running;

    static void ensureRunning(Context ctx) {
        if (running != null || !FpsApplier.hasAppliedProfile(ctx)) return;
        try {
            ctx.startForegroundService(new Intent(ctx, Auto120Service.class));
        } catch (IllegalStateException | SecurityException denied) {
            android.util.Log.w("UnlockFPS", "Automatic reapply service could not start", denied);
        }
    }

    static void refreshNotification(Context ctx) {
        if (running == null) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, running.buildNotification());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    private Handler handler;
    private PowerManager powerManager;
    private BroadcastReceiver screenReceiver;

    private final Runnable periodicApply = new Runnable() {
        @Override
        public void run() {
            FpsApplier.markServiceAlive(Auto120Service.this);
            boolean beast = FpsApplier.isBeastEnabled(Auto120Service.this);
            boolean due = !beast || tick % BEAST_EVERY_N_TICKS == 0;
            tick++;
            if (due && (powerManager == null || powerManager.isInteractive())) {
                FpsApplier.reapply(Auto120Service.this);
            }
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = this;
        handler = new Handler(Looper.getMainLooper());
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        createChannel();
        startForeground(NOTIF_ID, buildNotification());

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    FpsApplier.markServiceAlive(context);
                    FpsApplier.reapply(context);
                }
            }
        };
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        handler.postDelayed(periodicApply, REFRESH_INTERVAL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null && FpsApplier.isAutoEnabled(this)) FpsApplier.reapply(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIF_ID, buildNotification());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (handler != null) {
            handler.removeCallbacks(periodicApply);
        }
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }
        running = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE);
        int fps = FpsApplier.effectiveFps(this);
        return new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(fps > 0 ? getString(R.string.notif_title, fps) : getString(R.string.app_name))
            .setContentText(fps > 0 ? getString(R.string.notif_text, fps) : getString(R.string.panel_unknown))
            .setSmallIcon(R.drawable.ic_tile)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }
}
