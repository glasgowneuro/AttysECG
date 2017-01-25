package tech.glasgowneuro.attysecg;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

/**
 * Created by Bernd Porr on 23/01/17.
 * Vector ECG
 */

public class VectorPlotFragment extends Fragment {

    String TAG = "VectorPlotFragment";

    private int history_size = 250;
    private float range = 1;

    private SimpleXYSeries vectorHistorySeries = null;

    private XYPlot vectorPlot = null;

    View view = null;

    void setHistorySize(int historySize) {
        history_size = historySize;
    }

    void setGain(float _gain) {
        range = 750 / _gain;
        setScale();
    }

    void setScale() {
        if (vectorPlot != null) {
            vectorPlot.setRangeBoundaries(range, -range, BoundaryMode.FIXED);
            vectorPlot.setDomainBoundaries(-range, range, BoundaryMode.FIXED);
        }
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

        view = inflater.inflate(R.layout.vectorplotfragment, container, false);
        if (view == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "view == NULL!");
            }
        }

        // setup the APR Levels plot:
        vectorPlot = (XYPlot) view.findViewById(R.id.vectorPlotView);
        if (vectorPlot == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "vectorPlot == null");
            }
        }

        vectorHistorySeries = new SimpleXYSeries("");
        if (vectorHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "vectorHistorySeries == null");
            }
        }

        setScale();
        vectorPlot.addSeries(vectorHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        //vectorPlot.setDomainStepValue(2 / 10);
        vectorPlot.setDomainLabel("I/mV");
        vectorPlot.setRangeLabel("aVF/mV");

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        vectorPlot.getGraph().setDomainGridLinePaint(paint);
        vectorPlot.getGraph().setRangeGridLinePaint(paint);

        return view;

    }


    public synchronized void redraw() {

        if (vectorPlot != null) {
            vectorPlot.redraw();
        }
    }

    public synchronized void addValue(final float x, final float y) {

        if (vectorHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "vectorHistorySeries == null");
            }
            return;
        }
        // get rid the oldest sample in history:
        if (vectorHistorySeries.size() > history_size) {
            vectorHistorySeries.removeFirst();
        }

        // add the latest history sample:
        vectorHistorySeries.addLast(x * 1000, y * 1000);
    }


}
