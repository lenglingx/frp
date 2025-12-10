package com.imddy.frp.agent.handler;

import com.imddy.frp.agent.config.AgentConfig;
import com.imddy.frp.agent.proxy.ProxyConnectionManager;
import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.common.protocol.MessageType;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ServerConnectionHandler extends SimpleChannelInboundHandler<Message> {
    private final AgentConfig config;
    private final ProxyConnectionManager connectionManager;
    private final EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ServerConnectionHandler(AgentConfig config, ProxyConnectionManager connectionManager, EventLoopGroup workerGroup) {
        this.config = config;
        this.connectionManager = connectionManager;
        this.workerGroup = workerGroup;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.serverChannel = ctx.channel();

        // 发送注册消息
        Message registerMsg = Message.register(config.getTunnelName(), config.getProtocolType());
        ctx.writeAndFlush(registerMsg);

        log.info("Connected to FRP Server, sending register message");
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        switch (msg.getType()) {
            case REGISTER_SUCCESS:
                handleRegisterSuccess();
                break;
            case REGISTER_FAIL:
                handleRegisterFail(msg);
                break;
            case HEARTBEAT_ACK:
                log.debug("Received heartbeat ack");
                break;
            case CONNECT:
                handleConnect(msg);
                break;
            case DATA:
                handleData(msg);
                break;
            case DISCONNECT:
                handleDisconnect(msg);
                break;
            default:
                log.warn("Unknown message type: {}", msg.getType());
        }
    }

    private void handleRegisterSuccess() {
        log.info("Register success, agent is ready");
        startHeartbeat();
    }

    private void handleRegisterFail(Message msg) {
        log.error("Register failed: {}", msg.getMetadata());
        serverChannel.close();
    }

    private void handleConnect(Message msg) {
        String channelId = msg.getChannelId();
        log.info("New connection request: {}", channelId);

        // 连接到目标服务
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)  // 5秒超时
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new ProxyClientHandler(serverChannel, channelId));
                    }
                });

        String proxyHost = config.getProxy().getHost();
        int proxyPort = config.getProxy().getPort();

        bootstrap.connect(proxyHost, proxyPort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                connectionManager.addConnection(channelId, future.channel());
                log.info("Connected to proxy target: {}:{} for channel: {}", proxyHost, proxyPort, channelId);

                // 发送连接成功确认
                Message ackMsg = Message.connectAck(channelId, true);
                serverChannel.writeAndFlush(ackMsg);
            } else {
                log.error("Failed to connect to proxy target: {}:{}", proxyHost, proxyPort, future.cause());

                // 发送连接失败确认
                Message ackMsg = Message.connectAck(channelId, false);
                serverChannel.writeAndFlush(ackMsg);
            }
        });
    }


    private void handleData(Message msg) {
        String channelId = msg.getChannelId();
        Channel proxyChannel = connectionManager.getConnection(channelId);

        if (proxyChannel != null && proxyChannel.isActive()) {
            // 写入数据并等待完成
            proxyChannel.writeAndFlush(Unpooled.wrappedBuffer(msg.getData())).addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("Forward data to proxy target: channelId={}, size={}", channelId, msg.getData().length);
                } else {
                    log.error("Failed to forward data to proxy target: channelId={}", channelId, future.cause());
                    connectionManager.removeConnection(channelId);
                    Message disconnectMsg = Message.disconnect(channelId);
                    serverChannel.writeAndFlush(disconnectMsg);
                }
            });
        } else {
            log.warn("Proxy channel not found or inactive: {}", channelId);
            // 通知Server关闭连接
            Message disconnectMsg = Message.disconnect(channelId);
            serverChannel.writeAndFlush(disconnectMsg);
        }
    }

    private void handleDisconnect(Message msg) {
        String channelId = msg.getChannelId();
        connectionManager.removeConnection(channelId);
        log.info("Connection closed by server: {}", channelId);
    }

    private void startHeartbeat() {
        serverChannel.eventLoop().scheduleAtFixedRate(() -> {
            if (serverChannel.isActive()) {
                Message heartbeat = Message.heartbeat();
                serverChannel.writeAndFlush(heartbeat);
                log.debug("Sent heartbeat");
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.warn("Disconnected from FRP Server");
        connectionManager.closeAll();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in server connection handler", cause);
        ctx.close();
    }
}
