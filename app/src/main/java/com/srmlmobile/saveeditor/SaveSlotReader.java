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
        if (payload == null || !isGamePayload(payload)) return null;
        String name = findLikelyWorldName(payload);
        if (name == null) {
            int slash = path.lastIndexOf('/');
            name = slash >= 0 ? path.substring(slash + 1) : path;
        }
        return new SaveSlot(path, name, file.length);
    }

    private static byte[] findGamePayload(byte[] file) throws Exception {
        // Some game builds store the payload directly, others wrap it in a ZIP
        // archive whose filename has no .zip extension.
        if (isGamePayload(file)) return file;

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName() == null
                    || !entry.getName().toLowerCase(Locale.ROOT).endsWith("-sav")) {
                    zip.closeEntry();
                    continue;
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                boolean tooLarge = false;
                while ((n = zip.read(buffer)) != -1) {
                    if (out.size() + n > MAX_PAYLOAD) {
                        tooLarge = true;
                        break;
                    }
                    out.write(buffer, 0, n);
                }
                zip.closeEntry();

                if (!tooLarge) {
                    byte[] candidate = out.toByteArray();
                    if (isGamePayload(candidate)) return candidate;
                }
            }
        }
        return null;
    }

    static String diagnosticSummary(byte[] file) {
        StringBuilder result = new StringBuilder();
        result.append("bytes=").append(file.length).append(", first16=");
        int limit = Math.min(file.length, 16);
        for (int i = 0; i < limit; i++) {
            if (i > 0) result.append(' ');
            result.append(String.format(Locale.ROOT, "%02X", file[i] & 0xFF));
        }
        if (isGamePayload(file))
            return result.append(", source=raw, savPayloadSignature=recognized").toString();

        boolean zipHeader = file.length >= 4
            && file[0] == 'P' && file[1] == 'K' && file[2] == 3 && file[3] == 4;
        result.append(", zipLocalHeader=").append(zipHeader);
        int entries = 0;
        int savEntries = 0;
        boolean recognizedSav = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                boolean isSav = !entry.isDirectory() && entry.getName() != null
                    && entry.getName().toLowerCase(Locale.ROOT).endsWith("-sav");
                if (isSav) savEntries++;
                result.append(", entry[").append(entries).append("]=").append(entry.getName())
                    .append("(declaredSize=").append(entry.getSize()).append(", head=");
                int read = 0;
                while (read < 8) {
                    int n = zip.read(buffer, read, 8 - read);
                    if (n < 0) break;
                    read += n;
                }
                for (int i = 0; i < read; i++) {
                    if (i > 0) result.append(' ');
                    result.append(String.format(Locale.ROOT, "%02X", buffer[i] & 0xFF));
                }
                if (isSav && read >= 7 && (buffer[0] & 0xFF) == 0x06
                    && buffer[1] == 'S' && buffer[2] == 'R' && buffer[3] == 'G'
                    && buffer[4] == 'A' && buffer[5] == 'M' && buffer[6] == 'E') {
                    recognizedSav = true;
                }
                result.append(')');
                while (zip.read(buffer) != -1) { /* drain current entry */ }
                zip.closeEntry();
                if (entries >= 20) {
                    result.append(", entryListTruncated=true");
                    break;
                }
            }
        } catch (Throwable t) {
            result.append(", zipError=").append(t.getClass().getSimpleName())
                .append(':').append(t.getMessage());
        }
        result.append(", zipEntries=").append(entries)
            .append(", savEntries=").append(savEntries)
            .append(", savPayloadSignature=").append(recognizedSav ? "recognized" : "not-recognized");
        if (entries == 0) result.append(", note=no-readable-zip-entries");
        return result.toString();
    }

    // Save payloads use a one-byte type/length prefix before the ASCII signature.
    // Observed gamesave-sav begins: 06 53 52 47 41 4D 45 (\\x06 + "SRGAME").
    private static boolean isGamePayload(byte[] bytes) {
        if (bytes == null || bytes.length < 7 || (bytes[0] & 0xFF) != 0x06) return false;
        byte[] marker = "SRGAME".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < marker.length; i++) if (bytes[i + 1] != marker[i]) return false;
        return true;
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
