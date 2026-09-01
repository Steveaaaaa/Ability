package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientTransmutationQueue {
    private static final Queue<ClientboundTransmutationPayload> PENDING = new ConcurrentLinkedQueue<>();

    private ClientTransmutationQueue() {
    }

    public static void accept(ClientboundTransmutationPayload payload) {
        PENDING.add(payload);
    }

    public static ClientboundTransmutationPayload poll() {
        return PENDING.poll();
    }

    public static void clear() {
        PENDING.clear();
    }
}
