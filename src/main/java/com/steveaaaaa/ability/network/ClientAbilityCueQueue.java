package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientAbilityCueQueue {
    private static final Queue<ClientboundAbilityCuePayload> QUEUE = new ConcurrentLinkedQueue<>();

    private ClientAbilityCueQueue() {
    }

    public static void accept(ClientboundAbilityCuePayload payload) {
        QUEUE.add(payload);
    }

    public static ClientboundAbilityCuePayload poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
