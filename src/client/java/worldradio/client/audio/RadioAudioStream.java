package worldradio.client.audio;

import net.minecraft.client.sounds.AudioStream;
import worldradio.signal.VolumeCurve;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.DoubleSupplier;

/**
 * The vanilla streaming interface over one {@link PcmBuffer}, in mono, because OpenAL places only a mono buffer in the
 * world (see {@link RadioSoundInstance}). Never blocks the sound thread: it always hands
 * out a quarter second (real audio first, silence for whatever is missing), so OpenAL never runs dry and stops the
 * sound. A radio with speakers is louder than the engine's full volume; that part is put into the samples here. Small buffers keep the delay short: vanilla queues four at the start.
 */
public final class RadioAudioStream implements AudioStream {
    public static final AudioFormat FORMAT = new AudioFormat(StationStream.RATE, 16, 1, true, false);
    private static final int CHUNK = StationStream.RATE / 4;

    private final String url;
    private final PcmBuffer buffer;
    /** Factor for the samples, 1 or more; asked for every chunk. */
    private final DoubleSupplier gain;
    private final short[] samples = new short[CHUNK];
    private boolean closed;
    private volatile long reads;
    private volatile long realSamples;

    public RadioAudioStream(String url, DoubleSupplier gain) {
        this.url = url;
        this.gain = gain;
        this.buffer = StreamPool.acquire(url);
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int size) {
        realSamples += buffer.read(samples);
        reads++;
        double g = gain.getAsDouble();
        if (g > 1.0) for (int i = 0; i < CHUNK; i++) samples[i] = VolumeCurve.amplify(samples[i], g);
        ByteBuffer out = ByteBuffer.allocateDirect(CHUNK * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.asShortBuffer().put(samples);
        return out;
    }

    /** How often the sound engine pulled a chunk, and how many of the samples handed out were real audio. */
    public String stats() {
        return "reads=" + reads + " real=" + realSamples;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        StreamPool.release(url, buffer);
    }
}
