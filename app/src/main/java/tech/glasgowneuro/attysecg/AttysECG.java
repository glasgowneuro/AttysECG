package tech.glasgowneuro.attysecg;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import uk.me.berndporr.iirj.Butterworth;

import static java.lang.String.format;

public class AttysECG extends AppCompatActivity {

    private Timer timer = null;
    // screen refresh rate
    private final int REFRESH_IN_MS = 50;

    private final int MEDIANFILTER = 3;

    final int REQUEST_EXTERNAL_STORAGE = 1;

    private RealtimePlotView realtimePlotView = null;
    private InfoView infoView = null;
    private HRVView hrvView = null;
    private HeartratePlotFragment heartratePlotFragment = null;
    private VectorPlotFragment vectorPlotFragment = null;
    private ECGPlotFragment ecgPlotFragment = null;

    private MenuItem menuItemshowEinthoven = null;
    private MenuItem menuItemshowAugmented = null;
    private MenuItem menuItemplotWindowVector = null;
    private MenuItem menuItemshowHRV = null;
    ProgressBar progress = null;

    private AttysComm attysComm = null;
    private BluetoothDevice btAttysDevice = null;
    private byte samplingRate = AttysComm.ADC_RATE_250HZ;

    UpdatePlotTask updatePlotTask = null;

    private static final String TAG = "AttysECG";

    private Highpass highpass_II = null;
    private Highpass highpass_III = null;
    private float gain = 500;
    private float[] gain_settings = {250, 500, 1000};
    private Butterworth iirNotch_II = null;
    private Butterworth iirNotch_III = null;
    private double notchBW = 2.5; // Hz
    private int notchOrder = 2;
    private float powerlineHz = 50;
    private boolean full2chECGrecording = true;

    private boolean showEinthoven = true;
    private boolean showAugmented = true;
    private boolean showHRV = false;
    private float bpm = 0;
    private String bpmFromEinthovenLeadNo = "II";

    private ECG_rr_det ecg_rr_det_ch1 = null;
    private ECG_rr_det ecg_rr_det_ch2 = null;

    private float ytick = 0;

    private int[] actualChannelIdx;

    int ygapForInfo = 0;

    // debugging the ECG detector, commented out for production
    //double ecgDetOut;

    private int timestamp = 0;

    String[] labels = {
            "I", "II", "III",
            "aVR", "aVL", "aVF"};

    private String dataFilename = null;
    private byte dataSeparator = 0;

    private static String ATTYS_SUBDIR = "attys";
    public static File ATTYSDIR = new File(Environment.getExternalStorageDirectory().getPath(),
            ATTYS_SUBDIR);

    AlertDialog alertDialog = null;

    BeepGenerator beepGenerator = null;


    private class HRRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private PrintWriter textdataFileStream;
        private final File fullpath;

        // starts the recording
        private HRRecorder(String filename) throws IOException {
            fullpath = new File(AttysECG.ATTYSDIR, filename);
            textdataFileStream = new PrintWriter(new FileOutputStream(fullpath, true));
        }

        // saving one BPM signal
        private void saveData(float bpm) {
            if (null == textdataFileStream) return;
            char s = 9;
            long t = System.currentTimeMillis();
            String tmp = String.format(Locale.US, "%d%c", t, s);
            tmp = tmp + String.format(Locale.US, "%f", bpm);
            if (textdataFileStream != null) {
                textdataFileStream.format("%s\n", tmp);
                textdataFileStream.flush();
            }
        }

