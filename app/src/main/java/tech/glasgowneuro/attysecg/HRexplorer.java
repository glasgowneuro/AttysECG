package tech.glasgowneuro.attysecg;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.PanZoom;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HRexplorer extends AppCompatActivity {

    final String TAG = HRexplorer.class.getSimpleName();

    final String DATE_FORMAT = "dd.MM.yyyy HH:mm:ss";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hrexplorer);

        XYPlot hrPlot = (XYPlot) findViewById(R.id.hrPlotView);

        PanZoom panZoom = PanZoom.attach(hrPlot, PanZoom.Pan.BOTH, PanZoom.Zoom.STRETCH_HORIZONTAL);
        panZoom.setEnabled(true);

        SimpleXYSeries hrSeries = new SimpleXYSeries("");

        try {
            File f = new File(getBaseContext().getExternalFilesDir(null), AttysECG.HR_FILENAME);
            FileReader fileReader = new FileReader(f);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            while (bufferedReader.ready()) {
                String line = bufferedReader.readLine();
                String[] fields = line.split("\t");
                hrSeries.addLast(Float.parseFloat(fields[0]), Float.parseFloat(fields[1]));
            }
        } catch (FileNotFoundException e1) {
            Log.d(TAG,"No HR file");
        } catch (IOException e2) {
            Log.d(TAG,"File format error.",e2);
        }

        hrPlot.setRangeBoundaries(0, 200, BoundaryMode.FIXED);

        hrPlot.addSeries(hrSeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 255, 255), null, null, null));
        hrPlot.setDomainLabel("t/time of day");
        hrPlot.setRangeLabel("HR/bpm");

        Paint paint = new Paint();
        paint.setColor(Color.argb(128, 0, 255, 0));
        hrPlot.getGraph().setDomainGridLinePaint(paint);
        hrPlot.getGraph().setRangeGridLinePaint(paint);

        XYGraphWidget.LineLabelRenderer lineLabelRendererX = new XYGraphWidget.LineLabelRenderer() {
            @Override
            public void drawLabel(Canvas canvas,
                                  XYGraphWidget.LineLabelStyle style,
                                  Number val, float x, float y, boolean isOrigin) {
                Rect bounds = new Rect();
                final long v = val.longValue();
                final Date date = new Date(v);
                style.getPaint().getTextBounds("a", 0, 1, bounds);
                if (!isOrigin) {
                    final int canvasState = canvas.save();
                    canvas.rotate(style.getRotation(), x, y);
                    SimpleDateFormat dateFormat =
                            new SimpleDateFormat(DATE_FORMAT,Locale.US);
                    String dd = dateFormat.format(date);
                    drawLabel(canvas, dd,
                            style.getPaint(), x + (float)bounds.width() / 2, y + bounds.height(), isOrigin);
                    canvas.restoreToCount(canvasState);
                }
            }
        };
        hrPlot.getGraph().setLineLabelRenderer(XYGraphWidget.Edge.BOTTOM, lineLabelRendererX);
        Rect bounds = new Rect();
        XYGraphWidget.LineLabelStyle lineLabelStyleX = hrPlot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT);
        lineLabelStyleX.getPaint().getTextBounds(DATE_FORMAT, 0, DATE_FORMAT.length(), bounds);
        hrPlot.getGraph().setMarginBottom(bounds.width());
    }
}