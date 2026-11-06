package com.johnwong.danshoumode;

/** No Android dependencies: also exercised by the host-side geometry tests. */
public final class Geometry {
    private Geometry() {}
    public static float percentScale(int percent) {
        if(percent<40||percent>85)throw new IllegalArgumentException("缩放比例需在 40%–85% 之间");
        return percent/100f;
    }

    public static float[] inverseHandMatrix(float scale,int left,int top) {
        return new float[]{1/scale,0,-left/scale,0,1/scale,-top/scale,0,0,1};
    }
    /** Aspect-fill in the local coordinates of a shade or overview panel. */
    public static float[] wallpaperBounds(int imageW,int imageH,int panelW,int panelH) {
        float zoom=Math.max(panelW/(float)imageW,panelH/(float)imageH);
        float x=(panelW-imageW*zoom)*.5f, y=(panelH-imageH*zoom)*.5f;
        return new float[]{x,y,panelW-x,panelH-y};
    }

    public static int[] handFrame(int width,int height,float scale,boolean left) {
        if(width<320||height<=width||!Float.isFinite(scale)||scale<.4f||scale>.85f)throw new IllegalArgumentException("仅支持竖屏等比单手模式");
        int w=Math.round(width*scale),h=Math.round(height*scale);
        return new int[]{left?0:width-w,height-h,left?w:width,height};
    }
    public static boolean insideRounded(float x,float y,int[] r,float radius) {
        if(x<r[0]||x>=r[2]||y<r[1]||y>=r[3])return false;
        float rad=Math.min(radius,Math.min(r[2]-r[0],r[3]-r[1])/2f);
        float cx=Math.max(r[0]+rad,Math.min(r[2]-rad,x));
        float cy=Math.max(r[1]+rad,Math.min(r[3]-rad,y));
        return (x-cx)*(x-cx)+(y-cy)*(y-cy)<=rad*rad;
    }

    /** Result is [left, top, right, bottom] in post-rotation physical coordinates. */
    public static int[] fitLeftThird(int nativeWidth, int nativeHeight,
            int logicalWidth, int logicalHeight) {
        if (nativeWidth < 320 || nativeHeight <= nativeWidth ||
                logicalWidth <= 0 || logicalHeight <= 0) {
            throw new IllegalArgumentException("Unsupported display geometry");
        }
        int availableWidth = nativeHeight / 3;
        int availableHeight = nativeWidth;
        double scale = Math.min((double) availableWidth / logicalWidth,
                (double) availableHeight / logicalHeight);
        int width = Math.max(1, (int) Math.floor(logicalWidth * scale));
        int height = Math.max(1, (int) Math.floor(logicalHeight * scale));
        int top = (availableHeight - height) / 2;
        return new int[] {0, top, width, top + height};
    }
}
