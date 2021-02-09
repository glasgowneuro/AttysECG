package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Calculates the screensize and determines if it's a mobile or not
 */

public class Screensize {

    final String TAG="Screensize";

    public Screensize(Context context) {
        metrics = context.getResources().getDisplayMetrics();
        width = metrics.widthPixels / metrics.xdpi;
        height = metrics.heightPixels / metrics.ydpi;
        diagonal = (float) (Math.sqrt(width * width + height * height));
        Log.d(TAG, "screensize=" + diagonal + "in");
    }

    private final DisplayMetrics metrics;

    private final double width;
    private final double height;
    private final float diagonal;

    final public boolean isMobile() {
        return (diagonal < 5);
    }

    final public boolean isTablet() {
        return !(diagonal < 5);
    }

    final public float getSizeInInch() {
        return diagonal;
    }

    final public double getWidthInInch() { return width;}
    final public double getHeightInInch() { return height;}

    final public int getWidthInPixels() { return metrics.widthPixels;}
    final public int getHeightInPixels() { return metrics.heightPixels;}
}
