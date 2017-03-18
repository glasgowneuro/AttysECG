package tech.glasgowneuro.attysecg;

/**
 * Simple 1st order highpass filter to remove the DC from the signals
 */
public class Highpass {

    private float dc = 0;
    private float a = 0.05F;
    private boolean isActive = true;

    public void setAlpha(float alpha) {
        a = alpha;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean getIsActive() {return isActive;}

    public float filter(float v) {
        dc = a * v + (1 - a) * dc;
        if (isActive) {
            v = v - dc;
        }
        return v;
    }
}
