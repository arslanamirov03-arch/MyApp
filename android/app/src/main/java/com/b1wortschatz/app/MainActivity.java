package com.b1wortschatz.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Thin native wrapper around the offline HTML tracker.
 * The whole app lives in assets/index.html; this Activity only provides:
 *   - a WebView with DOM storage on, so localStorage progress persists across restarts
 *   - export: intercepts the blob: download and writes the JSON to the Downloads folder
 *   - import: wires the page's <input type="file"> to the system file picker
 */
public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // <-- keeps localStorage progress persistent
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri url = request.getUrl();
                String scheme = url.getScheme();
                // Keep app pages inside the WebView; hand real web links to the system.
                if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, url));
                    } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view,
                                             ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(
                            Intent.createChooser(intent, "Выбрать файл прогресса"),
                            FILE_CHOOSER_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        // Bridge used to receive the exported JSON (as a data: URL) from the page.
        webView.addJavascriptInterface(new DownloadBridge(), "AndroidDownloader");

        // The page exports progress via a blob: URL + <a download>. A WebView can't
        // download blob: URLs itself, so read the blob in JS and hand the bytes back.
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url != null && url.startsWith("blob:")) {
                webView.evaluateJavascript(blobReaderJs(url), null);
            } else if (url != null && url.startsWith("data:")) {
                new DownloadBridge().saveBase64(url);
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private static String blobReaderJs(String blobUrl) {
        return "(function(){" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('GET','" + blobUrl + "',true);" +
                "xhr.responseType='blob';" +
                "xhr.onload=function(){if(this.status===200){" +
                "var r=new FileReader();" +
                "r.onloadend=function(){AndroidDownloader.saveBase64(r.result);};" +
                "r.readAsDataURL(this.response);}};" +
                "xhr.send();})();";
    }

    private class DownloadBridge {
        @JavascriptInterface
        public void saveBase64(String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                String b64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                String filename = "b1-progress-" + System.currentTimeMillis() + ".json";
                final String where = saveToDownloads(filename, bytes);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Сохранено: " + where, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Не удалось сохранить файл", Toast.LENGTH_SHORT).show());
            }
        }
    }

    /** Returns a human-readable location of the saved file. */
    private String saveToDownloads(String filename, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            cv.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri item = getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (item == null) throw new Exception("insert failed");
            try (OutputStream os = getContentResolver().openOutputStream(item)) {
                os.write(data);
            }
            cv.clear();
            cv.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(item, cv, null, null);
            return "Загрузки/" + filename;
        } else {
            // Pre-Android 10: write into the app's own external files dir (no permission needed).
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(data);
            }
            return f.getAbsolutePath();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                results = new Uri[]{ data.getData() };
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
