package android.content;

import java.util.HashMap;

public class SharedPreferences {
    final HashMap<String, Object> map = new HashMap<>();

    public int getInt(String k, int def) {
        Object v = map.get(k);
        return v instanceof Integer ? (Integer) v : def;
    }

    public boolean getBoolean(String k, boolean def) {
        Object v = map.get(k);
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public Editor edit() { return new Editor(this); }

    public static class Editor {
        private final SharedPreferences p;

        Editor(SharedPreferences p) { this.p = p; }

        public Editor putInt(String k, int v) { p.map.put(k, v); return this; }

        public Editor putBoolean(String k, boolean v) { p.map.put(k, v); return this; }

        public void apply() { }

        public boolean commit() { return true; }
    }
}
