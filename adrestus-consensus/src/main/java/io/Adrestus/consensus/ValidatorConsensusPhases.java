package io.Adrestus.consensus;

import com.google.common.reflect.TypeToken;
import io.Adrestus.config.AdrestusConfiguration;
import io.Adrestus.core.AbstractBlock;
import io.Adrestus.core.CommitteeBlock;
import io.Adrestus.core.Resourses.CachedLatestBlocks;
import io.Adrestus.core.Resourses.CachedLeaderIndex;
import io.Adrestus.core.Resourses.CachedSecurityHeaders;
import io.Adrestus.core.RingBuffer.publisher.BlockEventPublisher;
import io.Adrestus.core.StatusType;
import io.Adrestus.core.TransactionBlock;
import io.Adrestus.crypto.bls.BLS381.ECP;
import io.Adrestus.crypto.bls.BLS381.ECP2;
import io.Adrestus.crypto.bls.mapper.ECP2mapper;
import io.Adrestus.crypto.bls.mapper.ECPmapper;
import io.Adrestus.crypto.bls.model.BLSPublicKey;
import io.Adrestus.crypto.bls.model.BLSSignature;
import io.Adrestus.crypto.bls.model.CachedBLSKeyPair;
import io.Adrestus.crypto.bls.model.Signature;
import io.Adrestus.crypto.vdf.VDFMessage;
import io.Adrestus.crypto.vdf.engine.VdfEngine;
import io.Adrestus.crypto.vdf.engine.VdfEnginePietrzak;
import io.Adrestus.crypto.vrf.VRFMessage;
import io.Adrestus.crypto.vrf.engine.VrfEngine2;
import io.Adrestus.network.ConsensusClient;
import io.Adrestus.network.ConsensusServer;
import io.Adrestus.util.ByteUtil;
import io.Adrestus.util.SerializationUtil;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static io.Adrestus.config.ConsensusConfiguration.HEARTBEAT_MESSAGE;

public class ValidatorConsensusPhases {

    public ValidatorConsensusPhases() {
    }

    protected static class VerifyVDF extends ValidatorConsensusPhases implements BFTConsensusPhase<VDFMessage> {
        private static Logger LOG = LoggerFactory.getLogger(VerifyVDF.class);
        private final VdfEngine vdf;
        private final SerializationUtil<VDFMessage> serialize;

        public VerifyVDF() {
            vdf = new VdfEnginePietrzak(AdrestusConfiguration.PIERRZAK_BIT);
            serialize = new SerializationUtil<VDFMessage>(VDFMessage.class);
        }

        @Override
        public void InitialSetup(){

        }
        @Override
        public void AnnouncePhase(ConsensusMessage<VDFMessage> data) {
            if (!data.getMessageType().equals(ConsensusMessageType.ANNOUNCE))
                LOG.info("Organizer not send correct header message expected " + ConsensusMessageType.ANNOUNCE);

            boolean verify = vdf.verify(CachedSecurityHeaders.getInstance().getSecurityHeader().getpRnd(), CachedLatestBlocks.getInstance().getCommitteeBlock().getDifficulty(), data.getData().getVDFSolution());
            if (!verify) {
                LOG.info("Abort consensus phase VDF solution is invalid");
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }
            CachedSecurityHeaders.getInstance().getSecurityHeader().setRnd(data.getData().getVDFSolution());
            data.setStatusType(ConsensusStatusType.SUCCESS);
            Signature sig = BLSSignature.sign(CachedSecurityHeaders.getInstance().getSecurityHeader().getRnd(), CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));
        }

