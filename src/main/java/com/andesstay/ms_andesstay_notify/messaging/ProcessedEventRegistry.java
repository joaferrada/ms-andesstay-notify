package com.andesstay.ms_andesstay_notify.messaging;

import com.andesstay.ms_andesstay_notify.config.NotifyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro de idempotencia: recuerda los eventId ya procesados con exito.
 *
 * RabbitMQ garantiza entrega "at-least-once": un mensaje puede llegar dos veces (por ejemplo si
 * el consumidor se cae despues de procesar y antes del ACK). Con este registro el segundo
 * procesamiento se detecta y se descarta con ACK.
 *
 * Implementacion en memoria (LRU acotado) porque notify no tiene BD; en produccion iria en
 * Redis o en una tabla con eventId como clave unica.
 */
@Component
public class ProcessedEventRegistry {

    private final Map<String, Boolean> processed;

    @Autowired
    public ProcessedEventRegistry(NotifyProperties props) {
        this(props.idempotency().maxEntries());
    }

    ProcessedEventRegistry(int maxEntries) {
        this.processed = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized boolean isProcessed(String eventId) {
        return processed.containsKey(eventId);
    }

    public synchronized void markProcessed(String eventId) {
        processed.put(eventId, Boolean.TRUE);
    }
}
