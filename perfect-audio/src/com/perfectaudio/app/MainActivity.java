package com.perfectaudio.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final int REQ_MEDIA = 11;
    private static final int REQ_BG = 12;

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setBackgroundColor(0xFFFAF9F5);
        web.addJavascriptInterface(new Bridge(), "PA");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    @Override
    public void onBackPressed() {
        // The web app decides: close its panels / go back to the library,
        // or let the system close the activity.
        web.evaluateJavascript("window.handleBack ? window.handleBack() : false", value -> {
            if (!"true".equals(value)) {
                moveTaskToBack(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void js(String code) {
        runOnUiThread(() -> {
            if (web != null) web.evaluateJavascript(code, null);
        });
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
    }

    private String queryDisplayName(Uri uri) {
        String name = "media";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.getString(idx) != null) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode != REQ_MEDIA && requestCode != REQ_BG) return;

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        final boolean isBg = requestCode == REQ_BG;
        js("window.onImportStart && window.onImportStart(" + uris.size() + ")");

        new Thread(() -> {
            JSONArray arr = new JSONArray();
            File dir = new File(getFilesDir(), isBg ? "bg" : "media");
            dir.mkdirs();
            for (Uri uri : uris) {
                try {
                    String name = queryDisplayName(uri);
                    String mime = getContentResolver().getType(uri);
                    if (mime == null) mime = "application/octet-stream";
                    String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    if (ext == null) {
                        int dot = name.lastIndexOf('.');
                        ext = dot >= 0 ? name.substring(dot + 1) : "bin";
                    }
                    File out = new File(dir, UUID.randomUUID().toString().substring(0, 8) + "." + ext);
                    try (InputStream in = getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("mime", mime);
                    o.put("path", "file://" + out.getAbsolutePath());
                    o.put("size", out.length());
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
            if (isBg) {
                String payload = arr.length() > 0 ? arr.opt(0).toString() : "null";
                js("window.onBackgroundPicked && window.onBackgroundPicked(" + payload + ")");
            } else {
                js("window.onMediaPicked && window.onMediaPicked(" + arr + ")");
            }
        }).start();
    }

    /** Copies an exported clip into the public Downloads folder (Android 10+). */
    private String saveToDownloads(File src) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, src.getName());
            cv.put(MediaStore.Downloads.MIME_TYPE,
                    src.getName().endsWith(".m4a") ? "audio/mp4" : "audio/wav");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri dest = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (dest == null) return null;
            try (InputStream in = new FileInputStream(src);
                 OutputStream os = getContentResolver().openOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(dest, cv, null, null);
            return Environment.DIRECTORY_DOWNLOADS;
        } catch (Exception e) {
            return null;
        }
    }

    class Bridge {

        @JavascriptInterface
        public void pickMedia() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            runOnUiThread(() -> startActivityForResult(i, REQ_MEDIA));
        }

        @JavascriptInterface
        public void pickBackground() {
            runOnUiThread(() -> {
                // System photo picker (gallery) returns originals at full quality;
                // fall back to the documents UI on devices without it.
                Intent picker = new Intent("android.provider.action.PICK_IMAGES");
                if (picker.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(picker, REQ_BG);
                    return;
                }
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                startActivityForResult(i, REQ_BG);
            });
        }

        @JavascriptInterface
        public void haptic(String type) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                long ms;
                int amp;
                if ("heavy".equals(type)) {
                    ms = 45;
                    amp = VibrationEffect.DEFAULT_AMPLITUDE;
                } else if ("medium".equals(type)) {
                    ms = 25;
                    amp = VibrationEffect.DEFAULT_AMPLITUDE;
                } else {
                    ms = 12;
                    amp = 90;
                }
                v.vibrate(VibrationEffect.createOneShot(ms, amp));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) return;
                v.vibrate(VibrationEffect.createOneShot(Math.max(1, Math.min(ms, 500)),
                        VibrationEffect.DEFAULT_AMPLITUDE));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public boolean deleteFile(String path) {
            try {
                String p = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
                File f = new File(p).getCanonicalFile();
                if (!f.getPath().startsWith(getFilesDir().getCanonicalPath())) return false;
                return f.delete();
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) {
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        /** Cuts [startSec, endSec] out of the track and saves it. */
        @JavascriptInterface
        public void exportClip(String path, double startSec, double endSec, String name) {
            new Thread(() -> {
                try {
                    String p = path.startsWith("file://") ? Uri.parse(path).getPath() : path;
                    File out = AudioExport.extract(p, (long) (startSec * 1000), (long) (endSec * 1000),
                            new File(getFilesDir(), "export"), name);
                    String savedTo = saveToDownloads(out);
                    JSONObject o = new JSONObject();
                    o.put("name", out.getName());
                    o.put("size", out.length());
                    o.put("inDownloads", savedTo != null);
                    js("window.onExportDone && window.onExportDone(" + o + ")");
                } catch (Throwable t) {
                    js("window.onExportFailed && window.onExportFailed('"
                            + esc(t.getMessage()) + "')");
                }
            }).start();
        }

        @JavascriptInterface
        public void shareExport(String fileName) {
            runOnUiThread(() -> {
                try {
                    File f = new File(new File(getFilesDir(), "export"), fileName);
                    if (!f.exists()) return;
                    Uri uri = ExportProvider.uriFor(f);
                    Intent send = new Intent(Intent.ACTION_SEND)
                            .setType(fileName.endsWith(".m4a") ? "audio/mp4" : "audio/wav")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Отправить отрезок"));
                } catch (Exception ignored) {
                }
            });
        }
    }
}
