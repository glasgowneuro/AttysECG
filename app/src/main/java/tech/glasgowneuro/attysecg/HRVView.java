package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.ListIterator;

import uk.me.berndporr.iirj.Butterworth;

public class HRVView extends View {
    final String TAG = "HRVView";

    private final int MAXSAMPLES = 400, INNERCIRCLEWIDTH = 40;
    private float heartRate = 60, maxHR = 100, minHR = 40;
    private float txtSizeMult = (float) 1.4;
    private ArrayList<Float> HRVValues = null;
    private int[] ringsColours;
    private float[] ringsStops;
    private int alphas[];
    private float maxCircleRadius;
    private final float HRVDecayConst = 1.0F;

    private Paint paintClear = null;
    private Paint paintWhite = null;
    private Paint paintBlack = null;
    private Paint paintTxt = null;
    private Paint paintRings = null;

    Butterworth smoothFilter = null;

    public HRVView(Context context) {
        super(context);
        init();
    }

    public HRVView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HRVView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        paintWhite = new Paint();
        paintWhite.setColor(Color.WHITE);
        paintBlack = new Paint();
        paintBlack.setColor(Color.BLACK);
        paintClear = new Paint();
        paintClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        HRVValues = new ArrayList<>();
        paintTxt = new Paint();
        paintTxt.setColor(Color.argb(255, 255, 255, 0));
        paintRings = new Paint();
        HRVValues = new ArrayList<>();

        maxCircleRadius = Math.min(getHeight(), getWidth()) / 2F;
        maxCircleRadius = 1000F;

        ringsColours = new int[MAXSAMPLES];
        ringsStops = new float[MAXSAMPLES];
        alphas = new int[MAXSAMPLES];

        for (int i = 0; i < MAXSAMPLES; i++) {
            ringsStops[i] = i / (float) MAXSAMPLES;
            alphas[i] = (int) (220F - (220F * ringsStops[i] * ringsStops[i]));
            HRVValues.add(heartRate);
        }
        smoothFilter = new Butterworth();
        smoothFilter.lowPass(2, 1, 0.05);

    }

    public void reset() {
        if ((heartRate > 30) && (heartRate<200)) {
            maxHR = heartRate + 10;
            minHR = heartRate - 10;
        } else {
            heartRate = 60;
            maxHR = 100;
            minHR = 40;
        }
        init();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        canvas.drawPaint(paintClear);

        String hrvTxt = String.format("%d", (int) heartRate);
        int centreX = getWidth() / 2;
        int centreY = getHeight() / 2;
        paintTxt.setTextSize((float) INNERCIRCLEWIDTH * txtSizeMult);
        Rect bounds = new Rect();
        paintTxt.getTextBounds(hrvTxt, 0, hrvTxt.length(), bounds);

        int i = MAXSAMPLES - 1;
        for(ListIterator li = HRVValues.listIterator();li.hasNext();) {
            float hr = (float) li.next();
            hr = (float) smoothFilter.filter(hr);
            ringsColours[i] = heartRateToColour(hr, i);
            i--;
        }
        paintRings.setShader(new RadialGradient(
                centreX,
                centreY,
                maxCircleRadius,
                ringsColours,
                ringsStops,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(centreX, centreY, maxCircleRadius, paintRings);
        canvas.drawText(hrvTxt,
                centreX - bounds.width() / 2,
                centreY + bounds.height() / 2,
                paintTxt);
    }


    private int heartRateToColour(final float _HR, final int index) {

        // normalise HR
        float HR = 5F + 250F * (_HR - minHR) / (maxHR - minHR);
        HR = Math.max(HR, 0F);
        HR = Math.min(HR, 255F);

        return Color.argb(alphas[index], (int) (HR / 1.8), (int) (HR / 1.5), (int) (HR / 1.01));
    }

    public synchronized void animate(float _heartRate, float samplingRate) {
        heartRate = _heartRate;
        if (_heartRate>30) {
            HRVValues.add(_heartRate);
        }

        if (HRVValues.size() > MAXSAMPLES) {
            HRVValues.remove(0);
        }

        float maxTmp = 0;
        float minTmp = 1000;
        for(ListIterator li = HRVValues.listIterator();li.hasNext();) {
            float hr = (float) li.next();
            if (hr > maxTmp) maxTmp = hr;
            if (hr < minTmp) minTmp = hr;
        }

        maxHR = maxHR + HRVDecayConst * (maxTmp-maxHR) / samplingRate;
        minHR = minHR + HRVDecayConst * (minTmp-minHR) / samplingRate;

        invalidate();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "minHR: " + minHR + " maxHR: " + maxHR);
        }
    }


}
