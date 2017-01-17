/**
 * Copyright 2016 Bernd Porr, mail@berndporr.me.uk
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package tech.glasgowneuro.attysecg;

import android.Manifest;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import uk.me.berndporr.iirj.Butterworth;

public class AttysECG extends AppCompatActivity {

    private Timer timer = null;
    // screen refresh rate
    private final int REFRESH_IN_MS = 50;

    private RealtimePlotView realtimePlotView = null;
    private InfoView infoView = null;

    private BluetoothAdapter BA;
    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;
    private byte samplingRate = AttysComm.ADC_RATE_250HZ;

    UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysECG";

    private Highpass[] highpass = null;
    private float[] gain;
    private Butterworth[] iirNotch;
    private double notchBW = 2.5; // Hz
    private int notchOrder = 2;
    private boolean[] invert;
    private float powerlineHz = 50;

    private boolean showAcc = false;
    private boolean showMag = false;
    private boolean showCh1 = true;
    private boolean showCh2 = true;

    private float ch1Div = 1;
    private float ch2Div = 1;

    private float magTick = 1000.0E-6F; //1000uT

    private float accTick = AttysComm.oneG; // 1G

    private int theChannelWeDoAnalysis = 0;

    private int[] actualChannelIdx;

    public enum DataAnalysis {
        NONE,
        AC,
        DC,
        ECG
    }

    int ygapForInfo = 0;

    private DataAnalysis dataAnalysis = DataAnalysis.DC;

    // debugging the ECG detector, commented out for production
    //double ecgDetOut;

    private int timestamp = 0;

    String[] labels = {
            "Acc x", "Acc y", "Acc z",
            "Mag x", "Mag y", "Mag z",
            "ADC 1", "ADC 2"};

    private String dataFilename = null;
    private byte dataSeparator = 0;

    /**
     * App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;
    private Action viewAction;

    private final String ATTYS_SUBDIR = "attys";
    private File attysdir = null;

    ProgressDialog progress = null;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AttysComm.MESSAGE_ERROR:
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth connection problem", Toast.LENGTH_SHORT).show();
                    if (attysComm != null) {
                        attysComm.cancel();
                    }
                    try {
                        attysComm.join();
                    } catch (Exception ee) {
                    }
                    progress.dismiss();
                    finish();
                    break;
                case AttysComm.MESSAGE_CONNECTED:
                    progress.dismiss();
                    break;
                case AttysComm.MESSAGE_CONFIGURE:
                    Toast.makeText(getApplicationContext(),
                            "Configuring Attys", Toast.LENGTH_SHORT).show();
                    progress.dismiss();
                    break;
                case AttysComm.MESSAGE_RETRY:
                    Toast.makeText(getApplicationContext(),
                            "Bluetooth - trying to connect. Please be patient.",
                            Toast.LENGTH_SHORT).show();
                    break;
                case AttysComm.MESSAGE_STARTED_RECORDING:
                    Toast.makeText(getApplicationContext(),
                            "Started recording data to external storage.",
                            Toast.LENGTH_SHORT).show();
                    break;
                case AttysComm.MESSAGE_STOPPED_RECORDING:
                    Toast.makeText(getApplicationContext(),
                            "Finished recording data to external storage.",
                            Toast.LENGTH_SHORT).show();
                    break;
                case AttysComm.MESSAGE_CONNECTING:
                    progress.setMessage("Connecting");
                    progress.show();
            }
        }
    };


    AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
        @Override
        public void haveMessage(int msg) {
            handler.sendEmptyMessage(msg);
        }
    };


    private BluetoothDevice connect2Bluetooth() {

        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, 0);

        BA = BluetoothAdapter.getDefaultAdapter();

        if (BA == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "no bluetooth adapter!");
            }
            finish();
        }

        Set<BluetoothDevice> pairedDevices;
        pairedDevices = BA.getBondedDevices();

        if (pairedDevices == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No paired devices available. Exiting.");
            }
            finish();
        }

        for (BluetoothDevice bt : pairedDevices) {
            String b = bt.getName();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Paired dev=" + b);
            }
            if (b.startsWith("GN-ATTYS")) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Found an Attys");
                }
                return bt;
            }
        }
        return null;
    }


    private class UpdatePlotTask extends TimerTask {

        private int ignoreECGdetector = 1000;
        private double max, min;
        private float t2 = 0;
        private int doNotDetect = 0;
        private float[] analysisBuffer;
        private int analysisPtr = 0;
        private int[] hrBuffer = new int[3];
        private int[] sortBuffer = new int[3];
        private Butterworth ecgDetector = new Butterworth();
        private Butterworth ecgDetNotch = new Butterworth();
        private String m_unit = "";
        private float scaling_factor = 1;

        private void resetAnalysis() {
            max = 0;
            min = 0;
            t2 = 0;
            doNotDetect = 0;
            ignoreECGdetector = attysComm.getSamplingRateInHz();
            analysisPtr = 0;
            hrBuffer[0] = 0;
            hrBuffer[1] = 0;
            hrBuffer[2] = 0;

            m_unit = AttysComm.CHANNEL_UNITS[theChannelWeDoAnalysis];

            if ((theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_X) ||
                    (theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_Y) ||
                    (theChannelWeDoAnalysis == AttysComm.INDEX_Magnetic_field_Z)) {
                scaling_factor = 1E6F;
                m_unit = "\u00b5" + m_unit;
            } else {
                scaling_factor = 1;
            }

            annotatePlot("---------------");
        }

        UpdatePlotTask() {
            analysisBuffer = new float[attysComm.getSamplingRateInHz()];
            // this fakes an R peak so we have a matched filter!
            ecgDetector.bandPass(2, attysComm.getSamplingRateInHz(), 20, 15);
            ecgDetNotch.bandStop(notchOrder, attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
        }

        private void annotatePlot(String largeText) {
            String small = "";
            if (showCh1) {
                small = small + "".format("ADC1 = %1.04fV/div (X%d), ", ch1Div, (int) gain[AttysComm.INDEX_Analogue_channel_1]);
            }
            if (showCh2) {
                small = small + "".format("ADC2 = %1.04fV/div (X%d), ", ch2Div, (int) gain[AttysComm.INDEX_Analogue_channel_2]);
            }
            if (showAcc) {
                small = small + "".format("ACC = %dG/div, ", Math.round(accTick / AttysComm.oneG));
            }
            if (showMag) {
                small = small + "".format("MAG = %d\u00b5T/div, ", Math.round(magTick / 1E-6));
            }
            if (attysComm.isRecording()) {
                small = small + " !!RECORDING to:" + dataFilename;
            }
            if (largeText != null) {
                largeText = "".format("%s: ", labels[theChannelWeDoAnalysis]) + largeText;
            }
            if (infoView != null) {
                if (attysComm != null) {
                    infoView.drawText(largeText, small);
                }
            }
        }

        private void doAnalysis(float v) {

            v = v * scaling_factor;

            switch (dataAnalysis) {
                case ECG:
                    if (theChannelWeDoAnalysis >= AttysComm.INDEX_Analogue_channel_1) {
                        double h = ecgDetNotch.filter(v * 1000);
                        h = ecgDetector.filter(h);
                        if (ignoreECGdetector > 0) {
                            ignoreECGdetector--;
                            h = 0;
                        }
                        h = h * h;
                        // debugging
                        //ecgDetOut = h;
                        if (h > max) {
                            max = h;
                        }
                        max = max - 0.1 * max / attysComm.getSamplingRateInHz();
                        //Log.d(TAG,"h="+h+",max="+max);
                        if (doNotDetect > 0) {
                            doNotDetect--;
                        } else {
                            if (h > (0.6 * max)) {
                                float t = (timestamp - t2) / attysComm.getSamplingRateInHz();
                                float bpm = 1 / t * 60;
                                if ((bpm > 30) && (bpm < 300)) {
                                    hrBuffer[2] = hrBuffer[1];
                                    hrBuffer[1] = hrBuffer[0];
                                    hrBuffer[0] = (int) bpm;
                                    System.arraycopy(hrBuffer, 0, sortBuffer, 0, hrBuffer.length);
                                    Arrays.sort(sortBuffer);
                                    int filtBPM = sortBuffer[1];
                                    if (filtBPM > 0) {
                                        annotatePlot(String.format("%03d BPM", (int) filtBPM));
                                    }
                                }
                                t2 = timestamp;
                                // advoid 1/4 sec
                                doNotDetect = attysComm.getSamplingRateInHz() / 4;
                            }
                        }
                    }
                    break;
                case NONE:
                    int interval = attysComm.getSamplingRateInHz();
                    if ((timestamp % interval) == 0) {
                        annotatePlot(null);
                    }
                    break;
                case DC:
                    double a = 1.0 / (double) (attysComm.getSamplingRateInHz());
                    // 1st order lowpass IIR filter
                    max = v * a + (1 - a) * max;
                    interval = attysComm.getSamplingRateInHz();
                    if ((timestamp % interval) == 0) {
                        annotatePlot(String.format("%1.05f%s", max, m_unit));
                    }
                    break;
                case AC:
                    analysisBuffer[analysisPtr] = v;
                    analysisPtr++;
                    //Log.d(TAG,String.format("ana=%d",analysisPtr));
                    if (!(analysisPtr < analysisBuffer.length)) {
                        analysisPtr = 0;
                        min = 2;
                        max = -2;
                        for (int i = 0; i < analysisBuffer.length; i++) {
                            if (analysisBuffer[i] > max) {
                                max = analysisBuffer[i];
                            }
                            if (analysisBuffer[i] < min) {
                                min = analysisBuffer[i];
                            }
                        }
                        double diff = max - min;
                        annotatePlot(String.format("%1.05f%spp", diff, m_unit));
                    }
                    break;
            }
        }

        public synchronized void run() {

            if (attysComm != null) {
                if (attysComm.hasFatalError()) {
                    // Log.d(TAG,String.format("No bluetooth connection"));
                    handler.sendEmptyMessage(AttysComm.MESSAGE_ERROR);
                    return;
                }
            }
            if (attysComm != null) {
                if (!attysComm.hasActiveConnection()) return;
            }

            int nCh = 0;
            if (attysComm != null) nCh = attysComm.NCHANNELS;
            if (attysComm != null) {
                float[] tmpSample = new float[nCh];
                float[] tmpMin = new float[nCh];
                float[] tmpMax = new float[nCh];
                float[] tmpTick = new float[nCh];
                String[] tmpLabels = new String[nCh];
                int n = attysComm.getNumSamplesAvilable();
                if (realtimePlotView != null) {
                    if (!realtimePlotView.startAddSamples(n)) return;
                    for (int i = 0; ((i < n) && (attysComm != null)); i++) {
                        float[] sample = null;
                        if (attysComm != null) {
                            sample = attysComm.getSampleFromBuffer();
                        }
                        if (sample != null) {
                            // debug ECG detector
                            // sample[AttysComm.INDEX_Analogue_channel_2] = (float)ecgDetOut;
                            timestamp++;
                            for (int j = 0; j < nCh; j++) {
                                float v = sample[j];
                                if (j >= AttysComm.INDEX_Analogue_channel_1) {
                                    v = highpass[j].filter(v);
                                    if (iirNotch[j] != null) {
                                        v = (float) iirNotch[j].filter((double) v);
                                    }
                                }
                                if (invert[j]) {
                                    v = -v;
                                }
                                if (j == theChannelWeDoAnalysis) {
                                    doAnalysis(v);
                                }
                                v = v * gain[j];
                                sample[j] = v;
                            }
                            int nRealChN = 0;
                            if (showCh1) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -attysComm.getADCFullScaleRange(0);
                                    tmpMax[nRealChN] = attysComm.getADCFullScaleRange(0);
                                    ch1Div = 1.0F / gain[AttysComm.INDEX_Analogue_channel_1];
                                    if (attysComm.getADCFullScaleRange(0) < 1) {
                                        ch1Div = ch1Div / 10;
                                    }
                                    tmpTick[nRealChN] = ch1Div * gain[AttysComm.INDEX_Analogue_channel_1];
                                    tmpLabels[nRealChN] = labels[AttysComm.INDEX_Analogue_channel_1];
                                    actualChannelIdx[nRealChN] = AttysComm.INDEX_Analogue_channel_1;
                                    tmpSample[nRealChN++] = sample[AttysComm.INDEX_Analogue_channel_1];
                                }
                            }
                            if (showCh2) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -attysComm.getADCFullScaleRange(1);
                                    tmpMax[nRealChN] = attysComm.getADCFullScaleRange(1);
                                    ch2Div = 1.0F / gain[AttysComm.INDEX_Analogue_channel_2];
                                    if (attysComm.getADCFullScaleRange(1) < 1) {
                                        ch2Div = ch2Div / 10;
                                    }
                                    tmpTick[nRealChN] = ch2Div * gain[AttysComm.INDEX_Analogue_channel_2];
                                    tmpLabels[nRealChN] = labels[AttysComm.INDEX_Analogue_channel_2];
                                    actualChannelIdx[nRealChN] = AttysComm.INDEX_Analogue_channel_2;
                                    tmpSample[nRealChN++] = sample[AttysComm.INDEX_Analogue_channel_2];
                                }
                            }
                            if (showAcc) {
                                if (attysComm != null) {
                                    float min = -attysComm.getAccelFullScaleRange();
                                    float max = attysComm.getAccelFullScaleRange();

                                    for (int k = 0; k < 3; k++) {
                                        tmpMin[nRealChN] = min;
                                        tmpMax[nRealChN] = max;
                                        tmpTick[nRealChN] = gain[k] * accTick;
                                        tmpLabels[nRealChN] = labels[k];
                                        actualChannelIdx[nRealChN] = k;
                                        tmpSample[nRealChN++] = sample[k];
                                    }
                                }
                            }
                            if (showMag) {
                                if (attysComm != null) {
                                    for (int k = 0; k < 3; k++) {
                                        if (attysComm != null) {
                                            tmpMin[nRealChN] = -attysComm.getMagFullScaleRange();
                                        }
                                        if (attysComm != null) {
                                            tmpMax[nRealChN] = attysComm.getMagFullScaleRange();
                                        }
                                        tmpLabels[nRealChN] = labels[k + 3];
                                        actualChannelIdx[nRealChN] = k + 3;
                                        tmpTick[nRealChN] = magTick;
                                        tmpSample[nRealChN++] = sample[k + 3];
                                    }
                                }
                            }
                            if (infoView != null) {
                                if (ygapForInfo == 0) {
                                    ygapForInfo = infoView.getInfoHeight();
                                    if ((Log.isLoggable(TAG, Log.DEBUG))&&(ygapForInfo>0)) {
                                        Log.d(TAG, "ygap=" + ygapForInfo);
                                    }
                                }
                            }
                            if (realtimePlotView != null) {
                                realtimePlotView.addSamples(Arrays.copyOfRange(tmpSample, 0, nRealChN),
                                        Arrays.copyOfRange(tmpMin, 0, nRealChN),
                                        Arrays.copyOfRange(tmpMax, 0, nRealChN),
                                        Arrays.copyOfRange(tmpTick, 0, nRealChN),
                                        Arrays.copyOfRange(tmpLabels, 0, nRealChN),
                                        ygapForInfo);
                            }
                        }
                    }
                    if (realtimePlotView != null) {
                        realtimePlotView.stopAddSamples();
                    }
                }
            }
        }
    }


    @Override
    public void onBackPressed() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Back button pressed");
        }
        killAttysComm();
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startActivity(startMain);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        progress = new ProgressDialog(this);

        attysdir = new File(Environment.getExternalStorageDirectory().getPath(),
                ATTYS_SUBDIR);
        if (!attysdir.exists()) {
            attysdir.mkdirs();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }

        setContentView(R.layout.activity_plot_window);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        int nChannels = AttysComm.NCHANNELS;
        highpass = new Highpass[nChannels];
        gain = new float[nChannels];
        iirNotch = new Butterworth[nChannels];
        invert = new boolean[nChannels];
        actualChannelIdx = new int[nChannels];
        for (int i = 0; i < nChannels; i++) {
            highpass[i] = new Highpass();
            iirNotch[i] = null;
            // set it to 1st ADC channel
            actualChannelIdx[i] = AttysComm.INDEX_Analogue_channel_1;
            gain[i] = 1;
            if ((i >= AttysComm.INDEX_Magnetic_field_X) && (i <= AttysComm.INDEX_Magnetic_field_Z)) {
                gain[i] = 20;
            }
        }

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    // this is called whenever the app is starting or re-starting
    @Override
    public void onStart() {
        super.onStart();

        startDAQ();

    }


    @Override
    public void onResume() {
        super.onResume();

        updatePlotTask.resetAnalysis();

    }


    public void startDAQ() {

        client.connect();
        viewAction = Action.newAction(
                Action.TYPE_VIEW,
                "Attys Homepage",
                Uri.parse("http://www.attys.tech")
        );
        AppIndex.AppIndexApi.start(client, viewAction);

        btAttysDevice = connect2Bluetooth();
        if (btAttysDevice == null) {
            Context context = getApplicationContext();
            CharSequence text = "Could not find any paired Attys devices.";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            finish();
        }

        attysComm = new AttysComm(btAttysDevice);
        attysComm.registerMessageListener(messageListener);

        getsetAttysPrefs();

        if (showCh1) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Analogue_channel_1;
        } else if (showCh2) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Analogue_channel_2;
        } else if (showAcc) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Acceleration_X;
        } else if (showMag) {
            theChannelWeDoAnalysis = AttysComm.INDEX_Magnetic_field_X;
        }

        for (int i = 0; i < AttysComm.NCHANNELS; i++) {
            highpass[i].setAlpha(1.0F / attysComm.getSamplingRateInHz());
        }

        realtimePlotView = (RealtimePlotView) findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);
        realtimePlotView.init();

        realtimePlotView.registerTouchEventListener(
                new RealtimePlotView.TouchEventListener() {
                    @Override
                    public void touchedChannel(int chNo) {
                        try {
                            theChannelWeDoAnalysis = actualChannelIdx[chNo];
                            updatePlotTask.resetAnalysis();
                        } catch (Exception e) {
                            if (Log.isLoggable(TAG, Log.ERROR)) {
                                Log.e(TAG, "Exception in the TouchEventListener (BUG!):", e);
                            }
                        }
                    }
                });

        infoView = (InfoView) findViewById(R.id.infoview);
        infoView.setZOrderOnTop(true);
        infoView.setZOrderMediaOverlay(true);

        attysComm.start();

        timer = new Timer();
        updatePlotTask = new UpdatePlotTask();
        updatePlotTask.resetAnalysis();
        timer.schedule(updatePlotTask, 0, REFRESH_IN_MS);
    }

    private void killAttysComm() {

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed timer");
            }
        }

        if (updatePlotTask != null) {
            updatePlotTask.cancel();
            updatePlotTask = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed update Plot Task");
            }
        }

        if (attysComm != null) {
            attysComm.cancel();
            try {
                attysComm.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            attysComm = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed AttysComm");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }
        killAttysComm();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Restarting");
        }
        killAttysComm();
    }


    @Override
    public void onPause() {
        super.onPause();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Paused");
        }

    }


    @Override
    public void onStop() {
        super.onStop();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped");
        }

        killAttysComm();

        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }


    private void enterFilename() {

        final EditText filenameEditText = new EditText(this);
        filenameEditText.setSingleLine(true);

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        filenameEditText.setHint("");
        filenameEditText.setText(dataFilename);

        new AlertDialog.Builder(this)
                .setTitle("Enter filename")
                .setMessage("Enter the filename of the data textfile")
                .setView(filenameEditText)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        dataFilename = filenameEditText.getText().toString();
                        dataFilename = dataFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
                        if (!dataFilename.contains(".")) {
                            switch (dataSeparator) {
                                case AttysComm.DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case AttysComm.DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case AttysComm.DATA_SEPARATOR_TAB:
                                    dataFilename = dataFilename + ".tsv";
                            }
                        }
                        Toast.makeText(getApplicationContext(),
                                "Press rec to record to '" + dataFilename + "'",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }


    private void shareData() {

        final int REQUEST_EXTERNAL_STORAGE = 1;
        String[] PERMISSIONS_STORAGE = {
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        final List files = new ArrayList();
        final String[] list = attysdir.list();
        for (String file : list) {
            if (files != null) {
                if (file != null) {
                    files.add(file);
                }
            }
        }

        final ListView listview = new ListView(this);
        ArrayAdapter adapter = new ArrayAdapter(this,
                android.R.layout.simple_list_item_multiple_choice,
                files);
        listview.setAdapter(adapter);
        listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                view.setSelected(true);
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("Share")
                .setMessage("Select filename(s)")
                .setView(listview)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SparseBooleanArray checked = listview.getCheckedItemPositions();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        ArrayList<Uri> files = new ArrayList<>();
                        for (int i = 0; i < listview.getCount(); i++) {
                            if (checked.get(i)) {
                                String filename = list[i];
                                File fp = new File(attysdir, filename);
                                files.add(Uri.fromFile(fp));
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "filename=" + filename);
                                }
                            }
                        }
                        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                        sendIntent.setType("text/*");
                        startActivity(Intent.createChooser(sendIntent, "Send your files"));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attysplot, menu);

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.preferences:
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                return true;

            case R.id.toggleRec:
                if (attysComm.isRecording()) {
                    File file = attysComm.getFile();
                    attysComm.stopRec();
                    if (file != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        Uri contentUri = Uri.fromFile(file);
                        mediaScanIntent.setData(contentUri);
                        sendBroadcast(mediaScanIntent);
                    }
                } else {
                    if (dataFilename != null) {
                        File file = new File(attysdir, dataFilename.trim());
                        attysComm.setDataSeparator(dataSeparator);
                        if (file.exists()) {
                            Toast.makeText(getApplicationContext(),
                                    "File exists already. Enter a different one.",
                                    Toast.LENGTH_LONG).show();
                            return true;
                        }
                        java.io.FileNotFoundException e = attysComm.startRec(file);
                        if (e != null) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Could not open data file: " + e.getMessage());
                            }
                            return true;
                        }
                        if (attysComm.isRecording()) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Saving to " + file.getAbsolutePath());
                            }
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "To record enter a filename first", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;

            case R.id.showCh1:
                showCh1 = !showCh1;
                item.setChecked(showCh1);
                return true;

            case R.id.showCh2:
                showCh2 = !showCh2;
                item.setChecked(showCh2);
                return true;

            case R.id.showaccelerometer:
                showAcc = !showAcc;
                item.setChecked(showAcc);
                return true;

            case R.id.showmagnetometer:
                showMag = !showMag;
                item.setChecked(showMag);
                return true;

            case R.id.enterFilename:
                enterFilename();
                return true;

            case R.id.Ch1toggleDC:
                boolean a = highpass[AttysComm.INDEX_Analogue_channel_1].getIsActive();
                a = !a;
                item.setChecked(a);
                highpass[AttysComm.INDEX_Analogue_channel_1].setActive(a);
                return true;

            case R.id.Ch2toggleDC:
                a = highpass[AttysComm.INDEX_Analogue_channel_2].getIsActive();
                a = !a;
                item.setChecked(a);
                highpass[AttysComm.INDEX_Analogue_channel_2].setActive(a);
                return true;

            case R.id.Ch1notch:
                if (iirNotch[AttysComm.INDEX_Analogue_channel_1] == null) {
                    iirNotch[AttysComm.INDEX_Analogue_channel_1] = new Butterworth();
                    iirNotch[AttysComm.INDEX_Analogue_channel_1].bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                } else {
                    iirNotch[AttysComm.INDEX_Analogue_channel_1] = null;
                }
                item.setChecked(iirNotch[AttysComm.INDEX_Analogue_channel_1] != null);
                return true;

            case R.id.Ch2notch:
                if (iirNotch[AttysComm.INDEX_Analogue_channel_2] == null) {
                    iirNotch[AttysComm.INDEX_Analogue_channel_2] = new Butterworth();
                    iirNotch[AttysComm.INDEX_Analogue_channel_2].bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                } else {
                    iirNotch[AttysComm.INDEX_Analogue_channel_2] = null;
                }
                item.setChecked(iirNotch[AttysComm.INDEX_Analogue_channel_2] != null);
                return true;

            case R.id.Ch1invert:
                a = invert[AttysComm.INDEX_Analogue_channel_1];
                a = !a;
                invert[AttysComm.INDEX_Analogue_channel_1] = a;
                item.setChecked(a);
                return true;

            case R.id.Ch2invert:
                a = invert[AttysComm.INDEX_Analogue_channel_2];
                a = !a;
                invert[AttysComm.INDEX_Analogue_channel_2] = a;
                item.setChecked(a);
                return true;

            case R.id.Ch1gain1:
            case R.id.Ch1gain2:
            case R.id.Ch1gain5:
            case R.id.Ch1gain10:
            case R.id.Ch1gain20:
            case R.id.Ch1gain50:
            case R.id.Ch1gain100:
            case R.id.Ch1gain200:
            case R.id.Ch1gain500:
                String t = item.getTitle().toString();
                int g = Integer.parseInt(t);
                gain[AttysComm.INDEX_Analogue_channel_1] = (float) g;
                Toast.makeText(getApplicationContext(),
                        String.format("Channel 1 gain set to x%d", g), Toast.LENGTH_LONG).show();
                return true;

            case R.id.Ch2gain1:
            case R.id.Ch2gain2:
            case R.id.Ch2gain5:
            case R.id.Ch2gain10:
            case R.id.Ch2gain20:
            case R.id.Ch2gain50:
            case R.id.Ch2gain100:
            case R.id.Ch2gain200:
            case R.id.Ch2gain500:
                t = item.getTitle().toString();
                g = Integer.parseInt(t);
                Toast.makeText(getApplicationContext(),
                        String.format("Channel 2 gain set to x%d", g), Toast.LENGTH_LONG).show();
                gain[AttysComm.INDEX_Analogue_channel_2] = (float) g;
                return true;

            case R.id.largeStatusOff:
                dataAnalysis = DataAnalysis.NONE;
                updatePlotTask.annotatePlot("");
                ygapForInfo = 0;
                return true;

            case R.id.largeStatusAC:
                dataAnalysis = DataAnalysis.AC;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.largeStatusDC:
                dataAnalysis = DataAnalysis.DC;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.largeStatusBPM:
                dataAnalysis = DataAnalysis.ECG;
                updatePlotTask.resetAnalysis();
                return true;

            case R.id.filebrowser:
                shareData();
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void getsetAttysPrefs() {
        byte mux;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Setting preferences");
        }
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        boolean ecg_mode = prefs.getBoolean("ECG_mode", false);
        if (ecg_mode) {
            mux = AttysComm.ADC_MUX_ECG_EINTHOVEN;
        } else {
            mux = AttysComm.ADC_MUX_NORMAL;
        }
        byte gain0 = (byte) (Integer.parseInt(prefs.getString("ch1_gainpref", "0")));
        attysComm.setAdc1_gain_index(gain0);
        attysComm.setAdc0_mux_index(mux);
        byte gain1 = (byte) (Integer.parseInt(prefs.getString("ch2_gainpref", "0")));
        attysComm.setAdc2_gain_index(gain1);
        attysComm.setAdc1_mux_index(mux);
        int current = Integer.parseInt(prefs.getString("ch2_current", "-1"));
        if (current < 0) {
            attysComm.enableCurrents(false, false, false);
        } else {
            attysComm.setBiasCurrent((byte) current);
            attysComm.enableCurrents(false, false, true);
        }
        byte data_separator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        attysComm.setDataSeparator(data_separator);

        int fullscaleAcc = Integer.parseInt(prefs.getString("accFullscale", "1"));

        attysComm.setAccel_full_scale_index((byte) fullscaleAcc);

        powerlineHz = Float.parseFloat(prefs.getString("powerline", "50"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }

        samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "0"));
        if (samplingRate > 1) samplingRate = 1;

        attysComm.setAdc_samplingrate_index(samplingRate);
    }

}
