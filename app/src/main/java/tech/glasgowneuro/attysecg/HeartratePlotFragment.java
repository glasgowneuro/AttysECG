package tech.glasgowneuro.attysecg;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYPlot;

import java.util.Locale;


/**
 * Created by Bernd Porr on 20/01/17.
 * Heartrate Plot
 */

public class HeartratePlotFragment extends Fragment {

    private final String TAG = "HeartratePlotFragment";

    private static final float MAXBPM = 200;
    private static final int HISTORY_SIZE = 60;

    private SimpleXYSeries bpmHistorySeries = null;

    private XYPlot bpmPlot = null;

    private TextView bpmText = null;
    private TextView nrmssdText = null;

    private double avgHR = 0;
    private double devHR = 0;
    private double rmsHR = 0;
    private double nrmssd = 0;

    private ToggleButton bpmAutoscaleButton = null;

    private TextView bpmStatsView = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        Log.d(TAG, "onCreate, creating Fragment");

        if (container == null) {
            return null;
        }

        final View view = inflater.inflate(R.layout.heartrateplotfragment, container, false);

        // setup the APR Levels plot:
        bpmPlot = view.findViewById(R.id.bpmPlotView);
        bpmText = view.findViewById(R.id.bpmTextView);
        nrmssdText = view.findViewById(R.id.nrmssdTextView);

        bpmHistorySeries = new SimpleXYSeries("BPM");
        bpmHistorySeries.useImplicitXVals();

        bpmPlot.setRangeBoundaries(0, MAXBPM, BoundaryMode.FIXED);
        bpmPlot.setDomainBoundaries(0, HISTORY_SIZE, BoundaryMode.FIXED);
        bpmPlot.addSeries(bpmHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        bpmPlot.setDomainLabel("Heartbeat #");
        bpmPlot.setRangeLabel("");

        if (getActivity() != null) {
            Screensize screensize = new Screensize(getContext());

            if (screensize.isTablet()) {
                bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 25);
            } else {
                bpmPlot.setDomainStep(StepMode.INCREMENT_BY_VAL, 50);
            }
        }

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        bpmPlot.getGraph().setDomainGridLinePaint(paint);
        bpmPlot.getGraph().setRangeGridLinePaint(paint);

        bpmStatsView = view.findViewById(R.id.bpmstats);
        final Button bpmResetButton = view.findViewById(R.id.bpmreset);
        bpmAutoscaleButton = view.findViewById(R.id.bpmautoscale);

        bpmAutoscaleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (getActivity() == null) return;
                Screensize screensize = new Screensize(getContext());
                if (isChecked) {
                    bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 20);
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
                } else {
                    bpmPlot.setRangeStep(StepMode.INCREMENT_BY_VAL, 25);
                    bpmPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);
                }
                bpmPlot.redraw();
            }
        });
        bpmAutoscaleButton.setChecked(true);

        bpmResetButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                reset();
            }
        });

        return view;
    }

    private void reset() {
        int n = bpmHistorySeries.size();
        for (int i = 0; i < n; i++) {
            bpmHistorySeries.removeLast();
        }
        bpmPlot.redraw();
    }

    private double hr2interval(int i) {
        return 60.0 / bpmHistorySeries.getY(i).doubleValue();
    }

    public synchronized void addValue(final float v) {

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (bpmText != null) {
                        bpmText.setText(String.format(Locale.US, "%03d BPM", (int) v));
                    }
                    if (nrmssdText != null) {
                        nrmssdText.setText(String.format(Locale.US, "%1.1f%% nRMSSD", nrmssd * 100));
                    }
                }
            });
        }

        if (bpmHistorySeries == null) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "bpmHistorySeries == null");
            }
            return;
        }

        // get rid the oldest sample in history:
        if (bpmHistorySeries.size() > HISTORY_SIZE) {
            bpmHistorySeries.removeFirst();
        }

        double sum = 0;
        final int startIdx = bpmHistorySeries.size() / 2;
        if (bpmStatsView != null) {
            int n = 0;
            for (int i = startIdx; i < bpmHistorySeries.size(); i++) {
                sum = sum + hr2interval(i);
                n++;
            }
            if (n > 0) {
                avgHR = sum / n;
            }
            double dev = 0;
            for (int i = startIdx; i < bpmHistorySeries.size(); i++) {
                dev = dev + Math.pow(hr2interval(i) - avgHR, 2);
            }
            if (n > 1) {
                devHR = Math.sqrt(dev / (n - 1));
            }

            n = 0;
            sum = 0;
            for (int i = startIdx; i < bpmHistorySeries.size() - 1; i++) {
                sum = sum + Math.pow(hr2interval(i) - hr2interval(i + 1), 2);
                n++;
            }
            if (n > 1) {
                rmsHR = Math.sqrt(sum / n);
            }
            if (avgHR > 0) {
                double a = rmsHR / avgHR;
                if (!Double.isInfinite(a) && !Double.isNaN(a)) {
                    nrmssd = a;
                }
            }
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    bpmStatsView.setText(String.format(Locale.US,
                            "avg = %3.02f BPM, sd = %3.02f ms, rmssd = %3.02f ms",
                            60 / avgHR, devHR * 1000, rmsHR * 1000));
                }
            });
        }

        // add the latest history sample:
        bpmHistorySeries.addLast(null, v);

        float maxBpm = MAXBPM;

        if (bpmAutoscaleButton.isChecked()) {
            maxBpm = 0;
            for (int i = 0; i < bpmHistorySeries.size(); i++) {
                float b = bpmHistorySeries.getY(i).floatValue();
                if (b > maxBpm) maxBpm = b;
            }
            bpmPlot.setRangeBoundaries(0, maxBpm, BoundaryMode.FIXED);
        }

        bpmPlot.redraw();
    }
}
