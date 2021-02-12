package tech.glasgowneuro.attysecg;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Creates a beep tone for the heartbeat
 */

public class BeepGenerator {

    static final String TAG = BeepGenerator.class.getCanonicalName();

    // audio
    private final AudioTrack sound;
    static public final int audioSamplingRate = 44100;
    static public double f = 1000; // Hz
    static public int duration = (int)(audioSamplingRate * 0.1); // samples

    public BeepGenerator(int sessionID) {
        Log.d(TAG,"Session ID = "+sessionID);
        final int frontPair =
                AudioFormat.CHANNEL_OUT_FRONT_LEFT | AudioFormat.CHANNEL_OUT_FRONT_RIGHT;
        int nAudioSamples = AudioTrack.getMinBufferSize(audioSamplingRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT);
        if (duration > nAudioSamples) nAudioSamples = duration;
        Log.d(TAG,"Audio buffer size = "+nAudioSamples);
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                .setSampleRate(audioSamplingRate)
                .setChannelMask(frontPair)
                .build();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        sound = new AudioTrack(audioAttributes,
                audioFormat,
                nAudioSamples,
                AudioTrack.MODE_STATIC,
                sessionID);
        byte[] rawAudio = new byte[nAudioSamples];
        for (int i = 1; i < (nAudioSamples - 1); i++) {
            double s = Math.sin(i*2.0*Math.PI*f/((double)audioSamplingRate));
            double e = Math.sin(i/(double)nAudioSamples*Math.PI);
            int v = (int) (127.0 * s * e);
            v = v + 0x80;
            rawAudio[i] = (byte) (v & 0xff);
        }
        rawAudio[0] = (byte)0x80;
        rawAudio[nAudioSamples-1] = (byte)0x80;
        sound.write(rawAudio, 0, rawAudio.length);
    }

    synchronized public void doBeep() {
        try {
            sound.pause();
            sound.flush();
            sound.reloadStaticData();
            sound.play();
        } catch (IllegalStateException ignored) {
        }
    }

    public void closeAudio() {
        sound.release();
    }

}
