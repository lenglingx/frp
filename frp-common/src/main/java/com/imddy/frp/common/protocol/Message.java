package com.imddy.frp.common.protocol;

import lombok.Data;
import java.io.Serializable;

@Data
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private String tunnelName;
    private String channelId;
    private byte[] data;
    private String metadata;

    public Message() {
    }

    public Message(MessageType type) {
        this.type = type;
    }

    public static Message register(String tunnelName, ProxyProtocol protocol) {
        Message message = new Message(MessageType.REGISTER);
        message.setTunnelName(tunnelName);
        message.setMetadata("{\"protocol\":\"" + protocol.getName() + "\"}");
        return message;
    }

    public static Message heartbeat() {
        return new Message(MessageType.HEARTBEAT);
    }

    public static Message connect(String tunnelName, String channelId) {
        Message message = new Message(MessageType.CONNECT);
        message.setTunnelName(tunnelName);
        message.setChannelId(channelId);
        return message;
    }

    // 新增：连接确认消息
    public static Message connectAck(String channelId, boolean success) {
        Message message = new Message(MessageType.CONNECT_ACK);
        message.setChannelId(channelId);
        message.setMetadata(success ? "success" : "failed");
        return message;
    }

    public static Message disconnect(String channelId) {
        Message message = new Message(MessageType.DISCONNECT);
        message.setChannelId(channelId);
        return message;
    }

    public static Message data(String channelId, byte[] data) {
        Message message = new Message(MessageType.DATA);
        message.setChannelId(channelId);
        message.setData(data);
        return message;
    }
}
