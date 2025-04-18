package io.Adrestus.core;

import io.Adrestus.Trie.MerkleNode;
import io.Adrestus.Trie.MerkleTreeOptimizedImp;
import io.Adrestus.config.AdrestusConfiguration;
import io.Adrestus.core.Resourses.CachedLatestBlocks;
import io.Adrestus.core.Resourses.CachedZoneIndex;
import io.Adrestus.core.Util.BlockSizeCalculator;
import io.Adrestus.crypto.HashUtil;
import io.Adrestus.crypto.WalletAddress;
import io.Adrestus.crypto.bls.BLS381.ECP;
import io.Adrestus.crypto.bls.BLS381.ECP2;
import io.Adrestus.crypto.bls.mapper.ECP2mapper;
import io.Adrestus.crypto.bls.mapper.ECPmapper;
import io.Adrestus.crypto.bls.model.*;
import io.Adrestus.crypto.elliptic.ECDSASign;
import io.Adrestus.crypto.elliptic.ECDSASignatureData;
import io.Adrestus.crypto.elliptic.ECKeyPair;
import io.Adrestus.crypto.elliptic.Keys;
import io.Adrestus.crypto.elliptic.mapper.BigDecimalSerializer;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.crypto.elliptic.mapper.CustomSerializerTreeMap;
import io.Adrestus.crypto.mnemonic.Mnemonic;
import io.Adrestus.crypto.mnemonic.MnemonicException;
import io.Adrestus.crypto.mnemonic.Security;
import io.Adrestus.crypto.mnemonic.WordList;
import io.Adrestus.erasure.code.ArrayDataDecoder;
import io.Adrestus.erasure.code.ArrayDataEncoder;
import io.Adrestus.erasure.code.EncodingPacket;
import io.Adrestus.erasure.code.OpenRQ;
import io.Adrestus.erasure.code.decoder.SourceBlockDecoder;
import io.Adrestus.erasure.code.encoder.SourceBlockEncoder;
import io.Adrestus.erasure.code.parameters.FECParameterObject;
import io.Adrestus.erasure.code.parameters.FECParameters;
import io.Adrestus.erasure.code.parameters.FECParametersPreConditions;
import io.Adrestus.network.CachedEventLoop;
import io.Adrestus.network.ConsensusClient;
import io.Adrestus.network.ConsensusServer;
import io.Adrestus.rpc.CachedSerializableErasureObject;
import io.Adrestus.rpc.RpcErasureClient;
import io.Adrestus.rpc.RpcErasureServer;
import io.Adrestus.util.GetTime;
import io.Adrestus.util.SerializationUtil;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.rpc.client.RpcClient;
import io.activej.rpc.client.sender.strategy.RpcStrategies;
import io.activej.rpc.server.RpcRequestHandler;
import io.activej.rpc.server.RpcServer;
import io.activej.serializer.annotations.Deserialize;
import io.activej.serializer.annotations.Serialize;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.activej.rpc.client.sender.strategy.RpcStrategies.servers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FullExampleErasureTest {

    private static final int TRANSACTION_SIZE = 100;
    private static final int TIMEOUT = 1500;

    private static final String delimeter = "||";

    private static SerializationUtil<Signature> valueMapper;
    private static BLSPrivateKey sk1;
    private static BLSPublicKey vk1;

    private static BLSPrivateKey sk2;
    private static BLSPublicKey vk2;

    private static BLSPrivateKey sk3;
    private static BLSPublicKey vk3;
    private static TransactionBlock transactionBlock;
    private static SerializationUtil<TransactionBlock> encode, encode2;
    private static SerializationUtil<Transaction> serenc;
    private static SerializationUtil<SerializableErasureObject> serenc_erasure;

    private static Eventloop eventloop = CachedEventLoop.getInstance().getEventloop();

    private static RpcServer serverOne, serverTwo, serverThree;

    private static Thread thread;
    private static InetSocketAddress address1, address2, address3;
    private static RpcErasureServer<SerializableErasureObject> server;

    @BeforeAll
    public static void setup() throws IOException, MnemonicException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, CloneNotSupportedException, DecoderException {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("google.com", 80));
        String IP = socket.getLocalAddress().getHostAddress();
        if (!IP.substring(0, 3).equals("192")) {
            return;
        }
        server = new RpcErasureServer<SerializableErasureObject>(new SerializableErasureObject(), IP, 7082, eventloop, 0);
        new Thread(server).start();
        CommitteeBlock committeeBlock = new CommitteeBlock();
        committeeBlock.setGeneration(1);
        committeeBlock.setViewID(1);
        CachedLatestBlocks.getInstance().setCommitteeBlock(committeeBlock);


        sk1 = new BLSPrivateKey(1);
        vk1 = new BLSPublicKey(sk1);

        sk2 = new BLSPrivateKey(2);
        vk2 = new BLSPublicKey(sk2);

        sk3 = new BLSPrivateKey(3);
        vk3 = new BLSPublicKey(sk3);


        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk1, "192.168.1.106");
        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk2, "192.168.1.116");
        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk3, "192.168.1.115");

        CachedZoneIndex.getInstance().setZoneIndex(0);
        ECDSASign ecdsaSign = new ECDSASign();

        List<SerializationUtil.Mapping> list = new ArrayList<>();
        list.add(new SerializationUtil.Mapping(ECP.class, ctx -> new ECPmapper()));
        list.add(new SerializationUtil.Mapping(ECP2.class, ctx -> new ECP2mapper()));
        list.add(new SerializationUtil.Mapping(BigDecimal.class, ctx -> new BigDecimalSerializer()));
        list.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        list.add(new SerializationUtil.Mapping(TreeMap.class, ctx -> new CustomSerializerTreeMap()));
        encode = new SerializationUtil<TransactionBlock>(TransactionBlock.class, list);
        encode2 = new SerializationUtil<TransactionBlock>(TransactionBlock.class, list);
        serenc = new SerializationUtil<Transaction>(Transaction.class, list);
        serenc_erasure = new SerializationUtil<SerializableErasureObject>(SerializableErasureObject.class, list);
        valueMapper = new SerializationUtil<Signature>(Signature.class, list);

        ArrayList<String> addreses = new ArrayList<>();
        ArrayList<Transaction> transactions = new ArrayList<>();
        ArrayList<ECKeyPair> keypair = new ArrayList<>();
        int version = 0x00;
        for (int i = 0; i < TRANSACTION_SIZE; i++) {
            Mnemonic mnem = new Mnemonic(Security.NORMAL, WordList.ENGLISH);
            char[] mnemonic_sequence = mnem.create();
            char[] passphrase = "p4ssphr4se".toCharArray();
            byte[] key = mnem.createSeed(mnemonic_sequence, passphrase);
            ECKeyPair ecKeyPair = Keys.create256r1KeyPair(new SecureRandom(key));
            String adddress = WalletAddress.generate_address((byte) version, ecKeyPair.getPublicKey());
            addreses.add(adddress);
            keypair.add(ecKeyPair);
        }


        for (int i = 0; i < TRANSACTION_SIZE - 1; i++) {
            Transaction transaction = new RegularTransaction();
            transaction.setFrom(addreses.get(i));
            transaction.setTo(addreses.get(i + 1));
            transaction.setStatus(StatusType.PENDING);
            transaction.setTimestamp(GetTime.GetTimeStampInString());
            transaction.setZoneFrom(1);
            transaction.setZoneTo(2);
            transaction.setAmount(BigDecimal.valueOf(100));
            transaction.setAmountWithTransactionFee(transaction.getAmount().multiply(BigDecimal.valueOf(10.0 / 100.0)));
            transaction.setNonce(1);
            transaction.setXAxis(keypair.get(i).getXpubAxis());
            transaction.setYAxis(keypair.get(i).getYpubAxis());
            byte byf[] = serenc.encode(transaction);
            transaction.setHash(HashUtil.sha256_bytetoString(byf));

            ECDSASignatureData signatureData = ecdsaSign.signSecp256r1Message(transaction.getHash().getBytes(StandardCharsets.UTF_8), keypair.get(i));
            transaction.setSignature(signatureData);
            transactions.add(transaction);
        }
        transactionBlock = new TransactionBlock();
        transactionBlock.getHeaderData().setPreviousHash("4c89512018237bd0cd458b50ffbf47190fc7aa7d4430093418fa7927a9a46ac3");
        transactionBlock.getHeaderData().setVersion(AdrestusConfiguration.version);
        transactionBlock.getHeaderData().setTimestamp(GetTime.GetTimeStampInString());
        transactionBlock.setStatustype(StatusType.PENDING);
        transactionBlock.setHeight(1);
        transactionBlock.setGeneration(1);
        transactionBlock.setViewID(1);
        transactionBlock.setZone(CachedZoneIndex.getInstance().getZoneIndex());
        transactionBlock.setLeaderPublicKey(vk1);
        transactionBlock.setBlockProposer(vk1.toRaw());
        transactionBlock.setTransactionList(transactions);
        transactionBlock.setHash("hash10");
        transactionBlock.setSize(1);
        transactionBlock.setPatriciaMerkleRoot("1d51602355c8255d11baf4915c500a92e9d027f478dfa2286ee509a7469c08ab");
        transactionBlock.setHash("1d51602355c8255d11baf4915c500a92e9d027f478dfa2286ee509a7469c08ab");

        Receipt.ReceiptBlock receiptBlock = new Receipt.ReceiptBlock(transactionBlock.getHeight(), transactionBlock.getGeneration(), transactionBlock.getMerkleRoot());
        ArrayList<Receipt> receiptList = new ArrayList<>();
        for (int i = 0; i < transactionBlock.getTransactionList().size(); i++) {
            Transaction transaction = transactionBlock.getTransactionList().get(i);
            receiptList.add(new Receipt(transaction.getZoneFrom(), transaction.getZoneTo(), receiptBlock, null, i));
        }

        Map<Integer, Map<Receipt.ReceiptBlock, List<Receipt>>> outbound = receiptList
                .stream()
                .collect(Collectors.groupingBy(Receipt::getZoneTo, Collectors.groupingBy(Receipt::getReceiptBlock)));

        OutBoundRelay outBoundRelay = new OutBoundRelay(outbound);
        transactionBlock.setOutbound(outBoundRelay);


        address1 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8080);
        address2 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8081);
        address3 = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 8084);
        Eventloop eventloop = Eventloop.builder()
                .withCurrentThread()
                .build();
        serverOne = RpcServer.builder(eventloop)
                .withMessageTypes(RPCExampleTest.HelloRequest.class, RPCExampleTest.HelloResponse.class)
                .withHandler(RPCExampleTest.HelloRequest.class,
                        helloServiceRequestHandler(new HelloServiceImplOne()))
                .withListenAddress(address1).build();
        serverOne.listen();


        serverTwo = RpcServer.builder(eventloop)
                .withMessageTypes(RPCExampleTest.HelloRequest.class, RPCExampleTest.HelloResponse.class)
                .withHandler(RPCExampleTest.HelloRequest.class,
                        helloServiceRequestHandler(new HelloServiceImplTwo()))
                .withListenAddress(address2).build();

        serverTwo.listen();

        serverThree = RpcServer.builder(eventloop)
                .withMessageTypes(RPCExampleTest.HelloRequest.class, RPCExampleTest.HelloResponse.class)
                .withHandler(RPCExampleTest.HelloRequest.class,
                        helloServiceRequestHandler(new HelloServiceImplThree()))
                .withListenAddress(address3).build();

        serverThree.listen();
        thread = new Thread(eventloop);
        thread.start();


        ArrayList<InetSocketAddress> lists = new ArrayList<>();
        lists.add(address1);
        lists.add(address1);
        lists.add(address1);

        RpcClient client = RpcClient.builder(eventloop)
                .withMessageTypes(RPCExampleTest.HelloRequest.class, RPCExampleTest.HelloResponse.class)
                .withStrategy(RpcStrategies.roundRobin(servers(lists))).build();

        try {
            client.startFuture().get(5, TimeUnit.SECONDS);

            String currentName;
            String currentResponse;

            currentName = "John";
            currentResponse = blockingRequest(client, currentName);
            System.out.println("Request with name \"" + currentName + "\": " + currentResponse);
            assertEquals("Hello, " + currentName + "!", currentResponse);

            currentName = "Winston";
            currentResponse = blockingRequest(client, currentName);
            System.out.println("Request with name \"" + currentName + "\": " + currentResponse);
            assertEquals("Hello Hello, " + currentName + "!", currentResponse);

            currentName = "Sophia"; // name starts with "s", so hash code is different from previous examples
            currentResponse = blockingRequest(client, currentName);
            System.out.println("Request with name \"" + currentName + "\": " + currentResponse);
            assertEquals("Hello Hello Hello, " + currentName + "!", currentResponse);

            HashUtil.XXH3("Hello Hello Hello, " + currentName + "!");
            BlockSizeCalculator blockSizeCalculator = new BlockSizeCalculator();
            blockSizeCalculator.setTransactionBlock(transactionBlock);
            byte[] buffer = encode.encode(transactionBlock, blockSizeCalculator.TransactionBlockSizeCalculator());
            byte[] buffer2 = serenc_erasure.encode(new SerializableErasureObject(new FECParameterObject(), buffer, new ArrayList<byte[]>()));
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    // This work for old configuration of the project and it is not working for the new configuration

    @Test
    public void test() throws IOException, DecoderException, InterruptedException, ParseException {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("google.com", 80));
        String IP = socket.getLocalAddress().getHostAddress();
        if (!IP.substring(0, 3).equals("192")) {
            return;
        }
        if (IP.equals(CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).get(vk1))) {
            ConsensusServer.getInstance(IP);
            ArrayList<String> proofs = new ArrayList<>();
            ArrayList<String> existed = new ArrayList<>();
            int count = 1;
            while (count < CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().size() - 1) {
                String rec = new String(ConsensusServer.getInstance(IP).receiveErasureData(), StandardCharsets.UTF_8);
                if (!existed.contains(rec)) {
                    System.out.println(rec);
                    existed.add(rec);
                    count++;
                    proofs.add(rec);
                }
            }
            ArrayList<String> identities = new ArrayList<>();
            ArrayList<byte[]> toSend = getChunks(4);
            int pos = 0;
            for (int j = 0; j < proofs.size(); j++) {
                StringJoiner joiner2 = new StringJoiner(delimeter);
                String[] splits = StringUtils.split(proofs.get(j), delimeter);
                BLSPublicKey blsPublicKey = BLSPublicKey.fromByte(Hex.decodeHex(splits[0]));
                Timestamp timestamp = GetTime.GetTimestampFromString(splits[1]);
                boolean val = GetTime.CheckIfTimestampIsUnderOneMinute(timestamp);
                Signature bls_sig2 = valueMapper.decode(Hex.decodeHex(splits[2]));
                String strsgn = joiner2.add(Hex.encodeHexString(blsPublicKey.toBytes())).add(splits[1]).toString();
                Boolean signcheck = BLSSignature.verify(bls_sig2, strsgn.getBytes(StandardCharsets.UTF_8), blsPublicKey);
                if (signcheck && val) {
                    identities.add(strsgn);
                    ConsensusServer.getInstance(IP).setErasureMessage(toSend.get(pos), strsgn);
                    pos++;
                }
            }
            Thread.sleep(8000);
        } else {
            for (Map.Entry<BLSPublicKey, String> entry : CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).entrySet()) {
                if (IP.equals(entry.getValue())) {
                    if (vk2.equals(entry.getKey())) {
                        CachedBLSKeyPair.getInstance().setPrivateKey(sk2);
                        CachedBLSKeyPair.getInstance().setPublicKey(vk2);
                        break;
                    } else if (vk3.equals(entry.getKey())) {
                        CachedBLSKeyPair.getInstance().setPrivateKey(sk3);
                        CachedBLSKeyPair.getInstance().setPublicKey(vk3);
                        break;
                    }
                }
            }
            List<String> list = new ArrayList<>();
            StringJoiner joiner = new StringJoiner(delimeter);
            String timeStampInString = GetTime.GetTimeStampInString();
            String pubkey = Hex.encodeHexString(CachedBLSKeyPair.getInstance().getPublicKey().toBytes());

            String toSign = joiner.add(pubkey).add(timeStampInString).toString();
            Signature bls_sig = BLSSignature.sign(toSign.getBytes(StandardCharsets.UTF_8), CachedBLSKeyPair.getInstance().getPrivateKey());
            String sig = Hex.encodeHexString(valueMapper.encode(bls_sig));

            list.add(pubkey);
            list.add(timeStampInString);
            list.add(sig);

            String toSend = String.join(delimeter, list);
            String rootIP = CachedLatestBlocks.getInstance()
                    .getCommitteeBlock()
                    .getStructureMap()
                    .get(0).values().stream().findFirst().get();
            ConsensusClient consensusClient = new ConsensusClient(rootIP, toSign);

            byte[] rec_buff = consensusClient.SendRetrieveErasureData(toSend.getBytes(StandardCharsets.UTF_8));
            SerializableErasureObject rootObj = serenc_erasure.decode(rec_buff);
            CachedSerializableErasureObject.getInstance().setSerializableErasureObject(rootObj);
            server.setSerializable_length(rec_buff.length);
            List<String> ips = CachedLatestBlocks.getInstance()
                    .getCommitteeBlock()
                    .getStructureMap()
                    .get(0).values()
                    .stream()
                    .filter(val -> !val.equals(IP) && !val.equals("192.168.1.106"))
                    .collect(Collectors.toList());
            ArrayList<InetSocketAddress> list_ip = new ArrayList<>();
            for (String ip : ips) {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(ip), 7082);
                list_ip.add(address);
            }
            RpcErasureClient<SerializableErasureObject> client = new RpcErasureClient<SerializableErasureObject>(new SerializableErasureObject(), list_ip, 7082, eventloop);
            client.connect();
            ArrayList<SerializableErasureObject> recserializableErasureObjects = (ArrayList<SerializableErasureObject>) client.getErasureChunks(new byte[0]);

            for (SerializableErasureObject obj : recserializableErasureObjects) {
                if (!obj.CheckChunksValidity(rootObj.getRootMerkleHash()))
                    throw new IllegalArgumentException("Merklee Hash is not valid");
            }
            recserializableErasureObjects.add(rootObj);

            Collections.shuffle(recserializableErasureObjects);
            FECParameterObject recobject = recserializableErasureObjects.get(0).getFecParameterObject();
            FECParameters recfecParams = FECParameters.newParameters(recobject.getDataLen(), recobject.getSymbolSize(), recobject.getNumberOfSymbols());
            final ArrayDataDecoder dec = OpenRQ.newDecoder(recfecParams, recobject.getSymbolOverhead());

            for (int i = 0; i < recserializableErasureObjects.size(); i++) {
                EncodingPacket encodingPacket = dec.parsePacket(ByteBuffer.wrap(recserializableErasureObjects.get(i).getOriginalPacketChunks()), false).value();
                final SourceBlockDecoder sbDec = dec.sourceBlock(encodingPacket.sourceBlockNumber());
                sbDec.putEncodingPacket(encodingPacket);
            }

            TransactionBlock copys = encode.decode(dec.dataArray());
            assertNotNull(copys);
            int g = 3;
            System.out.println("Done");
        }
    }


    private static ArrayList<byte[]> getChunks(int size) {
        BlockSizeCalculator sizeCalculator = new BlockSizeCalculator();
        sizeCalculator.setTransactionBlock(transactionBlock);
        byte[] buffer = encode.encode(transactionBlock, sizeCalculator.TransactionBlockSizeCalculator());

        long dataLen = buffer.length;
        int sizeOfCommittee = size;
        double loss = .6;
        int numSrcBlks = sizeOfCommittee;
        int symbSize = (int) (dataLen / sizeOfCommittee);
        FECParameterObject object = FECParametersPreConditions.CalculateFECParameters(dataLen, symbSize, numSrcBlks);
        FECParameters fecParams = FECParameters.newParameters(object.getDataLen(), object.getSymbolSize(), object.getNumberOfSymbols());

        byte[] data = new byte[fecParams.dataLengthAsInt()];
        System.arraycopy(buffer, 0, data, 0, data.length);
        final ArrayDataEncoder enc = OpenRQ.newEncoder(data, fecParams);
        ArrayList<SerializableErasureObject> serializableErasureObjects = new ArrayList<SerializableErasureObject>();
        ArrayList<EncodingPacket> n = new ArrayList<EncodingPacket>();
        for (SourceBlockEncoder sbEnc : enc.sourceBlockIterable()) {
            for (EncodingPacket srcPacket : sbEnc.sourcePacketsIterable()) {
                n.add(srcPacket);
            }
        }
        MerkleTreeOptimizedImp tree = new MerkleTreeOptimizedImp();
        ArrayList<MerkleNode> merkleNodes = new ArrayList<MerkleNode>();
        for (int i = 0; i < n.size(); i++) {
            SerializableErasureObject serializableErasureObject = new SerializableErasureObject(object, n.get(i).asArray(), new ArrayList<byte[]>());
            serializableErasureObjects.add(serializableErasureObject);
            merkleNodes.add(new MerkleNode(HashUtil.XXH3(serializableErasureObject.getOriginalPacketChunks())));
        }
        tree.constructTree(merkleNodes);
        String original_hash = tree.getRootHash();
        for (int j = 0; j < serializableErasureObjects.size(); j++) {
            tree.build_proofs(new MerkleNode(HashUtil.XXH3(serializableErasureObjects.get(j).getOriginalPacketChunks())));
            serializableErasureObjects.get(j).setProofs(tree.getMerkleeproofs());
            serializableErasureObjects.get(j).setRootMerkleHash(tree.getRootHash());
        }
        ArrayList<byte[]> toSend = new ArrayList<>();
        for (SerializableErasureObject obj : serializableErasureObjects) {
            toSend.add(serenc_erasure.encode(obj));
        }
        return toSend;
    }

    private interface HelloService {
        String hello(String name) throws Exception;
    }

    private static class HelloServiceImplTwo implements HelloService {
        @Override
        public String hello(String name) throws Exception {
            if (name.equals("--")) {
                throw new Exception("Illegal name");
            }
            return "Hello Hello, " + name + "!";
        }
    }

    private static class HelloServiceImplOne implements HelloService {
        @Override
        public String hello(String name) throws Exception {
            if (name.equals("--")) {
                throw new Exception("Illegal name");
            }
            return "Hello, " + name + "!";
        }
    }


    private static class HelloServiceImplThree implements HelloService {
        @Override
        public String hello(String name) throws Exception {
            if (name.equals("--")) {
                throw new Exception("Illegal name");
            }
            return "Hello Hello Hello, " + name + "!";
        }
    }

    private static RpcRequestHandler<RPCExampleTest.HelloRequest, RPCExampleTest.HelloResponse> helloServiceRequestHandler(HelloService helloService) {
        return request -> {
            String result;
            try {
                result = helloService.hello(request.name);
            } catch (Exception e) {
                return Promise.ofException(e);
            }
            return Promise.of(new RPCExampleTest.HelloResponse(result));
        };
    }

    protected static class HelloRequest {
        @Serialize
        public final String name;

        public HelloRequest(@Deserialize("name") String name) {
            this.name = name;
        }
    }

    protected static class HelloResponse {
        @Serialize
        public final String message;

        public HelloResponse(@Deserialize("message") String message) {
            this.message = message;
        }
    }

    private static String blockingRequest(RpcClient rpcClient, String name) throws Exception {
        try {
            return rpcClient.getReactor().submit(
                            () -> rpcClient
                                    .<RPCExampleTest.HelloRequest, RPCExampleTest.HelloResponse>sendRequest(new RPCExampleTest.HelloRequest(name), TIMEOUT))
                    .get(5, TimeUnit.SECONDS)
                    .message;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return name;
    }
}
