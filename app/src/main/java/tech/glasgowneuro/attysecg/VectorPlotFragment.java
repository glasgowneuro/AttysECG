package tech.glasgowneuro.attysecg;

import android.graphics.Color;
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
 * Created by bp1 on 23/01/17.
 */

public class VectorPlotFragment extends Fragment {

    String TAG = "VectorPlotFragment";

    private int history_size = 250;

    private SimpleXYSeries vectorHistorySeries = null;

    private XYPlot vectorPlot = null;

    View view = null;

    void setHistorySize(int historySize) {
        history_size = historySize;
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        Log.d(TAG, "onCreate, creating Fragment");

        if (container == null) {
            return null;
        }

        view = inflater.inflate(R.layout.vectorplotfragment, container, false);
        if (view == null) {
            Log.e(TAG,"view == NULL!");
        }
        // setup the APR Levels plot:
        vectorPlot = (XYPlot) view.findViewById(R.id.vectorPlotView);
        if (vectorPlot == null) {
            Log.e(TAG,"vectorPlot == null");
        }

        vectorHistorySeries = new SimpleXYSeries("");
        if (vectorHistorySeries == null) {
            Log.e(TAG, "vectorHistorySeries == null");
        }

        vectorPlot.setRangeBoundaries(2, -2, BoundaryMode.FIXED);
        vectorPlot.setDomainBoundaries(-2, 2, BoundaryMode.FIXED);
        vectorPlot.addSeries(vectorHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        //vectorPlot.setDomainStepValue(2 / 10);
        vectorPlot.setDomainLabel("I/mV");
        vectorPlot.setRangeLabel("aVF/mV");

        return view;

    }

    public synchronized void redraw() {
        if (vectorPlot != null) {
            vectorPlot.redraw();
        }
    }

    public synchronized void addValue(final float x, final float y) {

        if (vectorHistorySeries == null) {
            // Log.d(TAG, "vectorHistorySeries == null");
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
