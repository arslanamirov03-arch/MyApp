package android.os;

public class VibrationEffect {
    public static final int DEFAULT_AMPLITUDE = -1;

    public static VibrationEffect createOneShot(long ms, int amplitude) {
        return new VibrationEffect();
    }
}
