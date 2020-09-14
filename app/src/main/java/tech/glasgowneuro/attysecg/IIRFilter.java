package tech.glasgowneuro.attysecg;

import java.util.ArrayList;
import java.util.List;

class IIRFilter {
    private List<IIR2Filter> slaves = new ArrayList<>();
    private int order;

    void set_sos_coefficients(List<List<Float>> sos_coefficients) {
        order = sos_coefficients.size();
        for (int i = 0; i < order; i++) {
            slaves.add(i, new IIR2Filter());
            slaves.get(i).set_sos_coefficients(sos_coefficients.get(i));
        }
    }

    float doFilter(float data) {
        float output = data;
        for (int i=0; i<order; i++) {
            output = slaves.get(i).doFilter(output);
        }
        return output;
    }
}