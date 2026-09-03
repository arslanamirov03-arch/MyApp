package android.view;

public class MotionEvent {
    public static final int ACTION_DOWN = 0, ACTION_UP = 1, ACTION_MOVE = 2,
            ACTION_CANCEL = 3, ACTION_POINTER_DOWN = 5, ACTION_POINTER_UP = 6;

    private int action, index;
    private int[] ids = new int[0];
    private float[] xs = new float[0], ys = new float[0];

    /** Test factory — not part of the real API. */
    public static MotionEvent make(int action, int index, int[] ids, float[] xs, float[] ys) {
        MotionEvent e = new MotionEvent();
        e.action = action;
        e.index = index;
        e.ids = ids;
        e.xs = xs;
        e.ys = ys;
        return e;
    }

    public int getActionMasked() { return action; }

    public int getActionIndex() { return index; }

    public int getPointerCount() { return ids.length; }

    public int getPointerId(int i) { return ids[i]; }

    public float getX(int i) { return xs[i]; }

    public float getY(int i) { return ys[i]; }

    public float getX() { return xs.length > 0 ? xs[0] : 0; }

    public float getY() { return ys.length > 0 ? ys[0] : 0; }
}
