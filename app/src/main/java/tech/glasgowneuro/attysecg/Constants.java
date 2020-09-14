package tech.glasgowneuro.attysecg;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class Constants {

    static List<String> savedBPMs = new ArrayList<>();
    static List<String> savedDateTimes = new ArrayList<>();
    static List<Bitmap> savedPlots = new ArrayList<>();

    // Matched filter coefficients (from template)
    static float[] float_coefficients = {(float) 0.00631364, (float) -0.03792876, (float) -0.02550926,  (float) 0.13042721,  (float) 0.42606094,
            (float) 0.712838  ,  (float) 0.7865376 ,  (float) 0.5640064 ,  (float) 0.16566347, (float) -0.19206063,
            (float) -0.37875745, (float) -0.4173598 , (float) -0.39792314, (float) -0.36837435, (float) -0.32127404,
            (float) -0.25116646, (float) -0.18220228, (float) -0.13577957, (float) -0.10202894, (float) -0.06183188};

    // Band-Pass 0.5Hz - 5Hz & 60 taps
    static float[] FIR_coefficients = { (float) -1.15577091e-04, (float) -6.05338384e-06, (float) -1.15577091e-04, (float) 1.16939048e-04,
            (float) 2.01344659e-03, (float) 3.16797021e-03, (float)-1.15577091e-04, (float)-3.82797463e-03,
            (float) -1.15577091e-04,(float)  6.18990093e-03,(float) -1.15577091e-04,(float) -1.74093089e-02,
            (float) -2.16450558e-02,(float) -5.19936522e-03,(float) -1.15577091e-04,(float) -3.14430467e-02,
            (float) -6.42023418e-02,(float) -4.56894505e-02,(float) -1.15577091e-04,(float) -1.64276912e-02,
            (float) -1.07833444e-01,(float) -1.44137197e-01,(float) -1.15577091e-04,(float)  2.49878094e-01,
            (float) 3.74465713e-01, (float) 2.47650016e-01, (float)-1.15577091e-04, (float)-1.40274964e-01,
            (float) -1.03958640e-01,(float) -1.56874805e-02,(float) -1.15577091e-04,(float) -4.27110949e-02,
            (float) -5.93211922e-02,(float) -2.86946751e-02,(float) -1.15577091e-04,(float) -4.62001897e-03,
            (float) -1.88635835e-02,(float) -1.48779203e-02,(float) -1.15577091e-04,(float)  4.99716107e-03,
            (float) -1.15577091e-04,(float) -2.89706810e-03,(float) -1.15577091e-04,(float)  2.01442620e-03,
            (float) 1.09459613e-03, (float)-1.14626808e-05, (float)-1.15577091e-04, (float)-1.15577091e-04};

    // Band-Pass(float) 0.5Hz - 5Hz, 4th order Butterworth
    static List<List<Float>> sos_butter_order4_bp = Arrays.asList(
            Arrays.asList((float) 0.02607772, (float) 0.05215544, (float) 0.02607772, (float) 1, (float) -0.60740687, (float) 0.19313234),
            Arrays.asList((float) 1, (float) 2, (float) 1, (float) 1, (float) -1.47916952, (float) 0.57498111),
            Arrays.asList((float) 1, (float) -2, (float) 1, (float) 1, (float) -0.44995709, (float) 0.58165366),
            Arrays.asList((float) 1, (float) -2, (float) 1, (float) 1, (float) -1.79629148, (float) 0.86333651));
}
