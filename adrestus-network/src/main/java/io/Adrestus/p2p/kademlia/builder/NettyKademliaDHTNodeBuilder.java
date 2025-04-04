package io.Adrestus.p2p.kademlia.builder;

import io.Adrestus.config.NodeSettings;
import io.Adrestus.p2p.kademlia.NettyKademliaDHTNode;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.Adrestus.p2p.kademlia.connection.MessageSender;
import io.Adrestus.p2p.kademlia.factory.GsonFactory;
import io.Adrestus.p2p.kademlia.factory.KademliaMessageHandlerFactory;
import io.Adrestus.p2p.kademlia.factory.NettyServerInitializerFactory;
import io.Adrestus.p2p.kademlia.node.DHTKademliaNode;
import io.Adrestus.p2p.kademlia.node.DHTKademliaNodeAPI;
import io.Adrestus.p2p.kademlia.node.KeyHashGenerator;
import io.Adrestus.p2p.kademlia.repository.KademliaRepository;
import io.Adrestus.p2p.kademlia.server.KademliaNodeServer;
import io.Adrestus.p2p.kademlia.table.Bucket;
import io.Adrestus.p2p.kademlia.table.RoutingTable;
import lombok.Getter;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


@Getter
public class NettyKademliaDHTNodeBuilder<K extends Serializable, V extends Serializable> {
    private final BigInteger id;
    private final NettyConnectionInfo connectionInfo;
    private final Class<K> kClass;
    private final Class<V> vClass;
    private RoutingTable<BigInteger, NettyConnectionInfo, Bucket<BigInteger, NettyConnectionInfo>> routingTable;
    private MessageSender<BigInteger, NettyConnectionInfo> messageSender;
    private NodeSettings nodeSettings;
    private GsonFactory gsonFactory;
    private final KademliaRepository<K, V> repository;
    private final KeyHashGenerator<BigInteger, K> keyHashGenerator;
    private KademliaNodeServer<K, V> kademliaNodeServer;
    private KademliaMessageHandlerFactory<K, V> kademliaMessageHandlerFactory;
    private NettyServerInitializerFactory<K, V> nettyServerInitializerFactory;

    protected List<String> required = new ArrayList<>();

    public NettyKademliaDHTNodeBuilder(Class<K> kClass, Class<V> vClass, BigInteger id, NettyConnectionInfo connectionInfo, KademliaRepository<K, V> repository, KeyHashGenerator<BigInteger, K> keyHashGenerator) {
        this.kClass = kClass;
        this.vClass = vClass;
        this.id = id;
        this.connectionInfo = connectionInfo;
        this.repository = repository;
        this.keyHashGenerator = keyHashGenerator;
    }

    public NettyKademliaDHTNodeBuilder<K, V> routingTable(RoutingTable<BigInteger, NettyConnectionInfo, Bucket<BigInteger, NettyConnectionInfo>> routingTable) {
        this.routingTable = routingTable;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> messageSender(MessageSender<BigInteger, NettyConnectionInfo> messageSender) {
        this.messageSender = messageSender;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> nodeSettings(NodeSettings nodeSettings) {
        this.nodeSettings = nodeSettings;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> kademliaNodeServer(KademliaNodeServer<K, V> kademliaNodeServer) {
        this.kademliaNodeServer = kademliaNodeServer;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> kademliaMessageHandlerFactory(KademliaMessageHandlerFactory<K, V> kademliaMessageHandlerFactory) {
        this.kademliaMessageHandlerFactory = kademliaMessageHandlerFactory;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> nettyServerInitializerFactory(NettyServerInitializerFactory<K, V> nettyServerInitializerFactory) {
        this.nettyServerInitializerFactory = nettyServerInitializerFactory;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> gsonFactory(GsonFactory gsonFactory) {
        this.gsonFactory = gsonFactory;
        return this;
    }

    public NettyKademliaDHTNodeBuilder<K, V> withNodeSettings(NodeSettings nodeSettings) {
        this.nodeSettings = nodeSettings;
        return this;
    }

    public NettyKademliaDHTNode<K, V> build() {
        fillDefaults();

        DHTKademliaNodeAPI<BigInteger, NettyConnectionInfo, K, V> kademliaNode = new DHTKademliaNode<>(
                this.id,
                this.connectionInfo,
                this.routingTable,
                this.messageSender,
                this.nodeSettings, this.repository, this.keyHashGenerator
        );

        return new NettyKademliaDHTNode<>(kademliaNode, this.kademliaNodeServer);
    }

    protected void fillDefaults() {
        NettyKademliaDHTNodeDefaults.run(kClass, vClass, this);
    }


}
