package tech.glasgowneuro.attysecg;

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
    private final float adaptive_threshold_decay_constant = 0.25F;

    // the threshold for the detection is 0.6 times smaller than the amplitude
    private final float threshold_factor = 0.6F;

    // bandwidth of the powerline filter
    private final double notchBW = 2.5; // Hz

    // notch order of the powerline filter
    private final int notchOrder = 2;

    // 10mV as the threshold the bandpass filtered ECG is an artefact
    private double artefact_threshold = 10;

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

    // previously detected heartrate
    private float prevBPM = 0;

    // timewindow not to detect an R peak
    private int doNotDetect = 0;

    private int ignoreRRvalue = 2;

    // three HR's are stored and a median filter is applied to them
    private float[] hrBuffer;
    private float[] sortBuffer;

    // the R preak detector. This is a matched filter implemented as IIR
    private Butterworth ecgDetector = new Butterworth();

    // mains filter
    private Butterworth ecgDetNotch = new Butterworth();

    // sampling rate in Hz
    private float samplingRateInHz;

    // powerline interference
    private float powerlineHz;

    // heartrate in BPM after median filtering (3 bpm readings)
    private float filtBPM = 0;

    // heartrate in BPM without median filtering (might have 1/2 bpm readings)
    private float unfiltBPM = 0;

    private int medianFilterSize;

    // constructor
    // provide the sampling rate, the powerline frequency and the median filter size
    public ECG_rr_det(float _samplingrateInHz, float _powerlineHz, int _medianFilterSize) {
        init(_samplingrateInHz, _powerlineHz, _medianFilterSize);
    }

    // constructor
    // provide the sampling rate and the powerline frequency
    public ECG_rr_det(float _samplingrateInHz, float _powerlineHz) {
        init(_samplingrateInHz, _powerlineHz, 5);
    }

    private void init(float _samplingrateInHz, float _powerlineHz, int _medianFilterSize) {
        samplingRateInHz = _samplingrateInHz;
        powerlineHz = _powerlineHz;
        medianFilterSize = _medianFilterSize;
        hrBuffer = new float[medianFilterSize];
        sortBuffer = new float[medianFilterSize];
        // this fakes an R peak so we have a matched filter!
        ecgDetector.bandPass(6, samplingRateInHz, 20, 15);
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
        for (int i=0; i < medianFilterSize; i++){
            hrBuffer[i] = 0;
        }
    }

    float getFiltBPM() {
        return filtBPM;
    }

    float getUnFiltBPM() {
        return unfiltBPM;
    }

    // detect r peaks
    // input: ECG samples at the specified sampling rate and in V
    public void detect(float v) {
        double h = ecgDetNotch.filter(v * 1000);
        h = ecgDetector.filter(h);
        if (ignoreECGdetector > 0) {
            ignoreECGdetector--;
            return;
        }
        h = h * h;
        //Log.d(TAG," "+(int)(10*Math.sqrt(h)));
        // above 0.5mV
        if (Math.sqrt(h) > artefact_threshold) {
            // ignore signal for 1 sec
            ignoreECGdetector = ((int) samplingRateInHz);
            //Log.d(TAG,"artefact="+(Math.sqrt(h)));
            ignoreRRvalue = 2;
            return;
        }
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
                if ((bpm > 30) && (bpm < 250)) {
                    if (ignoreRRvalue > 0) {
                        ignoreRRvalue--;
                    } else {
                        for (int i = 0; i < (medianFilterSize - 1); i++) {
                            hrBuffer[i + 1] = hrBuffer[i];
                        }
                        hrBuffer[0] = bpm;
                        unfiltBPM = bpm;
                        System.arraycopy(hrBuffer, 0, sortBuffer, 0, hrBuffer.length);
                        Arrays.sort(sortBuffer);
                        filtBPM = sortBuffer[(int) Math.floor(medianFilterSize / 2)];
                        if (filtBPM > 0) {
                            // still missed a heartbeat?
                            if (Math.abs(filtBPM*2-prevBPM)<5) {
                                // that's most likely a missed heartbeat because it's
                                // exactly half of the previous heartrate
                                ignoreRRvalue = 3;
                            } else {
                                if (rrListener != null) {
                                    rrListener.haveRpeak(timestamp,
                                            filtBPM,
                                            unfiltBPM,
                                            amplitude, h / threshold);
                                }
                            }
                            prevBPM = filtBPM;
                        }
                    }
                } else {
                    ignoreRRvalue = 3;
                }
                t2 = timestamp;
                // advoid 1/5 sec
                doNotDetect = (int) samplingRateInHz / 5;
            }
        }
        timestamp++;
    }
}
