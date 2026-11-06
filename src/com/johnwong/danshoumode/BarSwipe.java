package com.johnwong.danshoumode;

/** A single touchscreen stroke starting within the visible navigation strip. */
final class BarSwipe {
    private float x, y, threshold;
    private long started;
    private boolean tracking, claimed, exit, left;

    void begin(float startX, float startY, long time, int[] frame, int band, int slop, boolean active) {
        cancel();
        if (startX < frame[0] || startX >= frame[2] || startY < frame[3] - band || startY >= frame[3]) return;
        x = startX; y = startY; started = time; exit = active;
        left = startX < (frame[0] + frame[2]) / 2f;
        threshold = Math.min(slop, Math.max(2f, band * .35f));
        tracking = true;
    }
    boolean move(float currentX, float currentY, long time) {
        if (!tracking) return false;
        float dx = Math.abs(currentX - x);
        float dy = (currentY - y) * (exit ? -1 : 1);
        if (time - started > 500 || dy < -threshold || (dx >= threshold && dx > dy)) {
            cancel();
            return false;
        }
        if (claimed || dy < threshold) return false;
        claimed = true;
        return true;
    }
    int finish(float currentX, float currentY, long time) {
        boolean prior = claimed;
        move(currentX, currentY, time);
        int result = prior && tracking && (currentY-y)*(exit?-1:1) >= threshold
                ? (exit ? 3 : left ? 1 : 2) : 0;
        cancel();
        return result;
    }
    void cancel() { tracking = claimed = false; }
}
