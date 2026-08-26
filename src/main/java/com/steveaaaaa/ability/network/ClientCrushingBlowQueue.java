package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientCrushingBlowQueue {
    private static final Queue<ClientboundCrushingBlowPayload> QUEUE = new ConcurrentLinkedQueue<>();

    private ClientCrushingBlowQueue() {
    }

    public static void accept(ClientboundCrushingBlowPayload payload) {
        QUEUE.add(payload);
    }

    public static ClientboundCrushingBlowPayload poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
