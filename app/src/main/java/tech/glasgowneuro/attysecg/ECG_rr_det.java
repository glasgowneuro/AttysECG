package tech.glasgowneuro.attysecg;

import android.util.Log;

import java.util.Arrays;

import uk.me.berndporr.iirj.Butterworth;

/**
 * ECG R-peak detector and heart rate detector
 * <p>
 * The detector uses the matched filter approach by creating
 * an IIR bandpass filter which looks like an R peak and
 * thus is a recursive matched filter. One could also say
 * it's a causal wavelet or perhaps just a bandpass filter
 * which covers the frequency range of the R peak. It's all
 * the same in different forms!
 * <p>
 * As an input the detector just gets the data samples
 * at a given sampling rate and then it detects the r-peak and
 * heart rate from it.
 * <p>
 * It also has a callback function which is called when
 * a heartbeat is detected (implemented as a listener).
 */


public class ECG_rr_det {

    String TAG = "ECG_rr_det";

    // how fast the adaptive threshold follows changes in ECG
    // amplitude. Realisic values: 0.1 .. 1.0
    // 0.1 = slow recovery after an artefact but no wrong detections
    // 1 = fast recovery after an artefact but possibly wrong detections
    private final float adaptive_threshold_decay_constant = 0.5F;

    // the threshold for the detection is 0.6 times smaller than the amplitude
    private final float threshold_factor = 0.6F;

    // bandwidth of the powerline filter
    private final double notchBW = 2.5; // Hz

    // notch order of the powerline filter
    private final int notchOrder = 2;

    // ignores 1000 samples to let the filter settle
    private int ignoreECGdetector = 1000;

    // adaptive amplitude value of the detector output
    private double amplitude = 0.0;

    public double getAmplitude() {
        return amplitude;
    }

    private long timestamp = 0;

    // previous timestamp
    private long t2 = 0;

    // timewindow not to detect an R peak
    private int doNotDetect = 0;

    // three HR's are stored and a median filter is applied to them
    private float[] hrBuffer = new float[3];
    private float[] sortBuffer = new float[3];

    // the R preak detector. This is a matched filter implemented as IIR
    private Butterworth ecgDetector = new Butterworth();

    // mains filter
    private Butterworth ecgDetNotch = new Butterworth();

    // sampling rate in Hz
    private final float samplingRateInHz;

    // powerline interference
    private final float powerlineHz;

    // heartrate in BPM after median filtering (3 bpm readings)
    private float filtBPM = 0;

    // heartrate in BPM without median filtering (might have 1/2 bpm readings)
    private float unfiltBPM = 0;

    // constructor
    // provide the sampling rate and the powerline frequency
    public ECG_rr_det(float _samplingrateInHz, float _powerlineHz) {
        samplingRateInHz = _samplingrateInHz;
        powerlineHz = _powerlineHz;
        // this fakes an R peak so we have a matched filter!
        ecgDetector.bandPass(2, samplingRateInHz, 20, 15);
        ecgDetNotch.bandStop(notchOrder, samplingRateInHz, powerlineHz, notchBW);
        reset();
    }

    // this is a callback which is called whenever an R peak is detected
    // gives back the sample number from last reset, the heartrate in bpm (filtered and unfiltered),
    // the amplitude of the R peak in arbitrary units and the confidence of the
    // detection: 1 means just OK, greater than one means more confident
    public interface RRlistener {
        void haveRpeak(long samplenumber,
                       float filtBpm,
                       float unFiltBpm,
                       double amplitude,
                       double confidence);
    }

    private RRlistener rrListener = null;

    public void setRrListener(RRlistener _rrListener) {
        rrListener = _rrListener;
    }

    // reset detector
    public void reset() {
        amplitude = 0;
        t2 = 0;
        timestamp = 0;
        doNotDetect = (int) samplingRateInHz;
        ignoreECGdetector = (int) samplingRateInHz;
        hrBuffer[0] = 0;
        hrBuffer[1] = 0;
        hrBuffer[2] = 0;
    }

    float getFiltBPM() {
        return filtBPM;
    }

    float getUnFiltBPM() {
        return unfiltBPM;
    }

    // detect r peaks
    // input: ECG samples at the specified sampling rate
    public void detect(float v) {
        double h = ecgDetNotch.filter(v * 1000);
        h = ecgDetector.filter(h);
        if (ignoreECGdetector > 0) {
            ignoreECGdetector--;
            h = 0;
        }
        h = h * h;
        if (h > amplitude) {
            amplitude = h;
        }
        amplitude = amplitude - adaptive_threshold_decay_constant * amplitude / samplingRateInHz;
        //Log.d(TAG,"h="+h+",amplitude="+amplitude);
        if (doNotDetect > 0) {
            doNotDetect--;
        } else {
            double threshold = threshold_factor * amplitude;
            //Log.d(TAG,"h="+h+",thres="+threshold);
            if (h > threshold) {
                //Log.d(TAG,"DET! h="+h+",thres="+threshold);
                float t = (timestamp - t2) / samplingRateInHz;
                float bpm = 1 / t * 60;
                //Log.d(TAG,"bpm="+bpm);
                if ((bpm > 30) && (bpm < 300)) {
                    hrBuffer[2] = hrBuffer[1];
                    hrBuffer[1] = hrBuffer[0];
                    hrBuffer[0] = bpm;
                    unfiltBPM = bpm;
                    System.arraycopy(hrBuffer, 0, sortBuffer, 0, hrBuffer.length);
                    Arrays.sort(sortBuffer);
                    filtBPM = sortBuffer[1];
                    if (filtBPM > 0) {
                        // Log.d(TAG,"h="+h+",amplitude="+amplitude+" bpm="+filtBPM);
                        rrListener.haveRpeak(timestamp, filtBPM, unfiltBPM, amplitude, h / threshold);
                    }
                }
                t2 = timestamp;
                // advoid 1/5 sec
                doNotDetect = (int) samplingRateInHz / 5;
            }
        }
        timestamp++;
    }
}
