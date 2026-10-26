package io.github.dsha.island.lite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal status/control page; no layout resources. */
public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 1;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("DSHA Island Lite");
        title.setTextSize(24);
        column.addView(title, matchWrap());

        TextView hint = new TextView(this);
        hint.setText("免 root 的实时通知胶囊：HyperOS 超级岛 / ColorOS 流体云 / OriginOS 原子通知 / HarmonyOS 实况窗，具体取决于系统是否支持 Android 16 Live Updates。\n"
                + "需要配合 DSHA 插件 dsh-island-lite 使用。");
        hint.setTextSize(14);
        hint.setPadding(0, pad / 2, 0, pad / 2);
        column.addView(hint, matchWrap());

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, pad / 2, 0, pad / 2);
        column.addView(status, matchWrap());

        column.addView(button("启动岛服务", v -> startIsland()), matchWrap());
        column.addView(button("停止岛服务", v -> {
            stopService(new Intent(this, IslandService.class));
            updateStatus();
        }), matchWrap());
        column.addView(button("通知权限", v -> requestNotificationPermission()), matchWrap());
        column.addView(button("检查更新", v -> checkUpdate()), matchWrap());
        column.addView(button("切换 root 桥接模式", v -> toggleRootBridge()), matchWrap());
        column.addView(button("打开 DSHA", v -> openDsha()), matchWrap());

        TextView note = new TextView(this);
        note.setText("\n端口：127.0.0.1:8798（仅本机）\n"
                + "如果胶囊没出现：确认通知权限已开；省电策略设为“无限制”。\n"
                + "这是 AOSP promoted 通道，不包含官方大岛/岛上按钮。\n"
                + "检查更新只在你点按钮时访问 GitHub API，不后台轮询。");
        note.setTextSize(13);
        column.addView(note, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void startIsland() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission();
            return;
        }
        try {
            startForegroundService(new Intent(this, IslandService.class));
            Toast.makeText(this, "已启动，等待 DSHA 插件推送", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败：" + t, Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private void requestNotificationPermission() {
        if (hasNotificationPermission()) {
            Toast.makeText(this, "通知权限已开", Toast.LENGTH_SHORT).show();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    private boolean hasNotificationPermission() {
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        updateStatus();
        if (requestCode == REQUEST_NOTIFICATIONS && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startIsland();
        }
    }

    private void checkUpdate() {
        Toast.makeText(this, "正在检查 GitHub Release…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            UpdateChecker.Result result = UpdateChecker.check(this);
            runOnUiThread(() -> showUpdateResult(result));
        }, "dsha-update-check").start();
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
        String title = result.newer()
                ? "发现新版本 " + result.latest
                : "已是最新";
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

    private void toggleRootBridge() {
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        boolean next = !prefs.getBoolean("root_bridge", false);
        prefs.edit().putBoolean("root_bridge", next).apply();
        if (IslandService.RUNNING) {
            try {
                startForegroundService(new Intent(this, IslandService.class));
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, next ? "root 桥接已开：由 LSPosed 模块上岛" : "root 桥接已关：Lite 自己上岛",
                Toast.LENGTH_LONG).show();
        updateStatus();
    }

    private boolean rootBridge() {
        return getSharedPreferences("config", MODE_PRIVATE).getBoolean("root_bridge", false);
    }

    private void openDsha() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("dsh.android.client");
        if (launch == null) {
            Toast.makeText(this, "未安装 DSHA", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(launch);
    }

    private void updateStatus() {
        if (status == null) return;
        status.setText("通知权限：" + (hasNotificationPermission() ? "已开" : "未开")
                + "\n岛服务：" + (IslandService.RUNNING ? "运行中" : "未运行")
                + "\n桥接模式：" + (rootBridge() ? "开（供 root 模块读取）" : "关（Lite 自己上岛）")
                + "\n" + Platform.describe(this)
                + "\n" + Platform.capabilityReport(this)
                + "\n监听：127.0.0.1:8798");
    }

    private Button button(String label, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
    }

    private static ViewGroup.LayoutParams matchWrap() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
