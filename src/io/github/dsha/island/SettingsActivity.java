package io.github.dsha.island;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal module settings/explanation page; network only on explicit update check. */
public final class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

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
        column.addView(text, matchWrap());

        Button check = new Button(this);
        check.setText("检查更新");
        check.setOnClickListener(v -> checkUpdate());
        column.addView(check, matchWrap());

        TextView foot = new TextView(this);
        foot.setText("\n检查更新只在你点按钮时访问 GitHub，不后台轮询、不自动下载。");
        foot.setTextSize(13);
        column.addView(foot, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    private void checkUpdate() {
        Toast.makeText(this, "正在检查 GitHub Release…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            UpdateChecker.Result result = UpdateChecker.check(this);
            runOnUiThread(() -> showUpdateResult(result));
        }, "dsha-module-update-check").start();
    }

    private void showUpdateResult(UpdateChecker.Result result) {
        if (result.error != null) {
            new AlertDialog.Builder(this)
                    .setTitle("检查失败")
                    .setMessage("当前：" + result.current + "\n" + result.error
                            + "\n\n可以手动打开 Release 页面查看。")
                    .setPositiveButton("打开 Release", (d, w) -> openRelease())
                    .setNegativeButton("关闭", null)
                    .show();
            return;
        }
        String title = result.newer() ? "发现新版本 " + result.latest : "已是最新";
        String notes = result.notes == null ? "" : result.notes.trim();
        if (notes.length() > 1500) notes = notes.substring(0, 1500) + "…";
        String message = "当前：" + result.current + "\n最新：" + result.latest
                + (notes.isEmpty() ? "" : "\n\n" + notes);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("关闭", null);
        if (result.url != null && !result.url.isEmpty()) {
            builder.setPositiveButton("打开 Release",
                    (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.url))));
        }
        builder.show();
    }

    private void openRelease() {
        startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Ralsei1337/dsha-island/releases")));
    }

    private static ViewGroup.LayoutParams matchWrap() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
