package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.content.Intent;
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
                AppLog.i(this, "SHIZUKU", "Permission callback result=" + grantResult);
                refreshStatus();
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) scanSaves();
                else show("Shizuku разрешение не выдано. Подробности записаны в журнал.");
            }
        });

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        AppLog.i(this, "SHIZUKU", "Binder received");
        runOnUiThread(this::refreshStatus);
    };
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        AppLog.e(this, "SHIZUKU", "Binder died / Shizuku disconnected");
        runOnUiThread(this::refreshStatus);
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppLog.i(this, "APP", "Application started. Android=" + android.os.Build.VERSION.SDK_INT
            + ", device=" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            + ", version=" + BuildConfig.VERSION_NAME);
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
        subtitle.setText("Диагностическая альфа · безопасный режим");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(14);
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setPadding(0, 24, 0, 16);
        root.addView(status);

        addButton(root, "Подключить / проверить Shizuku", this::requestShizuku);
        addButton(root, "Проверить папку сохранений", this::scanSaves);
        addButton(root, "Показать журнал диагностики", this::showLogs);
        addButton(root, "Поделиться журналом", this::shareLogs);

        TextView note = new TextView(this);
        note.setText("Пока доступен только диагностический режим. Оригинальные сохранения не изменяются. Журнал хранится внутри приложения.");
        note.setTextColor(Color.GRAY);
        note.setPadding(0, 12, 0, 12);
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        output = new TextView(this);
        output.setTextColor(Color.WHITE);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setPadding(0, 8, 0, 8);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        root.addView(button);
    }

    private void show(String message) {
        if (output != null) output.setText(message);
    }

    private void refreshStatus() {
        boolean binder = false;
        boolean granted = false;
        try {
            binder = Shizuku.pingBinder();
            granted = binder && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            AppLog.exception(this, "SHIZUKU_STATUS", "Failed to query Shizuku state", t);
        }
        status.setText("Shizuku: " + (binder ? (granted ? "подключён · разрешение выдано" : "подключён · нужно разрешение") : "не подключён"));
        AppLog.i(this, "SHIZUKU_STATUS", "binder=" + binder + ", permissionGranted=" + granted);
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                AppLog.e(this, "SHIZUKU", "Cannot request permission: Binder is not alive. Is Shizuku running?");
                show("Shizuku не подключён. Запусти Shizuku, затем повтори проверку. Событие записано в журнал.");
                refreshStatus();
                return;
            }
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                AppLog.i(this, "SHIZUKU", "Permission already granted");
                scanSaves();
            } else {
                AppLog.i(this, "SHIZUKU", "Requesting Shizuku permission");
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            AppLog.exception(this, "SHIZUKU", "Unexpected error while requesting permission", t);
            show("Ошибка Shizuku: " + t.getClass().getSimpleName() + ". Открой журнал диагностики.");
        }
    }

    private void scanSaves() {
        try {
            if (!Shizuku.pingBinder()) {
                AppLog.e(this, "SAVE_SCAN", "Blocked: Shizuku Binder is not alive");
                show("Shizuku не подключён. Проверка папки отменена.");
                refreshStatus();
                return;
            }
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                AppLog.e(this, "SAVE_SCAN", "Blocked: Shizuku permission not granted");
                show("Нет разрешения Shizuku. Сначала подключи его и выдай разрешение.");
                return;
            }
        } catch (Throwable t) {
            AppLog.exception(this, "SAVE_SCAN", "Unable to verify Shizuku state", t);
            show("Не удалось проверить Shizuku. Открой журнал.");
            return;
        }

        show("Проверяю путь и доступ…");
        AppLog.i(this, "SAVE_SCAN", "Starting read-only scan. target=" + SAVE_DIR);
        executor.execute(() -> {
            Process process = null;
            StringBuilder result = new StringBuilder();
            try {
                String command = "P='" + SAVE_DIR + "'; " +
                    "if [ ! -e \"$P\" ]; then echo 'RESULT=NOT_FOUND_OR_HIDDEN'; exit 21; " +
                    "elif [ ! -d \"$P\" ]; then echo 'RESULT=NOT_A_DIRECTORY'; exit 22; " +
                    "elif [ ! -r \"$P\" ] || [ ! -x \"$P\" ]; then echo 'RESULT=NO_READ_OR_TRAVERSE_PERMISSION'; exit 23; " +
                    "else echo 'RESULT=DIRECTORY_ACCESSIBLE'; ls -la \"$P\"; fi";
                AppLog.i(this, "SAVE_SCAN", "Executing read-only directory diagnostic through Shizuku");
                process = Shizuku.newProcess(new String[]{"sh", "-c", command}, null, null);
                process.getOutputStream().close();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) result.append(line).append('\n');
                int exit = process.waitFor();
                AppLog.i(this, "SAVE_SCAN", "Shell exitCode=" + exit + ", output=" + result.toString().trim());
                if (exit == 0) {
                    if (result.toString().contains("total 0") || result.toString().contains("RESULT=DIRECTORY_ACCESSIBLE\n")) {
                        AppLog.i(this, "SAVE_SCAN", "Directory check completed. Verify listing above; empty folder is possible.");
                    }
                } else {
                    AppLog.e(this, "SAVE_SCAN", "Directory check failed with diagnostic exitCode=" + exit);
                }
                String display = "Путь: " + SAVE_DIR + "\nКод завершения: " + exit + "\n\n" +
                    (result.length() == 0 ? "Команда не вернула вывод." : result.toString());
                runOnUiThread(() -> show(display));
            } catch (Throwable t) {
                AppLog.exception(this, "SAVE_SCAN", "Failed to execute/read Shizuku process", t);
                String message = "Ошибка проверки: " + t.getClass().getSimpleName() + ": " + t.getMessage()
                    + "\nПодробности записаны в журнал.";
                runOnUiThread(() -> show(message));
            } finally {
                if (process != null) process.destroy();
            }
        });
    }

    private void showLogs() {
        String logs = AppLog.read(this);
        show(logs.length() == 0 ? "Журнал пока пуст." : logs);
    }

    private void shareLogs() {
        String logs = AppLog.read(this);
        if (logs.length() == 0) logs = "Журнал пуст.";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "SRML Save Editor diagnostic log");
        intent.putExtra(Intent.EXTRA_TEXT, logs);
        startActivity(Intent.createChooser(intent, "Отправить журнал"));
        AppLog.i(this, "LOG_EXPORT", "Diagnostic log share sheet opened");
    }

    @Override protected void onDestroy() {
        AppLog.i(this, "APP", "Activity destroyed");
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        executor.shutdownNow();
        super.onDestroy();
    }
}
