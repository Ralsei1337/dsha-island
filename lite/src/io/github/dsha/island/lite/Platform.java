package io.github.dsha.island.lite;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.util.Locale;

/** Best-effort OEM detection. Only AOSP Live Updates are used; no vendor SDK. */
final class Platform {
    enum Oem { XIAOMI, OPPO, VIVO, HUAWEI, HONOR, OTHER }

    private Platform() {}

    static Oem oem() {
        String name = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        if (name.contains("xiaomi") || name.contains("redmi") || name.contains("poco")) {
            return Oem.XIAOMI;
        }
        if (name.contains("oppo") || name.contains("oneplus") || name.contains("realme")) {
            return Oem.OPPO;
        }
        if (name.contains("vivo") || name.contains("iqoo")) {
            return Oem.VIVO;
        }
        if (name.contains("huawei")) {
            return Oem.HUAWEI;
        }
        if (name.contains("honor")) {
            return Oem.HONOR;
        }
        return Oem.OTHER;
    }

    static String islandName(Context context) {
        switch (oem()) {
            case XIAOMI:
                return "超级岛";
            case OPPO:
                return "流体云";
            case VIVO:
                return "原子通知";
            case HUAWEI:
                return "实况窗";
            case HONOR:
                return "灵动胶囊";
            default:
                return "实时通知";
        }
    }

    static String channelName(Context context) {
        return "DSHA · " + islandName(context);
    }

    /** True only when the OS exposes Android 16 Live Updates and grants the app. */
    static boolean promoted(Context context) {
        if (Build.VERSION.SDK_INT < 36) return false;
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.canPostPromotedNotifications();
        } catch (Throwable ignored) {
            return false;
        }
    }

    static String capabilityReport(Context context) {
        if (!promoted(context)) {
            return "免 root 能力：普通通知（系统未开放 Live Updates）";
        }
        switch (oem()) {
            case XIAOMI:
                return "免 root 能力：AOSP 胶囊（官方大岛/岛上停止需 root）";
            case OPPO:
                return "免 root 能力：流体云 Live Update 卡片";
            case VIVO:
                return "免 root 能力：原子通知（仅当系统实现 Live Updates）";
            case HUAWEI:
                return "免 root 能力：普通通知（APK 不可用实况窗）";
            case HONOR:
                return "免 root 能力：灵动胶囊（仅当系统实现 Live Updates）";
            default:
                return "免 root 能力：实时通知（仅当系统实现 Live Updates）";
        }
    }

    static String describe(Context context) {
        return Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE
                + " · " + islandName(context)
                + " · " + (promoted(context) ? "Live Updates 可用" : "普通通知降级");
    }
}
