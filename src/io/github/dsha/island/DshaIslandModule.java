package io.github.dsha.island;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.json.JSONObject;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * SystemUI-only bridge: recognises DSHA's own notifications, adds the official
 * HyperOS island payload and promotes them. Never touches the DSHA process.
 */
public final class DshaIslandModule extends XposedModule {
    private static final String TAG = "DshaIsland";
    private static final String DSHA = "dsh.android.client";
    private static final String LITE = "io.github.dsha.island.lite";
    private static final String BRIDGE_CHANNEL = "dsha_island_bridge";
    private static final long THREAD_ARM_MS = 1_500L;
    private static final long GLOBAL_ARM_MS = 2_000L;

    private final Set<ClassLoader> pipelineLoaders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>()));
    private final Set<ClassLoader> factoryLoaders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>()));
    private final Set<ClassLoader> permissionLoaders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>()));

    private static final ThreadLocal<Long> THREAD_ARMED = new ThreadLocal<>();
    private volatile long armedAt = 0L;
    private volatile String lastEnriched = "";

    private volatile String bridgeState = "";
    private volatile String bridgeText = "";
    private volatile long bridgeStartedAt = 0L;
    private volatile long bridgeAt = 0L;

    private volatile boolean pollerStarted = false;
    private volatile String directState = "";
    private volatile String directText = "";
    private volatile long directStartedAt = 0L;
    private volatile long directAt = 0L;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) return;
        log(Log.INFO, TAG, "SystemUI loaded, installing DSHA island hooks");
        startPoller();
        try {
            install(param.getDefaultClassLoader());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "install failed", t);
        }
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) return;
        try {
            install(param.getClassLoader());
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "ready install failed", t);
        }
    }

    private void install(ClassLoader cl) {
        if (cl == null) return;
        if (!pipelineLoaders.contains(cl) && hookPipeline(cl)) pipelineLoaders.add(cl);
        if (factoryLoaders.add(cl)) {
            hookPluginFactory(cl);
            hookBridgeListener(cl);
        }
        if (!permissionLoaders.contains(cl) && hookPermissions(cl)) permissionLoaders.add(cl);
    }

    private boolean hookPermissions(ClassLoader cl) {
        boolean focus = hookPermission(cl, "miui.systemui.notification.NotificationSettingsManager",
                "canShowFocus", "canCustomFocus");
        boolean sig = hookPermission(cl, "miui.systemui.notification.focus.SignatureChecker",
                "checkSignatures");
        return focus || sig;
    }

    private boolean hookPipeline(ClassLoader cl) {
        try {
            Class<?> util = Class.forName("com.miui.systemui.notification.MiuiBaseNotifUtil", false, cl);
            Method m = util.getDeclaredMethod("generateInnerNotifBean", StatusBarNotification.class);
            hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                List<Object> args = chain.getArgs();
                Object arg = args.isEmpty() ? null : args.get(0);
                if (arg instanceof StatusBarNotification) {
                    StatusBarNotification sbn = (StatusBarNotification) arg;
                    if (LITE.equals(sbn.getPackageName())) {
                        try {
                            captureBridge(sbn);
                        } catch (Throwable t) {
                            log(Log.ERROR, TAG, "bridge capture failed", t);
                        }
                    } else if (DSHA.equals(sbn.getPackageName())) {
                        armedAt = SystemClock.elapsedRealtime();
                        try {
                            enrich(sbn);
                        } catch (Throwable t) {
                            log(Log.ERROR, TAG, "enrich failed", t);
                        }
                    }
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "hooked MiuiBaseNotifUtil.generateInnerNotifBean");
            return true;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "pipeline hook missing: " + t);
            return false;
        }
    }

    private void hookPluginFactory(ClassLoader cl) {
        try {
            Class<?> factory = Class.forName(
                    "com.android.systemui.shared.plugins.PluginInstance$PluginFactory", false, cl);
            for (Method m : factory.getDeclaredMethods()) {
                if (!"createPluginContext".equals(m.getName())) continue;
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof Context) {
                        ClassLoader pluginLoader = ((Context) result).getClassLoader();
                        if (pluginLoader != null) {
                            try {
                                install(pluginLoader);
                            } catch (Throwable t) {
                                log(Log.ERROR, TAG, "plugin install failed", t);
                            }
                        }
                    }
                    return result;
                });
            }
        } catch (Throwable ignored) {
            // Not present on every build; the default class loader path is enough.
        }
    }

    private void hookBridgeListener(ClassLoader cl) {
        try {
            Class<?> listener = Class.forName(
                    "com.android.systemui.statusbar.notification.MiuiNotificationListener", false, cl);
            for (Method m : listener.getDeclaredMethods()) {
                if (!"onNotificationPosted".equals(m.getName())) continue;
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    List<Object> args = chain.getArgs();
                    Object arg = args.isEmpty() ? null : args.get(0);
                    if (arg instanceof StatusBarNotification) {
                        StatusBarNotification sbn = (StatusBarNotification) arg;
                        if (LITE.equals(sbn.getPackageName())) {
                            try {
                                captureBridge(sbn);
                            } catch (Throwable t) {
                                log(Log.ERROR, TAG, "bridge listener capture failed", t);
                            }
                        }
                    }
                    return chain.proceed();
                });
            }
        } catch (Throwable ignored) {
            // Fallback: the generateInnerNotifBean path still captures rendered notifications.
        }
    }

    private boolean hookPermission(ClassLoader cl, String className, String... names) {
        boolean any = false;
        try {
            Class<?> clazz = Class.forName(className, false, cl);
            Set<String> wanted = new HashSet<>();
            Collections.addAll(wanted, names);
            for (Method m : clazz.getDeclaredMethods()) {
                if (!wanted.contains(m.getName()) || m.getReturnType() != boolean.class) continue;
                hook(m).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    if (touchesDsha(chain.getArgs()) || recentlyArmed()) return true;
                    return chain.proceed();
                });
                log(Log.INFO, TAG, "hooked " + className + "#" + m.getName());
                any = true;
            }
        } catch (Throwable ignored) {
            // Class lives in the focus plugin on some builds; retried from the plugin loader.
        }
        return any;
    }

    private void enrich(StatusBarNotification sbn) throws Exception {
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle extras = n.extras;
        if (extras == null) return;
        if (extras.containsKey("miui.focus.param")) return; // another module already owns it
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        Phase.Kind kind = Phase.parse(text);
        boolean summary = sbn.getId() == 0
                || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        // The active group summary duplicates the child island. Keep only the
        // terminal summary: DSHA cancels the terminal child after ~1s, so the
        // summary is the only completion notification.
        if (summary && !Phase.terminal(kind)) return;

        // Never islandify a foreground-service notification: it is the permanent
        // "DSHA is running in background" entry, not a task state.
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;
        if (sbn.getId() == 1001) return;
        // Only the per-task channel belongs on the island.
        if (!"dsh_task_progress".equals(n.getChannelId())) return;

        String seconds = Phase.seconds(text);
        IslandPayload.Model model;
        if (directFresh()) {
            String action = bridgeAction(directState);
            String detail = directDetail();
            String dur = directSeconds(seconds);
            model = IslandPayload.custom(action, detail, dur, seconds, "done".equals(directState));
        } else if (bridgeFresh()) {
            String action = bridgeAction(bridgeState);
            String detail = bridgeDetail();
            String dur = bridgeSeconds(seconds);
            model = IslandPayload.custom(action, detail, dur, seconds, "done".equals(bridgeState));
        } else {
            model = IslandPayload.model(kind, seconds);
        }

        Notification.Action stop = findAction(n, "停止", "Stop");
        if (stop == null && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0
                && n.actions != null && n.actions.length >= 2) {
            stop = n.actions[0]; // DSHA always adds stop first while active
        }
        Notification.Action open = findAction(n, "回聊天", "Return to chat", "返回聊天");
        if (open == null && n.contentIntent != null) {
            Icon icon = n.getSmallIcon();
            if (icon != null) {
                open = new Notification.Action.Builder(icon, "打开", n.contentIntent).build();
            }
        }

        String json = IslandPayload.json(model, stop, open);
        if (json.getBytes(StandardCharsets.UTF_8).length > 3_000) {
            log(Log.WARN, TAG, "param too large, falling back to promoted only");
            json = null;
        }

        // The focus card may render the original title/text as well as baseInfo.
        // Replace them with detail-only text so the phase/time is not repeated.
        extras.putCharSequence(Notification.EXTRA_TITLE, "DSHA");
        extras.putCharSequence(Notification.EXTRA_TEXT, "任务进行中");
        extras.putBoolean(Notification.EXTRA_COLORIZED, true);
        extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true);
        extras.putString("android.shortCriticalText", model.shortText);
        if (json != null) {
            extras.putString("miui.focus.param", json);
            Bundle pics = IslandPayload.pics(n.getSmallIcon());
            if (!pics.isEmpty()) extras.putBundle("miui.focus.pics", pics);
            Bundle acts = IslandPayload.actions(stop, open);
            if (!acts.isEmpty()) extras.putBundle("miui.focus.actions", acts);
        }

        THREAD_ARMED.set(SystemClock.elapsedRealtime());
        if (!model.shortText.equals(lastEnriched)) {
            lastEnriched = model.shortText;
            log(Log.INFO, TAG, "enriched phase=" + kind + " short=" + model.shortText
                    + " ch=" + n.getChannelId() + " id=" + sbn.getId()
                    + " fgs=" + ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0)
                    + " grp=" + ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0)
                    + " stop=" + (stop != null) + " open=" + (open != null));
        }
    }

    private void startPoller() {
        if (pollerStarted) return;
        pollerStarted = true;
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    pollDirect();
                } catch (Throwable ignored) {
                }
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }, "dsha-island-direct");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollDirect() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8797), 300);
            socket.setSoTimeout(500);
            OutputStream out = socket.getOutputStream();
            out.write(("GET /state HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            String response = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            int split = response.indexOf("\r\n\r\n");
            String body = split >= 0 ? response.substring(split + 4) : response;
            JSONObject object = new JSONObject(body);
            String state = object.optString("state", "");
            if (state.isEmpty()) return;
            directState = state;
            directText = object.optString("text", "");
            directStartedAt = object.optLong("startedAt", 0L);
            directAt = SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
        }
    }

    private boolean directFresh() {
        long at = directAt;
        return at != 0L && SystemClock.elapsedRealtime() - at <= 3_000L
                && !directState.isEmpty() && !"idle".equals(directState);
    }

    private String directDetail() {
        if ("thinking".equals(directState) || "waiting".equals(directState)) return "";
        return directText == null ? "" : directText.trim();
    }

    private String directSeconds(String fallback) {
        if ("done".equals(directState)) return "";
        long started = directStartedAt;
        if (started > 0) {
            long seconds = Math.max(0, (System.currentTimeMillis() - started) / 1000);
            return Long.toString(seconds);
        }
        return fallback;
    }

    private void captureBridge(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle ex = n.extras;
        if (ex == null) return;
        if (!BRIDGE_CHANNEL.equals(n.getChannelId())) return;
        String state = ex.getString("dsha.state", "");
        if (state.isEmpty()) return;
        bridgeState = state;
        bridgeText = ex.getString("dsha.text", "");
        bridgeStartedAt = ex.getLong("dsha.startedAt", 0L);
        bridgeAt = SystemClock.elapsedRealtime();
    }

    private boolean bridgeFresh() {
        long at = bridgeAt;
        return at != 0L && SystemClock.elapsedRealtime() - at <= 3_500L
                && !bridgeState.isEmpty() && !"idle".equals(bridgeState);
    }

    private static String bridgeAction(String state) {
        switch (state) {
            case "thinking":
                return "思考中";
            case "reading":
                return "读取";
            case "writing":
                return "写入";
            case "searching":
                return "搜索";
            case "todo":
                return "清单";
            case "waiting":
                return "等你确认";
            case "error":
                return "出错";
            case "done":
                return "任务完成";
            case "running":
            default:
                return "执行";
        }
    }

    private String bridgeDetail() {
        if ("thinking".equals(bridgeState) || "waiting".equals(bridgeState)) return "";
        return bridgeText == null ? "" : bridgeText.trim();
    }

    private String bridgeSeconds(String fallback) {
        if ("done".equals(bridgeState)) return "";
        long started = bridgeStartedAt;
        if (started > 0) {
            long seconds = Math.max(0, (SystemClock.elapsedRealtime() - started) / 1000);
            return Long.toString(seconds);
        }
        return fallback;
    }

    private static Notification.Action findAction(Notification n, String... titles) {
        if (n.actions == null) return null;
        for (Notification.Action action : n.actions) {
            CharSequence title = action.title;
            if (title == null) continue;
            for (String wanted : titles) {
                if (title.toString().contains(wanted)) return action;
            }
        }
        return null;
    }

    private static boolean touchesDsha(List<Object> args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                String s = (String) arg;
                if (s.equals(DSHA) || s.contains(DSHA)) return true;
            } else if (arg instanceof String[]) {
                for (String s : (String[]) arg) {
                    if (s != null && (s.equals(DSHA) || s.contains(DSHA))) return true;
                }
            } else if (arg instanceof ApplicationInfo) {
                if (DSHA.equals(((ApplicationInfo) arg).packageName)) return true;
            }
        }
        return false;
    }

    private boolean recentlyArmed() {
        long now = SystemClock.elapsedRealtime();
        Long threadArmed = THREAD_ARMED.get();
        if (threadArmed != null && now - threadArmed <= THREAD_ARM_MS) return true;
        long armed = armedAt;
        return armed != 0 && now - armed <= GLOBAL_ARM_MS;
    }
}
