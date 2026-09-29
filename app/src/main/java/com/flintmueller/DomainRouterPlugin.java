package com.flintmueller;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import jakarta.inject.Inject;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DomainRouterPlugin {

    private static final String BACKEND_HOST = "127.0.0.1";
    private static final long SCAN_INTERVAL_S = 30;

    private final ProxyServer proxy;
    private final Logger logger;

    private class Server {
        String name;
        int port;

        Server(String name, int port) {
            this.name = name;
            this.port = port;
        }
    }

    @Inject
    public DomainRouterPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    public void start() {
        logger.info("Domain Router initialised – scanning every {} s", SCAN_INTERVAL_S);

        // initial scan
        proxy.getScheduler().buildTask(this, task -> registerServers()).schedule();

        proxy.getScheduler().buildTask(this, task -> registerServers())
                .repeat(SCAN_INTERVAL_S, TimeUnit.SECONDS)
                .schedule();
    }

    private void registerServers() {
        for (Server server : getMSCSBackends()) {
            if (proxy.getServer(server.name).isPresent()) {
                continue;
            }

            ServerInfo info = new ServerInfo(server.name, new InetSocketAddress(BACKEND_HOST, server.port));

            proxy.registerServer(info);
            logger.info("Registered backend '{}' on {}:{}", server.name, BACKEND_HOST, server.port);
        }
    }

    private List<Server> getMSCSBackends() {
        // TODO
        return List.of();
    }
}
