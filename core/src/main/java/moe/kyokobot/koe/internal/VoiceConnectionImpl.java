package moe.kyokobot.koe.internal;

import moe.kyokobot.koe.*;
import moe.kyokobot.koe.media.MediaFrameProvider;
import moe.kyokobot.koe.codec.Codec;
import moe.kyokobot.koe.codec.CodecType;
import moe.kyokobot.koe.codec.FramePoller;
import moe.kyokobot.koe.codec.OpusCodec;
import moe.kyokobot.koe.gateway.VoiceGatewayConnection;
import moe.kyokobot.koe.handler.ConnectionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class VoiceConnectionImpl implements VoiceConnection {
    private static final Logger logger = LoggerFactory.getLogger(VoiceConnectionImpl.class);

    private final KoeClientImpl client;
    private final long guildId;
    private final EventDispatcher dispatcher;

    private VoiceGatewayConnection gatewayConnection;
    private ConnectionHandler connectionHandler;
    private VoiceServerInfo info;
    private Codec audioCodec;
    private FramePoller poller;
    private MediaFrameProvider sender;

    public VoiceConnectionImpl(@NotNull KoeClientImpl client, long guildId) {
        this.client = Objects.requireNonNull(client);
        this.guildId = guildId;
        this.dispatcher = new EventDispatcher();
        this.audioCodec = OpusCodec.INSTANCE;
        this.poller = client.getOptions().getFramePollerFactory().createFramePoller(this.audioCodec, this);
    }

    @Override
    public CompletionStage<Void> connect(VoiceServerInfo info) {
        disconnect();
        var conn = client.getGatewayVersion().createConnection(this, info);

        return conn.start().thenAccept(nothing -> {
            VoiceConnectionImpl.this.info = info;
            VoiceConnectionImpl.this.gatewayConnection = conn;
        });
    }

    @Override
    public void disconnect() {
        logger.debug("Disconnecting...");
        stopFramePolling();

        if (gatewayConnection != null && gatewayConnection.isOpen()) {
            gatewayConnection.close(1000, null);
            gatewayConnection = null;
        }

        if (connectionHandler != null) {
            connectionHandler.close();
            connectionHandler = null;
        }
    }

    @Override
    @NotNull
    public KoeClient getClient() {
        return client;
    }

    @Override
    @NotNull
    public KoeOptions getOptions() {
        return client.getOptions();
    }

    @Override
    @Nullable
    public MediaFrameProvider getSender() {
        return sender;
    }

    @Override
    public long getGuildId() {
        return guildId;
    }

    @Override
    @Nullable
    public VoiceGatewayConnection getGatewayConnection() {
        return gatewayConnection;
    }

    @Override
    @Nullable
    public VoiceServerInfo getVoiceServerInfo() {
        return info;
    }

    @Override
    public ConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    @Override
    public void setAudioSender(@Nullable MediaFrameProvider sender) {
        this.sender = sender;
    }

    @Override
    public void setAudioCodec(@NotNull Codec audioCodec) {
        if (Objects.requireNonNull(audioCodec).getType() != CodecType.AUDIO) {
            throw new IllegalArgumentException("Specified codec must be an audio codec!");
        }

        boolean wasPolling = poller != null && poller.isPolling();
        stopFramePolling();

        this.audioCodec = audioCodec;
        this.poller = client.getOptions().getFramePollerFactory().createFramePoller(this.audioCodec, this);

        if (wasPolling) {
            startFramePolling();
        }
    }

    @Override
    public void startFramePolling() {
        if (poller == null || poller.isPolling()) {
            return;
        }

        poller.start();
    }

    @Override
    public void stopFramePolling() {
        if (poller == null || !poller.isPolling()) {
            return;
        }

        poller.stop();
    }

    @Override
    public void registerListener(KoeEventListener listener) {
        dispatcher.register(listener);
    }

    @Override
    public void unregisterListener(KoeEventListener listener) {
        dispatcher.unregister(listener);
    }

    @Override
    public void close() {
        disconnect();
        client.removeConnection(guildId);
    }

    @Override
    public void updateSpeakingState(int mask) {
        if (this.gatewayConnection != null) {
            this.gatewayConnection.updateSpeaking(mask);
        }
    }

    public EventDispatcher getDispatcher() {
        return dispatcher;
    }

    public void setConnectionHandler(ConnectionHandler connectionHandler) {
        this.connectionHandler = connectionHandler;
    }
}
