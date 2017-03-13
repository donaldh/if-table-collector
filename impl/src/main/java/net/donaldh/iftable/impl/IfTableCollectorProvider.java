/*
 * Copyright © 2016 Cisco Systems Inc and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package net.donaldh.iftable.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationPublishService;
import org.opendaylight.controller.messagebus.spi.EventSource;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistration;
import org.opendaylight.controller.messagebus.spi.EventSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class IfTableCollectorProvider {

    private static final Logger LOG = LoggerFactory.getLogger(IfTableCollectorProvider.class);

    private final EventSourceRegistry eventSourceRegistry;
    private final DOMNotificationPublishService publishService;
    private final ScheduledExecutorService scheduler;
    private Snmp snmp;

    class EventSourceHandle {
        final IfTableEventSource eventSource;
        final EventSourceRegistration<EventSource> registration;

        EventSourceHandle(IfTableEventSource source) {
            eventSource = source;
            registration = eventSourceRegistry.registerEventSource(eventSource);
        }
    }

    private Map<String, EventSourceHandle> eventSources = new HashMap<>();

    public IfTableCollectorProvider(final DataBroker dataBroker, final EventSourceRegistry eventSourceRegistry,
            final DOMNotificationPublishService publishService) throws IOException {
        this.eventSourceRegistry = eventSourceRegistry;
        this.publishService = publishService;
        scheduler = Executors.newScheduledThreadPool(1);
        snmp = new Snmp(new DefaultUdpTransportMapping());
        snmp.listen();
    }

    void addNode(String id, String address, String community, int port, Long pollInterval) {
        IfTableEventSource eventSource = new IfTableEventSource(publishService, snmp, address, community, port);
        eventSources.put(id, new EventSourceHandle(eventSource));
    }

    void removeNode(String id) {
        EventSourceHandle handle = eventSources.remove(id);
        if (handle != null) {
            handle.registration.close();
        }
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("SoamProvider Session Initiated");
        scheduler.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try {
                    for (EventSourceHandle handle : eventSources.values()) {
                        handle.eventSource.execute();
                    }
                } catch (Throwable e) {
                    LOG.error(e.getClass().getName() + " : " + e.getLocalizedMessage());
                }
            }

        }, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        scheduler.shutdown();
        LOG.info("SoamProvider Closed");
    }

}