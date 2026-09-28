package com.flintmueller;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.slf4j.Logger;

import jakarta.inject.Inject;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Main plugin class.
 *
 * On startup (and every 30 s thereafter) it scans the mscs root directory for
 * sub-directories that contain a server.properties file, reads the
 * server-port, and registers the server with Velocity.
 *
 * This means you never touch velocity.toml to add a new backend —
 * create the mscs directory, and the next scan picks it up.
 */
public class DomainRouterPlugin {

    // ── tweak these ──────────────────────────────────────────────────────────
    private static final Path  MSCS_ROOT      = Path.of("/opt/mscs");
    private static final String BACKEND_HOST  = "127.0.0.1";   // all servers on same box
    private static final long  SCAN_INTERVAL_S = 30;           // rescan frequency
    // ─────────────────────────────────────────────────────────────────────────

    private final ProxyServer proxy;
    private final Logger logger;
    private final Set<String> registered = new LinkedHashSet<>();

    @Inject
    public DomainRouterPlugin(ProxyServer proxy, Logger logger) {
        this.proxy  = proxy;
        this.logger = logger;
    }

    /**
     * Called once by Velocity's DI container after injection.
     * Kick off the first scan immediately, then repeat.
     */
    public void start() {
        logger.info("Domain Router initialised – scanning {} every {} s", MSCS_ROOT, SCAN_INTERVAL_S);

        // initial scan (runs async so we don't block boot)
        proxy.getScheduler().buildTask(this, task -> discoverServers()).schedule();

        // periodic rescan
        proxy.getScheduler().buildTask(this, task -> discoverServers())
                .repeat(SCAN_INTERVAL_S, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Walk one level below MSCS_ROOT.  Every sub-directory that contains a
     * readable server.properties is treated as a backend server.
     */
    private void discoverServers() {
        if (!Files.isDirectory(MSCS_ROOT)) {
            logger.warn("{} does not exist or is not a directory – skipping scan", MSCS_ROOT);
            return;
        }

        final Set<String> found = new LinkedHashSet<>();

        try (var stream = Files.list(MSCS_ROOT)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                String name = dir.getFileName().toString();
                Path propsFile = dir.resolve("server.properties");

                if (!Files.isRegularFile(propsFile)) {
                    return;   // not a server dir
                }

                Integer port = readServerPort(propsFile);
                if (port == null) {
                    logger.warn("Found {} but could not read server-port – skipping", dir);
                    return;
                }

                found.add(name);

                // skip if already registered with Velocity
                if (proxy.getServer(name).isPresent()) {
                    registered.add(name);
                    return;
                }

                // register it
                ServerInfo info = ServerInfo.builder()
                        .name(name)
                        .address(new InetSocketAddress(BACKEND_HOST, port))
                        .build();

                proxy.createServer(info)
                        .thenAccept(rs -> {
                            registered.add(name);
                            logger.info("Registered backend '{}' on {}:{}", name, BACKEND_HOST, port);
                        })
                        .exceptionally(ex -> {
                            logger.error("Failed to register '{}': {}", name, ex.getMessage());
                            return null;
                        });
            });
        } catch (Exception e) {
            logger.error("Error scanning {}: {}", MSCS_ROOT, e.getMessage());
        }

        // optional: log when a server disappears from disk so you know the
        // stale registration is still in memory (Velocity has no clean
        // unregister API in 3.x, so we just note it).
        registered.forEach(name -> {
            if (!found.contains(name)) {
                logger.debug("Backend '{}' no longer on disk but still registered", name);
            }
        });
    }

    /**
     * Parse "server-port=NNNN" out of a server.properties file.
     * Returns null if the key is missing or the value isn't a valid int.
     */
    private Integer readServerPort(Path propsFile) {
        try (var lines = Files.lines(propsFile)) {
            return lines
                    .map(String::trim)
                    .filter(l -> l.startsWith("server-port="))
                    .map(l -> l.substring("server-port=".length()).trim())
                    .map(Integer::parseInt)
                    .findFirst()
                    .orElse(null);
        } catch (NumberFormatException e) {
            logger.warn("Invalid server-port value in {}", propsFile);
        } catch (Exception e) {
            logger.warn("Could not read {}: {}", propsFile, e.getMessage());
        }
        return null;
    }
}
