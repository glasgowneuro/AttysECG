package tech.glasgowneuro.attysecg;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

/**
 * Calculates the screensize and determines if it's a mobile or not
 */

public class Screensize {


    private final String TAG="Screensize";

    Screensize(WindowManager windowManager) {
        windowManager.getDefaultDisplay().getMetrics(metrics);
        width = metrics.widthPixels / metrics.xdpi;
        height = metrics.heightPixels / metrics.ydpi;
        diagonal = (float)(Math.sqrt(width*width + height*height));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "screensize=" + diagonal + "in");
        }
    }

    private DisplayMetrics metrics = new DisplayMetrics();

    private final double width;
    private final double height;
    private final float diagonal;

    public boolean isMobile() {
        return (diagonal < 5);
    }

    public boolean isTablet() {
        return !(diagonal < 5);
    }

    public float getSizeInInch() {
        return diagonal;
    }

    public double getWidthInInch() { return width;}
    public double getHeightInInch() { return height;}

    public int getWidthInPixels() { return metrics.widthPixels;}
    public int getHeightInPixels() { return metrics.heightPixels;}
}
