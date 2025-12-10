package com.imddy.frp.agent.handler;

import com.imddy.frp.common.protocol.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel serverChannel;
    private final String channelId;
    private volatile boolean closing = false;

    public ProxyClientHandler(Channel serverChannel, String channelId) {
        this.serverChannel = serverChannel;
        this.channelId = channelId;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            try {
                if (buf.readableBytes() > 0) {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);

                    // 发送数据到FRP Server
                    Message dataMsg = Message.data(channelId, data);

                    // 等待数据发送完成
                    serverChannel.writeAndFlush(dataMsg).addListener(future -> {
                        if (future.isSuccess()) {
                            log.debug("Data sent to server successfully: channelId={}, size={}", channelId, data.length);
                        } else {
                            log.error("Failed to send data to server: channelId={}", channelId, future.cause());
                            ctx.close();
                        }
                    });
                }
            } finally {
                buf.release();
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (!closing) {
            closing = true;
            // 给一点时间让最后的数据发送完成
            ctx.executor().schedule(() -> {
                // 通知Server连接断开
                Message disconnectMsg = Message.disconnect(channelId);
                serverChannel.writeAndFlush(disconnectMsg);
                log.info("Proxy client disconnected: {}", channelId);
            }, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Exception in proxy client handler: {}", channelId, cause);
        ctx.close();
    }
}
