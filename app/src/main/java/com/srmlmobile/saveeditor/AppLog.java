package com.srmlmobile.saveeditor;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AppLog {
    private static final String TAG = "SRMLSaveEditor";
    private static final long MAX_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();
    private AppLog() {}

    private static File logFile(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists() && !dir.mkdirs()) Log.e(TAG, "Cannot create log directory");
        return new File(dir, "diagnostic.log");
    }

    public static void i(Context c, String area, String message) { write(c, "INFO", area, message, null); }
    public static void w(Context c, String area, String message) { write(c, "WARN", area, message, null); }
    public static void e(Context c, String area, String message) { write(c, "ERROR", area, message, null); }

    public static void exception(Context c, String area, String message, Throwable error) {
        write(c, "ERROR", area, message, error);
    }

    private static void write(Context context, String level, String area, String message, Throwable error) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        StringBuilder entry = new StringBuilder()
            .append(time).append(" ").append(level).append("/").append(area)
            .append(": ").append(message == null ? "" : message);
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            entry.append("\n").append(sw);
        }
        entry.append("\n");
        String line = entry.toString();
        if ("ERROR".equals(level)) Log.e(TAG, line);
        else if ("WARN".equals(level)) Log.w(TAG, line);
        else Log.i(TAG, line);
        synchronized (LOCK) {
            try {
                File file = logFile(context);
                if (file.exists() && file.length() + line.length() > MAX_BYTES) {
                    File old = new File(file.getParentFile(), "diagnostic.previous.log");
                    if (old.exists()) old.delete();
                    if (!file.renameTo(old)) Log.w(TAG, "Could not rotate diagnostic log");
                }
                FileOutputStream out = new FileOutputStream(logFile(context), true);
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist diagnostic log", e);
            }
        }
    }

    public static String read(Context context) {
        synchronized (LOCK) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                File previous = new File(logFile(context).getParentFile(), "diagnostic.previous.log");
                if (previous.exists()) copy(previous, out);
                copy(logFile(context), out);
                byte[] bytes = out.toByteArray();
                String text = new String(bytes, StandardCharsets.UTF_8);
                int max = 30000;
                return text.length() > max ? "[Показана последняя часть журнала]\n" + text.substring(text.length() - max) : text;
            } catch (Exception e) {
                return "Не удалось прочитать журнал: " + e;
            }
        }
    }

    private static void copy(File file, ByteArrayOutputStream out) throws Exception {
        if (!file.exists()) return;
        FileInputStream in = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        in.close();
    }
}
