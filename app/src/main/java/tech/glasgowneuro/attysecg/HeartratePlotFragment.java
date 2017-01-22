package tech.glasgowneuro.attysecg;

import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

/**
 * Created by bp1 on 20/01/17.
 */

public class HeartratePlotFragment extends Fragment {

    String TAG = "HeartratePlotFragment";

    private static final int HISTORY_SIZE = 60;

    private SimpleXYSeries bpmHistorySeries = null;

    private XYPlot bpmPlot = null;

    private TextView bpmText = null;

    View view = null;

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

        view = inflater.inflate(R.layout.xyplotfragment, container, false);

        // setup the APR Levels plot:
        bpmPlot = (XYPlot) view.findViewById(R.id.bpmPlotView);
        bpmText = (TextView) view.findViewById(R.id.bpmTextView);

        bpmHistorySeries = new SimpleXYSeries("Heart rate / beats per minute");
        if (bpmHistorySeries == null) {
            Log.e(TAG, "bpmHistorySeries == null");
        }
        bpmHistorySeries.useImplicitXVals();

        bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
        bpmPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        bpmPlot.addSeries(bpmHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        bpmPlot.setDomainStepValue(HISTORY_SIZE / 10);
        bpmPlot.setDomainLabel("Heartbeat #");
        bpmPlot.setRangeLabel("rate/BPM");

        return view;

    }

    public synchronized void addValue(final float v) {

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (bpmText != null) {
                        bpmText.setText(String.format("%03d BPM", (int) v));
                    }
                }
            });
        }

        if (bpmHistorySeries == null) {
            Log.d(TAG, "bpmHistorySeries == null");
            return;
        }
        // get rid the oldest sample in history:
        if (bpmHistorySeries.size() > HISTORY_SIZE) {
            bpmHistorySeries.removeFirst();
        }

        // add the latest history sample:
        bpmHistorySeries.addLast(null, v);
        bpmPlot.redraw();
    }
}
