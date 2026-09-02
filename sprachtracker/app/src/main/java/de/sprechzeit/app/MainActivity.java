package de.sprechzeit.app;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Single-activity shell. The whole tracker UI lives in assets/ and is rendered by the WebView;
 * this class only provides a launcher window, durable storage and haptic feedback.
 */
public class MainActivity extends Activity {

    private static final String STATE_FILE = "sprechzeit-state.json";

    private WebView web;
    private boolean webReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(0xFFFAF7F2);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100); // ignore system font scaling so the layout stays as designed

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webReady = true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "Native");
        web.loadUrl("file:///android_asset/index.html");

        setContentView(web);
        applySystemBarStyle();
    }

    private void applySystemBarStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onBackPressed() {
        if (!webReady) {
            super.onBackPressed();
            return;
        }
        // Let the page close an overlay or return to the main view first.
        web.evaluateJavascript("window.appHandleBack ? window.appHandleBack() : false",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (!"true".equals(value)) {
                            MainActivity.super.onBackPressed();
                        }
                    }
                });
    }

    /** Exposed to the page as `Native`. */
    public class Bridge {

        @JavascriptInterface
        public String load() {
            File f = new File(getFilesDir(), STATE_FILE);
            if (!f.exists()) {
                return "";
            }
            try (InputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public boolean save(String json) {
            if (json == null) {
                return false;
            }
            // Write to a temp file first so a crash mid-write cannot destroy existing progress.
            File tmp = new File(getFilesDir(), STATE_FILE + ".tmp");
            File dst = new File(getFilesDir(), STATE_FILE);
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            } catch (Exception e) {
                return false;
            }
            return tmp.renameTo(dst);
        }

        @JavascriptInterface
        public void haptic(int ms) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v == null || !v.hasVibrator()) {
                    return;
                }
                int duration = Math.max(1, Math.min(ms, 400));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(duration);
                }
            } catch (Exception ignored) {
                // Haptics are cosmetic; never let them break the flow.
            }
        }
    }
}
