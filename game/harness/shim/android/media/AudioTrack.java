package android.media;

public class AudioTrack {
    public static final int MODE_STATIC = 0;

    public AudioTrack() { }

    public AudioTrack(int stream, int rate, int chan, int enc, int bytes, int mode) { }

    public static class Builder {
        public Builder setAudioAttributes(AudioAttributes a) { return this; }

        public Builder setAudioFormat(AudioFormat f) { return this; }

        public Builder setBufferSizeInBytes(int n) { return this; }

        public Builder setTransferMode(int m) { return this; }

        public AudioTrack build() { return new AudioTrack(); }
    }

    public int write(short[] data, int off, int len) { return len; }

    public void setLoopPoints(int a, int b, int c) { }

    public void setStereoVolume(float l, float r) { }

    public void play() { }

    public void pause() { }

    public void flush() { }

    public void release() { }
}
