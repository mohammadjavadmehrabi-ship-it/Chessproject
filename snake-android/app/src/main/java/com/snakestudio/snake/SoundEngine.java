package com.snakestudio.snake;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Zero-dependency synthesized retro-modern PCM sound effects engine.
 * Safe across Android API 21-35+, runs playback off the UI thread.
 */
public final class SoundEngine {
    public static final int SND_EAT = 0;
    public static final int SND_BONUS = 1;
    public static final int SND_TURN = 2;
    public static final int SND_BUTTON = 3;
    public static final int SND_GAME_OVER = 4;
    public static final int SND_NEW_RECORD = 5;
    public static final int SND_POWERUP = 6;
    public static final int SND_SHIELD_SAVE = 7;
    public static final int SND_WARP = 8;

    private static final int SAMPLE_RATE = 22050;

    private final short[][] clips = new short[9][];
    private final Object queueLock = new Object();
    private final int[] queue = new int[10];
    private int qHead = 0;
    private int qTail = 0;
    private int qCount = 0;

    private volatile boolean muted = false;
    private volatile boolean running = true;
    private Thread workerThread;

    public SoundEngine() {
        clips[SND_EAT] = buildTwoTone(523.25f, 783.99f, 52, 72, 0.34f);
        clips[SND_BONUS] = buildArpeggio(new float[]{659.25f, 830.61f, 987.77f, 1318.51f}, 52, 0.36f);
        clips[SND_TURN] = buildTone(340f, 385f, 16, 0.09f);
        clips[SND_BUTTON] = buildTone(560f, 650f, 26, 0.22f);
        clips[SND_GAME_OVER] = buildTone(420f, 120f, 360, 0.36f);
        clips[SND_NEW_RECORD] = buildArpeggio(new float[]{523.25f, 659.25f, 783.99f, 1046.50f}, 75, 0.38f);
        clips[SND_POWERUP] = buildArpeggio(new float[]{440f, 659.25f, 880f, 1174.66f}, 45, 0.34f);
        clips[SND_SHIELD_SAVE] = buildTwoTone(880f, 330f, 65, 110, 0.38f);
        clips[SND_WARP] = buildTone(280f, 1120f, 165, 0.30f);

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                playbackLoop();
            }
        }, "SnakeSoundThread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public void play(int soundId) {
        if (muted || !running) return;
        if (soundId < 0 || soundId >= clips.length) return;
        synchronized (queueLock) {
            if (qCount < queue.length) {
                queue[qTail] = soundId;
                qTail = (qTail + 1) % queue.length;
                qCount++;
                queueLock.notify();
            }
        }
    }

    public void release() {
        running = false;
        synchronized (queueLock) {
            queueLock.notifyAll();
        }
    }

    private void playbackLoop() {
        AudioTrack track = null;
        try {
            int minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bufSize = Math.max(minBuf, SAMPLE_RATE / 4 * 2);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            track = new AudioTrack(attrs, format, bufSize,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                track.release();
                return;
            }
            track.play();

            while (running) {
                int id = -1;
                synchronized (queueLock) {
                    while (running && qCount == 0) {
                        queueLock.wait(250);
                    }
                    if (!running) break;
                    if (qCount > 0) {
                        id = queue[qHead];
                        qHead = (qHead + 1) % queue.length;
                        qCount--;
                    }
                }
                if (id >= 0 && !muted) {
                    short[] pcm = clips[id];
                    if (pcm != null && pcm.length > 0) {
                        track.write(pcm, 0, pcm.length);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Silently disable audio if hardware is unavailable
        } finally {
            if (track != null) {
                try {
                    track.stop();
                } catch (Throwable ignored) {
                }
                try {
                    track.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static short[] buildTone(float freqStart, float freqEnd, int durationMs, float gain) {
        int count = (SAMPLE_RATE * durationMs) / 1000;
        short[] buf = new short[count];
        double phase = 0.0;
        for (int i = 0; i < count; i++) {
            float t = i / (float) Math.max(1, count - 1);
            float freq = freqStart + (freqEnd - freqStart) * t;
            phase += 2.0 * Math.PI * freq / SAMPLE_RATE;
            double wave = 0.78 * Math.sin(phase) + 0.22 * Math.sin(phase * 2.0);
            double env = Math.min(1.0, i / (SAMPLE_RATE * 0.006)) * Math.pow(1.0 - t, 1.4);
            buf[i] = (short) Math.max(-32767, Math.min(32767, wave * env * gain * 32767.0));
        }
        return buf;
    }

    private static short[] buildTwoTone(float f1, float f2, int ms1, int ms2, float gain) {
        short[] a = buildTone(f1, f1 * 1.03f, ms1, gain);
        short[] b = buildTone(f2, f2 * 1.04f, ms2, gain);
        short[] out = new short[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static short[] buildArpeggio(float[] notes, int noteMs, float gain) {
        int nLen = (SAMPLE_RATE * noteMs) / 1000;
        short[] out = new short[nLen * notes.length];
        for (int n = 0; n < notes.length; n++) {
            short[] part = buildTone(notes[n], notes[n] * 1.02f, noteMs, gain);
            int copyLen = Math.min(nLen, part.length);
            System.arraycopy(part, 0, out, n * nLen, copyLen);
        }
        return out;
    }
}
