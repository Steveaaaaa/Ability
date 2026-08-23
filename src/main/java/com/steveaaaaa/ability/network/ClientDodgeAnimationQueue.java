package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientDodgeAnimationQueue {
    private static final Queue<ClientboundDodgeAnimationPayload> QUEUE = new ConcurrentLinkedQueue<>();

    private ClientDodgeAnimationQueue() {
    }

    public static void accept(ClientboundDodgeAnimationPayload payload) {
        QUEUE.add(payload);
    }

    public static ClientboundDodgeAnimationPayload poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
