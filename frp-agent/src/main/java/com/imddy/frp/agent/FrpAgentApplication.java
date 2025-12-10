package com.imddy.frp.agent;

import com.imddy.frp.agent.config.AgentConfig;
import com.imddy.frp.common.util.ConfigLoader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrpAgentApplication {
    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "frp-agent.yml";

        try {
            AgentConfig config = ConfigLoader.loadConfig(configPath, AgentConfig.class);
            FrpAgent agent = new FrpAgent(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down FRP Agent...");
                agent.stop();
            }));

            agent.start();

            // 保持运行
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("Failed to start FRP Agent", e);
            System.exit(1);
        }
    }
}
