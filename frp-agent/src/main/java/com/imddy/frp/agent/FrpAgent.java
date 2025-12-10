package com.imddy.frp.agent;

import com.imddy.frp.agent.config.AgentConfig;
import com.imddy.frp.agent.handler.ServerConnectionHandler;
import com.imddy.frp.agent.proxy.ProxyConnectionManager;
import com.imddy.frp.common.codec.MessageCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class FrpAgent {
    private final AgentConfig config;
    private final ProxyConnectionManager connectionManager;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;
    private volatile boolean running = true;

    public FrpAgent(AgentConfig config) {
        this.config = config;
        this.connectionManager = new ProxyConnectionManager();
        this.workerGroup = new NioEventLoopGroup();
    }

    public void start() {
        connect();
    }

    private void connect() {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new MessageCodec());
                        pipeline.addLast(new ServerConnectionHandler(config, connectionManager, workerGroup));
                    }
                });

        String host = config.getFrpTunnel().getHost();
        int port = config.getFrpTunnel().getPort();

        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                serverChannel = future.channel();
                log.info("Connected to FRP Server: {}:{}", host, port);

                // 监听连接断开，自动重连
                serverChannel.closeFuture().addListener((ChannelFutureListener) closeFuture -> {
                    if (running) {
                        log.warn("Connection lost, reconnecting in 5 seconds...");
                        workerGroup.schedule(this::connect, 5, TimeUnit.SECONDS);
                    }
                });
            } else {
                log.error("Failed to connect to FRP Server: {}:{}, retrying in 5 seconds...",
                        host, port, future.cause());
                if (running) {
                    workerGroup.schedule(this::connect, 5, TimeUnit.SECONDS);
                }
            }
        });
    }

    public void stop() {
        running = false;
        if (serverChannel != null) {
            serverChannel.close();
        }
        connectionManager.closeAll();
        workerGroup.shutdownGracefully();
        log.info("FRP Agent stopped");
    }
}
