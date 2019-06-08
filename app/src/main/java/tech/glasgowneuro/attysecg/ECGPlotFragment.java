package tech.glasgowneuro.attysecg;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import java.util.Locale;

/**
 * Plots one PQRST complex in detail
 */

public class ECGPlotFragment extends Fragment {

    String TAG = "ECGPlotFragment";

    private float range = 1;

    private SimpleXYSeries pqrstSeries = null;

    private XYPlot pqrstPlot = null;

    private PanZoom panZoom = null;

    private Spinner maxYspinner = null;

    private Spinner leadSpinner = null;
    private boolean offerAllChannels = true;

    private Button resetButton = null;

    private ToggleButton recordButton = null;

    private static String[] LEAD = {"I", "II", "III", "aVR", "aVL", "aVF"};

    private float[] leadsBuffer = new float[6];

    private static String[] MAXY = {"0.2 mV", "0.45 mV", "0.9 mV", "1.35 mV", "1.8 mV", "2.25 mV"};

    View view = null;

    // we start with Einthoven II
    private int lead = 1;

    void setScale() {
        if (pqrstPlot != null) {
            pqrstPlot.setRangeBoundaries(-range, range, BoundaryMode.FIXED);
            pqrstPlot.setDomainBoundaries(-displayTime / 2, displayTime / 2, BoundaryMode.FIXED);
            pqrstPlot.redraw();
        }
    }

    private float samplingRate = 250;
    private int ringBufferSize = 1000;
    float[] ringBuffer = null;
    private int inPtr = 0;
    private int rPtr = -1;
    private int rCtr = 0;
    private float displayTime = 0.45F*2;
    private int nSamplesDispl = 1000;

    public void setSamplingRate(float _samplingrate) {
        samplingRate = _samplingrate;
        nSamplesDispl = (int) (samplingRate * displayTime);
        ringBufferSize = (int) (samplingRate * 10);
        ringBuffer = new float[ringBufferSize];
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCreate, creating Fragment");
        }

        if (container == null) {
            return null;
        }

