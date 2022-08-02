package tech.glasgowneuro.attysecg;

import android.util.Log;
import android.view.SurfaceHolder;
import android.app.Activity;
import android.view.Surface;

public class HRVOculus implements SurfaceHolder.Callback {

    static {
        System.loadLibrary("attysecg");
    }

    private long mNativeHandle;
    private SurfaceHolder mSurfaceHolder;
    private static final String TAG = HRVOculus.class.getSimpleName();

    public static native long onCreate(Activity obj);

    public static native void onStart(long handle);

    public static native void onResume(long handle);

    public static native void onPause(long handle);

    public static native void onStop(long handle);

    public static native void onDestroy(long handle);

    // Surface lifecycle
    public static native void onSurfaceCreated(long handle, Surface s);

    public static native void onSurfaceChanged(long handle, Surface s);

    public static native void onSurfaceDestroyed(long handle);

    // Input
    public static native void onKeyEvent(long handle, int keyCode, int action);

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mNativeHandle != 0) {
            onSurfaceCreated(mNativeHandle, holder.getSurface());
            mSurfaceHolder = holder;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "GLES3JNIActivity::surfaceChanged()");
        if (mNativeHandle != 0) {
            onSurfaceChanged(mNativeHandle, holder.getSurface());
            mSurfaceHolder = holder;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "GLES3JNIActivity::surfaceDestroyed()");
        if (mNativeHandle != 0) {
            onSurfaceDestroyed(mNativeHandle);
            mSurfaceHolder = null;
        }
    }


}