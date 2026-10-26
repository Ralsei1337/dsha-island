package io.github.dsha.island;

import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Builds param_v2 with no duplicated phase/time:
 *   big A: action + detail (or time when no detail)
 *   big B: time only when detail is present
 *   focus card: DSHA + detail, no subTitle
 */
final class IslandPayload {
    static final String PIC = "miui.focus.pic_dsha";
    static final String PIC_STOP = "miui.focus.pic_stop";
    static final String PIC_OPEN = "miui.focus.pic_open";

    static final String ACTION_STOP = "miui.focus.action_stop";
    static final String ACTION_OPEN = "miui.focus.action_open";

    static final class Model {
        final String shortText;
        final String action;
        final String detail;
        final String actionSeconds;
        final String totalSeconds;
        final int timeoutSeconds;
        final int property;
        final boolean firstFloat;

        Model(String action, String detail, String actionSeconds, String totalSeconds,
              int timeoutSeconds, int property, boolean firstFloat) {
            this.action = action == null ? "" : action;
            this.detail = detail == null ? "" : detail;
            this.actionSeconds = actionSeconds == null ? "" : actionSeconds;
            this.totalSeconds = totalSeconds == null ? "" : totalSeconds;
            this.shortText = text(this.action, this.detail);
            this.timeoutSeconds = timeoutSeconds;
            this.property = property;
            this.firstFloat = firstFloat;
        }
    }

    private IslandPayload() {}

    static Model model(Phase.Kind kind, String seconds) {
        String total = formatDuration(seconds);
        String action = actionFor(kind);
        int timeout = Phase.terminal(kind) ? 8 : 3600;
        int property = kind == Phase.Kind.WAITING || kind == Phase.Kind.ATTENTION ? 2 : 1;
        boolean firstFloat = kind == Phase.Kind.WAITING || kind == Phase.Kind.ATTENTION;
        // Island has no time; the expanded card shows the total.
        return new Model(action, "", "", total, timeout, property, firstFloat);
    }

    /** Bridge state: action + path/command + current-action seconds + total turn seconds. */
    static Model custom(String action, String detail, String actionSeconds,
                        String totalSeconds, boolean terminal) {
        return new Model(action, cut(detail, 24),
                formatDuration(actionSeconds), formatDuration(totalSeconds),
                terminal ? 8 : 3600, 1, false);
    }

    static String json(Model m, Notification.Action stop, Notification.Action open) throws Exception {
        JSONObject param = new JSONObject();
        param.put("protocol", 1);
        param.put("business", "dsha_agent");
        param.put("updatable", true);
        param.put("enableFloat", false);
        param.put("islandFirstFloat", m.firstFloat);
        param.put("ticker", "DSHA");
        param.put("tickerPic", PIC);
        param.put("aodTitle", "DSHA");

        JSONObject island = new JSONObject();
        island.put("islandProperty", m.property);
        island.put("islandTimeout", m.timeoutSeconds);
        island.put("highlightColor", "#3482FF");

        JSONObject big = new JSONObject();
        JSONObject left = new JSONObject();
        left.put("type", 1);
        left.put("picInfo", pic(PIC));
        JSONObject leftText = new JSONObject();
        leftText.put("title", m.action);
        if (!m.detail.isEmpty()) leftText.put("content", m.detail);
        leftText.put("showHighlightColor", false);
        left.put("textInfo", leftText);
        big.put("imageTextInfoLeft", left);

        // B area carries the current-action elapsed time only.
        if (!m.actionSeconds.isEmpty()) {
            JSONObject right = new JSONObject();
            right.put("title", m.actionSeconds);
            right.put("showHighlightColor", false);
            big.put("textInfo", right);
        }
        island.put("bigIslandArea", big);

        JSONObject small = new JSONObject();
        small.put("picInfo", pic(PIC));
        island.put("smallIslandArea", small);
        param.put("param_island", island);

        JSONObject base = new JSONObject();
        base.put("type", 2);
        base.put("title", "DSHA");
        if (!m.totalSeconds.isEmpty()) {
            base.put("subTitle", "任务总耗时 " + m.totalSeconds);
        }
        base.put("content", m.detail.isEmpty()
                ? ("任务完成".equals(m.action) || "已停止".equals(m.action) ? "任务已结束" : "任务进行中")
                : m.detail);
        param.put("baseInfo", base);

        JSONArray actions = new JSONArray();
        if (open != null) {
            actions.put(action(ACTION_OPEN, PIC_OPEN, 0));
        }
        param.put("actions", actions);

        if (stop != null) {
            String title = stop.title == null ? "停止" : stop.title.toString();
            JSONObject actionInfo = new JSONObject();
            actionInfo.put("action", ACTION_STOP);
            actionInfo.put("actionTitle", title);
            actionInfo.put("actionIcon", PIC_STOP);
            actionInfo.put("actionIntentType", 0);

            JSONObject hint = new JSONObject();
            hint.put("type", 2);
            hint.put("title", "任务控制");
            hint.put("content", "当前任务");
            hint.put("actionInfo", actionInfo);
            param.put("hintInfo", hint);
        }

        return new JSONObject().put("param_v2", param).toString();
    }

    static Bundle pics(Icon icon) {
        Bundle bundle = new Bundle();
        if (icon == null) return bundle;
        bundle.putParcelable(PIC, icon);
        bundle.putParcelable(PIC_STOP, icon);
        bundle.putParcelable(PIC_OPEN, icon);
        return bundle;
    }

    static Bundle actions(Notification.Action stop, Notification.Action open) {
        Bundle bundle = new Bundle();
        if (stop != null) bundle.putParcelable(ACTION_STOP, stop);
        if (open != null) bundle.putParcelable(ACTION_OPEN, open);
        return bundle;
    }

    private static String actionFor(Phase.Kind kind) {
        switch (kind) {
            case WAITING:
                return "等你确认";
            case TOOL:
                return "执行";
            case REPLY:
                return "思考中";
            case STOPPING:
                return "正在停止";
            case DONE_OK:
                return "任务完成";
            case DONE_STOP:
                return "已停止";
            case DONE_LIMIT:
                return "已达上限";
            case ATTENTION:
                return "需要处理";
            case RUNNING:
            default:
                return "运行中";
        }
    }

    private static String text(String action, String detail) {
        if (action.isEmpty()) return cut(detail, 28);
        if (detail.isEmpty()) return action;
        String out = action + " " + detail;
        return cut(out, 28);
    }

    private static JSONObject pic(String key) throws Exception {
        return new JSONObject().put("type", 1).put("pic", key);
    }

    private static JSONObject action(String key, String icon, int type) throws Exception {
        return new JSONObject()
                .put("action", key)
                .put("actionIcon", icon)
                .put("actionIconDark", icon)
                .put("actionIntentType", type)
                .put("type", 0);
    }

    private static String formatDuration(String seconds) {
        if (seconds == null || seconds.isEmpty()) return "";
        try {
            int total = Integer.parseInt(seconds.trim());
            if (total < 60) return total + "s";
            return (total / 60) + "分" + (total % 60) + "秒";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String cut(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
