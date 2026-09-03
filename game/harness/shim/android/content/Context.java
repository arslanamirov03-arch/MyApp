package android.content;

import java.io.File;
import java.util.HashMap;

public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String VIBRATOR_SERVICE = "vibrator";

    private final HashMap<String, SharedPreferences> prefs = new HashMap<>();

    public SharedPreferences getSharedPreferences(String name, int mode) {
        SharedPreferences p = prefs.get(name);
        if (p == null) { p = new SharedPreferences(); prefs.put(name, p); }
        return p;
    }

    public File getCacheDir() {
        File f = new File(System.getProperty("java.io.tmpdir"), "bro-harness");
        f.mkdirs();
        return f;
    }

    public Object getSystemService(String name) { return null; }
}
