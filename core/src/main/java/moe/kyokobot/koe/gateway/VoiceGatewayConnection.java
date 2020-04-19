package moe.kyokobot.koe.gateway;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface VoiceGatewayConnection {
    boolean isOpen();

    CompletableFuture<Void> start();

    void close(int code, @Nullable String reason);

    void updateSpeaking(int mask);
}
