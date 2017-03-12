package tech.glasgowneuro.attysecg;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

/**
 * Created by bp1 on 12/03/17.
 */

public class ECGPlotFragment extends Fragment {

    String TAG = "ECGPlotFragment";

    private float range = 1;

    private SimpleXYSeries pqrstSeries = null;

    private XYPlot pqrstPlot = null;

    private Spinner maxYspinner = null;

    private Spinner leadSpinner = null;

    private ToggleButton recordButton = null;

    private static String[] LEAD = {"I", "II", "III", "aVR", "aVL", "aVF"};

    private float[] leadsBuffer = new float[6];

    private static String[] MAXY = {"0.2 mV", "0.5 mV", "1.0 mV", "1.2 mV", "1.5 mV", "2.0 mV"};

    View view = null;

    // we start with Einthoven II
    private int lead = 1;

    void setScale() {
        if (pqrstPlot != null) {
            pqrstPlot.setRangeBoundaries(range, -range, BoundaryMode.FIXED);
            pqrstPlot.setDomainBoundaries(-displayTime/2,displayTime/2, BoundaryMode.FIXED);
            pqrstPlot.redraw();
        }
    }

    private float samplingRate = 250;
    private int ringBufferSize = 1000;
    float[] ringBuffer = null;
    private int inPtr = 0;
    private int rPtr = -1;
    private int rCtr = 0;
    private float displayTime = 2;
    private int nSamplesDispl = 1000;

    public void setSamplingRate(float _samplingrate) {
        samplingRate = _samplingrate;
        nSamplesDispl = (int)(samplingRate*displayTime);
        ringBufferSize = (int) (samplingRate * 10);
        ringBuffer = new float[ringBufferSize];
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
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

        pqrstSeries = new SimpleXYSeries("");
        if (pqrstSeries == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "pqrstSeries == null");
            }
        }

        setScale();
        pqrstPlot.addSeries(pqrstSeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        pqrstPlot.setDomainLabel("t/sec");
        pqrstPlot.setRangeLabel(LEAD[lead]+"/mV");

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        pqrstPlot.getGraph().setDomainGridLinePaint(paint);
        pqrstPlot.getGraph().setRangeGridLinePaint(paint);

        recordButton = (ToggleButton) view.findViewById(R.id.ecgplotfragment_record);
        recordButton.setChecked(true);

        leadSpinner = (Spinner) view.findViewById(R.id.ecgplotfragment_lead);
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

    public synchronized void addValue(float I,float II,float III,float aVR,float aVL,float aVF) {
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
            //Log.d(TAG,"rCtr="+rCtr);
            if (rCtr == 0) {
                if (pqrstSeries == null) {
                    return;
                }
                // get rid the oldest sample in history:
                while (pqrstSeries.size() > 0) {
                    pqrstSeries.removeFirst();
                }

                int outPtr = rPtr;
                for (int i=0;i<(nSamplesDispl/2);i++) {
                    outPtr--;
                    if (outPtr < 0) {
                        outPtr = ringBufferSize-1;
                    }
                }
                for (int i = 0; i < nSamplesDispl; i++) {
                    // add the latest history sample:
                    pqrstSeries.addLast((i-nSamplesDispl/2)/samplingRate, ringBuffer[outPtr]*1000);
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


}
