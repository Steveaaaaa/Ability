package com.steveaaaaa.ability.network;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ClientGolemReinforcementQueue {
    private static final Queue<ClientboundGolemReinforcementPayload> QUEUE = new ConcurrentLinkedQueue<>();

    private ClientGolemReinforcementQueue() {
    }

    public static void accept(ClientboundGolemReinforcementPayload payload) {
        QUEUE.add(payload);
    }

    public static ClientboundGolemReinforcementPayload poll() {
        return QUEUE.poll();
    }

    public static void clear() {
        QUEUE.clear();
    }
}
