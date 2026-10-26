package io.github.dsha.island;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/** Minimal module settings/explanation page; no resources, no permissions. */
public final class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);

        TextView text = new TextView(this);
        text.setText(
                "DSHA Island\n\n"
                        + "作用域：com.android.systemui（只改 SystemUI，不改 DSHA）\n\n"
                        + "它做什么：\n"
                        + "1. 识别 dsh.android.client 的任务通知；\n"
                        + "2. 写入 HyperOS 官方 miui.focus.param（大岛/小岛/焦点卡）；\n"
                        + "3. 打开 promoted ongoing + shortCriticalText；\n"
                        + "4. 只对 DSHA 放开焦点通知校验，不全局解锁。\n\n"
                        + "怎么用：\n"
                        + "LSPosed 管理器 → 模块 → 启用 → 作用域只勾 com.android.systemui → 重启。\n\n"
                        + "怎么关：\n"
                        + "LSPosed 里停用本模块并重启；DSHA 恢复原样。\n\n"
                        + "注意：这是非官方 HyperOS 接口，系统升级可能失效；"
                        + "只在个人设备使用，不要用于伪造其他应用的通知。");
        text.setTextIsSelectable(true);
        text.setTextSize(15);
        text.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(text, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }
}
