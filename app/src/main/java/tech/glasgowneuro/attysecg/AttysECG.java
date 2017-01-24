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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.io.File;
import java.io.PrintWriter;
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
    private HeartratePlotFragment heartratePlotFragment = null;
    private VectorPlotFragment vectorPlotFragment = null;

    private BluetoothAdapter BA;
    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;
    private byte samplingRate = AttysComm.ADC_RATE_250HZ;

    UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysECG";

    private Highpass highpass_II = null;
    private Highpass highpass_III = null;
    private float gain = 500;
    private Butterworth iirNotch_II = null;
    private Butterworth iirNotch_III = null;
    private double notchBW = 2.5; // Hz
    private int notchOrder = 2;
    private float powerlineHz = 50;

    private boolean showEinthoven = true;
    private boolean showAugmented = true;
    private float filtBPM = 0;

    private float ytick = 0;

    private int[] actualChannelIdx;

    public enum PlotWindowContent {
        NONE,
        BPM,
        VECTOR
    }

    int ygapForInfo = 0;

    private PlotWindowContent plotWindowContent = PlotWindowContent.NONE;

    // debugging the ECG detector, commented out for production
    //double ecgDetOut;

    private int timestamp = 0;

    String[] labels = {
            "I", "II", "III",
            "aVR", "aVL", "aVF"};

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



    private class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private PrintWriter textdataFileStream = null;
        private File textdataFile = null;
        private byte data_separator = AttysComm.DATA_SEPARATOR_TAB;
        float samplingInterval = 0;
        float bpm = 0;

        // starts the recording
        public java.io.FileNotFoundException startRec(File file) {
            samplingInterval = 1.0F / attysComm.getSamplingRateInHz();
            try {
                textdataFileStream = new PrintWriter(file);
                textdataFile = file;
                messageListener.haveMessage(AttysComm.MESSAGE_STARTED_RECORDING);
            } catch (java.io.FileNotFoundException e) {
                textdataFileStream = null;
                textdataFile = null;
                return e;
            }
            return null;
        }

        // stops it
        public void stopRec() {
            if (textdataFileStream != null) {
                textdataFileStream.close();
                messageListener.haveMessage(AttysComm.MESSAGE_STOPPED_RECORDING);
                textdataFileStream = null;
                textdataFile = null;
            }
        }

        // are we recording?
        public boolean isRecording() {
            return (textdataFileStream != null);
        }

        public File getFile() {
            return textdataFile;
        }

        public void setDataSeparator(byte s) {
            data_separator = s;
        }

        public byte getDataSeparator() {
            return data_separator;
        }

        public void setBPM(float _bpm) {
            bpm = _bpm;
        }

        private void saveData(float I, float II, float III,
                              float aVR, float aVL, float aVF) {

            if (textdataFileStream == null) return;

            char s = ' ';
            switch (data_separator) {
                case AttysComm.DATA_SEPARATOR_SPACE:
                    s = ' ';
                    break;
                case AttysComm.DATA_SEPARATOR_COMMA:
                    s = ',';
                    break;
                case AttysComm.DATA_SEPARATOR_TAB:
                    s = 9;
                    break;
            }
            float t = timestamp + samplingInterval;
            String tmp = String.format("%f%c", t, s);
            tmp = tmp + String.format("%f%c", I, s);
            tmp = tmp + String.format("%f%c", II, s);
            tmp = tmp + String.format("%f%c", III, s);
            tmp = tmp + String.format("%f%c", aVR, s);
            tmp = tmp + String.format("%f%c", aVL, s);
            tmp = tmp + String.format("%f%c", aVF, s);
            tmp = tmp + String.format("%f", bpm);
            bpm = 0;

            if (textdataFileStream != null) {
                textdataFileStream.format("%s\n", tmp);
            }
        }
    }

    DataRecorder dataRecorder = new DataRecorder();

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
        private float[] hrBuffer = new float[3];
        private float[] sortBuffer = new float[3];
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

            m_unit = AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_1];

            scaling_factor = 1;

            annotatePlot();
        }

        UpdatePlotTask() {
            analysisBuffer = new float[attysComm.getSamplingRateInHz()];
            // this fakes an R peak so we have a matched filter!
            ecgDetector.bandPass(2, attysComm.getSamplingRateInHz(), 20, 15);
            ecgDetNotch.bandStop(notchOrder, attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
        }

        private void annotatePlot() {
            String small = "";
            small = small + "".format("x = 1sec/div, y = %1.04fV/div", ytick);
            if (attysComm.isRecording()) {
                small = small + " !!RECORDING to:" + dataFilename;
            }
            if (infoView != null) {
                if (attysComm != null) {
                    infoView.drawText(small);
                }
            }
        }

        private void doAnalysis(float v) {

            v = v * scaling_factor;

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
                        hrBuffer[0] = bpm;
                        System.arraycopy(hrBuffer, 0, sortBuffer, 0, hrBuffer.length);
                        Arrays.sort(sortBuffer);
                        filtBPM = sortBuffer[1];
                        if (filtBPM > 0) {
                            dataRecorder.setBPM(filtBPM);
                            if (heartratePlotFragment != null) {
                                heartratePlotFragment.addValue(filtBPM);
                            }
                        }
                    }
                    t2 = timestamp;
                    // advoid 1/4 sec
                    doNotDetect = attysComm.getSamplingRateInHz() / 4;
                }
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

                float max = attysComm.getADCFullScaleRange(0) / gain;
                ytick = 1.0F / gain / 10;
                annotatePlot();

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

                            float II = sample[AttysComm.INDEX_Analogue_channel_1];
                            II = highpass_II.filter(II);
                            if (iirNotch_II != null) {
                                II = (float) iirNotch_II.filter((double) II);
                            }
                            doAnalysis(II);

                            float III = sample[AttysComm.INDEX_Analogue_channel_2];
                            III = highpass_III.filter(III);
                            if (iirNotch_III != null) {
                                III = (float) iirNotch_III.filter((double) III);
                            }

                            // https://pdfs.semanticscholar.org/8160/8b62b6efb007d112b438655dd2c897759fb1.pdf
                            // Corrected Formula for the Calculation of the Electrical Heart Axis
                            // Dragutin Novosel, Georg Noll1, Thomas F. Lüscher1

                            // I-II+III = 0
                            float I = II - III;

                            float aVR = III/2 - II;
                            float aVL = II/2 - III;
                            float aVF = II/2 + III/2;

                            if (vectorPlotFragment != null) {
                                vectorPlotFragment.addValue(I,aVF);
                            }

                            dataRecorder.saveData(I,II,III,aVR,aVL,aVF);

                            int nRealChN = 0;
                            if (showEinthoven) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[0];
                                    actualChannelIdx[nRealChN] = 0;
                                    tmpSample[nRealChN++] = I;
                                }
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[1];
                                    actualChannelIdx[nRealChN] = 1;
                                    tmpSample[nRealChN++] = II;
                                }
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[2];
                                    actualChannelIdx[nRealChN] = 2;
                                    tmpSample[nRealChN++] = III;
                                }
                            }
                            if (showAugmented) {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[3];
                                    actualChannelIdx[nRealChN] = 3;
                                    tmpSample[nRealChN++] = aVR;
                                }
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[4];
                                    actualChannelIdx[nRealChN] = 4;
                                    tmpSample[nRealChN++] = aVL;
                                }
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = labels[5];
                                    actualChannelIdx[nRealChN] = 5;
                                    tmpSample[nRealChN++] = aVF;
                                }
                            }
                            if (infoView != null) {
                                if (ygapForInfo == 0) {
                                    ygapForInfo = infoView.getInfoHeight();
                                    if ((Log.isLoggable(TAG, Log.DEBUG)) && (ygapForInfo > 0)) {
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
                    if (vectorPlotFragment != null) {
                        vectorPlotFragment.redraw();
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

        setContentView(R.layout.main_activity_layout);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        int nChannels = AttysComm.NCHANNELS;
        iirNotch_II = new Butterworth();
        iirNotch_III = new Butterworth();
        actualChannelIdx = new int[nChannels];
        highpass_II = new Highpass();
        highpass_III = new Highpass();
        iirNotch_II = null;
        iirNotch_III = null;
        actualChannelIdx[0] = AttysComm.INDEX_Analogue_channel_1;
        gain = 500;

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

        highpass_II.setAlpha(1.0F / attysComm.getSamplingRateInHz());
        highpass_III.setAlpha(1.0F / attysComm.getSamplingRateInHz());

        realtimePlotView = (RealtimePlotView) findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);
        realtimePlotView.init();

        realtimePlotView.registerTouchEventListener(
                new RealtimePlotView.TouchEventListener() {
                    @Override
                    public void touchedChannel(int chNo) {
                        try {
                            // theChannelWeDoAnalysis = actualChannelIdx[chNo];
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
        getMenuInflater().inflate(R.menu.main_menu_attysecg, menu);

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
                if (dataRecorder.isRecording()) {
                    File file = dataRecorder.getFile();
                    dataRecorder.stopRec();
                    if (file != null) {
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        Uri contentUri = Uri.fromFile(file);
                        mediaScanIntent.setData(contentUri);
                        sendBroadcast(mediaScanIntent);
                    }
                } else {
                    if (dataFilename != null) {
                        File file = new File(attysdir, dataFilename.trim());
                        dataRecorder.setDataSeparator(dataSeparator);
                        if (file.exists()) {
                            Toast.makeText(getApplicationContext(),
                                    "File exists already. Enter a different one.",
                                    Toast.LENGTH_LONG).show();
                            return true;
                        }
                        java.io.FileNotFoundException e = dataRecorder.startRec(file);
                        if (e != null) {
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Could not open data file: " + e.getMessage());
                            }
                            return true;
                        }
                        if (dataRecorder.isRecording()) {
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

            case R.id.showEinthoven:
                showEinthoven = !showEinthoven;
                item.setChecked(showEinthoven);
                return true;

            case R.id.showAugmented:
                showAugmented = !showAugmented;
                item.setChecked(showAugmented);
                return true;

            case R.id.Ch1gain200:
                gain = 200;
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.Ch1gain500:
                gain = 500;
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.Ch1gain1000:
                gain = 1000;
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.enterFilename:
                enterFilename();
                return true;

            case R.id.notch:
                if (iirNotch_II == null) {
                    iirNotch_II = new Butterworth();
                    iirNotch_III = new Butterworth();
                    iirNotch_II.bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                    iirNotch_III.bandStop(notchOrder,
                            attysComm.getSamplingRateInHz(), powerlineHz, notchBW);
                } else {
                    iirNotch_II = null;
                    iirNotch_III = null;
                }
                item.setChecked(iirNotch_II != null);
                return true;

            case R.id.plotWindowBPM:

                deletePlotWindow();
                // Create a new Fragment to be placed in the activity layout
                heartratePlotFragment = new HeartratePlotFragment();
                // Add the fragment to the 'fragment_container' FrameLayout
                Log.d(TAG, "Adding fragment");
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container, heartratePlotFragment, "heartratePlotFragment")
                        .commit();
                showPlotFragment();
                plotWindowContent = PlotWindowContent.BPM;
                return true;

            case R.id.plotWindowVector:

                deletePlotWindow();
                vectorPlotFragment = new VectorPlotFragment();
                vectorPlotFragment.setHistorySize(attysComm.getSamplingRateInHz()/2);
                vectorPlotFragment.setGain(gain);
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container, vectorPlotFragment, "vectorPlotFragment")
                        .commit();

                showPlotFragment();
                plotWindowContent = PlotWindowContent.VECTOR;
                return true;

            case R.id.plotWindowOff:
                hidePlotFragment();
                deletePlotWindow();
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


    private void showPlotFragment() {
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        frameLayout = (FrameLayout) findViewById(R.id.fragment_plot_container);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.5f));

    }

    private void hidePlotFragment() {
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));
    }


    private void deletePlotWindow() {
        if (heartratePlotFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(heartratePlotFragment).commit();
            heartratePlotFragment = null;
        }
        if (vectorPlotFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(vectorPlotFragment).commit();
            vectorPlotFragment = null;
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

        mux = AttysComm.ADC_MUX_ECG_EINTHOVEN;
        byte adcgain = (byte) (Integer.parseInt(prefs.getString("gainpref", "0")));
        attysComm.setAdc1_gain_index(adcgain);
        attysComm.setAdc0_mux_index(mux);
        attysComm.setAdc2_gain_index(adcgain);
        attysComm.setAdc1_mux_index(mux);

        byte data_separator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        attysComm.setDataSeparator(data_separator);

        powerlineHz = Float.parseFloat(prefs.getString("powerline", "50"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }

        samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "0"));
        if (samplingRate > 1) samplingRate = 1;

        attysComm.setAdc_samplingrate_index(samplingRate);
    }

}
