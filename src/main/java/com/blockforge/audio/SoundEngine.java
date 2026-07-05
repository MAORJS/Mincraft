package com.blockforge.audio;

import com.blockforge.Settings;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Minimal OpenAL sound engine. Every sound is synthesized at startup from
 * simple waveforms — no audio files ship with the game. Degrades to a no-op
 * if no audio device is available (e.g. headless machines).
 */
public final class SoundEngine {

    public static final int CLICK = 0;
    public static final int BREAK = 1;
    public static final int PLACE = 2;

    private static final int SAMPLE_RATE = 22050;
    private static final int SOURCE_POOL = 8;

    private final Settings settings;
    private boolean enabled;
    private long device;
    private long context;
    private final int[] buffers = new int[3];
    private final int[] sources = new int[SOURCE_POOL];
    private int nextSource = 0;

    public SoundEngine(Settings settings) {
        this.settings = settings;
        try {
            device = alcOpenDevice((CharSequence) null);
            if (device == 0) {
                System.out.println("audio: no device, sound disabled");
                return;
            }
            context = alcCreateContext(device, (int[]) null);
            if (context == 0 || !alcMakeContextCurrent(context)) {
                alcCloseDevice(device);
                return;
            }
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);

            buffers[CLICK] = makeBuffer(synthClick());
            buffers[BREAK] = makeBuffer(synthBreak());
            buffers[PLACE] = makeBuffer(synthPlace());
            for (int i = 0; i < SOURCE_POOL; i++) {
                sources[i] = alGenSources();
            }
            enabled = true;
        } catch (Throwable t) {
            System.out.println("audio: init failed (" + t.getMessage() + "), sound disabled");
            enabled = false;
        }
    }

    // ---------------------------------------------------------------- synths

    /** Short bright blip for UI clicks. */
    private static short[] synthClick() {
        int n = SAMPLE_RATE * 35 / 1000;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.exp(-t * 90);
            double sq = Math.signum(Math.sin(2 * Math.PI * 950 * t));
            pcm[i] = (short) (sq * env * 9000);
        }
        return pcm;
    }

    /** Crunchy noise burst for breaking blocks. */
    private static short[] synthBreak() {
        int n = SAMPLE_RATE * 140 / 1000;
        short[] pcm = new short[n];
        Random r = new Random(42);
        double last = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.exp(-t * 26);
            // one-pole lowpass over white noise for a softer crunch
            last = last * 0.55 + (r.nextDouble() * 2 - 1) * 0.45;
            pcm[i] = (short) (last * env * 16000);
        }
        return pcm;
    }

    /** Low thump for placing blocks. */
    private static short[] synthPlace() {
        int n = SAMPLE_RATE * 90 / 1000;
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.exp(-t * 34);
            double freq = 170 - t * 300; // slight downward pitch sweep
            double s = Math.sin(2 * Math.PI * freq * t);
            pcm[i] = (short) (s * env * 14000);
        }
        return pcm;
    }

    private static int makeBuffer(short[] pcm) {
        ShortBuffer data = BufferUtils.createShortBuffer(pcm.length);
        data.put(pcm).flip();
        int buf = alGenBuffers();
        alBufferData(buf, AL_FORMAT_MONO16, data, SAMPLE_RATE);
        return buf;
    }

    // ----------------------------------------------------------------- play

    public void play(int soundId) {
        play(soundId, 1f);
    }

    public void play(int soundId, float gain) {
        if (!enabled) return;
        if (soundId == CLICK && !settings.uiSounds) return;
        float volume = settings.masterVolume * settings.effectsVolume * gain;
        if (volume <= 0.001f) return;
        int src = sources[nextSource];
        nextSource = (nextSource + 1) % SOURCE_POOL;
        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buffers[soundId]);
        alSourcef(src, AL_GAIN, Math.min(1f, volume));
        alSourcef(src, AL_PITCH, 0.9f + (float) Math.random() * 0.2f);
        alSourcePlay(src);
    }

    public void delete() {
        if (!enabled) return;
        for (int s : sources) alDeleteSources(s);
        for (int b : buffers) alDeleteBuffers(b);
        alcMakeContextCurrent(0);
        alcDestroyContext(context);
        alcCloseDevice(device);
    }
}
