package io.github.dsha.island.lite;

import android.content.Context;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Checks the public GitHub release. No auto-download, no background polling. */
final class UpdateChecker {
    private static final String LATEST =
            "https://api.github.com/repos/Ralsei1337/dsha-island/releases/latest";

    private UpdateChecker() {}

    static final class Result {
        final String current;
        final String latest;
        final String url;
        final String notes;
        final String error;

        Result(String current, String latest, String url, String notes, String error) {
            this.current = current;
            this.latest = latest;
            this.url = url;
            this.notes = notes;
            this.error = error;
        }

        boolean newer() {
            return error == null && compare(latest, current) > 0;
        }
    }

    static Result check(Context context) {
        String current = versionName(context);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(LATEST).openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("User-Agent", "DSHA-Island-Lite");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int code = connection.getResponseCode();
            if (code != 200) {
                Result fallback = checkRedirect(current);
                return fallback != null ? fallback
                        : new Result(current, current, null, null, "GitHub HTTP " + code);
            }
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
            in.close();
            String body = buffer.toString(StandardCharsets.UTF_8.name());
            JSONObject json = new JSONObject(body);
            String tag = json.optString("tag_name", "");
            String url = json.optString("html_url", "");
            String notes = json.optString("body", "");
            if (tag.isEmpty()) {
                return new Result(current, current, null, null, "Release 没有 tag_name");
            }
            return new Result(current, tag, url, notes, null);
        } catch (Throwable t) {
            Result fallback = checkRedirect(current);
            return fallback != null ? fallback
                    : new Result(current, current, null, null,
                    t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** API rate limits are common on shared IPs; the release page redirect has none. */
    private static Result checkRedirect(String current) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    "https://github.com/Ralsei1337/dsha-island/releases/latest").openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("User-Agent", "DSHA-Island-Lite");
            int code = connection.getResponseCode();
            String url = connection.getURL().toString();
            int tag = url.indexOf("/tag/");
            if (code == 200 && tag >= 0) {
                String latest = url.substring(tag + 5);
                int slash = latest.indexOf('/');
                if (slash >= 0) latest = latest.substring(0, slash);
                if (!latest.isEmpty()) {
                    return new Result(current, latest, url, "", null);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    static String versionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "?";
        }
    }

    /** Returns >0 when {@code left} is newer than {@code right}. */
    static int compare(String left, String right) {
        int[] a = parse(left);
        int[] b = parse(right);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i] ? 1 : -1;
        }
        return 0;
    }

    private static int[] parse(String version) {
        int[] out = new int[3];
        if (version == null) return out;
        String clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) clean = clean.substring(1);
        String[] parts = clean.split("[^0-9]+");
        int index = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                out[index++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                out[index - 1] = 0;
            }
            if (index == 3) break;
        }
        return out;
    }
}
