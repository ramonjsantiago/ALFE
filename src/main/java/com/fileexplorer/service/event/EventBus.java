package com.fileexplorer.service.event;

import javafx.application.Platform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal in-process pub/sub bus.
 * - Typed subscriptions by event class.
 * - Delivery is always on the JavaFX Application Thread.
 */
public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();

/**
 * subscribe.
 *
 * @param eventType TODO
 * @param handler TODO
 * @return TODO
 */
    public <E> AutoCloseable subscribe(Class<E> eventType, Consumer<E> handler) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(handler, "handler");
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> {
            List<Consumer<?>> list = subscribers.get(eventType);
            if (list != null) {
                list.remove(handler);
            }
        };
    }

/**
 * publish.
 *
 * @param event TODO
 */
    public void publish(Object event) {
        if (event == null) return;
        Runnable deliver = () -> {
            List<Consumer<?>> list = subscribers.get(event.getClass());
            if (list == null || list.isEmpty()) return;
            for (Consumer<?> raw : list) {
                @SuppressWarnings("unchecked")
                Consumer<Object> c = (Consumer<Object>) raw;
                try {
                    c.accept(event);
                } catch (Throwable ignored) {
                    // Intentionally swallow handler exceptions to avoid breaking the bus.
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            deliver.run();
        } else {
            Platform.runLater(deliver);
        }
    }
}
