package io.github.dsha.island;

import android.app.Notification;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds the official HyperOS island payload (param_v2) from a coarse phase. */
final class IslandPayload {
    static final String PIC = "miui.focus.pic_dsha";
    static final String PIC_STOP = "miui.focus.pic_stop";
    static final String PIC_OPEN = "miui.focus.pic_open";

    static final String ACTION_STOP = "miui.focus.action_stop";
    static final String ACTION_OPEN = "miui.focus.action_open";

    static final class Model {
        final String shortText;
        final String bigTitle;
        final String bigContent;
        final String bigRight;
        final String baseContent;
        final int timeoutSeconds;
        final int property;
        final boolean firstFloat;

        Model(String shortText, String bigTitle, String bigContent, String bigRight,
              String baseContent, int timeoutSeconds, int property, boolean firstFloat) {
            this.shortText = shortText;
            this.bigTitle = bigTitle;
            this.bigContent = bigContent;
            this.bigRight = bigRight;
            this.baseContent = baseContent;
            this.timeoutSeconds = timeoutSeconds;
            this.property = property;
            this.firstFloat = firstFloat;
        }
    }

    private IslandPayload() {}

    static Model model(Phase.Kind kind, String seconds) {
        String sec = seconds == null ? "" : seconds.trim();
        String dur = formatDuration(sec);
        switch (kind) {
            case WAITING:
                return new Model("等你确认", "等你确认", "", "打开应用处理",
                        "有任务在等你", 3600, 2, true);
            case TOOL:
                return new Model(join("执行中", sec), "执行中", sec, "正在执行工具",
                        "正在执行工具", 3600, 1, false);
            case REPLY:
                return new Model(join("思考中", sec), "思考中", sec, "正在生成回复",
                        "正在生成回复", 3600, 1, false);
            case STOPPING:
                return new Model("正在停止", "正在停止", sec, "等待结束",
                        "已请求停止", 3600, 1, false);
            case DONE_OK:
                return new Model("任务完成", "任务完成", dur, "查看结果",
                        "任务完成" + (dur.isEmpty() ? "" : " · " + dur), 8, 1, false);
            case DONE_STOP:
                return new Model("已停止", "已停止", dur, "查看结果",
                        "任务已停止" + (dur.isEmpty() ? "" : " · " + dur), 8, 1, false);
            case DONE_LIMIT:
                return new Model("已达上限", "已达上限", dur, "查看结果",
                        "已达到输出上限", 8, 1, false);
            case ATTENTION:
                return new Model("需要处理", "需要处理", "", "打开应用",
                        "任务需要处理", 8, 2, true);
            case RUNNING:
            default:
                return new Model(join("DSHA 运行中", sec), "运行中", sec, "DSHA",
                        "DSHA 正在后台运行", 3600, 1, false);
        }
    }

    static String json(Model m, Notification.Action stop, Notification.Action open) throws Exception {
        JSONObject param = new JSONObject();
        param.put("protocol", 1);
        param.put("business", "dsha_agent");
        param.put("updatable", true);
        param.put("enableFloat", false);
        param.put("islandFirstFloat", m.firstFloat);
        param.put("ticker", m.shortText);
        param.put("tickerPic", PIC);
        param.put("aodTitle", "DSHA " + m.shortText);

        JSONObject island = new JSONObject();
        island.put("islandProperty", m.property);
        island.put("islandTimeout", m.timeoutSeconds);
        island.put("highlightColor", "#3482FF");

        JSONObject big = new JSONObject();
        JSONObject left = new JSONObject();
        left.put("type", 1);
        left.put("picInfo", pic(PIC));
        JSONObject leftText = new JSONObject();
        leftText.put("title", m.bigTitle);
        if (!m.bigContent.isEmpty()) leftText.put("content", m.bigContent);
        leftText.put("showHighlightColor", false);
        left.put("textInfo", leftText);
        big.put("imageTextInfoLeft", left);

        JSONObject right = new JSONObject();
        right.put("title", m.bigRight);
        right.put("showHighlightColor", false);
        big.put("textInfo", right);
        island.put("bigIslandArea", big);

        JSONObject small = new JSONObject();
        small.put("picInfo", pic(PIC));
        island.put("smallIslandArea", small);
        param.put("param_island", island);

        JSONObject base = new JSONObject();
        base.put("type", 2);
        base.put("title", "DSHA");
        base.put("subTitle", m.shortText);
        base.put("content", m.baseContent);
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
            hint.put("title", m.shortText);
            hint.put("content", m.bigTitle);
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

    private static String join(String prefix, String seconds) {
        return seconds == null || seconds.isEmpty() ? prefix : prefix + " " + seconds + "s";
    }

    private static String formatDuration(String seconds) {
        if (seconds == null || seconds.isEmpty()) return "";
        try {
            int total = Integer.parseInt(seconds);
            if (total < 60) return total + "s";
            return (total / 60) + "分" + (total % 60) + "秒";
        } catch (NumberFormatException ignored) {
            return "";
        }
    }
}
