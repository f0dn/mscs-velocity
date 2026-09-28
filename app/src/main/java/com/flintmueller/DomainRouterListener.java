package com.flintmueller;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.ConnectionHandshakeEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Handshake listener.
 *
 * When a client connects to  survival.example.com:25565
 * the Minecraft handshake packet carries the hostname it used.
 * We grab the first DNS label ("survival"), look up the matching
 * RegisteredServer, and override the routing node — same effect as
 * forced-host, but fully dynamic.
 */
public class DomainRouterListener {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public DomainRouterListener(ProxyServer proxy, Logger logger) {
        this.proxy  = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onHandshake(ConnectionHandshakeEvent event) {
        // Hostname the client actually dialed, e.g. "survival.example.com"
        String host = event.getInboundConnection().getServerAddress().getHostString().toLowerCase();

        // Quick bail: IP addresses and bare domains have no useful subdomain
        if (host.chars().allMatch(c -> c == '.' || Character.isDigit(c))) {
            return;                       // looks like an IP → default routing
        }
        if (!host.contains(".")) {
            return;                       // bare hostname → default routing
        }

        // First label = intended server name
        // survival.example.com → "survival"
        // a.b.example.com      → "a"   (adjust if you need multi-level)
        String subdomain = host.split("\\.", 2)[0];

        Optional<RegisteredServer> target = proxy.getServer(subdomain);

        target.ifPresent(server -> {
            event.getInboundConnection().setNode(server);
            // logger.debug("Routed {} → {}", host, server.getName());
        });
        // if no match, Velocity falls through to its normal first-server logic
    }
}
