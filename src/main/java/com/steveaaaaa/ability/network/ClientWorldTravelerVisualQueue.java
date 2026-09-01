package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientWorldTravelerVisualQueue {
    private static final Queue<ClientboundWorldTravelerVisualPayload> PENDING = new ConcurrentLinkedQueue<>();

    private ClientWorldTravelerVisualQueue() {
    }

    public static void accept(ClientboundWorldTravelerVisualPayload payload) {
        PENDING.add(payload);
    }

    public static ClientboundWorldTravelerVisualPayload poll() {
        return PENDING.poll();
    }

    public static void clear() {
        PENDING.clear();
    }
}