        @Override
        public void PreparePhase(ConsensusMessage<VDFMessage> data) {
            if (!data.getMessageType().equals(ConsensusMessageType.PREPARE))
                LOG.info("Organizer not send correct header message expected " + ConsensusMessageType.PREPARE);

            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            Bytes toVerify = Bytes.wrap(CachedSecurityHeaders.getInstance().getSecurityHeader().getRnd());
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            // data.clear();
            data.setStatusType(ConsensusStatusType.SUCCESS);


            byte[] message = serialize.encode(data.getData());
            Signature sig = BLSSignature.sign(message, CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));
        }

        @Override
        public void CommitPhase(ConsensusMessage<VDFMessage> data) {
            if (!data.getMessageType().equals(ConsensusMessageType.COMMIT)) {
                LOG.info("Organizer not send correct header message expected " + ConsensusMessageType.COMMIT);
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            byte[] message = serialize.encode(data.getData());
            Bytes toVerify = Bytes.wrap(message);
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
            }

            //commit save to db
        }
    }


    protected static class VerifyVRF extends ValidatorConsensusPhases implements VRFConsensusPhase<VRFMessage> {
        private static Logger LOG = LoggerFactory.getLogger(VerifyVRF.class);
        private final VrfEngine2 group;
        private final SerializationUtil<VRFMessage> serialize;

        public VerifyVRF() {
            this.group = new VrfEngine2();
            serialize = new SerializationUtil<VRFMessage>(VRFMessage.class);
        }

        @Override
        public void Initialize(VRFMessage message) throws Exception {
            if (!message.getType().equals(VRFMessage.vrfMessageType.INIT) ||
                    !message.getBlockHash().equals(CachedLatestBlocks.getInstance().getCommitteeBlock().getHash()))
                throw new IllegalArgumentException("Organizer not produce valid vrf request");

            StringBuilder hashToVerify = new StringBuilder();


            hashToVerify.append(CachedLatestBlocks.getInstance().getCommitteeBlock().getHash());
            hashToVerify.append(CachedLatestBlocks.getInstance().getCommitteeBlock().getViewID());

            byte[] ri = group.prove(CachedBLSKeyPair.getInstance().getPrivateKey().toBytes(), hashToVerify.toString().getBytes(StandardCharsets.UTF_8));
            byte[] pi = group.proofToHash(ri);


            VRFMessage.VRFData data = new VRFMessage.VRFData(CachedBLSKeyPair.getInstance().getPublicKey().toBytes(), ri, pi);
            message.setData(data);
        }

        @Override
        public void AggregateVRF(VRFMessage message) throws Exception {

        }

        @Override
        public void InitialSetup(){

        }

        @Override
        public void AnnouncePhase(ConsensusMessage<VRFMessage> data) throws Exception {

            if (!data.getMessageType().equals(ConsensusMessageType.ANNOUNCE))
                throw new IllegalArgumentException("Organizer not send correct header message expected " + ConsensusMessageType.ANNOUNCE);


            List<VRFMessage.VRFData> list = data.getData().getSigners();

            if (list.isEmpty())
                throw new IllegalArgumentException("Validators not produce valid vrf inputs and list is empty");

            StringBuilder hashToVerify = new StringBuilder();


            hashToVerify.append(CachedLatestBlocks.getInstance().getCommitteeBlock().getHash());
            hashToVerify.append(CachedLatestBlocks.getInstance().getCommitteeBlock().getViewID());


            for (int i = 0; i < list.size(); i++) {

                byte[] prove = group.verify(list.get(i).getBls_pubkey(), list.get(i).getRi(), hashToVerify.toString().getBytes(StandardCharsets.UTF_8));
                boolean retval = Arrays.equals(prove, list.get(i).getPi());

                if (!retval) {
                    LOG.info("VRF computation is not valid for this validator");
                    list.remove(i);
                }
            }


            byte[] res = list.get(0).getRi();
            for (int i = 0; i < list.size(); i++) {
                if (i == list.size() - 1) {
                    boolean retval = Arrays.equals(data.getData().getPrnd(), res);
                    if (!retval) {
                        throw new IllegalArgumentException("pRnd is not the same leader failure change view protocol");
                    }
                    CachedSecurityHeaders.getInstance().getSecurityHeader().setpRnd(data.getData().getPrnd());
                    break;
                }
                res = ByteUtil.xor(res, list.get(i + 1).getRi());
            }
            Signature sig = BLSSignature.sign(CachedSecurityHeaders.getInstance().getSecurityHeader().getpRnd(), CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));
        }

        @Override
        public void PreparePhase(ConsensusMessage<VRFMessage> data) {
            if (!data.getMessageType().equals(ConsensusMessageType.PREPARE)) {
                LOG.info("Organizer not send correct header message expected " + ConsensusMessageType.PREPARE);
                //  data.clear();
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }
            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            Bytes toVerify = Bytes.wrap(data.getData().getPrnd());
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            // data.clear();
            data.setStatusType(ConsensusStatusType.SUCCESS);


            byte[] message = serialize.encode(data.getData());
            Signature sig = BLSSignature.sign(message, CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));
        }

        @Override
        public void CommitPhase(ConsensusMessage<VRFMessage> data) {
            if (!data.getMessageType().equals(ConsensusMessageType.COMMIT)) {
                LOG.info("Organizer not send correct header message expected " + ConsensusMessageType.COMMIT);
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            byte[] message = serialize.encode(data.getData());
            Bytes toVerify = Bytes.wrap(message);
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
            }

            //commit save to db
        }


    }


    protected static class VerifyTransactionBlock extends ValidatorConsensusPhases implements BFTConsensusPhase<TransactionBlock> {
        private static Logger LOG = LoggerFactory.getLogger(VerifyTransactionBlock.class);
        private static final Type fluentType = new TypeToken<ConsensusMessage<TransactionBlock>>() {
        }.getType();
        private final SerializationUtil<AbstractBlock> block_serialize;
        private final SerializationUtil<ConsensusMessage> consensus_serialize;
        private final boolean DEBUG;

        private ConsensusClient consensusClient;
        private BLSPublicKey leader_bls;
        private int current;

        public VerifyTransactionBlock(boolean DEBUG) {
            this.DEBUG = DEBUG;
            List<SerializationUtil.Mapping> list = new ArrayList<>();
            list.add(new SerializationUtil.Mapping(ECP.class, ctx -> new ECPmapper()));
            list.add(new SerializationUtil.Mapping(ECP2.class, ctx -> new ECP2mapper()));
            this.block_serialize = new SerializationUtil<AbstractBlock>(AbstractBlock.class, list);
            this.consensus_serialize = new SerializationUtil<ConsensusMessage>(fluentType, list);
        }

        @Override
        public void InitialSetup(){
            if (!DEBUG) {
                this.current = CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyIndex(1, CachedLatestBlocks.getInstance().getTransactionBlock().getLeaderPublicKey());
                if (current == CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).size() - 1) {
                    this.leader_bls = CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyByIndex(1, 0);
                    this.consensusClient = new ConsensusClient(CachedLatestBlocks.getInstance().getCommitteeBlock().getValue(1, this.leader_bls));
                    this.consensusClient.receive_handler();
                } else {
                    this.leader_bls = CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyByIndex(1, current + 1);
                    this.consensusClient = new ConsensusClient(CachedLatestBlocks.getInstance().getCommitteeBlock().getValue(1, this.leader_bls));
                    this.consensusClient.receive_handler();
                }

            }
        }

        @Override
        public void AnnouncePhase(ConsensusMessage<TransactionBlock> data) throws InterruptedException {

            if (!DEBUG) {
                consensusClient.send_heartbeat(HEARTBEAT_MESSAGE);
                String heartbeat = consensusClient.rec_heartbeat();
                if (heartbeat == null) {
                    LOG.info("AnnouncePhase: heartbeat message is null");
                    data.setStatusType(ConsensusStatusType.ABORT);
                    return;
                }
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("AnnouncePhase: Leader is not active fail to send message");
                    data.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {
                    try {
                        data = consensus_serialize.decode(receive);
                        if (!data.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                            LOG.info("AnnouncePhase: This is not the valid leader for this round");
                            data.setStatusType(ConsensusStatusType.ABORT);
                            return;
                        } else {
                            byte[] message = block_serialize.encode(data.getData());
                            boolean verify = BLSSignature.verify(data.getChecksumData().getSignature(), message, data.getChecksumData().getBlsPublicKey());
                            if (!verify)
                                throw new IllegalArgumentException("AnnouncePhase: Abort consensus phase BLS leader signature is invalid during announce phase");
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.info("AnnouncePhase: Problem at message deserialization Abort");
                        data.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    }
                }
            }
            if (!data.getMessageType().equals(ConsensusMessageType.ANNOUNCE))
                LOG.info("AnnouncePhase: Organizer not send correct header message expected " + ConsensusMessageType.ANNOUNCE);

            BlockEventPublisher publisher = new BlockEventPublisher(1024);


            publisher
                    .withDuplicateHandler()
                    .withGenerationHandler()
                    .withHashHandler()
                    .withHeaderEventHandler()
                    .withHeightEventHandler()
                    .withTimestampEventHandler()
                    .withTransactionMerkleeEventHandler()
                    .mergeEvents();


            publisher.start();
            publisher.publish(data.getData());
            publisher.getJobSyncUntilRemainingCapacityZero();

            if (data.getData().getStatustype().equals(StatusType.ABORT)) {
                LOG.info("AnnouncePhase: Block is not valid marked as ABORT");
                return;
            }
            data.setStatusType(ConsensusStatusType.SUCCESS);
            Signature sig = BLSSignature.sign(block_serialize.encode(data.getData()), CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));

            if (DEBUG)
                return;

            byte[] toSend = consensus_serialize.encode(data);
            consensusClient.pushMessage(toSend);
            int i = 1;
        }

        @Override
        public void PreparePhase(ConsensusMessage<TransactionBlock> data) throws InterruptedException {

            if (!DEBUG) {
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("PreparePhase: Leader is not active fail to send message");
                    data.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {
                    try {
                        data = consensus_serialize.decode(receive);
                        if (!data.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                            LOG.info("PreparePhase: This is not the valid leader for this round");
                            data.setStatusType(ConsensusStatusType.ABORT);
                            return;
                        } else {
                            byte[] message = block_serialize.encode(data.getData());
                            boolean verify = BLSSignature.verify(data.getChecksumData().getSignature(), message, data.getChecksumData().getBlsPublicKey());
                            if (!verify)
                                throw new IllegalArgumentException("PreparePhase: Abort consensus phase BLS leader signature is invalid during prepare phase");
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.info("PreparePhase: Problem at message deserialization Abort");
                        data.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    }
                }
            }


            if (!data.getMessageType().equals(ConsensusMessageType.PREPARE)) {
                LOG.info("PreparePhase: Organizer not send correct header message expected " + ConsensusMessageType.PREPARE);
                //  data.clear();
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }
            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            Bytes toVerify = Bytes.wrap(block_serialize.encode(data.getData()));
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("PreparePhase: Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            // data.clear();
            data.setStatusType(ConsensusStatusType.SUCCESS);


            byte[] message = block_serialize.encode(data.getData());
            Signature sig = BLSSignature.sign(message, CachedBLSKeyPair.getInstance().getPrivateKey());
            data.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));

            if (DEBUG)
                return;


            byte[] toSend = consensus_serialize.encode(data);
            consensusClient.pushMessage(toSend);
        }

        @Override
        public void CommitPhase(ConsensusMessage<TransactionBlock> data) throws InterruptedException {

            if (!DEBUG) {
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("CommitPhase: Leader is not active fail to send message");
                    data.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {
                    try {
                        data = consensus_serialize.decode(receive);
                        if (!data.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                            LOG.info("CommitPhase: This is not the valid leader for this round");
                            data.setStatusType(ConsensusStatusType.ABORT);
                            return;
                        } else {
                            byte[] message = block_serialize.encode(data.getData());
                            boolean verify = BLSSignature.verify(data.getChecksumData().getSignature(), message, data.getChecksumData().getBlsPublicKey());
                            if (!verify)
                                throw new IllegalArgumentException("CommitPhase: Abort consensus phase BLS leader signature is invalid during commit phase");
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.info("CommitPhase: Problem at message deserialization Abort");
                        data.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    }
                }
            }

            if (!data.getMessageType().equals(ConsensusMessageType.COMMIT)) {
                LOG.info("CommitPhase: Organizer not send correct header message expected " + ConsensusMessageType.COMMIT);
                data.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            List<BLSPublicKey> publicKeys = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = data.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            byte[] message = block_serialize.encode(data.getData());
            Bytes toVerify = Bytes.wrap(message);
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("CommitPhase: Abort consensus phase BLS multi_signature is invalid in prepare phase");
                data.setStatusType(ConsensusStatusType.ABORT);
            }
            if (DEBUG)
                return;

            if (current == CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).size() - 1)
                data.getData().setLeaderPublicKey(CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyByIndex(1, 0));
            else {
                data.getData().setLeaderPublicKey(CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyByIndex(1, current + 1));
            }
            CachedLatestBlocks.getInstance().setTransactionBlock(data.getData());
            //commit save to db

            consensusClient.send_heartbeat(HEARTBEAT_MESSAGE);
            LOG.info("Block is finalized with Success");
        }
    }

    protected static class VerifyCommitteeBlock extends ValidatorConsensusPhases implements BFTConsensusPhase<CommitteeBlock> {

        private static Logger LOG = LoggerFactory.getLogger(VerifyTransactionBlock.class);
        private static final Type fluentType = new TypeToken<ConsensusMessage<CommitteeBlock>>() {
        }.getType();
        private final SerializationUtil<CommitteeBlock> block_serialize;
        private final SerializationUtil<ConsensusMessage> consensus_serialize;
        private final boolean DEBUG;


        private CountDownLatch latch;
        private int N;
        private int F;
        private int current;
        private ConsensusClient consensusClient;
        private BLSPublicKey leader_bls;
        public VerifyCommitteeBlock(boolean DEBUG) {
            this.DEBUG = DEBUG;
            List<SerializationUtil.Mapping> list = new ArrayList<>();
            list.add(new SerializationUtil.Mapping(ECP.class, ctx -> new ECPmapper()));
            list.add(new SerializationUtil.Mapping(ECP2.class, ctx -> new ECP2mapper()));
            this.block_serialize = new SerializationUtil<CommitteeBlock>(CommitteeBlock.class, list);
            this.consensus_serialize = new SerializationUtil<ConsensusMessage>(fluentType, list);
        }

        @Override
        public void InitialSetup(){
            if (!DEBUG) {
                //this.N = 1;
                this.N = CachedLatestBlocks.getInstance().getCommitteeBlock().getStructureMap().get(1).size() - 1;
                this.F = (this.N - 1) / 3;
                this.latch = new CountDownLatch(N);
                this.current = CachedLeaderIndex.getInstance().getCommitteePositionLeader();
                this.leader_bls = CachedLatestBlocks.getInstance().getCommitteeBlock().getPublicKeyByIndex(0, current);
                this.consensusClient = new ConsensusClient(CachedLatestBlocks.getInstance().getCommitteeBlock().getValue(0, this.leader_bls));
            }
        }

        @SneakyThrows
        @Override
        public void AnnouncePhase(ConsensusMessage<CommitteeBlock> block) {
            if (!DEBUG) {
                consensusClient.send_heartbeat(HEARTBEAT_MESSAGE);
                String heartbeat = consensusClient.rec_heartbeat();
                if (heartbeat == null) {
                    LOG.info("AnnouncePhase: heartbeat message is null");
                    block.setStatusType(ConsensusStatusType.ABORT);
                    return;
                }
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("AnnouncePhase: Leader is not active fail to send message");
                    block.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {

                    block = consensus_serialize.decode(receive);
                    if (!block.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                        LOG.info("AnnouncePhase: This is not the valid leader for this round");
                        block.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    } else {
                        byte[] message = block_serialize.encode(block.getData());
                        boolean verify = BLSSignature.verify(block.getChecksumData().getSignature(), message, block.getChecksumData().getBlsPublicKey());
                        if (!verify) {
                            block.setStatusType(ConsensusStatusType.ABORT);
                            throw new IllegalArgumentException("AnnouncePhase: Abort consensus phase BLS leader signature is invalid during announce phase");
                        }
                    }
                }
            }
            if (!block.getMessageType().equals(ConsensusMessageType.ANNOUNCE))
                LOG.info("AnnouncePhase: Organizer not send correct header message expected " + ConsensusMessageType.ANNOUNCE);

            BlockEventPublisher publisher = new BlockEventPublisher(1024);


            publisher
                    .withHashHandler()
                    .withHeaderEventHandler()
                    .withTimestampEventHandler()
                    .withDuplicateHandler()
                    .withHeightEventHandler()
                    .withSortedStakingEventHandler()
                    .withMinimumStakingEventHandler()
                    .withVerifyDifficultyEventHandler()
                    .withVerifyVDFEventHandler()
                    .withVRFEventHandler()
                    .withRandomnessEventHandler()
                    .mergeEvents();


            publisher.start();
            publisher.publish(block.getData());
            publisher.getJobSyncUntilRemainingCapacityZero();

            if (block.getData().getStatustype().equals(StatusType.ABORT)) {
                LOG.info("AnnouncePhase: Block is not valid marked as ABORT");
                return;
            }
            block.setStatusType(ConsensusStatusType.SUCCESS);
            Signature sig = BLSSignature.sign(block_serialize.encode(block.getData()), CachedBLSKeyPair.getInstance().getPrivateKey());
            block.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));

            if (DEBUG)
                return;

            byte[] toSend = consensus_serialize.encode(block);
            consensusClient.pushMessage(toSend);
        }

        @Override
        public void PreparePhase(ConsensusMessage<CommitteeBlock> block) {

            if (!DEBUG) {
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("PreparePhase: Leader is not active fail to send message");
                    block.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {
                    try {
                        block = consensus_serialize.decode(receive);
                        if (!block.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                            LOG.info("PreparePhase: This is not the valid leader for this round");
                            block.setStatusType(ConsensusStatusType.ABORT);
                            return;
                        } else {
                            byte[] message = block_serialize.encode(block.getData());
                            boolean verify = BLSSignature.verify(block.getChecksumData().getSignature(), message, block.getChecksumData().getBlsPublicKey());
                            if (!verify)
                                throw new IllegalArgumentException("PreparePhase: Abort consensus phase BLS leader signature is invalid during prepare phase");
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.info("PreparePhase: Problem at message deserialization Abort");
                        block.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    }
                }
            }

            if (!block.getMessageType().equals(ConsensusMessageType.PREPARE)) {
                LOG.info("PreparePhase: Organizer not send correct header message expected " + ConsensusMessageType.PREPARE);
                //  data.clear();
                block.setStatusType(ConsensusStatusType.ABORT);
                return;
            }
            List<BLSPublicKey> publicKeys = block.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature =block.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            Bytes toVerify = Bytes.wrap(block_serialize.encode(block.getData()));
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("PreparePhase: Abort consensus phase BLS multi_signature is invalid in prepare phase");
                block.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            // data.clear();
            block.setStatusType(ConsensusStatusType.SUCCESS);


            byte[] message = block_serialize.encode(block.getData());
            Signature sig = BLSSignature.sign(message, CachedBLSKeyPair.getInstance().getPrivateKey());
            block.setChecksumData(new ConsensusMessage.ChecksumData(sig, CachedBLSKeyPair.getInstance().getPublicKey()));

            if (DEBUG)
                return;

            byte[] toSend = consensus_serialize.encode(block);
            consensusClient.pushMessage(toSend);
        }

        @Override
        public void CommitPhase(ConsensusMessage<CommitteeBlock> block) {

            if (!DEBUG) {
                byte[] receive = this.consensusClient.deque_message();
                if (receive == null) {
                    LOG.info("CommitPhase: Leader is not active fail to send message");
                    block.setStatusType(ConsensusStatusType.ABORT);
                    return;
                } else {
                    try {
                        block = consensus_serialize.decode(receive);
                        if (!block.getChecksumData().getBlsPublicKey().toRaw().equals(leader_bls.toRaw())) {
                            LOG.info("CommitPhase: This is not the valid leader for this round");
                            block.setStatusType(ConsensusStatusType.ABORT);
                            return;
                        } else {
                            byte[] message = block_serialize.encode(block.getData());
                            boolean verify = BLSSignature.verify(block.getChecksumData().getSignature(), message, block.getChecksumData().getBlsPublicKey());
                            if (!verify)
                                throw new IllegalArgumentException("CommitPhase: Abort consensus phase BLS leader signature is invalid during commit phase");
                        }
                    } catch (IllegalArgumentException e) {
                        LOG.info("CommitPhase: Problem at message deserialization Abort");
                        block.setStatusType(ConsensusStatusType.ABORT);
                        return;
                    }
                }
            }

            if (!block.getMessageType().equals(ConsensusMessageType.COMMIT)) {
                LOG.info("CommitPhase: Organizer not send correct header message expected " + ConsensusMessageType.COMMIT);
                block.setStatusType(ConsensusStatusType.ABORT);
                return;
            }

            List<BLSPublicKey> publicKeys = block.getSignatures().stream().map(ConsensusMessage.ChecksumData::getBlsPublicKey).collect(Collectors.toList());
            List<Signature> signature = block.getSignatures().stream().map(ConsensusMessage.ChecksumData::getSignature).collect(Collectors.toList());


            Signature aggregatedSignature = BLSSignature.aggregate(signature);
            byte[] message = block_serialize.encode(block.getData());
            Bytes toVerify = Bytes.wrap(message);
            boolean verify = BLSSignature.fastAggregateVerify(publicKeys, toVerify, aggregatedSignature);
            if (!verify) {
                LOG.info("CommitPhase: Abort consensus phase BLS multi_signature is invalid in prepare phase");
                block.setStatusType(ConsensusStatusType.ABORT);
            }
            if (DEBUG)
                return;


            CachedLatestBlocks.getInstance().setCommitteeBlock(block.getData());
            //commit save to db

            consensusClient.send_heartbeat(HEARTBEAT_MESSAGE);
            LOG.info("Block is finalized with Success");
        }
    }
}
