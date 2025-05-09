package io.Adrestus.api;

import io.Adrestus.TreeFactory;
import io.Adrestus.Trie.PatriciaTreeNode;
import io.Adrestus.config.AdrestusConfiguration;
import io.Adrestus.config.TestingConfiguration;
import io.Adrestus.core.*;
import io.Adrestus.core.Resourses.CachedEpochGeneration;
import io.Adrestus.core.Resourses.CachedLatestBlocks;
import io.Adrestus.core.Resourses.CachedLeaderIndex;
import io.Adrestus.core.Resourses.CachedZoneIndex;
import io.Adrestus.crypto.HashUtil;
import io.Adrestus.crypto.WalletAddress;
import io.Adrestus.crypto.bls.model.BLSPrivateKey;
import io.Adrestus.crypto.bls.model.BLSPublicKey;
import io.Adrestus.crypto.elliptic.ECDSASign;
import io.Adrestus.crypto.elliptic.ECDSASignatureData;
import io.Adrestus.crypto.elliptic.ECKeyPair;
import io.Adrestus.crypto.elliptic.Keys;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.crypto.mnemonic.Mnemonic;
import io.Adrestus.crypto.mnemonic.Security;
import io.Adrestus.crypto.mnemonic.WordList;
import io.Adrestus.util.GetTime;
import io.Adrestus.util.SerializationUtil;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class ReplaySameTransactionTest {
    private static final int version = 0x00;
    private static ArrayList<String> addreses = new ArrayList<>();
    private static ArrayList<ECKeyPair> keypair = new ArrayList<>();

    private ECDSASign ecdsaSign = new ECDSASign();

    private static SerializationUtil<Transaction> serenc;

    private static BLSPrivateKey sk1;
    private static BLSPublicKey vk1;

    private static BLSPrivateKey sk2;
    private static BLSPublicKey vk2;

    private static BLSPrivateKey sk3;
    private static BLSPublicKey vk3;

    private static BLSPrivateKey sk4;
    private static BLSPublicKey vk4;


    private static BLSPrivateKey sk5;
    private static BLSPublicKey vk5;

    private static BLSPrivateKey sk6;
    private static BLSPublicKey vk6;

    @BeforeAll
    public static void setup() throws Exception {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        TestingConfiguration.END = 30;

        for (int i = 0; i < 2; i++) {
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
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(adddress, new PatriciaTreeNode(BigDecimal.valueOf(1000), 0));
        }

        List<SerializationUtil.Mapping> lists = new ArrayList<>();
        lists.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        serenc = new SerializationUtil<Transaction>(Transaction.class, lists);


        sk1 = new BLSPrivateKey(1);
        vk1 = new BLSPublicKey(sk1);

        sk2 = new BLSPrivateKey(2);
        vk2 = new BLSPublicKey(sk2);

        sk3 = new BLSPrivateKey(3);
        vk3 = new BLSPublicKey(sk3);

        sk4 = new BLSPrivateKey(4);
        vk4 = new BLSPublicKey(sk4);


        sk5 = new BLSPrivateKey(5);
        vk5 = new BLSPublicKey(sk5);

        sk6 = new BLSPrivateKey(6);
        vk6 = new BLSPublicKey(sk6);


        TransactionBlock prevblock = new TransactionBlock();
        CommitteeBlock committeeBlock = new CommitteeBlock();
        committeeBlock.setGeneration(1);
        committeeBlock.setViewID(1);
        prevblock.setHeight(1);
        prevblock.setHash("hash");
        prevblock.getHeaderData().setTimestamp(GetTime.GetTimeStampInString());
        CachedLatestBlocks.getInstance().setCommitteeBlock(committeeBlock);

        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).put(vk1, "192.168.1.106");
        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).put(vk3, "192.168.1.116");
        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).put(vk4, "192.168.1.110");
        CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).put(vk5, "192.168.1.112");
        //CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk4, "192.168.1.110");
        //CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk5, "192.168.1.112");
        //CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(0).put(vk6, "192.168.1.115");


        try {
            prevblock.setBlockProposer(CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(CachedZoneIndex.getInstance().getZoneIndex()).keySet().stream().findFirst().get().toRaw());
            prevblock.setLeaderPublicKey(CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(CachedZoneIndex.getInstance().getZoneIndex()).keySet().stream().findFirst().get());

            CachedLatestBlocks.getInstance().setTransactionBlock(prevblock);
            CachedEpochGeneration.getInstance().setEpoch_counter(0);
            CachedLeaderIndex.getInstance().setTransactionPositionLeader(0);
        } catch (NoSuchElementException e) {
            System.out.println(e.toString());
        }
    }


    //RUN THIS with consensustransaction Timer 4 support on adrestus protocol
    @Test
    @SneakyThrows
    public void test() {
        if (System.out.getClass().getName().contains("maven")) {
            return;
        }
        Transaction transaction = new RegularTransaction();
        transaction.setFrom(addreses.get(0));
        transaction.setTo(addreses.get(1));
        transaction.setStatus(StatusType.PENDING);
        transaction.setTimestamp(GetTime.GetTimeStampInString());
        transaction.setZoneFrom(1);
        transaction.setZoneTo(1);
        transaction.setAmount(BigDecimal.valueOf(100));
        transaction.setAmountWithTransactionFee(transaction.getAmount().multiply(BigDecimal.valueOf(10.0 / 100.0)));
        transaction.setNonce(1);

        byte byf[] = serenc.encode(transaction, 1024);
        transaction.setHash(HashUtil.sha256_bytetoString(byf));

        ECDSASignatureData signatureData = ecdsaSign.signSecp256r1Message(transaction.getHash().getBytes(StandardCharsets.UTF_8), keypair.get(0));
        transaction.setSignature(signatureData);

        for (int i = 0; i < 6; i++) {
            MessageListener messageListener = new MessageListener();
            Strategy transactionStrategy = new Strategy(new TransactionStrategy(transaction, messageListener));
            transactionStrategy.SendTransactionSync();
            Thread.sleep(2500);
        }
    }
}
