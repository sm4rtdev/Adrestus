package io.Adrestus.p2p.kademlia;


import io.Adrestus.config.KademliaConfiguration;
import io.Adrestus.config.NodeSettings;
import io.Adrestus.p2p.kademlia.builder.NettyKademliaDHTNodeBuilder;
import io.Adrestus.p2p.kademlia.client.OkHttpMessageSender;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.Adrestus.p2p.kademlia.exception.DuplicateStoreRequest;
import io.Adrestus.p2p.kademlia.exception.UnsupportedBoundingException;
import io.Adrestus.p2p.kademlia.model.LookupAnswer;
import io.Adrestus.p2p.kademlia.model.StoreAnswer;
import io.Adrestus.p2p.kademlia.node.KeyHashGenerator;
import io.Adrestus.p2p.kademlia.repository.KademliaRepository;
import io.Adrestus.p2p.kademlia.util.BoundedHashUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DHTTest {

    private static OkHttpMessageSender<String, String> nettyMessageSender1;
    private static OkHttpMessageSender<String, String> nettyMessageSender2;
    private static NettyKademliaDHTNode<String, String> node1;
    private static NettyKademliaDHTNode<String, String> node2;


    @SneakyThrows
    @BeforeAll
    public static void init() {
        KademliaConfiguration.IDENTIFIER_SIZE = 4;
        KademliaConfiguration.BUCKET_SIZE = 100;
        KademliaConfiguration.PING_SCHEDULE_TIME_VALUE = 5;
        NodeSettings.getInstance();

        KeyHashGenerator<BigInteger, String> keyHashGenerator = key -> {
            try {
                return new BoundedHashUtil(NodeSettings.getInstance().getIdentifierSize()).hash(key.hashCode(), BigInteger.class);
            } catch (UnsupportedBoundingException e) {
                return null;
            }
        };

        nettyMessageSender1 = new OkHttpMessageSender<>(String.class, String.class);

        // node 1
        node1 = new NettyKademliaDHTNodeBuilder<String, String>(
                String.class,
                String.class,
                BigInteger.valueOf(1),
                new NettyConnectionInfo("127.0.0.1", 8081),
                new SampleRepository(),
                keyHashGenerator
        ).withNodeSettings(NodeSettings.getInstance()).build();
        node1.start();


        // node 2
        node2 = new NettyKademliaDHTNodeBuilder<String, String>(
                String.class,
                String.class,
                BigInteger.valueOf(2),
                new NettyConnectionInfo("127.0.0.1", 8082),
                new SampleRepository(),
                keyHashGenerator
        ).withNodeSettings(NodeSettings.getInstance()).build();
        System.out.println("Bootstrapped? " + node2.start(node1).get(5, TimeUnit.SECONDS));

    }

    @AfterAll
    public static void cleanup() {
        node2.stop();
        node1.stop();
        nettyMessageSender1.stop();
    }

    @Test
    void testDhtStoreLookup() throws DuplicateStoreRequest, ExecutionException, InterruptedException, TimeoutException {
        String[] values = new String[]{"V", "ABC", "SOME VALUE"};
        for (String v : values) {
            System.out.println("Testing DHT for K: " + v.hashCode() + " & V: " + v);
            StoreAnswer<BigInteger, String> storeAnswer = node2.store("" + v.hashCode(), v).get(5, TimeUnit.SECONDS);
            Assertions.assertEquals(StoreAnswer.Result.STORED, storeAnswer.getResult());

            LookupAnswer<BigInteger, String, String> lookupAnswer = node1.lookup("" + v.hashCode()).get(5, TimeUnit.SECONDS);
            Assertions.assertEquals(LookupAnswer.Result.FOUND, lookupAnswer.getResult());
            Assertions.assertEquals(lookupAnswer.getValue(), v);
            System.out.println("Node " + node1.getId() + " found " + v.hashCode() + " from " + lookupAnswer.getNodeId());

            lookupAnswer = node2.lookup("" + v.hashCode()).get(5, TimeUnit.SECONDS);
            Assertions.assertEquals(LookupAnswer.Result.FOUND, lookupAnswer.getResult());
            Assertions.assertEquals(v, lookupAnswer.getValue());
            System.out.println("Node " + node2.getId() + " found " + v.hashCode() + " from " + lookupAnswer.getNodeId());
        }

    }

    @Test
    void testNetworkKnowledge() {
        Assertions.assertTrue(node1.getRoutingTable().contains(BigInteger.valueOf(2)));
        Assertions.assertTrue(node2.getRoutingTable().contains(BigInteger.valueOf(1)));
    }

    public static class SampleRepository implements KademliaRepository<String, String> {
        protected final Map<String, String> data = new HashMap<>();

        @Override
        public void store(String key, String value) {
            data.putIfAbsent(key, value);
        }

        @Override
        public String get(String key) {
            return data.get(key);
        }

        @Override
        public void remove(String key) {
            data.remove(key);
        }

        @Override
        public boolean contains(String key) {
            return data.containsKey(key);
        }

        @Override
        public List<String> getList() {
            return null;
        }
    }
}
