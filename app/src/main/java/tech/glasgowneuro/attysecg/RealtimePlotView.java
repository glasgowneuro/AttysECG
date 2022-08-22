package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Plots the data on a surface view. Optimised for speed.
 */
public class RealtimePlotView extends Surface {

    static private int xpos = 0;
    static private int nLeft = 0;
    static private int nMaxChannels = 0;
    static private float[][] ypos = null;
    static Paint paint = new Paint();
    static Paint paintBlack = new Paint();
    static Paint paintXCoord = new Paint();
    static Paint paintYCoord = new Paint();
    static Paint paintLabel = new Paint();
    static Canvas canvas = null;
    static private final int gap = 10;
    static private final int xtic = 250;
    static private float[] yZero = null;
    static private float yHeight = 0;
    static private final String TAG = "RealtimePlotView";

    int getWidth() {
        return 500;
    }

    int getHeight() {
        return 500;
    }

    public RealtimePlotView(SurfaceTexture surfaceTexture) {
        super(surfaceTexture);
    }

    public interface TouchEventListener {
        void touchedChannel(int chNo);
    }

    public void registerTouchEventListener(TouchEventListener t) {
    }

    public void init() {
        xpos = 0;
        paint.setColor(Color.WHITE);
        paintBlack.setColor(Color.BLACK);
        paintXCoord.setColor(Color.argb(128, 0, 255, 0));
        paintYCoord.setColor(Color.argb(128, 0, 128, 0));
        paintLabel.setColor(Color.argb(128, 0, 255, 0));
    }

    public void resetX() {
        xpos = 0;
    }

    public void setMaxChannels(int n) {
        nMaxChannels = n;
        yZero = new float[n];
        for (int i = 0; i < n; i++) {
            yZero[i] = -1;
        }
    }

    private void initYpos(int width) {
        ypos = new float[nMaxChannels][width * 5 + gap * 2];
        xpos = 0;
    }

    public synchronized boolean startAddSamples(int n) {
        if (canvas != null) {
            //Log.d(TAG,"Canvas still locked.");
            return false;
        }
        int width = getWidth();
        nLeft = n;
        int xr = xpos + n + gap;
        if (xr > (width - 1)) {
            xr = width - 1;
        }
        Rect rect = new Rect(xpos, 0, xr, getHeight());
        try {
            canvas = lockCanvas(rect);
        } catch (java.lang.IllegalStateException e) {
            Log.d(TAG, "Surface couldn't be locked", e);
            canvas = null;
        }
        return true;
    }

    public synchronized void stopAddSamples() {
        if (canvas != null) {
            try {
                unlockCanvasAndPost(canvas);
            } catch (Exception ignored) {
            }
            canvas = null;
        }
    }

    public synchronized void addSamples(float[] newData,
                                        float[] minV, float[] maxV, float[] ytick,
                                        String[] label,
                                        int ygap) {
        int width = getWidth();
        int height = getHeight() - ygap;

        int nCh = newData.length;
        if (nCh == 0) return;

        float base = (float) height / nCh;
        yHeight = base / 2;

        if (ypos == null) initYpos(width);

        if (nMaxChannels == 0) return;
        if (isValid()) {
            Rect rect = new Rect(xpos, 0, xpos + gap, height + ygap);
            if (canvas != null) {
                paintLabel.setTextSize((float) canvas.getHeight() / 30);
                canvas.drawRect(rect, paintBlack);
                for (int i = 0; i < nCh; i++) {
                    float dy = base / (maxV[i] - minV[i]);
                    yZero[i] = ygap + base * (i + 1) - ((0 - minV[i]) * dy);
                    float yTmp = base * (i + 1) - ((newData[i] - minV[i]) * dy);
                    ypos[i][xpos + 1] = yTmp;
                    canvas.drawPoint(xpos, yZero[i], paintXCoord);
                    float yTmpTicTicPosBorder = base * (i + 1) - ((maxV[i] - minV[i]) * dy);
                    float yTmpTicPos;
                    float yTmpTicNeg;
                    int ticCtr = 1;
                    boolean doCoord;
                    if ((xpos % 2) == 0) {
                        do {
                            yTmpTicPos = base * (i + 1) - ((ytick[i] * ticCtr - minV[i]) * dy);
                            yTmpTicNeg = base * (i + 1) - ((-ytick[i] * ticCtr - minV[i]) * dy);
                            doCoord = yTmpTicPos > yTmpTicTicPosBorder;
                            if (doCoord) {
                                canvas.drawPoint(xpos, yTmpTicPos + ygap, paintXCoord);
                                canvas.drawPoint(xpos, yTmpTicNeg + ygap, paintXCoord);
                            }
                            ticCtr++;
                        } while (doCoord);
                    }
                    if ((xpos % xtic) == 0) {
                        canvas.drawLine(xpos, ygap, xpos, height + ygap, paintYCoord);
                    }
                    canvas.drawLine(xpos, ypos[i][xpos] + ygap, xpos + 1, ypos[i][xpos + 1] + ygap, paint);
                    canvas.drawText(label[i], 0F, yZero[i] - 1, paintLabel);
                }
            }
            xpos++;
            nLeft--;
            //Log.d(TAG,"xpos="+xpos);
            if (xpos >= (width - 1)) {
                xpos = 0;
                if (canvas != null) {
                    unlockCanvasAndPost(canvas);
                    canvas = null;
                }
                rect = new Rect(xpos, 0, nLeft + gap, getHeight());
                canvas = lockCanvas(rect);
            }
        }
    }

}