        view = inflater.inflate(R.layout.ecgplotfragment, container, false);
        if (view == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "view == NULL!");
            }
        }

        // setup the APR Levels plot:
        pqrstPlot = (XYPlot) view.findViewById(R.id.ecgPlotView);
        if (pqrstPlot == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "pqrstPlot == null");
            }
        }

        panZoom = PanZoom.attach(pqrstPlot, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_BOTH);
        panZoom.setEnabled(true);

        pqrstSeries = new SimpleXYSeries("");

        setScale();
        pqrstPlot.addSeries(pqrstSeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        pqrstPlot.setDomainLabel("t/sec");
        pqrstPlot.setRangeLabel(LEAD[lead] + "/mV");

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        pqrstPlot.getGraph().setDomainGridLinePaint(paint);
        pqrstPlot.getGraph().setRangeGridLinePaint(paint);

        XYGraphWidget.LineLabelRenderer lineLabelRendererY = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                Rect bounds = new Rect();
                style.getPaint().getTextBounds("a", 0, 1, bounds);
                drawLabel(canvas, String.format(Locale.US, "%02.4f ", val.floatValue()),
                        style.getPaint(), x + (float)bounds.width() / 2, y + bounds.height(), isOrigin);
            }
        };
        pqrstPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.LEFT, lineLabelRendererY);

        XYGraphWidget.LineLabelStyle lineLabelStyleY = pqrstPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        Rect bounds = new Rect();
        String dummyTxt = String.format(Locale.US, "%02.4f ", 0.000597558899);
        lineLabelStyleY.getPaint().getTextBounds(dummyTxt, 0, dummyTxt.length(), bounds);
        pqrstPlot.getGraph().setMarginLeft(bounds.width());


        XYGraphWidget.LineLabelRenderer lineLabelRendererX = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                Rect bounds = new Rect();
                style.getPaint().getTextBounds("a", 0, 1, bounds);
                if (!isOrigin) {
                    final int canvasState = canvas.save();
                    canvas.rotate(style.getRotation(), x, y);
                    drawLabel(canvas, String.format(Locale.US, "%02.4f ", val.floatValue()),
                            style.getPaint(), x + (float)bounds.width() / 2, y + bounds.height(), isOrigin);
                    canvas.restoreToCount(canvasState);
                }
            }
        };
        pqrstPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, lineLabelRendererX);

        XYGraphWidget.LineLabelStyle lineLabelStyleX = pqrstPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        bounds = new Rect();
        dummyTxt = String.format(Locale.US, "%02.4f ", 0.000597558899);
        lineLabelStyleX.getPaint().getTextBounds(dummyTxt, 0, dummyTxt.length(), bounds);
        pqrstPlot.getGraph().setMarginBottom(bounds.width());


        recordButton = view.findViewById(R.id.ecgplotfragment_record);
        recordButton.setChecked(true);

        resetButton = view.findViewById(R.id.ecgplotfragment_reset);
        resetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setScale();
            }
        });

        leadSpinner = view.findViewById(R.id.ecgplotfragment_lead);
        ArrayAdapter<String> adapterChannel = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                LEAD);
        adapterChannel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        leadSpinner.setAdapter(adapterChannel);
        leadSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                lead = position;
                if (pqrstPlot != null) {
                    pqrstPlot.setRangeLabel(LEAD[lead] + "/mV");
                    pqrstPlot.setTitle("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        leadSpinner.setBackgroundResource(android.R.drawable.btn_default);
        leadSpinner.setSelection(1);
        leadSpinner.setEnabled(offerAllChannels);

        maxYspinner = (Spinner) view.findViewById(R.id.ecgplotfragment_maxy);
        ArrayAdapter<String> adapterMaxY = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_dropdown_item,
                MAXY);
        adapterMaxY.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        maxYspinner.setAdapter(adapterMaxY);
        maxYspinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                range = Float.parseFloat(MAXY[position].split(" ")[0]);
                setScale();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        maxYspinner.setBackgroundResource(android.R.drawable.btn_default);
        maxYspinner.setSelection(1);

        rPtr = -1;

        return view;

    }

    public synchronized void redraw() {

        if (pqrstPlot != null) {
            pqrstPlot.redraw();
        }
    }

    public synchronized void addValue(float I, float II, float III, float aVR, float aVL, float aVF) {
        leadsBuffer[0] = I;
        leadsBuffer[1] = II;
        leadsBuffer[2] = III;
        leadsBuffer[3] = aVR;
        leadsBuffer[4] = aVL;
        leadsBuffer[5] = aVF;
        if (ringBuffer == null) return;
        ringBuffer[inPtr] = leadsBuffer[lead];
        inPtr++;
        if (inPtr == ringBufferSize) {
            inPtr = 0;
        }
        if (rPtr > -1) {
            rCtr--;
            if (rCtr == 0) {
                if (pqrstSeries == null) {
                    return;
                }
                while (pqrstSeries.size() > 0) {
                    pqrstSeries.removeFirst();
                }

                int outPtr = rPtr;
                for (int i = 0; i < (nSamplesDispl / 2); i++) {
                    outPtr--;
                    if (outPtr < 0) {
                        outPtr = ringBufferSize - 1;
                    }
                }
                for (int i = 0; i < (nSamplesDispl-1); i++) {
                    pqrstSeries.addLast((i - nSamplesDispl / 2) / samplingRate, ringBuffer[outPtr] * 1000);
                    outPtr++;
                    if (outPtr == ringBufferSize) {
                        outPtr = 0;
                    }
                }
                redraw();
                rPtr = -1;
            }
        }
    }

    public void rDet() {
        if (recordButton != null) {
            if (!recordButton.isChecked()) return;
        }
        if (rPtr < 0) {
            rPtr = inPtr;
            rCtr = nSamplesDispl / 2;
        }
    }

    public void setOfferAllChannels(boolean _offerAllChannels) {
        offerAllChannels = _offerAllChannels;
        if (leadSpinner != null) {
            leadSpinner.setEnabled(offerAllChannels);
        }
    }

}
