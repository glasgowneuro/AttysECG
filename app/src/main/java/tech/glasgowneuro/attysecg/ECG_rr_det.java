package tech.glasgowneuro.attysecg;

import uk.me.berndporr.firj.Fir1;
import uk.me.berndporr.iirj.Butterworth;

/**
 * ECG R-peak detector and heart rate detector.
 * Sampling rate needs to be 250Hz (default Attys rate).
 * <p>
 * The detector uses the wavelet approach with a DB3
 * wavelet which looks like an R peak and then used
 * as a matched filter.
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

    private static final double[] waveletDB3 = {
            1.10265752e-02,
            2.67449277e-02,
            4.19878574e-02,
            6.03947231e-02,
            7.61275365e-02,
            9.21548684e-02,
            1.11568926e-01,
            1.32278887e-01,
            6.45829680e-02,
            -3.97635130e-02,
            -1.38929884e-01,
            -2.62428322e-01,
            -3.62246804e-01,
            -4.62843343e-01,
            -5.89607507e-01,
            -7.25363076e-01,
            -3.36865858e-01,
            2.67715108e-01,
            8.40176767e-01,
            1.55574430e+00,
            1.18688954e+00,
            4.20276324e-01,
            -1.51697311e-01,
            -9.42076108e-01,
            -7.93172332e-01,
            -3.26343710e-01,
            -1.24552779e-01,
            2.12909254e-01,
            1.75770320e-01,
            1.47523075e-02,
            8.22192707e-03,
            -3.02920592e-02,
            -2.21119497e-02,
            7.30703025e-03,
            2.83200488e-03,
            -1.16759765e-03
    };

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

    // ignoring RR values at th start
    private int ignoreRRvalue = 3;

    // the R preak detector. This is a matched filter implemented as an FIR.
    private Fir1 ecgDetector = new Fir1(waveletDB3);

    // mains filter
    private Butterworth ecgDetNotch = new Butterworth();

    // sampling rate in Hz
    private float samplingRateInHz;

    // powerline interference
    private float powerlineHz;

    // constructor
    // provide the sampling rate, the powerline frequency and the median filter size
    public ECG_rr_det(float _samplingrateInHz, float _powerlineHz) {
        init(_samplingrateInHz, _powerlineHz);
    }

    private void init(float _samplingrateInHz, float _powerlineHz) {
        samplingRateInHz = _samplingrateInHz;
        powerlineHz = _powerlineHz;
        // this fakes an R peak so we have a matched filter!
        ecgDetNotch.bandStop(notchOrder, samplingRateInHz, powerlineHz, notchBW);
        reset();
    }

    // this is a callback which is called whenever an R peak is detected
    // gives back the sample number from last reset, the heartrate in bpm (filtered and unfiltered),
    // the amplitude of the R peak in arbitrary units and the confidence of the
    // detection: 1 means just OK, greater than one means more confident
    public interface RRlistener {
        void haveRpeak(long samplenumber,
                       float bpm,
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
                        if (bpm > 0) {
                            // still missed a heartbeat?
                            if (((bpm * 1.5) < prevBPM) || ((bpm * 0.75) > prevBPM)) {
                                ignoreRRvalue = 3;
                            } else {
                                if (rrListener != null) {
                                    rrListener.haveRpeak(timestamp,
                                            bpm,
                                            amplitude, h / threshold);
                                }
                            }
                            prevBPM = bpm;
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
