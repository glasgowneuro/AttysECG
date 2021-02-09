package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Plots the data on a surface view. Optimised for speed.
 */
public class RealtimePlotView extends SurfaceView implements SurfaceHolder.Callback {

    static private int xpos = 0;
    static private int nLeft = 0;
    static private SurfaceHolder holder = null;
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

    public interface TouchEventListener {
        void touchedChannel(int chNo);
    }

    static private TouchEventListener touchEventListener = null;

    public void registerTouchEventListener(TouchEventListener t) {
        touchEventListener = t;
    }

    public void init() {
        xpos = 0;
        holder = getHolder();
        paint.setColor(Color.WHITE);
        paintBlack.setColor(Color.BLACK);
        paintXCoord.setColor(Color.argb(128, 0, 255, 0));
        paintYCoord.setColor(Color.argb(128, 0, 128, 0));
        paintLabel.setColor(Color.argb(128, 0, 255, 0));
    }

    public void resetX() {
        xpos = 0;
    }

    public RealtimePlotView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public RealtimePlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RealtimePlotView(Context context) {
        super(context);
        init();
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

    public void surfaceDestroyed(SurfaceHolder holder) {
        //Log.d(TAG,"Surface destroyed");
    }

    public void surfaceCreated(SurfaceHolder holder) {
        setWillNotDraw(false);
        initYpos(getWidth());
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        initYpos(width);
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
        if (holder != null) {
            canvas = holder.lockCanvas(rect);
        } else {
            canvas = null;
        }
        return true;
    }

    public synchronized void stopAddSamples() {
        if (holder != null) {
            if (canvas != null) {
                try {
                    holder.unlockCanvasAndPost(canvas);
                } catch (Exception ignored) {
                }
                canvas = null;
            }
        }
    }


    public int getChannelIdFromY(int y) {
        for (int i = 0; i < nMaxChannels; i++) {
            if ((Math.abs(y - yZero[i])) < yHeight) {
                return i;
            }
        }
        return -1;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        float y = event.getY();
        int idx = getChannelIdFromY((int) y);
        if (idx != -1) {
            if (touchEventListener != null) {
                touchEventListener.touchedChannel(idx);
            }
        }
        return super.onTouchEvent(event);
    }


    public synchronized void addSamples(float[] newData,
                                        float[] minV, float[] maxV, float[] ytick,
                                        String[] label,
                                        int ygap) {
        int width = getWidth();
        int height = getHeight()-ygap;

        int nCh = newData.length;
        if (nCh == 0) return;

        float base = (float)height / nCh;
        yHeight = base / 2;

        if (ypos == null) initYpos(width);

        if (nMaxChannels == 0) return;
        Surface surface = holder.getSurface();
        if (surface.isValid()) {
            Rect rect = new Rect(xpos, 0, xpos + gap, height+ygap);
            if (canvas != null) {
                paintLabel.setTextSize((float)canvas.getHeight() / 30);
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
                                canvas.drawPoint(xpos, yTmpTicPos+ygap, paintXCoord);
                                canvas.drawPoint(xpos, yTmpTicNeg+ygap, paintXCoord);
                            }
                            ticCtr++;
                        } while (doCoord);
                    }
                    if ((xpos % xtic) == 0) {
                        canvas.drawLine(xpos, ygap, xpos, height+ygap, paintYCoord);
                    }
                    canvas.drawLine(xpos, ypos[i][xpos]+ygap, xpos + 1, ypos[i][xpos + 1]+ygap, paint);
                    canvas.drawText(label[i], 0F, yZero[i] - 1, paintLabel);
                }
            }
            xpos++;
            nLeft--;
            if (xpos >= (width - 1)) {
                xpos = 0;
                if (holder != null) {
                    if (canvas != null) {
                        holder.unlockCanvasAndPost(canvas);
                        canvas = null;
                    }
                }
                rect = new Rect(xpos, 0, nLeft + gap, getHeight());
                if (holder != null) {
                    canvas = holder.lockCanvas(rect);
                } else {
                    canvas = null;
                }
            }
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
    }

}
