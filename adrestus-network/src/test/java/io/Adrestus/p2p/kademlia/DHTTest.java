package io.Adrestus.p2p.kademlia;


import io.ep2p.kademlia.NodeSettings;
import io.ep2p.kademlia.exception.DuplicateStoreRequest;
import io.ep2p.kademlia.model.LookupAnswer;
import io.ep2p.kademlia.model.StoreAnswer;
import io.Adrestus.p2p.kademlia.builder.NettyKademliaDHTNodeBuilder;
import io.Adrestus.p2p.kademlia.client.NettyMessageSender;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.ep2p.kademlia.node.KeyHashGenerator;
import io.ep2p.kademlia.repository.KademliaRepository;
import io.ep2p.kademlia.util.BoundedHashUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class DHTTest {

    private static NettyMessageSender<String, String> nettyMessageSender1;
    private static NettyMessageSender<String, String> nettyMessageSender2;
    private static NettyKademliaDHTNode<String, String> node1;
    private static NettyKademliaDHTNode<String, String> node2;


    @SneakyThrows
    @BeforeAll
    public static void init() {
        NodeSettings.Default.IDENTIFIER_SIZE = 4;
        NodeSettings.Default.BUCKET_SIZE = 100;
        NodeSettings.Default.PING_SCHEDULE_TIME_VALUE = 5;

        KeyHashGenerator<BigInteger, String> keyHashGenerator = key -> new BoundedHashUtil(NodeSettings.Default.IDENTIFIER_SIZE).hash(key.hashCode(), BigInteger.class);

        nettyMessageSender1 = new NettyMessageSender<>();
        nettyMessageSender2 = new NettyMessageSender<>();

        // node 1
        node1 = new NettyKademliaDHTNodeBuilder<>(
                BigInteger.valueOf(1),
                new NettyConnectionInfo("127.0.0.1", 8081),
                new SampleRepository(),
                keyHashGenerator
        ).build();
        node1.start();


        // node 2
        node2 = new NettyKademliaDHTNodeBuilder<>(
                BigInteger.valueOf(2),
                new NettyConnectionInfo("127.0.0.1", 8082),
                new SampleRepository(),
                keyHashGenerator
        ).build();
        System.out.println("Bootstrapped? " + node2.start(node1).get(5, TimeUnit.SECONDS));

    }

    @AfterAll
    public static void cleanup(){
        nettyMessageSender1.stop();
        nettyMessageSender2.stop();
        node1.stop();
        node2.stop();
    }

    @Test
    void testDhtStoreLookup() throws DuplicateStoreRequest, ExecutionException, InterruptedException {
        String[] values = new String[]{"V", "ABC", "SOME VALUE"};
        for (String v : values){
            System.out.println("Testing DHT for K: " + v.hashCode() + " & V: " + v);
            StoreAnswer<BigInteger, String> storeAnswer = node2.store("" + v.hashCode(), v).get();
            Assertions.assertEquals(StoreAnswer.Result.STORED, storeAnswer.getResult());

            LookupAnswer<BigInteger, String, String> lookupAnswer = node1.lookup("" + v.hashCode()).get();
            Assertions.assertEquals(LookupAnswer.Result.FOUND, lookupAnswer.getResult());
            Assertions.assertEquals(lookupAnswer.getValue(), v);
            System.out.println("Node " + node1.getId() + " found " + v.hashCode() + " from " + lookupAnswer.getNodeId());

            lookupAnswer = node2.lookup("" + v.hashCode()).get();
            Assertions.assertEquals(LookupAnswer.Result.FOUND, lookupAnswer.getResult());
            Assertions.assertEquals(v, lookupAnswer.getValue());
            System.out.println("Node " + node2.getId() + " found " + v.hashCode() + " from " + lookupAnswer.getNodeId());
        }

    }

    @Test
    void testNetworkKnowledge(){
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
    }
}
