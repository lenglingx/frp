package com.imddy.frp.server;

import com.imddy.frp.common.util.ConfigLoader;
import com.imddy.frp.server.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrpServerApplication {
    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "frp-server.yml";

        try {
            ServerConfig config = ConfigLoader.loadConfig(configPath, ServerConfig.class);
            FrpServer server = new FrpServer(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down FRP Server...");
                server.stop();
            }));

            server.start();

            // 保持运行
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Failed to start FRP Server", e);
            System.exit(1);
        }
    }
}
