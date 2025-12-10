package com.imddy.frp.common.codec;

import com.imddy.frp.common.protocol.Message;
import com.imddy.frp.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
public class MessageCodec extends MessageToMessageCodec<ByteBuf, Message> {
    private static final short MAGIC_NUMBER = (short) 0xCAFE;

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        ByteBuf buf = ctx.alloc().buffer();

        // Magic Number
        buf.writeShort(MAGIC_NUMBER);

        // Message Type
        buf.writeByte(msg.getType().getCode());

        // Tunnel Name
        writeString(buf, msg.getTunnelName());

        // Channel ID
        writeString(buf, msg.getChannelId());

        // Metadata
        writeString(buf, msg.getMetadata());

        // Data
        if (msg.getData() != null && msg.getData().length > 0) {
            buf.writeInt(msg.getData().length);
            buf.writeBytes(msg.getData());
        } else {
            buf.writeInt(0);
        }

        out.add(buf);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buf, List<Object> out) throws Exception {
        if (buf.readableBytes() < 3) {
            return;
        }

        buf.markReaderIndex();

        // Magic Number
        short magic = buf.readShort();
        if (magic != MAGIC_NUMBER) {
            log.error("Invalid magic number: {}", magic);
            buf.resetReaderIndex();
            return;
        }

        // Message Type
        byte typeCode = buf.readByte();
        MessageType type = MessageType.fromCode(typeCode);

        Message message = new Message(type);

        // Tunnel Name
        message.setTunnelName(readString(buf));

        // Channel ID
        message.setChannelId(readString(buf));

        // Metadata
        message.setMetadata(readString(buf));

        // Data
        int dataLen = buf.readInt();
        if (dataLen > 0) {
            byte[] data = new byte[dataLen];
            buf.readBytes(data);
            message.setData(data);
        }

        out.add(message);
    }

    private void writeString(ByteBuf buf, String str) {
        if (str == null || str.isEmpty()) {
            buf.writeShort(0);
        } else {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            buf.writeShort(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    private String readString(ByteBuf buf) {
        short len = buf.readShort();
        if (len <= 0) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

}
