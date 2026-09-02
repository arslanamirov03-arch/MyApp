package com.arslan.wortliste;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Wortliste B2 — список слов с отметками «выучено».
 *
 * Прогресс пишется сразу в три независимых места (SharedPreferences,
 * state.json и state.bak.json), чтобы его нельзя было потерять из-за
 * одного сбоя. JS-сторона выбирает самую свежую копию по метке времени.
 *
 * Внутри намеренно нет анонимных классов и лямбд: d8 из build-tools 34
 * падает на таких класс-файлах, собранных JDK 21.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "wortliste_prefs";
    private static final String KEY_STATE = "state_json";

    private WebView web;
    private File primary;
    private File backup;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);

        primary = new File(getFilesDir(), "state.json");
        backup = new File(getFilesDir(), "state.bak.json");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.WHITE);
            getWindow().setNavigationBarColor(Color.WHITE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View d = getWindow().getDecorView();
            int f = d.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            d.setSystemUiVisibility(f);
        }

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setBackgroundColor(Color.parseColor("#FAF9F7"));
        web.addJavascriptInterface(new Bridge(this), "Native");

        // Клавиатура не должна ломать вёрстку списка.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onPause() {
        // Просим страницу дописать прогресс до того, как система нас свернёт.
        if (web != null) {
            try {
                web.evaluateJavascript("window.__flush && window.__flush()", null);
            } catch (Throwable ignored) {
            }
        }
        super.onPause();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("window.__handleBack ? window.__handleBack() : false",
                new BackCallback(this));
    }

    void backFallthrough() {
        super.onBackPressed();
    }

    /**
     * Ответ страницы на «назад»: "true" — она сама закрыла шторку или поиск.
     *
     * Интерфейс намеренно взят сырым, без {@code <String>}: d8 из build-tools 34
     * падает с NullPointerException на мосту, который javac генерирует для
     * параметризованного ValueCallback.
     */
    @SuppressWarnings("rawtypes")
    private static class BackCallback implements ValueCallback {
        private final MainActivity host;

        BackCallback(MainActivity host) {
            this.host = host;
        }

        @Override
        public void onReceiveValue(Object value) {
            if (!"true".equals(value)) host.backFallthrough();
        }
    }

    /* ---------------- файловые помощники ---------------- */

    private static String readFile(File f) {
        if (f == null || !f.exists()) return null;
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            String s = out.toString("UTF-8");
            return s.length() == 0 ? null : s;
        } catch (Throwable t) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean writeFile(File f, String data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            out.write(data.getBytes("UTF-8"));
            out.flush();
            out.getFD().sync();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /* ---------------- задачи для UI-потока ---------------- */

    private static class CopyTask implements Runnable {
        private final MainActivity host;
        private final String text;

        CopyTask(MainActivity host, String text) {
            this.host = host;
            this.text = text;
        }

        @Override
        public void run() {
            try {
                ClipboardManager cm =
                        (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Wortliste B2", text));
            } catch (Throwable e) {
                Toast.makeText(host, "Не удалось скопировать", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static class ShareTask implements Runnable {
        private final MainActivity host;
        private final String text;

        ShareTask(MainActivity host, String text) {
            this.host = host;
            this.text = text;
        }

        @Override
        public void run() {
            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, "Wortliste B2 — копия прогресса");
                i.putExtra(Intent.EXTRA_TEXT, text);
                host.startActivity(Intent.createChooser(i, "Куда сохранить копию"));
            } catch (Throwable e) {
                Toast.makeText(host, "Нет приложения для отправки", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /* ---------------- мост в JavaScript ---------------- */

    private static class Bridge {
        private final MainActivity host;

        Bridge(MainActivity host) {
            this.host = host;
        }

        @JavascriptInterface
        public void save(String json) {
            if (json == null || json.length() == 0) return;

            // Прежнюю версию сдвигаем в резерв, только потом переписываем основную.
            String old = readFile(host.primary);
            if (old != null && !old.equals(json)) writeFile(host.backup, old);

            writeFile(host.primary, json);

            try {
                SharedPreferences p = host.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                p.edit().putString(KEY_STATE, json).commit();
            } catch (Throwable ignored) {
            }
        }

        @JavascriptInterface
        public String loadPrimary() {
            return readFile(host.primary);
        }

        @JavascriptInterface
        public String loadBackup() {
            return readFile(host.backup);
        }

        @JavascriptInterface
        public String loadPrefs() {
            try {
                return host.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_STATE, null);
            } catch (Throwable t) {
                return null;
            }
        }

        @JavascriptInterface
        public void copy(String text) {
            if (text == null) return;
            host.runOnUiThread(new CopyTask(host, text));
        }

        @JavascriptInterface
        public void share(String text) {
            if (text == null) return;
            host.runOnUiThread(new ShareTask(host, text));
        }
    }
}
