package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 4201;
    private static final String SAVE_DIR =
        "/storage/emulated/0/Android/data/com.playdigious.slimerancher/files/pt_saves";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView output;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, grantResult) -> runOnUiThread(() -> {
            if (requestCode == REQUEST_CODE) {
                refreshStatus();
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    scanSaves();
                }
            }
        });

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::refreshStatus;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::refreshStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        buildUi();
        refreshStatus();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 42, 28, 24);
        root.setBackgroundColor(Color.rgb(8, 9, 12));

        TextView title = new TextView(this);
        title.setText("SRML Save Editor");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Первая тестовая сборка · доступ к сохранениям");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(0, 28, 0, 18);
        root.addView(status);

        Button connect = new Button(this);
        connect.setText("Подключить Shizuku");
        connect.setOnClickListener(v -> requestShizuku());
        root.addView(connect);

        Button scan = new Button(this);
        scan.setText("Проверить папку сохранений");
        scan.setOnClickListener(v -> scanSaves());
        root.addView(scan);

        TextView note = new TextView(this);
        note.setText("На этом этапе приложение только проверяет доступ и показывает найденные файлы. Оригиналы не изменяются.");
        note.setTextColor(Color.GRAY);
        note.setPadding(0, 18, 0, 12);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void refreshStatus() {
        boolean binder = Shizuku.pingBinder();
        boolean granted = binder && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        status.setText("Shizuku: " + (binder ? (granted ? "подключён, разрешение выдано" : "подключён, нужно разрешение") : "не подключён"));
    }

    private void requestShizuku() {
        if (!Shizuku.pingBinder()) {
            output.setText("Сначала запусти Shizuku и подключи его способом, доступным на устройстве.");
            refreshStatus();
            return;
        }
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            scanSaves();
        } else {
            Shizuku.requestPermission(REQUEST_CODE);
        }
    }

    private void scanSaves() {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            output.setText("Доступ закрыт. Подключи Shizuku и выдай приложению разрешение.");
            refreshStatus();
            return;
        }
        output.setText("Проверяю доступ к папке сохранений…");
        executor.execute(() -> {
            StringBuilder result = new StringBuilder();
            Process process = null;
            try {
                process = Shizuku.newProcess(new String[]{"sh", "-c",
                    "if [ -d '" + SAVE_DIR + "' ]; then ls -la '" + SAVE_DIR + "'; else echo 'SAVE_DIRECTORY_NOT_FOUND_OR_NOT_ACCESSIBLE'; fi"},
                    null, null);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) result.append(line).append('\n');
                int code = process.waitFor();
                if (code != 0) result.append("\nКоманда завершилась с кодом ").append(code);
            } catch (Exception e) {
                result.append("Ошибка проверки: ").append(e.getClass().getSimpleName())
                    .append(": ").append(e.getMessage());
            } finally {
                if (process != null) process.destroy();
            }
            String text = result.length() == 0 ? "Папка пуста или нет доступа." : result.toString();
            runOnUiThread(() -> output.setText(text));
        });
    }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        executor.shutdownNow();
        super.onDestroy();
    }
}