        private void shutdown() {
            if (null == textdataFileStream) return;
            textdataFileStream.flush();
            textdataFileStream.close();
            textdataFileStream = null;
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(fullpath);
            mediaScanIntent.setData(contentUri);
            sendBroadcast(mediaScanIntent);
        }
    }

    private HRRecorder hrRecorder = null;

    private class DataRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private final static byte DATA_SEPARATOR_TAB = 0;
        private final static byte DATA_SEPARATOR_COMMA = 1;
        private final static byte DATA_SEPARATOR_SPACE = 2;


        private PrintWriter textdataFileStream = null;
        private File textdataFile = null;
        private byte data_separator = DATA_SEPARATOR_TAB;
        long sample = 0;
        float bpm = 0;

        // starts the recording
        private java.io.FileNotFoundException startRec(File file) {
            sample = 0;
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
        private void stopRec() {
            if (textdataFileStream != null) {
                textdataFileStream.close();
                if (messageListener != null) {
                    messageListener.haveMessage(AttysComm.MESSAGE_STOPPED_RECORDING);
                }
                textdataFileStream = null;
                if (textdataFile != null) {
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(textdataFile);
                    mediaScanIntent.setData(contentUri);
                    sendBroadcast(mediaScanIntent);
                }
                textdataFile = null;
            }
        }

        // are we recording?
        private boolean isRecording() {
            return (textdataFileStream != null);
        }

        private File getFile() {
            return textdataFile;
        }

        private void setDataSeparator(byte s) {
            data_separator = s;
        }

        private byte getDataSeparator() {
            return data_separator;
        }

        private void setBPM(float _bpm) {
            bpm = _bpm;
        }

        private void saveData(float I, float II, float III,
                              float aVR, float aVL, float aVF) {

            if (textdataFileStream == null) return;

            char s = ' ';
            switch (data_separator) {
                case DATA_SEPARATOR_SPACE:
                    s = ' ';
                    break;
                case DATA_SEPARATOR_COMMA:
                    s = ',';
                    break;
                case DATA_SEPARATOR_TAB:
                    s = 9;
                    break;
            }
            double t = (double) sample / attysComm.getSamplingRateInHz();
            String tmp = format(Locale.US, "%f%c", t, s);
            if (full2chECGrecording) {
                tmp = tmp + format(Locale.US, "%f%c", I, s);
                tmp = tmp + format(Locale.US, "%f%c", II, s);
                tmp = tmp + format(Locale.US, "%f%c", III, s);
                tmp = tmp + format(Locale.US, "%f%c", aVR, s);
                tmp = tmp + format(Locale.US, "%f%c", aVL, s);
                tmp = tmp + format(Locale.US, "%f%c", aVF, s);
                tmp = tmp + format(Locale.US, "%f", bpm);
            } else {
                tmp = tmp + format(Locale.US, "%f%c", II, s);
                tmp = tmp + format(Locale.US, "%f", bpm);
            }
            bpm = 0;
            sample++;

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
                        attysComm.stop();
                    }
                    progress.setVisibility(View.GONE);
                    finish();
                    break;
                case AttysComm.MESSAGE_CONNECTED:
                    progress.setVisibility(View.GONE);
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
                    progress.setVisibility(View.VISIBLE);
            }
        }
    };


    AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
        @Override
        public void haveMessage(int msg) {
            handler.sendEmptyMessage(msg);
        }
    };


    private class UpdatePlotTask extends TimerTask {

        private String m_unit = "";
        private float scaling_factor = 1;

        private void resetAnalysis() {

            m_unit = AttysComm.CHANNEL_UNITS[AttysComm.INDEX_Analogue_channel_1];

            scaling_factor = 1;

            annotatePlot();
        }

        private void annotatePlot() {
            String small = String.format(Locale.US,
                    "1 sec/div, %1.01f mV/div, HR = %d BPM (from %s)",
                    ytick * 1000,
                    ((int) bpm),
                    bpmFromEinthovenLeadNo);
            if (dataRecorder.isRecording()) {
                small = small + " !!RECORDING to:" + dataFilename;
            }
            if (infoView != null) {
                if (attysComm != null) {
                    infoView.drawText(small);
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
            if (attysComm != null) nCh = AttysComm.NCHANNELS;
            if (attysComm != null) {
                float[] tmpSample = new float[nCh];
                float[] tmpMin = new float[nCh];
                float[] tmpMax = new float[nCh];
                float[] tmpTick = new float[nCh];
                String[] tmpLabels = new String[nCh];

                float max = attysComm.getADCFullScaleRange(0) / gain;
                ytick = 1.0F / gain / 10;
                annotatePlot();

                int n = 0;
                if (attysComm != null) {
                    n = attysComm.getNumSamplesAvilable();
                }
                if (realtimePlotView != null) {
                    if (!realtimePlotView.startAddSamples(n)) return;
                    for (int i = 0; ((i < n) && (attysComm != null)); i++) {
                        float[] sample = null;
                        sample = attysComm.getSampleFromBuffer();
                        if (sample != null) {
                            // debug ECG detector
                            // sample[AttysComm.INDEX_Analogue_channel_2] = (float)ecgDetOut;
                            timestamp++;

                            float II = sample[AttysComm.INDEX_Analogue_channel_1];

                            II = highpass_II.filter(II);

                            if (iirNotch_II != null) {
                                II = (float) iirNotch_II.filter((double) II);
                            }

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

                            if (ecg_rr_det_ch1 != null) {
                                ecg_rr_det_ch1.detect(II);
                            }

                            if (ecg_rr_det_ch2 != null) {
                                ecg_rr_det_ch2.detect(I);
                            }

                            float aVR = III / 2 - II;
                            float aVL = II / 2 - III;
                            float aVF = II / 2 + III / 2;

                            if (vectorPlotFragment != null) {
                                vectorPlotFragment.addValue(I, aVF);
                            }

                            if (ecgPlotFragment != null) {
                                ecgPlotFragment.addValue(I, II, III, aVR, aVL, aVF);
                            }

                            dataRecorder.saveData(I, II, III, aVR, aVL, aVF);

                            int nRealChN = 0;

                            if (full2chECGrecording) {

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
                            } else {
                                if (attysComm != null) {
                                    tmpMin[nRealChN] = -max;
                                    tmpMax[nRealChN] = max;
                                    tmpTick[nRealChN] = ytick;
                                    tmpLabels[nRealChN] = "Ch1";
                                    actualChannelIdx[nRealChN] = 1;
                                    tmpSample[nRealChN++] = II;
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
                    if (hrvView != null) {
                        if (hrvView.getVisibility() == View.VISIBLE) {
                            final float _bpm = bpm;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    hrvView.animate(_bpm,
                                            (float) (attysComm.getSamplingRateInHz() / 2.0));
                                }
                            });
                        }
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

    public static void createSubDir() {
        if (!ATTYSDIR.exists()) {
            ATTYSDIR.mkdirs();
        }
    }


    private void startRRrec() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("hrv_logging", true)) {
            AttysECG.createSubDir();
            if (null == hrRecorder) {
                try {
                    hrRecorder = new HRRecorder(prefs.getString("hrv_filename","hrv.tsv"));
                } catch (Exception e) {
                    hrRecorder = null;
                    Log.d(TAG,"Could not save the hrv file",e);
                    Toast.makeText(getApplicationContext(),
                            "Could not create the heartreate file.",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            if (null != hrRecorder) {
                hrRecorder.shutdown();
            }
            hrRecorder = null;
        }
    }


    private void stopRRRec() {
        if (null != hrRecorder) {
            hrRecorder.shutdown();
            hrRecorder = null;
        }
    }



    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestPermissions();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.main_activity_layout);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        progress = findViewById(R.id.indeterminateBar);
        hrvView = findViewById(R.id.hrvview);
        hrvView.setVisibility(View.INVISIBLE);

        int nChannels = AttysComm.NCHANNELS;
        iirNotch_II = new Butterworth();
        iirNotch_III = new Butterworth();
        actualChannelIdx = new int[nChannels];
        highpass_II = new Highpass();
        highpass_III = new Highpass();
        iirNotch_II = null;
        iirNotch_III = null;
        actualChannelIdx[0] = AttysComm.INDEX_Analogue_channel_1;
        startRRrec();
    }

    // this is called whenever the app is starting or re-starting
    @Override
    public void onStart() {
        super.onStart();

        startDAQ();
        adjustMenu();
        startRRrec();
    }


    private void adjustMenu() {
        if (menuItemplotWindowVector != null) {
            menuItemplotWindowVector.setEnabled(full2chECGrecording);
        }
        if (menuItemshowAugmented != null) {
            menuItemshowAugmented.setEnabled(full2chECGrecording);
        }
        if (menuItemshowEinthoven != null) {
            menuItemshowEinthoven.setEnabled(full2chECGrecording);
        }
        if (ecgPlotFragment != null) {
            ecgPlotFragment.setOfferAllChannels(full2chECGrecording);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePlotTask.resetAnalysis();

    }

    private void r_peak_detected(float bpm) {
        dataRecorder.setBPM(bpm);
        if (null != hrRecorder) {
            hrRecorder.saveData(bpm);
        }
        if (heartratePlotFragment != null) {
            heartratePlotFragment.addValue(bpm);
        }
        if (ecgPlotFragment != null) {
            ecgPlotFragment.rDet();
        }
        if (beepGenerator != null) {
            BeepGenerator.doBeep();
        }
        this.bpm = bpm;
    }


    private void noAttysFoundAlert() {
        alertDialog = new AlertDialog.Builder(this)
                .setTitle("No Attys found or bluetooth disabled")
                .setMessage("Before you can use the Attys you need to pair it with this device.")
                .setPositiveButton("Configure bluetooth", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent i = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(i);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        finish();
                    }
                })
                .show();
    }


    public void startDAQ() {

        btAttysDevice = AttysComm.findAttysBtDevice();
        if (btAttysDevice == null) {
            noAttysFoundAlert();
        }

        attysComm = new AttysComm(btAttysDevice);
        attysComm.registerMessageListener(messageListener);

        getsetAttysPrefs();

        highpass_II.setAlpha(1.0F / attysComm.getSamplingRateInHz());
        highpass_III.setAlpha(1.0F / attysComm.getSamplingRateInHz());

        realtimePlotView = findViewById(R.id.realtimeplotview);
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

        infoView = findViewById(R.id.infoview);
        infoView.setZOrderOnTop(true);
        infoView.setZOrderMediaOverlay(true);

        attysComm.start();

        ecg_rr_det_ch1 = new ECG_rr_det(attysComm.getSamplingRateInHz(), powerlineHz, MEDIANFILTER);

        ecg_rr_det_ch1.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
                                  float filtbpm,
                                  double amplitude,
                                  double confidence) {
                if (full2chECGrecording) {
                    if (ecg_rr_det_ch1.getAmplitude() > ecg_rr_det_ch2.getAmplitude()) {
                        r_peak_detected(bpm);
                        bpmFromEinthovenLeadNo = "II";
                        //Log.d(TAG,"RR det ch1");
                    }
                } else {
                    r_peak_detected(bpm);
                    bpmFromEinthovenLeadNo = "Ch1";
                }
            }
        });

        ecg_rr_det_ch2 = new ECG_rr_det(attysComm.getSamplingRateInHz(), powerlineHz, MEDIANFILTER);

        ecg_rr_det_ch2.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
                                  float filtbpm,
                                  double amplitude,
                                  double confidence) {
                if (full2chECGrecording) {
                    if (ecg_rr_det_ch2.getAmplitude() > ecg_rr_det_ch1.getAmplitude()) {
                        r_peak_detected(bpm);
                        bpmFromEinthovenLeadNo = "I";
                        //Log.d(TAG,"RR det ch3");
                    }
                }
            }
        });

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
            attysComm.stop();
            attysComm = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed AttysComm");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopRRRec();

        killAttysComm();

        if (dataRecorder != null) {
            dataRecorder.stopRec();
            dataRecorder = null;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }

        if (alertDialog != null) {
            if (alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            alertDialog = null;
        }
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

        stopRRRec();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Paused");
        }

    }


    @Override
    public void onStop() {
        super.onStop();

        stopRRRec();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped");
        }

        killAttysComm();

    }

    private void requestPermissions() {
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
    }


    /**
     * Called after permission has been granted
     */
    @Override
    final public void onRequestPermissionsResult(int requestCode,
                                                 String[] permissions,
                                                 int[] grantResults) {
        Log.v(TAG, "External storage permission results: " + requestCode);
        if (REQUEST_EXTERNAL_STORAGE != requestCode) return;
        if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Write permission to external memory granted");
            createSubDir();
        }
    }


    private void enterFilename() {

        createSubDir();

        final EditText filenameEditText = new EditText(this);
        filenameEditText.setSingleLine(true);

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
                                case DataRecorder.DATA_SEPARATOR_COMMA:
                                    dataFilename = dataFilename + ".csv";
                                    break;
                                case DataRecorder.DATA_SEPARATOR_SPACE:
                                    dataFilename = dataFilename + ".dat";
                                    break;
                                case DataRecorder.DATA_SEPARATOR_TAB:
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

        final List<String> files = new ArrayList<>();
        final String[] list = ATTYSDIR.list();
        if (list != null) {
            for (String file : list) {
                if (file != null) {
                    files.add(file);
                }
            }
        }

        final ListView listview = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
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
                        if (null != list) {
                            for (int i = 0; i < listview.getCount(); i++) {
                                if (checked.get(i)) {
                                    String filename = list[i];
                                    File fp = new File(ATTYSDIR, filename);
                                    final Uri u = FileProvider.getUriForFile(
                                            getBaseContext(),
                                            getApplicationContext().getPackageName() + ".fileprovider",
                                            fp);
                                    files.add(u);
                                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                                        Log.d(TAG, "filename=" + filename);
                                    }
                                }
                            }
                        }
                        sendIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
                        sendIntent.setType("text/*");
                        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(sendIntent, "Send your files"));
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();

        ViewGroup.LayoutParams layoutParams = listview.getLayoutParams();
        Screensize screensize = new Screensize(getWindowManager());
        layoutParams.height = screensize.getHeightInPixels() / 2;
        listview.setLayoutParams(layoutParams);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_attysecg, menu);

        menuItemshowEinthoven = menu.findItem(R.id.showEinthoven);
        menuItemshowAugmented = menu.findItem(R.id.showAugmented);
        menuItemplotWindowVector = menu.findItem(R.id.plotWindowVector);
        menuItemshowHRV = menu.findItem(R.id.showHRV);

        adjustMenu();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("hr_display", true)) {
            openWindowBPM();
        }

        return true;
    }

    private void toggleShowHRV() {
        showHRV = !showHRV;
        if (showHRV) {
            hrvView.reset();
            hrvView.setVisibility(View.VISIBLE);
        } else {
            hrvView.setVisibility(View.INVISIBLE);
        }
        menuItemshowHRV.setChecked(showHRV);
    }

    private void openWindowBPM() {
        deletePlotWindow();
        // Create a new Fragment to be placed in the activity layout
        heartratePlotFragment = new HeartratePlotFragment();
        // Add the fragment to the 'fragment_container' FrameLayout
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Adding heartrate fragment");
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_plot_container,
                        heartratePlotFragment,
                        "heartratePlotFragment")
                .commit();
        showPlotFragment();
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
                        File file = new File(ATTYSDIR, dataFilename.trim());
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

            case R.id.showHRV:
                toggleShowHRV();
                return true;

            case R.id.heartbeatsound:
                boolean b = item.isChecked();
                b = !b;
                item.setChecked(b);
                if (beepGenerator != null) {
                    beepGenerator.closeAudio();
                }
                if (b) {
                    beepGenerator = new BeepGenerator();
                } else {
                    beepGenerator = null;
                }

            case R.id.Ch1gain200:
                gain = gain_settings[0];
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.Ch1gain500:
                gain = gain_settings[1];
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.Ch1gain1000:
                gain = gain_settings[2];
                if (vectorPlotFragment != null) {
                    vectorPlotFragment.setGain(gain);
                }
                return true;

            case R.id.enterFilename:
                enterFilename();
                return true;

            case R.id.plotWindowBPM:
                openWindowBPM();
                return true;

            case R.id.plotWindowVector:

                if (full2chECGrecording) {
                    deletePlotWindow();
                    vectorPlotFragment = new VectorPlotFragment();
                    vectorPlotFragment.setHistorySize(attysComm.getSamplingRateInHz() / 2);
                    vectorPlotFragment.setGain(gain);
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.fragment_plot_container,
                                    vectorPlotFragment,
                                    "vectorPlotFragment")
                            .commit();

                    showPlotFragment();
                }
                return true;

            case R.id.plotWindowECG:

                deletePlotWindow();
                ecgPlotFragment = new ECGPlotFragment();
                ecgPlotFragment.setSamplingRate(attysComm.getSamplingRateInHz());
                getSupportFragmentManager().beginTransaction()
                        .add(R.id.fragment_plot_container,
                                ecgPlotFragment,
                                "ecgPlotFragment")
                        .commit();

                showPlotFragment();
                return true;

            case R.id.plotWindowOff:
                hidePlotFragment();
                deletePlotWindow();
                return true;

            case R.id.filebrowser:
                shareData();
                return true;

            case R.id.sourcecode:
                String url = "https://github.com/glasgowneuro/AttysECG";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;

            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    private void showPlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));

        frameLayout = findViewById(R.id.fragment_plot_container);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.5f));

    }

    private void hidePlotFragment() {
        FrameLayout frameLayout = findViewById(R.id.mainplotlayout);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 0.0f));
    }


    private synchronized void deletePlotWindow() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (fragments != null) {
            if (!(fragments.isEmpty())) {
                for (Fragment fragment : fragments) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        if (fragment != null) {
                            Log.d(TAG, "Removing fragment: " + fragment.getTag());
                        }
                    }
                    if (fragment != null) {
                        getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                    }
                }
            }
        }
        heartratePlotFragment = null;
        vectorPlotFragment = null;
        ecgPlotFragment = null;
    }


    private void getsetAttysPrefs() {
        byte mux;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Setting preferences");
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        boolean olddisplayAllCh = full2chECGrecording;
        full2chECGrecording = !(prefs.getBoolean("single_ch", false));
        if (olddisplayAllCh != full2chECGrecording) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Changing gain settings");
            }
            if (full2chECGrecording) {
                gain_settings[0] = 200;
                gain_settings[1] = 500;
                gain_settings[2] = 1000;
            } else {
                gain_settings[0] = 100;
                gain_settings[1] = 200;
                gain_settings[2] = 500;
            }
            gain = gain_settings[1];
        }

        mux = AttysComm.ADC_MUX_ECG_EINTHOVEN;
        byte adcgain = (byte) (Integer.parseInt(prefs.getString("gainpref", "0")));
        attysComm.setAdc1_gain_index(adcgain);
        attysComm.setAdc0_mux_index(mux);
        attysComm.setAdc2_gain_index(adcgain);
        attysComm.setAdc1_mux_index(mux);

        byte data_separator = (byte) (Integer.parseInt(prefs.getString("data_separator", "0")));
        dataRecorder.setDataSeparator(data_separator);

        powerlineHz = Float.parseFloat(prefs.getString("powerlineFreq", "50"));
        if (powerlineHz > 60) {
            powerlineHz = 60;
            Log.e(TAG, "Illegal mains frequency in the prefs.");
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "powerline=" + powerlineHz);
        }

        boolean notchOn = prefs.getBoolean("mainsfilter", true);

        if (notchOn) {
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

        samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "1"));
        if ((samplingRate > AttysComm.ADC_RATE_250HZ) || (samplingRate < AttysComm.ADC_RATE_125HZ)) {
            samplingRate = AttysComm.ADC_RATE_250HZ;
            Log.e(TAG, "Illegal samplingrate in the preferences");
        }

        attysComm.setAdc_samplingrate_index(samplingRate);
    }

}
