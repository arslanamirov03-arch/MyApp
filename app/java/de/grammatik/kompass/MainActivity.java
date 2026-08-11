package de.grammatik.kompass;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_EXPORT = 1;
    private static final int REQ_IMPORT = 2;
    private static final String PREFS = "gk_prefs";
    private static final String KEY_PROGRESS = "progress_json";

    private WebView webView;
    private String pendingExportJson = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#F0EEE6"));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(Color.parseColor("#F0EEE6"));
        getWindow().setNavigationBarColor(Color.parseColor("#F0EEE6"));
        int flags = getWindow().getDecorView().getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("handleBack()", new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"true".equals(value)) {
                    finish();
                }
            }
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void saveProgress(String json) {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            p.edit().putString(KEY_PROGRESS, json).apply();
        }

        @JavascriptInterface
        public String loadProgress() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRESS, null);
        }

        @JavascriptInterface
        public void exportProgress(String json) {
            pendingExportJson = json;
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "grammatik-kompass-progress.json");
            startActivityForResult(i, REQ_EXPORT);
        }

        @JavascriptInterface
        public void importProgress() {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT) {
            boolean ok = false;
            if (resultCode == Activity.RESULT_OK && data != null && pendingExportJson != null) {
                try {
                    Uri uri = data.getData();
                    OutputStream os = getContentResolver().openOutputStream(uri, "wt");
                    os.write(pendingExportJson.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    ok = true;
                } catch (Exception ignored) { }
            }
            pendingExportJson = null;
            final boolean result = ok;
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript("onProgressExported(" + result + ")", null);
                }
            });
        } else if (requestCode == REQ_IMPORT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    Uri uri = data.getData();
                    InputStream is = getContentResolver().openInputStream(uri);
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    final String json = sb.toString()
                            .replace("\\", "\\\\").replace("'", "\\'")
                            .replace("\r", "").replace("\n", "");
                    webView.post(new Runnable() {
                        @Override
                        public void run() {
                            webView.evaluateJavascript("onProgressImported('" + json + "')", null);
                        }
                    });
                } catch (Exception ignored) { }
            }
        }
    }
}
