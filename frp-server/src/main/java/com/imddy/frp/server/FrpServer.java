package com.imddy.frp.server;

import com.imddy.frp.common.codec.MessageCodec;
import com.imddy.frp.common.protocol.ProxyProtocol;
import com.imddy.frp.server.config.ServerConfig;
import com.imddy.frp.server.handler.*;
import com.imddy.frp.server.tunnel.TunnelManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrpServer {
    private final ServerConfig config;
    private final TunnelManager tunnelManager;
    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public FrpServer(ServerConfig config) {
        this.config = config;
        this.tunnelManager = new TunnelManager();
        this.bossGroup = new NioEventLoopGroup(1);
        this.workerGroup = new NioEventLoopGroup();
    }

    public void start() throws Exception {
        // 注册所有隧道
        for (ServerConfig.TunnelConfig tunnel : config.getTunnels()) {
            tunnelManager.registerTunnel(tunnel);
            startAgentServer(tunnel);
            startProxyServer(tunnel);
        }

        // 启动管理服务器
        startManagementServer();

        log.info("FRP Server started successfully");
    }

    private void startAgentServer(ServerConfig.TunnelConfig tunnel) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1024 * 1024, 0, 4, 0, 4));
                        pipeline.addLast(new LengthFieldPrepender(4));
                        pipeline.addLast(new MessageCodec());
                        pipeline.addLast(new AgentServerHandler(tunnelManager));
                    }
                });

        bootstrap.bind(tunnel.getAgentPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Agent server started on port: {} for tunnel: {} ({})",
                        tunnel.getAgentPort(), tunnel.getName(), tunnel.getType());
            } else {
                log.error("Failed to start agent server on port: {}", tunnel.getAgentPort(), future.cause());
            }
        });
    }

    private void startProxyServer(ServerConfig.TunnelConfig tunnel) throws Exception {
        ProxyProtocol protocol = tunnel.getProtocolType();

        switch (protocol) {
            case TCP:
                startTcpProxyServer(tunnel);
                break;
            case UDP:
                startUdpProxyServer(tunnel);
                break;
            case HTTP:
                startHttpProxyServer(tunnel);
                break;
            case HTTPS:
                startHttpsProxyServer(tunnel);
                break;
            default:
                log.error("Unsupported protocol: {}", protocol);
        }
    }

    private void startTcpProxyServer(ServerConfig.TunnelConfig tunnel) {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // 直接添加handler，它会在channelActive中自动注册
                        ClientProxyHandler handler = new ClientProxyHandler(tunnelManager, tunnel.getName());
                        ch.pipeline().addLast(handler);
                    }
                });

        bootstrap.bind(tunnel.getOpenPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("TCP proxy server started on port: {} for tunnel: {}",
                        tunnel.getOpenPort(), tunnel.getName());
            } else {
                log.error("Failed to start TCP proxy server on port: {}", tunnel.getOpenPort(), future.cause());
            }
        });
    }


    private void startUdpProxyServer(ServerConfig.TunnelConfig tunnel) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline().addLast(new UdpProxyHandler(tunnelManager, tunnel.getName()));
                    }
                });

        bootstrap.bind(tunnel.getOpenPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("UDP proxy server started on port: {} for tunnel: {}",
                        tunnel.getOpenPort(), tunnel.getName());
            } else {
                log.error("Failed to start UDP proxy server on port: {}", tunnel.getOpenPort(), future.cause());
            }
        });
    }

    private void startHttpsProxyServer(ServerConfig.TunnelConfig tunnel) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        // 生成自签名证书
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // 添加SSL处理
                        pipeline.addLast(sslContext.newHandler(ch.alloc()));
                        // SSL之后也是透明代理
                        ClientProxyHandler handler = new ClientProxyHandler(tunnelManager, tunnel.getName());
                        pipeline.addLast(handler);
                    }
                });

        bootstrap.bind(tunnel.getOpenPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("HTTPS proxy server started on port: {} for tunnel: {}",
                        tunnel.getOpenPort(), tunnel.getName());
            } else {
                log.error("Failed to start HTTPS proxy server on port: {}",
                        tunnel.getOpenPort(), future.cause());
            }
        });
    }

    private void startHttpProxyServer(ServerConfig.TunnelConfig tunnel) throws Exception {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // HTTP也使用透明代理
                        ClientProxyHandler handler = new ClientProxyHandler(tunnelManager, tunnel.getName());
                        pipeline.addLast(handler);
                    }
                });

        bootstrap.bind(tunnel.getOpenPort()).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("HTTP proxy server started on port: {} for tunnel: {}",
                        tunnel.getOpenPort(), tunnel.getName());
            } else {
                log.error("Failed to start HTTP proxy server on port: {}",
                        tunnel.getOpenPort(), future.cause());
            }
        });
    }


    private void startManagementServer() {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(65536));
                        pipeline.addLast(new ManagementHandler(tunnelManager));
                    }
                });

        int managementPort = config.getManagement().getPort();
        bootstrap.bind(managementPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Management server started on port: {}", managementPort);
                log.info("Access management API at: http://localhost:{}/api/tunnels", managementPort);
            } else {
                log.error("Failed to start management server on port: {}", managementPort, future.cause());
            }
        });
    }

    public void stop() {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("FRP Server stopped");
    }
}
