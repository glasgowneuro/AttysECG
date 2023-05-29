package tech.glasgowneuro.attysecg;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.os.IBinder;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
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

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import tech.glasgowneuro.attyscomm.AttysComm;
import tech.glasgowneuro.attyscomm.AttysService;
import uk.me.berndporr.iirj.Butterworth;

import static java.lang.String.format;

public class AttysECG extends AppCompatActivity {

    private static final String TAG = AttysECG.class.getSimpleName();

    private AttysService attysService = null;

    // screen refresh rate
    private static final int REFRESH_IN_MS = 50;

    public static final String HR_FILENAME = "hr.tsv";

    private Timer timer = null;

    private static int audioSessionID;

    private RealtimePlotView realtimePlotView = null;
    private InfoView infoView = null;
    private HRVView hrvView = null;
    private HeartratePlotFragment heartratePlotFragment = null;
    private VectorPlotFragment vectorPlotFragment = null;
    private ECGPlotFragment ecgPlotFragment = null;
    private LeadsView leadsView = null;

    private MenuItem menuItemshowEinthoven = null;
    private MenuItem menuItemshowAugmented = null;
    private MenuItem menuItemplotWindowVector = null;
    private MenuItem menuItemshowHRV = null;

    MenuItem menuItemPref = null;
    MenuItem menuItemRec = null;
    MenuItem menuItemBrowser = null;
    MenuItem menuItemSource = null;

    private ProgressBar progress = null;

    private boolean leadsOff = false;

    private UpdatePlotTask updatePlotTask = new UpdatePlotTask();

    private Highpass highpass_II = null;
    private Highpass highpass_III = null;
    private float gain = 500;
    final private float[] gain_settings = {250, 500, 1000};
    private Butterworth iirNotch_II = null;
    private Butterworth iirNotch_III = null;
    static final private double notchBW = 2.5; // Hz
    static final private int notchOrder = 2;
    private float powerlineHz = 50;
    private boolean full2chECGrecording = true;

    private boolean showEinthoven = true;
    private boolean showAugmented = true;
    private boolean showHRV = false;
    private float bpm = 0;
    private String bpmFromEinthovenLeadNo = "II";

    private ECG_rr_det ecg_rr_det_ch1 = null;
    private ECG_rr_det ecg_rr_det_ch2 = null;

    private int IIok = 0;
    private int IIIok = 0;

    private int visCtr = 0;

    private float ytick = 0;

    private int ygapForInfo = 0;

    private int timestamp = 0;

    private static final String[] labels = {
            "I", "II", "III",
            "aVR", "aVL", "aVF"};

    private String dataFilename = null;

    private AlertDialog alertDialog = null;

    private BeepGenerator beepGenerator = null;

    private ServiceConnection serviceConnection = null;

    private final DataRecorder dataRecorder = new DataRecorder();

