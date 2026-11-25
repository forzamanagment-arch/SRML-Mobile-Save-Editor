package com.srmlmobile.saveeditor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE = 4201;
    private static final String SAVE_DIR = "/storage/emulated/0/Android/data/com.playdigious.slimerancher/files/pt_saves";
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status, output;
    private LinearLayout savesContainer;
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (code, result) -> runOnUiThread(() -> {
            if (code == REQUEST_CODE) {
                AppLog.i(this, "SHIZUKU", "Permission callback result=" + result);
                refreshStatus();
                if (result == PackageManager.PERMISSION_GRANTED) scanSaves();
                else show("Разрешение Shizuku не выдано.");
            }
        });
    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
        () -> runOnUiThread(this::refreshStatus);
    private final Shizuku.OnBinderDeadListener binderDeadListener =
        () -> runOnUiThread(this::refreshStatus);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppLog.i(this, "APP", "Started Android=" + android.os.Build.VERSION.SDK_INT
            + ", device=" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
            + ", version=" + BuildConfig.VERSION_NAME);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        buildUi();
        refreshStatus();
        if (hasPermission()) executor.execute(() -> { try { ShizukuFileAccess.ensureBackup(); } catch (Throwable t) { AppLog.exception(this, "BACKUP", "Backup directory check failed", t); } });
        else show("Подключи Shizuku и выдай разрешение. После этого приложение проверит папку Backup.");
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 30, 24, 20);
        root.setBackgroundColor(Color.rgb(8, 9, 12));

        TextView title = new TextView(this);
        title.setText("SRML Save Editor");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Выбор мира для редактирования");
        subtitle.setTextColor(Color.LTGRAY);
        root.addView(subtitle);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setPadding(0, 18, 0, 12);
        root.addView(status);

        addButton(root, "Подключить / проверить Shizuku", this::requestShizuku);
        addButton(root, "Обновить список миров", this::scanSaves);
        addButton(root, "Открыть редактор сохранения", () -> startActivity(new Intent(this, SaveEditorActivity.class)));

        TextView header = new TextView(this);
        header.setText("ТВОИ СОХРАНЕНИЯ");
        header.setTextColor(Color.WHITE);
        header.setTextSize(13);
        header.setPadding(0, 20, 0, 8);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        savesContainer = new LinearLayout(this);
        savesContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(savesContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        output = new TextView(this);
        output.setTextColor(Color.LTGRAY);
        output.setTextSize(13);
        output.setTextIsSelectable(true);
        output.setPadding(0, 12, 0, 8);
        savesContainer.addView(output);

        addButton(root, "Показать журнал диагностики", this::showLogs);
        addButton(root, "Поделиться журналом", this::shareLogs);

        TextView note = new TextView(this);
        note.setText("Сейчас доступно чтение и выбор мира. Оригинальные файлы не меняются. День появится после подтверждения его поля в формате сохранения.");
        note.setTextColor(Color.GRAY);
        note.setPadding(0, 8, 0, 0);
        root.addView(note);
        setContentView(root);
    }

    private void addButton(LinearLayout root, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> action.run());
        root.addView(button);
    }

    private void show(String message) { if (output != null) output.setText(message); }

    private boolean hasPermission() {
        try {
            return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            AppLog.exception(this, "SHIZUKU", "Permission check failed", t);
            return false;
        }
    }

    private void refreshStatus() {
        boolean binder = false, granted = false;
        try {
            binder = Shizuku.pingBinder();
            granted = binder && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { AppLog.exception(this, "SHIZUKU", "Status check failed", t); }
        if (status != null) status.setText("Shizuku: " + (binder
            ? (granted ? "подключён · доступ разрешён" : "подключён · нужно разрешение")
            : "не подключён"));
        AppLog.i(this, "SHIZUKU_STATUS", "binder=" + binder + ", permission=" + granted);
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                show("Shizuku не запущен. Запусти его и повтори проверку.");
                refreshStatus();
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                scanSaves();
            } else {
                Shizuku.requestPermission(REQUEST_CODE);
            }
        } catch (Throwable t) {
            AppLog.exception(this, "SHIZUKU", "Request failed", t);
            show("Ошибка Shizuku: " + t.getClass().getSimpleName());
        }
    }

    private Process startShell(String command) throws Exception {
        // Shizuku API 13.1.5 marks newProcess private; invoke that API method reflectively.
        java.lang.reflect.Method method = Shizuku.class.getDeclaredMethod(
            "newProcess", String[].class, String[].class, String.class);
        method.setAccessible(true);
        return (Process) method.invoke(null, new String[]{"sh", "-c", command}, null, null);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private byte[] readRemoteFile(String path) throws Exception {
        Process process = startShell("cat -- " + shellQuote(path));
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > MAX_FILE_BYTES) throw new IllegalStateException("Файл больше 64 МБ");
                out.write(buffer, 0, n);
            }
            int exit = process.waitFor();
            if (exit != 0) throw new IllegalStateException("cat exit=" + exit);
            return out.toByteArray();
        } finally { process.destroy(); }
    }

    private byte[] readLocalFile(File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > MAX_FILE_BYTES)
                    throw new IllegalStateException("Копия архива больше 64 МБ");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private void scanSaves() {
        if (!hasPermission()) {
            show("Сначала подключи Shizuku и выдай разрешение приложению.");
            refreshStatus();
            return;
        }
        show("Читаю pt_saves. Это может занять несколько секунд…");
        AppLog.i(this, "SAVE_SCAN", "Reading save directory " + SAVE_DIR);
        executor.execute(() -> {
            try {
                String command = "if [ -d " + shellQuote(SAVE_DIR) + " ] && [ -r "
                    + shellQuote(SAVE_DIR) + " ]; then find " + shellQuote(SAVE_DIR)
                    + " -maxdepth 1 -type f -print 2>/dev/null; else echo __NO_ACCESS__; fi";
                Process process = startShell(command);
                String listing;
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = process.getInputStream().read(buffer)) != -1) out.write(buffer, 0, n);
                    int exit = process.waitFor();
                    listing = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    if (exit != 0) throw new IllegalStateException("find exit=" + exit);
                } finally { process.destroy(); }

                if (listing.contains("__NO_ACCESS__"))
                    throw new IllegalStateException("pt_saves не найдена или недоступна через Shizuku");

                List<SaveSlot> found = new ArrayList<>();
                File tempDir = new File(getCacheDir(), "save_scan_tmp");
                if (!tempDir.exists() && !tempDir.mkdirs())
                    throw new IllegalStateException("Не удалось создать временную папку сканирования");
                int index = 0;
                try {
                    for (String path : listing.split("\\r?\\n")) {
                        String sourcePath = path.trim();
                        if (sourcePath.isEmpty()) continue;
                        String originalName = new File(sourcePath).getName();
                        File temporaryZip = new File(tempDir, originalName + ".zip");
                        index++;
                        try {
                            // The game stores ZIP archives without an extension. Copy each
                            // source file into app cache with .zip appended; never rename originals.
                            byte[] sourceBytes = readRemoteFile(sourcePath);
                            try (FileOutputStream fileOut = new FileOutputStream(temporaryZip)) {
                                fileOut.write(sourceBytes);
                                fileOut.getFD().sync();
                            }
                            byte[] copiedBytes = readLocalFile(temporaryZip);
                            AppLog.i(this, "SAVE_SCAN", "Read sizes for " + sourcePath
                                + ": remote=" + sourceBytes.length + " cache=" + copiedBytes.length);
                            AppLog.i(this, "SAVE_SCAN", "Archive probe " + sourcePath + ": "
                                + SaveSlotReader.diagnosticSummary(copiedBytes));
                            SaveSlot slot = SaveSlotReader.inspect(sourcePath, copiedBytes);
                            if (slot != null) {
                                found.add(slot);
                                AppLog.i(this, "SAVE_SCAN", "Recognized save archive " + sourcePath);
                            } else {
                                AppLog.w(this, "SAVE_SCAN", "Archive was read, but no recognized SRGAME payload was accepted in " + sourcePath + "; check the Archive probe fields savEntries and savPayloadSignature.");
                            }
                        } catch (Throwable e) {
                            AppLog.w(this, "SAVE_SCAN", "Skipped " + sourcePath + ": " + e);
                        } finally {
                            if (temporaryZip.exists() && !temporaryZip.delete())
                                AppLog.w(this, "SAVE_SCAN", "Could not delete temporary copy " + temporaryZip.getName());
                        }
                    }
                } finally {
                    File[] leftovers = tempDir.listFiles();
                    if (leftovers != null) for (File file : leftovers) file.delete();
                    tempDir.delete();
                }
                runOnUiThread(() -> renderSaves(found));
            } catch (Throwable t) {
                AppLog.exception(this, "SAVE_SCAN", "Save scan failed", t);
                runOnUiThread(() -> show("Не удалось прочитать pt_saves: " + t.getMessage()
                    + "\nПроверь Shizuku и наличие сохранений в игре."));
            }
        });
    }

    private void renderSaves(List<SaveSlot> found) {
        savesContainer.removeAllViews();
        if (found.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Миры не найдены. Возможно, папка пуста или формат файла ещё не распознан.");
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, 12, 0, 12);
            savesContainer.addView(empty);
            show("Проверка завершена: подходящих файлов сохранений нет.");
            AppLog.w(this, "SAVE_SCAN", "No save slots detected");
            return;
        }
        for (SaveSlot slot : found) {
            Button item = new Button(this);
            item.setAllCaps(false);
            item.setText(slot.displayName + "\n" + slot.fileName + " · " + slot.sizeText);
            item.setOnClickListener(v -> beginEditing(slot));
            savesContainer.addView(item);
        }
        show("Найдено миров: " + found.size() + ". Нажми на нужный мир, чтобы выбрать его.");
        AppLog.i(this, "SAVE_SCAN", "Found " + found.size() + " save slots");
    }

    private void beginEditing(SaveSlot slot) {
        if (!hasPermission()) { show("Нет разрешения Shizuku. Оригинал не затронут."); requestShizuku(); return; }
        show("Создаю и проверяю резервную копию…");
        executor.execute(() -> {
            try {
                String path = slot.fileName.startsWith("/") ? slot.fileName : SAVE_DIR + "/" + slot.fileName;
                ShizukuFileAccess.Stage stage = ShizukuFileAccess.backupStage(this, path);
                AppLog.i(this, "SAVE_EDIT", "Backup=" + stage.backup + "; staged=" + stage.file);
                runOnUiThread(() -> {
                    Intent intent = new Intent(this, SaveEditorActivity.class);
                    intent.putExtra("stage_path", stage.file.getAbsolutePath());
                    intent.putExtra("target_path", stage.target);
                    intent.putExtra("backup_path", stage.backup);
                    startActivity(intent);
                });
            } catch (Throwable t) {
                AppLog.exception(this, "SAVE_EDIT", "Could not stage save", t);
                runOnUiThread(() -> show("Не удалось подготовить сохранение: " + t.getMessage() + "\nОригинал не удаляется, если Backup не прошёл проверку."));
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
        AppLog.i(this, "LOG_EXPORT", "Share sheet opened");
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
