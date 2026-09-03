package android.media;

public class SoundPool {
    public SoundPool() { }

    public SoundPool(int max, int stream, int quality) { }

    public static class Builder {
        public Builder setMaxStreams(int n) { return this; }

        public Builder setAudioAttributes(AudioAttributes a) { return this; }

        public SoundPool build() { return new SoundPool(); }
    }

    public int load(String path, int priority) { return 1; }

    public int play(int id, float l, float r, int pri, int loop, float rate) { return 1; }

    public void release() { }
}
