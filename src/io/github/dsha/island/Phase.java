package io.github.dsha.island;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** DSHA notification text -> island phase. Kept deliberately dumb: no DSHA internals. */
final class Phase {
    enum Kind { WAITING, TOOL, REPLY, STOPPING, DONE_OK, DONE_STOP, DONE_LIMIT, ATTENTION, RUNNING }

    private static final Pattern SECONDS = Pattern.compile("(\\d+)\\s*s(?:\\b|$)");

    private Phase() {}

    static Kind parse(CharSequence text) {
        String s = text == null ? "" : text.toString();
        if (s.contains("等待处理") || s.contains("Waiting for input")) return Kind.WAITING;
        if (s.contains("已请求停止") || s.contains("Stop requested")) return Kind.STOPPING;
        if (s.contains("正在执行工具") || s.contains("Running tool")) return Kind.TOOL;
        if (s.contains("正在生成回复") || s.contains("Generating reply")) return Kind.REPLY;
        if (s.contains("任务完成") || s.contains("Task completed")) return Kind.DONE_OK;
        if (s.contains("任务已停止") || s.contains("Task stopped")) return Kind.DONE_STOP;
        if (s.contains("输出上限") || s.contains("Output limit")) return Kind.DONE_LIMIT;
        if (s.contains("需要处理") || s.contains("needs attention")) return Kind.ATTENTION;
        return Kind.RUNNING;
    }

    static String seconds(CharSequence text) {
        if (text == null) return "";
        Matcher m = SECONDS.matcher(text);
        return m.find() ? m.group(1) : "";
    }

    static boolean terminal(Kind kind) {
        return kind == Kind.DONE_OK || kind == Kind.DONE_STOP
                || kind == Kind.DONE_LIMIT || kind == Kind.ATTENTION;
    }
}
