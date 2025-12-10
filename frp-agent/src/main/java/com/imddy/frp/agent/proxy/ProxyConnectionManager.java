package com.imddy.frp.agent.proxy;

import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ProxyConnectionManager {
    private final Map<String, Channel> connections = new ConcurrentHashMap<>();

    public void addConnection(String channelId, Channel channel) {
        connections.put(channelId, channel);
        log.info("Added proxy connection: {}", channelId);
    }

    public Channel getConnection(String channelId) {
        return connections.get(channelId);
    }

    public void removeConnection(String channelId) {
        Channel channel = connections.remove(channelId);
        if (channel != null) {
            channel.close();
            log.info("Removed proxy connection: {}", channelId);
        }
    }

    public void closeAll() {
        connections.values().forEach(Channel::close);
        connections.clear();
        log.info("Closed all proxy connections");
    }
}
