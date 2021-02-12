package tech.glasgowneuro.attysecg;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Creates a beep tone for the heartbeat
 */

public class BeepGenerator {

    // audio
    static private AudioTrack sound;
    public static final int audioSamplingRate = 44100;
    public static final int nAudioSamples = audioSamplingRate / 20; // 50ms
    public static double f = 1000; // Hz

    public BeepGenerator(int sessionID) {
        final int frontPair =
                AudioFormat.CHANNEL_OUT_FRONT_LEFT | AudioFormat.CHANNEL_OUT_FRONT_RIGHT;
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                .setSampleRate(audioSamplingRate)
                .setChannelMask(frontPair)
                .build();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .build();
        sound = new AudioTrack(audioAttributes,
                audioFormat,
                nAudioSamples,
                AudioTrack.MODE_STATIC,
                sessionID);
        byte[] rawAudio = new byte[nAudioSamples];
        for (int i = 0; i < nAudioSamples; i++) {
            double s = Math.sin(i*2.0*Math.PI*f/((double)audioSamplingRate));
            double a = ((double)i)*0.01;
            if (a>1) a = 1;
            double b = ((double)(nAudioSamples-i))*0.01;
            if (b>1) b = 1;
            if ((i == 0) || (i == (nAudioSamples-1))) s = 0;
            int v = (int) (120.0 * s * a * b);
            v = v + 0x80;
            rawAudio[i] = (byte) (v & 0xff);
        }
        sound.write(rawAudio, 0, rawAudio.length);
    }

    static synchronized public void doBeep() {
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
