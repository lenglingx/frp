# FRP

这个FRP项目参考了：MuYun-FRP
MuYun-FRP：https://github.com/ximatai/MuYunFRP
由于MuYun-FRP使用了verxt和quarkus，我这里使用了AI帮忙修改成了纯netty。

### FRP 是一种代理服务，主要解决网络单向可通的情况下，把内网的服务暴露到公网的问题。

典型的应用场景如下：

1. 在家庭宽带环境内搭建的网络服务想暴露到公网，供他人访问。
2. 一个服务器没有公网，但是有一个跳板机同时可以访问公网和该服务器，则可借助跳板机，将内网服务暴露到公网。

### 名词解释：

* `FRP-Server`，服务端，负责接收客户端的请求，并对数据进行转发，通常运行在公网上。
* `Tunnel`，服务端所拉起的一个通道，该通道包含两个端口，`openPort`是服务代理后可供用户直接访问的端口，`agentPort`是供
  `agent`端链接的端口。 一个`FRP-Server`可以配置多个`Tunnel`。
* `FRP-Agent`，代理客户端，通常运行在私网内，用来扮演请求真实服务即`Upstream Server`和数据转发的角色。所以要在其中配置
  `frp-tunnel`信息以及真实上游服务的`proxy`信息。

### 网络图

![FRP](https://github.com/user-attachments/assets/f4817a58-d26d-425f-af48-abf2ec077de9)

### 使用

#### 1. 环境要求：`JRE 21+`

#### 2. 服务端配置及启动

典型配置文件（*frp-server.yml*）：

```yml
management:
  port: 8089

tunnels:
  - name: ssh-tunnel
    type: tcp
    openPort: 8022
    agentPort: 8023

  - name: mysql-tunnel
    type: tcp
    openPort: 8306
    agentPort: 8307

  - name: web-tunnel
    type: http
    openPort: 8080
    agentPort: 8081

```

路径存放：

```
├─frp-agent
│  │  dependency-reduced-pom.xml
│  │  pom.xml
│  │
│  ├─src
│  │  ├─main
│  │  │  ├─java
│  │  │  │  └─com
│  │  │  │      └─imddy
│  │  │  │          └─frp
│  │  │  │              └─agent
│  │  │  │                  │  FrpAgent.java
│  │  │  │                  │  FrpAgentApplication.java
│  │  │  │                  │
│  │  │  │                  ├─config
│  │  │  │                  │      AgentConfig.java
│  │  │  │                  │
│  │  │  │                  ├─handler
│  │  │  │                  │      ProxyClientHandler.java
│  │  │  │                  │      ServerConnectionHandler.java
│  │  │  │                  │
│  │  │  │                  └─proxy
│  │  │  │                          ProxyConnectionManager.java
│  │  │  │
│  │  │  └─resources
│  │  │          frp-agent-demo.yml
│  │  │          frp-agent.yml
│  │  │          logback.xml
│  └─target
│      │  frp-agent.jar
│      │  frp-agent.yml
│      │  original-frp-agent.jar
│      │
│      ├─classes
│      │  │  frp-agent-demo.yml
│      │  │  frp-agent.yml
│      │  │  logback.xml

```

启动命令：

```shell
java -jar frp-server.jar
```

#### 3. Agent端配置及启动

典型配置文件（*frp-agent.yml*）：

```yml
type: tcp
tunnelName: ssh-tunnel

frpTunnel:
  host: 127.0.0.1
  port: 8023

proxy:
  host: 192.168.202.113
  port: 22

```

路径存放：

```
└─frp-server
    │  dependency-reduced-pom.xml
    │  pom.xml
    │
    ├─src
    │  ├─main
    │  │  ├─java
    │  │  │  └─com
    │  │  │      └─imddy
    │  │  │          └─frp
    │  │  │              └─server
    │  │  │                  │  FrpServer.java
    │  │  │                  │  FrpServerApplication.java
    │  │  │                  │
    │  │  │                  ├─config
    │  │  │                  │      ServerConfig.java
    │  │  │                  │
    │  │  │                  ├─handler
    │  │  │                  │      AgentServerHandler.java
    │  │  │                  │      ClientProxyHandler.java
    │  │  │                  │      HttpProxyHandler.java
    │  │  │                  │      ManagementHandler.java
    │  │  │                  │      UdpProxyHandler.java
    │  │  │                  │
    │  │  │                  └─tunnel
    │  │  │                          TunnelManager.java
    │  │  │
    │  │  └─resources
    │  │          frp-server-demo.yml
    │  │          frp-server.yml
    │  │          logback.xml
    └─target
        │  frp-server.jar
        │  original-frp-server.jar
        │
        ├─classes
        │  │  frp-server-demo.yml
        │  │  frp-server.yml
        │  │  logback.xml
        │  │
```

启动命令：

```shell
java -jar frp-agent.jar
java -jar frp-agent.jar frp-agent.yml 
```

其他补充：

1. 如果需要把`jar`当做服务持续启动的话，可以参考下面的命令：

    ```shell
    nohup java -jar frp-agent.jar > /dev/null 2>&1 & 
    ```
2. 如果遇到启动失败，请检查端口占用情况。典型的报错信息为：`java.net.BindException: Address already in use`

### For Developer
项目打包
```shell script
mvn clean package 
```


### 使用效果截图

![FRP](https://github.com/user-attachments/assets/f4817a58-d26d-425f-af48-abf2ec077de9)

![FRP](https://github.com/user-attachments/assets/f4817a58-d26d-425f-af48-abf2ec077de9)

![FRP](https://github.com/user-attachments/assets/f4817a58-d26d-425f-af48-abf2ec077de9)

![FRP](https://github.com/user-attachments/assets/f4817a58-d26d-425f-af48-abf2ec077de9)