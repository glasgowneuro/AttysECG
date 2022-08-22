package tech.glasgowneuro.attysecg;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.app.Activity;
import android.view.Surface;
import android.view.SurfaceView;

public class HRVOculus extends SurfaceView implements SurfaceHolder.Callback {

    private long mNativeHandle = 0;
    private SurfaceHolder mSurfaceHolder;
    private static final String TAG = HRVOculus.class.getSimpleName();

    static {
        System.loadLibrary("attysecg");
    }

    static public int createTexture()
    {
        int[] texture = new int[1];
        GLES20.glGenTextures(1,texture, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        Log.d(TAG,"Texture="+texture[0]);
        return texture[0];
    }

    static public SurfaceTexture getSurfaceTexture() {
        final int t = createTexture();
        final SurfaceTexture st = new SurfaceTexture(t);
        st.setDefaultBufferSize(500*4,500*4);
        return st;
    }

    public void init(Activity a) {
        Log.d(TAG,"HRVinit");
        getHolder().addCallback(this);
        mNativeHandle = onCreate(a);

        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(false);
    }

    public void destroy() {
        onDestroy(mNativeHandle);
    }

    public void start() {
        onResume(mNativeHandle);
    }

    public void stop() {
        onPause(mNativeHandle);
    }

    public HRVOculus(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public HRVOculus(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HRVOculus(Context context) {
        super(context);
    }

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
            Log.d(TAG,"surfaceCreated. Have holder.");
        } else {
            Log.e(TAG,"Could not create surface. handle = null");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "GLES3JNIActivity::surfaceChanged()");
        if (mNativeHandle != 0) {
            onSurfaceChanged(mNativeHandle, holder.getSurface());
            mSurfaceHolder = holder;
            Log.d(TAG, "surfaceChanged. New holder.");
        } else {
            Log.e(TAG, "Could not updated surface in surfaceChanged. handle = null");
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