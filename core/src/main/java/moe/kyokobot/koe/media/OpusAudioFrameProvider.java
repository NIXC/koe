package moe.kyokobot.koe.media;

import io.netty.buffer.ByteBuf;
import moe.kyokobot.koe.VoiceConnection;
import moe.kyokobot.koe.codec.Codec;
import moe.kyokobot.koe.codec.OpusCodec;
import moe.kyokobot.koe.gateway.SpeakingFlags;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link MediaFrameProvider} which automatically takes care of
 * checking codec type, sending silent frames and updating speaking state.
 */
public abstract class OpusAudioFrameProvider implements MediaFrameProvider {
    private static final int SILENCE_FRAME_COUNT = 5;
    private final VoiceConnection connection;

    // volatile because of multiple event loop threads accessing these fields.
    private AtomicInteger counter = new AtomicInteger();
    private volatile long lastFramePolled = 0;
    private volatile boolean lastSpeaking = false;
    private volatile boolean speaking = false;
    private int speakingMask = SpeakingFlags.NORMAL;

    public OpusAudioFrameProvider(VoiceConnection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public int getSpeakingMask() {
        return speakingMask;
    }

    public void setSpeakingMask(int speakingMask) {
        this.speakingMask = speakingMask;
    }

    @Override
    public final boolean canSendFrame(Codec codec) {
        if (codec.getPayloadType() != OpusCodec.PAYLOAD_TYPE) {
            return false;
        }

        if (counter.get() > 0) {
            return true;
        }

        return canProvide();
    }

    @Override
    public final void retrieve(Codec codec, ByteBuf buf) {
        if (codec.getPayloadType() != OpusCodec.PAYLOAD_TYPE) {
            return;
        }

        if (counter.get() > 0) {
            counter.decrementAndGet();
            buf.writeBytes(OpusCodec.SILENCE_FRAME);
            return;
        }

        int startIndex = buf.writerIndex();
        retrieveOpusFrame(buf);
        boolean written = buf.writerIndex() != startIndex;

        if (written && !speaking) {
            setSpeaking(true);
        }

        if (!written) {
            counter.set(SILENCE_FRAME_COUNT);
        }

        long now = System.currentTimeMillis();
        boolean changeTalking = (now - lastFramePolled) > OpusCodec.FRAME_DURATION;
        lastFramePolled = now;
        if (changeTalking) {
            setSpeaking(written);
        }
    }

    private void setSpeaking(boolean state) {
        this.speaking = state;
        if (this.speaking != this.lastSpeaking) {
            this.lastSpeaking = state;
            connection.updateSpeakingState(state ? this.speakingMask : 0);
        }
    }

    /**
     * Called every time Opus frame poller tries to retrieve an Opus audio frame.
     *
     * @return If this method returns true, Koe will attempt to retrieve an Opus audio frame.
     */
    public abstract boolean canProvide();

    /**
     * If {@link #canProvide()} returns true, this method will attempt to retrieve an Opus audio frame.
     * <p>
     * This method must not block, otherwise it might cause severe performance issues, due to event loop thread
     * getting blocked, therefore it's recommended to load all data before or in parallel, not when Koe frame poller
     * calls this method. If no data gets written, the frame won't be sent.
     *
     * @param targetBuffer the target {@link ByteBuf} audio data should be written to.
     */
    public abstract void retrieveOpusFrame(ByteBuf targetBuffer);
}
