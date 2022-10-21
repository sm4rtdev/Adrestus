package io.Adrestus.p2p.kademlia.node;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.Adrestus.config.KademliaConfiguration;
import io.Adrestus.p2p.kademlia.NettyKademliaDHTNode;
import io.Adrestus.p2p.kademlia.NodeSettings;
import io.Adrestus.p2p.kademlia.builder.NettyKademliaDHTNodeBuilder;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.Adrestus.p2p.kademlia.protocol.MessageType;
import io.Adrestus.p2p.kademlia.protocol.handler.MessageHandler;
import io.Adrestus.p2p.kademlia.protocol.handler.PongMessageHandler;
import io.Adrestus.p2p.kademlia.protocol.message.KademliaMessage;
import io.Adrestus.p2p.kademlia.protocol.message.PongKademliaMessage;
import io.Adrestus.p2p.kademlia.repository.KademliaData;
import io.Adrestus.p2p.kademlia.repository.KademliaRepository;
import io.Adrestus.p2p.kademlia.repository.KademliaRepositoryImp;
import io.Adrestus.p2p.kademlia.util.BoundedHashUtil;
import io.Adrestus.p2p.kademlia.util.LoggerKademlia;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DHTBootstrapNode {

    private final NettyConnectionInfo nettyConnectionInfo;
    private final KeyHashGenerator<BigInteger, String> keyHashGenerator;
    private final KademliaRepository repository;
    private MessageHandler<BigInteger, NettyConnectionInfo> handler;
    private NettyKademliaDHTNode<String, KademliaData> bootStrapNode;

    public DHTBootstrapNode(NettyConnectionInfo nettyConnectionInfo) {
        LoggerKademlia.setLevelOFF();
        this.nettyConnectionInfo = nettyConnectionInfo;
        this.keyHashGenerator = key -> new BoundedHashUtil(NodeSettings.Default.IDENTIFIER_SIZE).hash(key.hashCode(), BigInteger.class);
        this.repository = new KademliaRepositoryImp();
        this.setupOptions();
        this.InitHandler();
    }


    private void InitHandler(){
        handler = new PongMessageHandler<BigInteger, NettyConnectionInfo>() {
            @Override
            public <I extends KademliaMessage<BigInteger, NettyConnectionInfo, ?>, O extends KademliaMessage<BigInteger, NettyConnectionInfo, ?>> O doHandle(KademliaNodeAPI<BigInteger, NettyConnectionInfo> kademliaNode, I message) {
                kademliaNode.getRoutingTable().getBuckets().stream().forEach(x -> System.out.println(x.toString()));
                return (O) doHandle(kademliaNode, (PongKademliaMessage<BigInteger, NettyConnectionInfo>) message);
            }
        };
    }

    private void setupOptions() {
        NodeSettings.Default.IDENTIFIER_SIZE = 4;
        NodeSettings.Default.BUCKET_SIZE = 100;
        NodeSettings.Default.MAXIMUM_STORE_AND_LOOKUP_TIMEOUT_VALUE = 1;
        NodeSettings.Default.MAXIMUM_STORE_AND_LOOKUP_TIMEOUT_TIME_UNIT = TimeUnit.SECONDS;
        NodeSettings.Default.ENABLED_FIRST_STORE_REQUEST_FORCE_PASS = true;
        NodeSettings.Default.PING_SCHEDULE_TIME_UNIT = TimeUnit.SECONDS;
        NodeSettings.Default.PING_SCHEDULE_TIME_VALUE = 2;
    }

    public BigInteger nextRandomBigInteger() {
        BigInteger n = BigInteger.valueOf(123456789);
        Random rand = new Random();
        BigInteger result = new BigInteger(n.bitLength(), rand);
        while (result.compareTo(n) >= 0) {
            result = new BigInteger(n.bitLength(), rand);
        }
        return result;
    }

    public void start() {
        bootStrapNode = new NettyKademliaDHTNodeBuilder<>(
                KademliaConfiguration.BootstrapNodeID,
                this.nettyConnectionInfo,
                this.repository,
                keyHashGenerator
        ).withNodeSettings(NodeSettings.Default.build()).build();
        bootStrapNode.registerMessageHandler(MessageType.PONG, handler);
        bootStrapNode.start();
    }

    public NettyConnectionInfo getNettyConnectionInfo() {
        return nettyConnectionInfo;
    }

    public KeyHashGenerator<BigInteger, String> getKeyHashGenerator() {
        return keyHashGenerator;
    }

    public KademliaRepository getRepository() {
        return repository;
    }

    public MessageHandler<BigInteger, NettyConnectionInfo> getHandler() {
        return handler;
    }

    public void setHandler(MessageHandler<BigInteger, NettyConnectionInfo> handler) {
        this.handler = handler;
    }

    public NettyKademliaDHTNode<String, KademliaData> getBootStrapNode() {
        return bootStrapNode;
    }

    public void setBootStrapNode(NettyKademliaDHTNode<String, KademliaData> bootStrapNode) {
        this.bootStrapNode = bootStrapNode;
    }

    public void close() {
        this.bootStrapNode.stopNow();
    }
}
