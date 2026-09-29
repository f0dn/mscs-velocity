package com.flintmueller;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import jakarta.inject.Inject;

import java.net.InetSocketAddress;
import java.util.Optional;

public class DomainRouterListener {

    private final ProxyServer proxy;
    private final Logger logger;

    @Inject
    public DomainRouterListener(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onChooseServer(PlayerChooseInitialServerEvent event) {
        Optional<InetSocketAddress> vHost = event.getPlayer().getVirtualHost();
        if (vHost.isEmpty()) {
            return;
        }

        String host = vHost.get().getHostString();

        if (host.chars().allMatch(c -> c == '.' || Character.isDigit(c))) {
            return; // ip address
        }
        if (!host.contains(".")) {
            return; // bare hostname
        }

        String subdomain = host.split("\\.", 2)[0];

        Optional<RegisteredServer> target = proxy.getServer(subdomain);

        target.ifPresent(server -> {
            event.setInitialServer(server);
            logger.debug("Routed {} → {}", host, subdomain);
        });
    }
}