    private void startAttysService() {
        final Intent intent = new Intent(getBaseContext(), AttysService.class);
        serviceConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d(TAG, "Attys service connected. Starting now.");
                startService(intent);
                AttysService.AttysBinder binder = (AttysService.AttysBinder) service;
                attysService = binder.getService();
                if (null == attysService) {
                    Log.e(TAG, "attysService=null in onServiceConnected");
                    return;
                }
                startDAQ();
            }
            public void onServiceDisconnected(ComponentName className) {
                if (attysService != null) {
                    attysService.stopAttysComm();
                }
                attysService = null;
            }
        };
        Log.d(TAG, "Binding Player service");
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }


    private void stopAttysService() {
        if (serviceConnection == null) return;
        if (attysService != null) {
            attysService.stopAttysComm();
        }
        unbindService(serviceConnection);
        stopService(new Intent(getBaseContext(), AttysService.class));
    }


    private class HRRecorder {
        /////////////////////////////////////////////////////////////
        // saving data into a file

        private PrintWriter textdataFileStream;
        private final File fullpath;

        // starts the recording
        private HRRecorder(String filename) throws IOException {
            fullpath = new File(getBaseContext().getExternalFilesDir(null), filename);
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
            if (fullpath != null) {
                MediaScannerConnection.scanFile(getBaseContext(),
                        new String[]{fullpath.toString()}, null,
                        (path, uri) -> Log.d(TAG, "Scanned:" + path));
            }
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
        private void startRec(File file) throws java.io.FileNotFoundException {
            sample = 0;
            textdataFileStream = new PrintWriter(file);
            textdataFile = file;
            messageListener.haveMessage(AttysComm.MESSAGE_STARTED_RECORDING);
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
                    MediaScannerConnection.scanFile(getBaseContext(),
                            new String[] { textdataFile.toString() }, null,
                            new MediaScannerConnection.OnScanCompletedListener() {
                                public void onScanCompleted(String path, Uri uri) {
                                    if (null != path) {
                                        Log.d(TAG, "Scanned:" + path);
                                    }
                                }
                            });
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
            double t = (double) sample / attysService.getAttysComm().getSamplingRateInHz();
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

    public void handleMessage(final int msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (msg) {
                    case AttysComm.MESSAGE_ERROR:
                        Toast.makeText(getApplicationContext(),
                                "Bluetooth connection problem", Toast.LENGTH_SHORT).show();
                        attysService.getAttysComm().stop();
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
        });
    }

    AttysComm.MessageListener messageListener = new AttysComm.MessageListener() {
        @Override
        public void haveMessage(int msg) {
            handleMessage(msg);
        }
    };


    private class UpdatePlotTask extends TimerTask {

        private void resetAnalysis() {
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
                infoView.drawText(small);
            }
            if ((leadsView != null) && leadsOff) {
                final boolean r = (IIok == 0);
                final boolean f = (IIok == 0);
                final boolean l = (IIIok == 0);
                if (r && l) {
                    if (visCtr > 0) {
                        visCtr--;
                    }
                } else {
                    visCtr = 30;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (visCtr > 0) {
                            leadsView.setVisibility(View.VISIBLE);
                        } else {
                            leadsView.setVisibility(View.INVISIBLE);
                        }
                        leadsView.setLeadStatus(r, l, f);
                    }
                });
            }
        }

        public synchronized void run() {

            if (attysService.getAttysComm().hasFatalError()) {
                // Log.d(TAG,String.format("No bluetooth connection"));
                handleMessage(AttysComm.MESSAGE_ERROR);
                return;
            }
            if (!attysService.getAttysComm().hasActiveConnection()) return;

            int nCh = 0;
            nCh = AttysComm.NCHANNELS;
            float[] tmpSample = new float[nCh];
            float[] tmpMin = new float[nCh];
            float[] tmpMax = new float[nCh];
            float[] tmpTick = new float[nCh];
            String[] tmpLabels = new String[nCh];

            float max = attysService.getAttysComm().getADCFullScaleRange(0) / gain;
            ytick = 1.0F / gain / 10;
            annotatePlot();

            int n = 0;
            n = attysService.getAttysComm().getNumSamplesAvilable();
            if (realtimePlotView != null) {
                if (!realtimePlotView.startAddSamples(n)) return;
                for (int i = 0; (i < n); i++) {
                    float[] sample = attysService.getAttysComm().getSampleFromBuffer();
                    if (sample != null) {
                        timestamp++;

                        float II = sample[AttysComm.INDEX_Analogue_channel_1];
                        float thres = attysService.getAttysComm().getADCFullScaleRange(0) * 0.9F;
                        if (Math.abs(II) > thres) {
                            IIok = attysService.getAttysComm().getSamplingRateInHz();
                        } else {
                            if (IIok > 0) IIok--;
                        }

                        II = highpass_II.filter(II);

                        if (iirNotch_II != null) {
                            II = (float) iirNotch_II.filter((double) II);
                        }

                        float III = sample[AttysComm.INDEX_Analogue_channel_2];
                        thres = attysService.getAttysComm().getADCFullScaleRange(1) * 0.9F;
                        if (Math.abs(III) > thres) {
                            IIIok = attysService.getAttysComm().getSamplingRateInHz();
                        } else {
                            if (IIIok > 0) IIIok--;
                        }

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
                            ecg_rr_det_ch1.detect(II, (IIok > 0) || (IIIok > 0));
                        }

                        if (ecg_rr_det_ch2 != null) {
                            ecg_rr_det_ch2.detect(I, (IIok > 0) || (IIIok > 0));
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
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[0];
                                tmpSample[nRealChN++] = I;
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[1];
                                tmpSample[nRealChN++] = II;
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[2];
                                tmpSample[nRealChN++] = III;
                            }
                            if (showAugmented) {
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[3];
                                tmpSample[nRealChN++] = aVR;
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[4];
                                tmpSample[nRealChN++] = aVL;
                                tmpMin[nRealChN] = -max;
                                tmpMax[nRealChN] = max;
                                tmpTick[nRealChN] = ytick;
                                tmpLabels[nRealChN] = labels[5];
                                tmpSample[nRealChN++] = aVF;
                            }
                        } else {
                            tmpMin[nRealChN] = -max;
                            tmpMax[nRealChN] = max;
                            tmpTick[nRealChN] = ytick;
                            tmpLabels[nRealChN] = "Ch1";
                            tmpSample[nRealChN++] = II;
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
                                        (float) (attysService.getAttysComm().getSamplingRateInHz() / 2.0));
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

    private void toBackground() {
        killTimer();
        if (dataRecorder.isRecording()) return;
        attysService.stopAttysComm();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Killed AttysComm");
        }
    }

    @Override
    public void onBackPressed() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Back button pressed");
        }
        toBackground();
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startActivity(startMain);
    }


    private void startRRrec() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("hrv_logging", false)) {
            if (null == hrRecorder) {
                try {
                    hrRecorder = new HRRecorder(HR_FILENAME);
                } catch (Exception e) {
                    hrRecorder = null;
                    Log.d(TAG,"Could not save the hr file: "+HR_FILENAME,e);
                    Toast.makeText(getApplicationContext(),
                            "Could not create the heartrate file: "+HR_FILENAME,
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

    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), isGranted -> {
                // isGranted is a map of the permissions (Strings) to boolean values.
                if (isGranted.containsValue(false)) {
                    finish();
                }
                if (AttysComm.findAttysBtDevice() == null) {
                    noAttysFoundAlert();
                }
            });

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestPermissionsAndroid12() {
        final String[] ANDROID_12_PERMISSIONS = new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        };
        for(String p:ANDROID_12_PERMISSIONS) {
            if (!(ContextCompat.checkSelfPermission(getBaseContext(), p) ==
                    PackageManager.PERMISSION_GRANTED)) {
                requestPermissionLauncher.launch(ANDROID_12_PERMISSIONS);
                return;
            }
        }
    }


    private void requestBTpermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionsAndroid12();
        } else {
            if (AttysComm.findAttysBtDevice() == null) {
                noAttysFoundAlert();
            }
        }
    }


    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.equals(intent.getAction(), Intent.ACTION_BOOT_COMPLETED)) {
                Log.d(TAG,"Boot receiver event.");
                // Register your reporting alarms here.
            }
        }
    }


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        startAttysService();

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        setContentView(R.layout.main_activity_layout);

        Toolbar myToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioSessionID = audioManager.generateAudioSessionId();

        progress = findViewById(R.id.indeterminateBar);
        hrvView = findViewById(R.id.hrvview);
        hrvView.setVisibility(View.INVISIBLE);
        leadsView = findViewById(R.id.leadsview);

        realtimePlotView = findViewById(R.id.realtimeplotview);
        realtimePlotView.setMaxChannels(15);
        realtimePlotView.init();

        infoView = findViewById(R.id.infoview);
        infoView.setZOrderOnTop(true);
        infoView.setZOrderMediaOverlay(true);

        requestBTpermissions();

        iirNotch_II = new Butterworth();
        iirNotch_III = new Butterworth();
        highpass_II = new Highpass();
        highpass_III = new Highpass();
        iirNotch_II = null;
        iirNotch_III = null;

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

    private void setRecColour(int c) {
        if (null == menuItemRec) return;
        SpannableString s = new SpannableString(menuItemRec.getTitle());
        s.setSpan(new ForegroundColorSpan(c), 0, s.length(), 0);
        menuItemRec.setTitle(s);
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
            beepGenerator.doBeep();
        }
        this.bpm = bpm;
    }


    private void noAttysFoundAlert() {
        alertDialog = new AlertDialog.Builder(this)
                .setTitle("No Attys found or bluetooth disabled")
                .setMessage("Before you can use the Attys you need to pair it with this device.")
                .setPositiveButton("Configure bluetooth", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        try {
                            Intent i = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                            startActivity(i);
                        } catch (Exception e) {
                            FirebaseCrashlytics.getInstance().recordException(e);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getApplicationContext(),
                                            "Bluetooth settings have been blocked by the phone. "+
                                            "Please enter them manually.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
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
        if (null == attysService) {
            Log.d(TAG,"attys service is still null.");
            return;
        }
        attysService.getAttysComm().registerMessageListener(messageListener);

        getsetAttysPrefs();

        highpass_II.setAlpha(1.0F / attysService.getAttysComm().getSamplingRateInHz());
        highpass_III.setAlpha(1.0F / attysService.getAttysComm().getSamplingRateInHz());

        ecg_rr_det_ch1 = new ECG_rr_det(attysService.getAttysComm().getSamplingRateInHz(), powerlineHz);

        ecg_rr_det_ch1.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
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

        ecg_rr_det_ch2 = new ECG_rr_det(attysService.getAttysComm().getSamplingRateInHz(), powerlineHz);

        ecg_rr_det_ch2.setRrListener(new ECG_rr_det.RRlistener() {
            @Override
            public void haveRpeak(long samplenumber,
                                  float bpm,
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
        attysService.startAttysComm();
    }

    private void killTimer() {

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Killed timer");
            }
        }

        updatePlotTask.cancel();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Killed update Plot Task");
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        dataRecorder.stopRec();
        stopRRRec();
        toBackground();
        attysService.stopAttysComm();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Destroy!");
        }

        if (alertDialog != null) {
            if (alertDialog.isShowing()) {
                alertDialog.dismiss();
            }
            alertDialog = null;
        }
        stopAttysService();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Restarting");
        }

        startDAQ();
    }


    @Override
    public void onStop() {
        super.onStop();

        toBackground();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stopped");
        }
    }


    private void shareData() {

        final List<String> files = new ArrayList<>();
        final String[] list = Objects.requireNonNull(getBaseContext().getExternalFilesDir(null)).list();
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
                .setTitle("Share files")
                .setMessage("Folder:\n"+ Objects.requireNonNull(getBaseContext().getExternalFilesDir(null)).toString())
                .setView(listview)
                .setPositiveButton("SHARE", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SparseBooleanArray checked = listview.getCheckedItemPositions();
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND_MULTIPLE);
                        ArrayList<Uri> files = new ArrayList<>();
                        if (null != list) {
                            for (int i = 0; i < listview.getCount(); i++) {
                                if (checked.get(i)) {
                                    String filename = list[i];
                                    File fp = new File(getBaseContext().getExternalFilesDir(null), filename);
                                    final Uri u = FileProvider.getUriForFile(
                                            getBaseContext(),
                                            getApplicationContext().getPackageName() + ".fileprovider",
                                            fp);
                                    files.add(u);
                                    Log.d(TAG, "filename=" + filename);
                                }
                            }
                        }
                        if (files.isEmpty()) return;
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
        Screensize screensize = new Screensize(getBaseContext());
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

        menuItemPref = menu.findItem(R.id.preferences);
        menuItemRec = menu.findItem(R.id.toggleRec);
        menuItemBrowser = menu.findItem(R.id.filebrowser);
        menuItemSource = menu.findItem(R.id.sourcecode);

        setRecColour(Color.GRAY);

        adjustMenu();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("hr_display", true)) {
            openWindowBPM();
        }

        return true;
    }

    private void enableMenuitems(boolean doit) {
        menuItemPref.setEnabled(doit);
        menuItemSource.setEnabled(doit);
        menuItemBrowser.setEnabled(doit);
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
                .commitAllowingStateLoss();
        showPlotFragment();
    }

    private void toggleRec() {
        if (dataRecorder.isRecording()) {
            dataRecorder.stopRec();
            setRecColour(Color.GRAY);
            enableMenuitems(true);
        } else {
            Date date = new Date();
            SimpleDateFormat dateFormat =
                    new SimpleDateFormat("yyyy-dd-MM-HH-mm-ss",Locale.US);
            dataFilename = "attysecg-" + dateFormat.format(date) + ".tsv";
            File file = new File(getBaseContext().getExternalFilesDir(null),dataFilename);
            try {
                dataRecorder.startRec(file);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),
                        "Could not save the file.",
                        Toast.LENGTH_LONG).show();
                Log.d(TAG, "Could not open data file: " + e.getMessage());
                return;
            }
            if (dataRecorder.isRecording()) {
                setRecColour(Color.RED);
                enableMenuitems(false);
                Log.d(TAG, "Saving to " + file.getAbsolutePath());
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {

            case R.id.preferences:
                Intent intent = new Intent(this, PrefsActivity.class);
                startActivity(intent);
                return true;

            case R.id.toggleRec:
                toggleRec();
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
                    beepGenerator = new BeepGenerator(audioSessionID);
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

            case R.id.plotWindowBPM:
                openWindowBPM();
                return true;

            case R.id.plotWindowVector:

                if (full2chECGrecording) {
                    deletePlotWindow();
                    vectorPlotFragment = new VectorPlotFragment();
                    vectorPlotFragment.setHistorySize(attysService.getAttysComm().getSamplingRateInHz() / 2);
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
                ecgPlotFragment.setSamplingRate(attysService.getAttysComm().getSamplingRateInHz());
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

            case R.id.hrExplorer:
                i = new Intent(AttysECG.this,HRexplorer.class);
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
        attysService.getAttysComm().setAdc1_gain_index(adcgain);
        attysService.getAttysComm().setAdc0_mux_index(mux);
        attysService.getAttysComm().setAdc2_gain_index(adcgain);
        attysService.getAttysComm().setAdc1_mux_index(mux);

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
                    attysService.getAttysComm().getSamplingRateInHz(), powerlineHz, notchBW);
            iirNotch_III.bandStop(notchOrder,
                    attysService.getAttysComm().getSamplingRateInHz(), powerlineHz, notchBW);
        } else {
            iirNotch_II = null;
            iirNotch_III = null;
        }

        byte samplingRate = (byte) Integer.parseInt(prefs.getString("samplingrate", "1"));
        if ((samplingRate > AttysComm.ADC_RATE_250HZ) || (samplingRate < AttysComm.ADC_RATE_125HZ)) {
            samplingRate = AttysComm.ADC_RATE_250HZ;
            Log.e(TAG, "Illegal samplingrate in the preferences");
        }

        attysService.getAttysComm().setAdc_samplingrate_index(samplingRate);

        leadsOff = prefs.getBoolean("leadsoff", true);

        if (leadsOff) {
            attysService.getAttysComm().setBiasCurrent(AttysComm.ADC_CURRENT_22NA);
            attysService.getAttysComm().enableCurrents(false,true,true);
            leadsView.setVisibility(View.VISIBLE);
        } else {
            leadsView.setVisibility(View.INVISIBLE);
        }
    }

}
