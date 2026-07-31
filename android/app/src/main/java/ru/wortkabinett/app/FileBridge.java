package ru.wortkabinett.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Мост между страницей и файловой системой: в WebView обычное скачивание
 * blob-ссылки не работает, поэтому готовый файл передаётся сюда в base64
 * и сохраняется в общую папку «Загрузки».
 */
public class FileBridge {

    private final Context context;

    FileBridge(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * @return сообщение для показа пользователю — куда лёг файл или что помешало.
     */
    @JavascriptInterface
    public String saveFile(String fileName, String base64, String mimeType) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            String safeName = sanitize(fileName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return saveViaMediaStore(safeName, mimeType, data);
            }
            return saveToPublicDownloads(safeName, data);
        } catch (Exception e) {
            return "Не удалось сохранить файл: " + e.getMessage();
        }
    }

    private String saveViaMediaStore(String fileName, String mimeType, byte[] data) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
        values.put(MediaStore.Downloads.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        Uri item = resolver.insert(collection, values);
        if (item == null) return "Система не выделила место для файла";

        try (OutputStream out = resolver.openOutputStream(item)) {
            if (out == null) return "Не удалось открыть файл для записи";
            out.write(data);
        }

        values.clear();
        values.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(item, values, null, null);

        return "Сохранено в «Загрузки»: " + fileName;
    }

    private String saveToPublicDownloads(String fileName, byte[] data) throws Exception {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) {
            return "Папка «Загрузки» недоступна";
        }
        File target = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(data);
        }
        return "Сохранено в «Загрузки»: " + fileName;
    }

    /** Имя файла приходит из пользовательского названия списка — чистим его. */
    private String sanitize(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", " ").trim();
        if (cleaned.isEmpty()) cleaned = "wortliste";
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
