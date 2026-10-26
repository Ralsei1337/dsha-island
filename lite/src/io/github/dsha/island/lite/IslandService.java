package io.github.dsha.island.lite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Foreground service that hosts the loopback HTTP sink and posts one promoted
 * notification. The notification is the island; no SystemUI hooks are involved.
 */
public final class IslandService extends Service {
    private static final String TAG = "DshaIslandLite";
    static final String CHANNEL = "dsha_island_lite";
    static final String CHANNEL_BRIDGE = "dsha_island_bridge";
    static final int ID = 3001;
    static final int PORT = 8798;
    static volatile boolean RUNNING = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile ServerSocket server;
    private volatile boolean accepting;
    private volatile String state = "idle";
    private volatile String text = "";
    private volatile String title = "DSHA";
    private volatile int done = -1;
    private volatile int total = -1;
    private volatile long actionStartedAt = 0L;

    private final Runnable idle = () -> {
        state = "idle";
        text = "";
        updateNotification();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        RUNNING = true;
        createChannel();
        try {
            foreground(build());
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed", t);
        }
        startServer();
        main.postDelayed(tick, 1_000L);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            foreground(build());
        } catch (Throwable t) {
            Log.e(TAG, "refresh foreground failed", t);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        RUNNING = false;
        accepting = false;
        try {
            if (server != null) server.close();
        } catch (Throwable ignored) {
        }
        main.removeCallbacks(idle);
        main.removeCallbacks(tick);
        stopForeground(STOP_FOREGROUND_REMOVE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel =
                new NotificationChannel(CHANNEL, Platform.channelName(this), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("DSHA 任务状态胶囊（免 root）");
        channel.setShowBadge(false);
        nm.createNotificationChannel(channel);

        NotificationChannel bridge =
                new NotificationChannel(CHANNEL_BRIDGE, "DSHA 状态桥", NotificationManager.IMPORTANCE_LOW);
        bridge.setDescription("供 root/LSPosed 模块读取的静默状态");
        bridge.setShowBadge(false);
        bridge.setSound(null, null);
        bridge.enableVibration(false);
        nm.createNotificationChannel(bridge);
    }

    private boolean rootBridge() {
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        return prefs.getBoolean("root_bridge", false);
    }

    private void foreground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "specialUse FGS rejected, falling back", t);
            }
        }
        startForeground(ID, notification);
    }

    private Notification build() {
        boolean idleState = "idle".equals(state);
        String shortText = idleState ? "" : shortText();
        String body;
        if (idleState) {
            body = "等待 DSHA 任务";
        } else {
            body = text.isEmpty() ? shortText : text;
            String elapsed = elapsed();
            if (!"done".equals(state) && !elapsed.isEmpty()) body += " · " + elapsed;
        }

        boolean bridge = rootBridge();
        boolean promoted = Platform.promoted(this);
        Notification.Builder builder = new Notification.Builder(this, bridge ? CHANNEL_BRIDGE : CHANNEL)
                .setSmallIcon(R.drawable.ic_lite)
                .setContentTitle(bridge ? "DSHA 状态桥" : (idleState ? "DSHA Island Lite" : title))
                .setContentText(body)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openDsha());

        if (!idleState && !bridge && promoted) {
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setShortCriticalText(shortText);
                builder.setRequestPromotedOngoing(true);
            }
            builder.setColorized(true);
        }
        if (total > 0) {
            builder.setProgress(total, Math.max(0, Math.min(done, total)), false);
        }

        Bundle extras = new Bundle();
        extras.putString("dsha.state", state);
        extras.putString("dsha.text", text);
        extras.putString("dsha.title", title);
        extras.putLong("dsha.startedAt", actionStartedAt);
        extras.putInt("dsha.done", done);
        extras.putInt("dsha.total", total);
        builder.addExtras(extras);
        return builder.build();
    }

    private String shortText() {
        String detail = text == null ? "" : text.trim();
        String base;
        switch (state) {
            case "thinking":
                return "思考中";
            case "reading":
                base = "读取";
                break;
            case "writing":
                base = "写入";
                break;
            case "searching":
                base = "搜索";
                break;
            case "waiting":
                return "等你确认";
            case "todo":
                base = "清单";
                break;
            case "error":
                return "出错";
            case "done":
                return "任务完成";
            case "running":
            default:
                base = "执行";
                break;
        }
        String out = detail.isEmpty() ? base : base + " " + detail;
        return out.length() <= 28 ? out : out.substring(0, 27) + "…";
    }

    private String elapsed() {
        long started = actionStartedAt;
        if (started <= 0) return "";
        long seconds = Math.max(0, (SystemClock.elapsedRealtime() - started) / 1000);
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "分" + (seconds % 60) + "秒";
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!RUNNING) return;
            if (!"idle".equals(state) && !"done".equals(state)) updateNotification();
            main.postDelayed(this, 1_000L);
        }
    };

    private PendingIntent openDsha() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("dsh.android.client");
        if (launch == null) launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        try {
            nm.notify(ID, build());
        } catch (Throwable t) {
            Log.e(TAG, "notify failed", t);
        }
    }

    private void scheduleIdle() {
        main.removeCallbacks(idle);
        main.postDelayed(idle, 8_000L);
    }

    private void startServer() {
        accepting = true;
        Thread thread = new Thread(() -> {
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT));
                server = socket;
                Log.i(TAG, Platform.describe(this) + " · listening on 127.0.0.1:" + PORT);
                while (accepting) {
                    try {
                        handle(socket.accept());
                    } catch (Throwable t) {
                        if (accepting) Log.w(TAG, "accept failed", t);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "server failed", t);
            }
        }, "dsha-island-lite-http");
        thread.setDaemon(true);
        thread.start();
    }

    private void handle(Socket client) {
        try (Socket socket = client;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = socket.getOutputStream()) {
            String request = reader.readLine();
            if (request == null) return;
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                // consume headers
            }
            String[] parts = request.split(" ");
            if (parts.length < 2) return;
            String target = parts[1];
            int mark = target.indexOf('?');
            String path = mark < 0 ? target : target.substring(0, mark);
            Map<String, String> params = parse(mark < 0 ? "" : target.substring(mark + 1));
            apply(path, params);

            byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\n"
                    + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
            out.flush();
        } catch (Throwable t) {
            Log.w(TAG, "request failed", t);
        }
    }

    private void apply(String path, Map<String, String> params) {
        main.post(() -> {
            switch (path) {
                case "/state":
                case "/start": {
                    String nextState = cut(params.getOrDefault("state", "running"), 24);
                    String nextText = cut(params.getOrDefault("text", ""), 80);
                    String nextTitle = cut(params.getOrDefault("title", "DSHA"), 24);
                    if (!nextState.equals(state) || !nextText.equals(text)) {
                        actionStartedAt = SystemClock.elapsedRealtime();
                    }
                    state = nextState;
                    text = nextText;
                    title = nextTitle;
                    done = parseInt(params.get("todoDone"));
                    total = parseInt(params.get("todoTotal"));
                    main.removeCallbacks(idle);
                    updateNotification();
                    break;
                }
                case "/done":
                    state = "done";
                    actionStartedAt = 0L;
                    title = cut(params.getOrDefault("title", "DSHA"), 24);
                    text = cut(params.getOrDefault("text", "任务完成"), 80);
                    main.removeCallbacks(idle);
                    updateNotification();
                    scheduleIdle();
                    break;
                case "/idle":
                case "/cancel":
                    state = "idle";
                    text = "";
                    actionStartedAt = 0L;
                    main.removeCallbacks(idle);
                    updateNotification();
                    break;
                case "/ping":
                default:
                    break;
            }
        });
    }

    private static Map<String, String> parse(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null || query.isEmpty()) return out;
        for (String pair : query.split("&")) {
            int at = pair.indexOf('=');
            if (at <= 0) continue;
            try {
                String key = URLDecoder.decode(pair.substring(0, at), "UTF-8");
                String value = URLDecoder.decode(pair.substring(at + 1), "UTF-8");
                out.put(key, value);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static int parseInt(String value) {
        if (value == null) return -1;
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
