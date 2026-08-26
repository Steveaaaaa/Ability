package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientColdCurrentQueue {
    private static final Queue<ClientboundColdCurrentPayload> QUEUE = new ConcurrentLinkedQueue<>();

    private ClientColdCurrentQueue() {
    }

    public static void accept(ClientboundColdCurrentPayload payload) {
        QUEUE.add(payload);
    }

    public static ClientboundColdCurrentPayload poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
