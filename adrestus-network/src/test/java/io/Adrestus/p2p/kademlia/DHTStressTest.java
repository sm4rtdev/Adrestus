package io.Adrestus.p2p.kademlia;

import com.google.common.net.InetAddresses;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.Adrestus.TreeFactory;
import io.Adrestus.Trie.PatriciaTreeNode;
import io.Adrestus.config.AdrestusConfiguration;
import io.Adrestus.config.KademliaConfiguration;
import io.Adrestus.config.NodeSettings;
import io.Adrestus.crypto.HashUtil;
import io.Adrestus.crypto.SecurityAuditProofs;
import io.Adrestus.crypto.WalletAddress;
import io.Adrestus.crypto.bls.model.BLSPrivateKey;
import io.Adrestus.crypto.bls.model.BLSPublicKey;
import io.Adrestus.crypto.bls.model.BLSSignature;
import io.Adrestus.crypto.bls.model.Signature;
import io.Adrestus.crypto.elliptic.ECDSASign;
import io.Adrestus.crypto.elliptic.ECDSASignatureData;
import io.Adrestus.crypto.elliptic.ECKeyPair;
import io.Adrestus.crypto.elliptic.Keys;
import io.Adrestus.p2p.kademlia.adapter.PublicKeyTypeAdapter;
import io.Adrestus.p2p.kademlia.client.OkHttpMessageSender;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.Adrestus.p2p.kademlia.exception.DuplicateStoreRequest;
import io.Adrestus.p2p.kademlia.exception.UnsupportedBoundingException;
import io.Adrestus.p2p.kademlia.model.FindNodeAnswer;
import io.Adrestus.p2p.kademlia.model.StoreAnswer;
import io.Adrestus.p2p.kademlia.node.DHTBootstrapNode;
import io.Adrestus.p2p.kademlia.node.DHTRegularNode;
import io.Adrestus.p2p.kademlia.node.KeyHashGenerator;
import io.Adrestus.p2p.kademlia.repository.KademliaData;
import io.Adrestus.p2p.kademlia.util.BoundedHashUtil;
import org.apache.commons.codec.binary.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongycastle.util.encoders.Hex;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DHTStressTest {
    private static int version = 0x00;
    private static KademliaData kademliaData;
    private static BLSPublicKey vk;
    private static KademliaData seridata;
    private static OkHttpMessageSender<String, String> nettyMessageSender1, nettyMessageSender3, nettyMessageSender4, nettyMessageSender5;
    private static OkHttpMessageSender<String, String> nettyMessageSender2;

    @BeforeAll
    public static void setup() throws Exception {
        String mnemonic_code = "fd8cee9c1a3f3f57ab51b25740b24341ae093c8f697fde4df948050d3acd1700f6379d716104d2159e4912509c40ac81714d833e93b822e5ba0fadd68d5568a2";
        SecureRandom random = SecureRandom.getInstance(AdrestusConfiguration.ALGORITHM, AdrestusConfiguration.PROVIDER);
        random.setSeed(Hex.decode(mnemonic_code));

        ECKeyPair ecKeyPair = Keys.create256r1KeyPair(random);
        String adddress = WalletAddress.generate_address((byte) version, ecKeyPair.getPublicKey());
        ECDSASign ecdsaSign = new ECDSASign();


        ECDSASignatureData signatureData = ecdsaSign.signSecp256r1Message(HashUtil.sha256(StringUtils.getBytesUtf8(adddress)), ecKeyPair);


        BLSPrivateKey sk = new BLSPrivateKey(42);
        vk = new BLSPublicKey(sk);
        BLSPublicKey copy = BLSPublicKey.fromByte(Hex.decode(KademliaConfiguration.BLSPublicKeyHex));
        assertEquals(copy, vk);


        kademliaData = new KademliaData(new SecurityAuditProofs(adddress, ecKeyPair.getPublicKey(), signatureData), new NettyConnectionInfo("127.0.0.1", 8080));
        kademliaData.getAddressData().setValidatorBlSPublicKey(vk);
        TreeFactory.getMemoryTree(0).store(adddress, new PatriciaTreeNode(BigDecimal.valueOf(1000), 0));
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(PublicKey.class, new PublicKeyTypeAdapter())
                .create();
        String jsonString = gson.toJson(kademliaData);
        KademliaData copydata = gson.fromJson(jsonString, KademliaData.class);
        assertEquals(kademliaData, copydata);

        kademliaData.setHash(jsonString);
        Signature bls_sig = BLSSignature.sign(StringUtils.getBytesUtf8(kademliaData.getHash()), sk);


        String jsonString2 = gson.toJson(kademliaData);
        seridata = gson.fromJson(jsonString2, KademliaData.class);
        KademliaData clonebale = (KademliaData) seridata.clone();
        assertEquals(seridata, clonebale);

        clonebale.setHash("");


        //checks
        String clonedhash = gson.toJson(clonebale);
        assertEquals(seridata.getHash(), clonedhash);
        boolean verify2 = ecdsaSign.secp256r1Verify(HashUtil.sha256(StringUtils.getBytesUtf8(seridata.getAddressData().getAddress())), seridata.getAddressData().getECDSAPublicKey(), seridata.getAddressData().getECDSASignature());
        assertEquals(true, verify2);
        System.out.println("done");
    }

    @Test
    public void test() throws DuplicateStoreRequest, ExecutionException, InterruptedException, TimeoutException {
        Random random = new Random();
        int count = 2;
        KeyHashGenerator<BigInteger, String> keyHashGenerator = key -> {
            try {
                return new BoundedHashUtil(NodeSettings.getInstance().getIdentifierSize()).hash(key.hashCode(), BigInteger.class);
            } catch (UnsupportedBoundingException e) {
                return null;
            }
        };
        while (count > 0) {
            String ipString1 = InetAddresses.fromInteger(random.nextInt()).getHostAddress();
            String ipString2 = InetAddresses.fromInteger(random.nextInt()).getHostAddress();
            String ipString3 = InetAddresses.fromInteger(random.nextInt()).getHostAddress();
            String ipString4 = InetAddresses.fromInteger(random.nextInt()).getHostAddress();
            BigInteger id1 = new BigInteger(HashUtil.convertIPtoHex(ipString1, 16));
            BigInteger id2 = new BigInteger(HashUtil.convertIPtoHex(ipString2, 16));
            BigInteger id3 = new BigInteger(HashUtil.convertIPtoHex(ipString3, 16));
            BigInteger id4 = new BigInteger(HashUtil.convertIPtoHex(ipString4, 16));


            nettyMessageSender1 = new OkHttpMessageSender<>(String.class, String.class);
            nettyMessageSender2 = new OkHttpMessageSender<>(String.class, String.class);
            nettyMessageSender3 = new OkHttpMessageSender<>(String.class, String.class);
            nettyMessageSender4 = new OkHttpMessageSender<>(String.class, String.class);
            nettyMessageSender5 = new OkHttpMessageSender<>(String.class, String.class);

            DHTBootstrapNode dhtBootstrapNode = new DHTBootstrapNode(
                    new NettyConnectionInfo(KademliaConfiguration.LOCAL_NODE_IP, KademliaConfiguration.BootstrapNodePORT), id1, keyHashGenerator);
            dhtBootstrapNode.start();

            DHTRegularNode regularNode = new DHTRegularNode(
                    new NettyConnectionInfo(KademliaConfiguration.LOCAL_NODE_IP, KademliaConfiguration.PORT), id2, keyHashGenerator);
            regularNode.start(dhtBootstrapNode);

            DHTRegularNode regularNode2 = new DHTRegularNode(
                    new NettyConnectionInfo(KademliaConfiguration.LOCAL_NODE_IP, KademliaConfiguration.PORT + 1), id3, keyHashGenerator);
            regularNode2.start(dhtBootstrapNode);

            DHTRegularNode regularNode3 = new DHTRegularNode(
                    new NettyConnectionInfo(KademliaConfiguration.LOCAL_NODE_IP, KademliaConfiguration.PORT + 2), id4, keyHashGenerator);
            regularNode3.start(dhtBootstrapNode);

            StoreAnswer<BigInteger, String> storeAnswer = regularNode.getRegular_node().store("V", seridata).get(5, TimeUnit.SECONDS);
            StoreAnswer<BigInteger, String> storeAnswer2 = regularNode2.getRegular_node().store("F", seridata).get(5, TimeUnit.SECONDS);
            StoreAnswer<BigInteger, String> storeAnswer3 = regularNode3.getRegular_node().store("G", seridata).get(5, TimeUnit.SECONDS);
            Thread.sleep(3000);
            KademliaData cp = regularNode.getRegular_node().lookup("V").get(5, TimeUnit.SECONDS).getValue();
            KademliaData cp2 = regularNode2.getRegular_node().lookup("V").get(5, TimeUnit.SECONDS).getValue();
            KademliaData cp3 = regularNode3.getRegular_node().lookup("V").get(5, TimeUnit.SECONDS).getValue();
            KademliaData cp4 = dhtBootstrapNode.getBootStrapNode().lookup("V").get(5, TimeUnit.SECONDS).getValue();
            assertEquals(seridata, cp);
            assertEquals(seridata, cp2);
            assertEquals(seridata, cp3);
            assertEquals(seridata, cp4);

            FindNodeAnswer<BigInteger, NettyConnectionInfo> sa = regularNode2.getRegular_node().getRoutingTable().findClosest(id3);
            System.out.println("Done:" + count);
            nettyMessageSender1.stop();
            nettyMessageSender2.stop();
            nettyMessageSender3.stop();
            nettyMessageSender4.stop();
            nettyMessageSender5.stop();
            regularNode.getRegular_node().stop();
            regularNode2.getRegular_node().stop();
            regularNode3.getRegular_node().stop();
            dhtBootstrapNode.getBootStrapNode().stop();
            count--;
        }
    }
}
