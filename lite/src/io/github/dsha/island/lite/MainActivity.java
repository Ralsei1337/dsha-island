package io.github.dsha.island.lite;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal status/control page; no layout resources, no network permissions. */
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
        hint.setText("免 root 的超级岛胶囊：只显示 DSHA 图标 + 一行状态。\n"
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
        column.addView(button("打开 DSHA", v -> openDsha()), matchWrap());

        TextView note = new TextView(this);
        note.setText("\n端口：127.0.0.1:8798（仅本机）\n"
                + "如果胶囊没出现：确认通知权限已开；省电策略设为“无限制”。\n"
                + "这是 AOSP promoted 通道，不包含官方大岛/岛上按钮。");
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
