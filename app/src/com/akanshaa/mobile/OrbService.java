package com.akanshaa.mobile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The floating orb: a small always-on-top disc you can tap to talk, over any other app.
 * Runs as a foreground service so the microphone survives when the screen goes off.
 */
public class OrbService extends Service implements Assistant.Ui {

    private static final String CHANNEL = "akanshaa_orb";
    private static final int NOTE_ID = 4201;

    private WindowManager wm;
    private OrbView orb;
    private Assistant assistant;

    public static boolean overlayAllowed(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    public static void start(Context c) {
        Intent i = new Intent(c, OrbService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void stop(Context c) { c.stopService(new Intent(c, OrbService.class)); }

    @Override
    public void onCreate() {
        super.onCreate();
        assistant = Assistant.get(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        postNotification();
        if (!overlayAllowed(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (orb == null) addOrb();
        assistant.addUi(this);
        return START_STICKY;
    }

    private void postNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Akanshaa orb", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the microphone ready while the screen is off.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Akanshaa is listening")
                .setContentText("Tap the orb to talk.")
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTE_ID, n);
        }
    }

    private void addOrb() {
        int size = (int) (84 * getResources().getDisplayMetrics().density);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        lp.x = 24;
        lp.y = (int) (220 * getResources().getDisplayMetrics().density);

        orb = new OrbView(this);
        try {
            wm.addView(orb, lp);
        } catch (Exception e) {
            orb = null;   // security exception if the permission was revoked mid-flight
        }
    }

    @Override
    public void onDestroy() {
        assistant.removeUi(this);
        if (orb != null && wm != null) {
            try { wm.removeView(orb); } catch (Exception ignored) { }
            orb = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // ---- Assistant.Ui ------------------------------------------------------

    @Override public void onState(String state) { if (orb != null) orb.setState(state); }
    @Override public void onUser(String text) { }
    @Override public void onAkanshaa(String text) { }
    @Override public void openSettings() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---- the disc itself ---------------------------------------------------

    private final class OrbView extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String state = "ready";
        private float downX, downY, startX, startY;
        private long downAt;
        private boolean moved;

        OrbView(Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(6f);
            dot.setColor(Color.parseColor("#E6F1FF"));
        }

        void setState(String s) {
            state = s == null ? "ready" : s;
            postInvalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - 8f;

            fill.setColor(Color.parseColor("#0E1830"));
            fill.setAlpha(232);
            canvas.drawCircle(cx, cy, r, fill);

            int rgb = "listening".equals(state) ? Color.parseColor("#35E0F2")
                    : "thinking".equals(state) ? Color.parseColor("#8B5CF6")
                    : "speaking".equals(state) ? Color.parseColor("#F472B6")
                    : Color.parseColor("#2A3E63");
            ring.setColor(rgb);
            canvas.drawCircle(cx, cy, r, ring);

            float sweep = "listening".equals(state) || "thinking".equals(state) ? 120f : 360f;
            float angle = "listening".equals(state) ? (System.currentTimeMillis() / 8) % 360 : 0;
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, angle, sweep, false, ring);
            canvas.drawCircle(cx, cy, 5f, dot);

            if ("listening".equals(state) || "thinking".equals(state)) postInvalidateDelayed(32);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.abs(dx) > 18 || Math.abs(dy) > 18) moved = true;
                    if (moved) {
                        lp.x = (int) (startX + dx);
                        lp.y = (int) (startY + dy);
                        wm.updateViewLayout(orb, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && System.currentTimeMillis() - downAt < 400) {
                        assistant.listenOnce();
                    } else if (!moved) {
                        assistant.stop();
                    }
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }
}
