package com.srmlmobile.saveeditor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class SaveSlot {
    final String fileName, displayName, sizeText;
    SaveSlot(String path, String displayName, int bytes) {
        int slash = path.lastIndexOf('/');
        this.fileName = slash >= 0 ? path.substring(slash + 1) : path;
        this.displayName = displayName;
        this.sizeText = String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    }
}

final class SaveSlotReader {
    private static final int MAX_PAYLOAD = 64 * 1024 * 1024;

    static SaveSlot inspect(String path, byte[] file) throws Exception {
        byte[] payload = findGamePayload(file);
        if (payload == null || !startsWith(payload, "SRGAME")) return null;
        String name = findLikelyWorldName(payload);
        if (name == null) {
            int slash = path.lastIndexOf('/');
            name = slash >= 0 ? path.substring(slash + 1) : path;
        }
        return new SaveSlot(path, name, file.length);
    }

    private static byte[] findGamePayload(byte[] file) throws Exception {
        if (startsWith(file, "SRGAME")) return file;
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName() == null
                    || !entry.getName().toLowerCase(Locale.ROOT).contains("gamesave-sav")) continue;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    if (out.size() + n > MAX_PAYLOAD) return null;
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } finally { zip.close(); }
        return null;
    }

    private static boolean startsWith(byte[] bytes, String marker) {
        byte[] expected = marker.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (bytes[i] != expected[i]) return false;
        return true;
    }

    // Read-only heuristic: observed saves contain a UTF-8 world label in the header.
    // It is displayed as a candidate, never used to rewrite save data.
    private static String findLikelyWorldName(byte[] data) {
        String header = new String(data, 0, Math.min(data.length, 768), StandardCharsets.UTF_8);
        String[] parts = header.split("[^\\p{L}\\p{N}_ -]+");
        for (String part : parts) {
            String value = part.trim();
            if (value.length() < 3 || value.length() > 32) continue;
            if (value.equals("SRGAME") || value.equals("SRGSUMM") || value.equals("SRV3")) continue;
            if (value.matches("[A-Z0-9_]+") || value.matches("[0-9.]+")) continue;
            if (value.matches(".*[\\p{L}].*")) return value;
        }
        return null;
    }

    private SaveSlotReader() {}
}
