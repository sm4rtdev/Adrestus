package io.Adrestus.protocol;

import io.Adrestus.config.AdrestusConfiguration;
import io.Adrestus.config.SocketConfigOptions;
import io.Adrestus.core.RegularTransaction;
import io.Adrestus.core.StatusType;
import io.Adrestus.core.Transaction;
import io.Adrestus.crypto.HashUtil;
import io.Adrestus.crypto.WalletAddress;
import io.Adrestus.crypto.elliptic.ECDSASign;
import io.Adrestus.crypto.elliptic.ECDSASignatureData;
import io.Adrestus.crypto.elliptic.ECKeyPair;
import io.Adrestus.crypto.elliptic.Keys;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.crypto.elliptic.mapper.SignatureDataSerializer;
import io.Adrestus.crypto.mnemonic.Mnemonic;
import io.Adrestus.crypto.mnemonic.MnemonicException;
import io.Adrestus.crypto.mnemonic.Security;
import io.Adrestus.crypto.mnemonic.WordList;
import io.Adrestus.network.TCPTransactionConsumer;
import io.Adrestus.network.TransactionChannelHandler;
import io.Adrestus.util.GetTime;
import io.Adrestus.util.SerializationUtil;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.csp.binary.BinaryChannelSupplier;
import io.activej.csp.supplier.ChannelSuppliers;
import io.activej.eventloop.Eventloop;
import io.activej.net.socket.tcp.ITcpSocket;
import io.activej.net.socket.tcp.TcpSocket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static io.activej.promise.Promises.loop;
import static io.activej.reactor.Reactor.getCurrentReactor;

public class TransactionChannelTest {
    private static Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
    private static SerializationUtil<Transaction> encode;
    private static SerializationUtil<Transaction> decode;
    static ITcpSocket socket;
    ;
    static int counter;
    private static ArrayList<Transaction> toSendlist = new ArrayList<>();
    private static ArrayList<String> addreses = new ArrayList<>();
    private static ArrayList<ECKeyPair> keypair = new ArrayList<>();
    private static int version = 0x00;
    private static ECDSASign ecdsaSign = new ECDSASign();

    private static int start = 0;
    private static int end = 10;

    @BeforeAll
    public static void setup() throws InterruptedException, MnemonicException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        TCPTransactionConsumer<byte[]> print = x -> {
            Transaction transaction = decode.decode(x);
            counter++;
            System.out.println("Counter" + counter + "Server Message:" + transaction);
            return "";
        };
        (new Thread() {
            public void run() {
                try {
                    new TransactionChannelHandler<byte[]>("localhost").BindServerAndReceive(print);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
        //Thread.sleep(100);


        for (int i = start; i < end; i++) {
            Mnemonic mnem = new Mnemonic(Security.NORMAL, WordList.ENGLISH);
            char[] mnemonic_sequence = "sample sail jungle learn general promote task puppy own conduct green affair ".toCharArray();
            char[] passphrase = ("p4ssphr4se" + String.valueOf(i)).toCharArray();
            byte[] key = mnem.createSeed(mnemonic_sequence, passphrase);
            SecureRandom randoms = SecureRandom.getInstance(AdrestusConfiguration.ALGORITHM, AdrestusConfiguration.PROVIDER);
            randoms.setSeed(key);
            ECKeyPair ecKeyPair = Keys.create256r1KeyPair(randoms);
            String adddress = WalletAddress.generate_address((byte) version, ecKeyPair.getPublicKey());
            addreses.add(adddress);
            keypair.add(ecKeyPair);
        }
    }

    @Test
    public void NetworkChannelTest() {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        List<SerializationUtil.Mapping> mapp_list = new ArrayList<>();
        mapp_list.add(new SerializationUtil.Mapping(ECDSASignatureData.class, ctx -> new SignatureDataSerializer()));
        mapp_list.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        decode = new SerializationUtil<Transaction>(Transaction.class, mapp_list);
        encode = new SerializationUtil<Transaction>(Transaction.class, mapp_list);

        for (int j = 0; j < end - 1; j++) {
            Transaction transaction = new RegularTransaction();
            transaction.setFrom(addreses.get(j));
            transaction.setTo(addreses.get(j + 1));
            transaction.setStatus(StatusType.PENDING);
            transaction.setTimestamp(GetTime.GetTimeStampInString());
            transaction.setZoneFrom(0);
            transaction.setZoneTo(0);
            transaction.setAmount(BigDecimal.valueOf(j));
            transaction.setAmountWithTransactionFee(transaction.getAmount().multiply(BigDecimal.valueOf(10.0 / 100.0)));
            transaction.setNonce(1);
            byte before_hash[] = encode.encode(transaction);
            transaction.setHash(HashUtil.sha256_bytetoString(before_hash));

            ECDSASignatureData signatureData = ecdsaSign.signSecp256r1Message(transaction.getHash().getBytes(StandardCharsets.UTF_8), keypair.get(j));
            transaction.setSignature(signatureData);

            toSendlist.add(transaction);
        }
        //byte transaction_hash[] = serenc.encodeWithTerminatedBytes(transaction);
        //Transaction copy=serenc.decode(transaction_hash);
        //int a=1;
        eventloop.connect(new InetSocketAddress("localhost", SocketConfigOptions.TRANSACTION_PORT), (socketChannel, e) -> {
            if (e == null) {
                System.out.println("Connected to server, enter some text and send it by pressing 'Enter'.");
                try {
                    socket = TcpSocket.wrapChannel(getCurrentReactor(), socketChannel, null);
                } catch (IOException ioException) {
                    throw new RuntimeException(ioException);
                }
                BinaryChannelSupplier bufsSupplier = BinaryChannelSupplier.of(ChannelSuppliers.ofSocket(socket));
                loop(0,
                        i -> i < toSendlist.size(),
                        i -> loadData(toSendlist.get(i))
                                .then(byff -> socket.write(byff))
                                .then(() -> bufsSupplier.needMoreData())
                                .map($2 -> i + 1))
                        .whenComplete(socket::close)
                        .whenException(ex -> {
                            throw new RuntimeException(ex);
                        });
            } else {
                System.out.printf("Could not connect to server, make sure it is started: %s%n", e);
            }
        });
        System.out.println("send");
        eventloop.run();
    }

    private static @NotNull Promise<ByteBuf> loadData(Transaction transaction) {
        byte transaction_hash[] = encode.encode(transaction, 1024);
        ByteBuf sizeBuf = ByteBufPool.allocate(2); // enough to serialize size 1024
        sizeBuf.writeVarInt(transaction_hash.length);
        ByteBuf appendedBuf = ByteBufPool.append(sizeBuf, ByteBuf.wrapForReading(transaction_hash));
        return Promise.of(appendedBuf);
    }
}
