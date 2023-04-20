package io.Adrestus.protocol;

import io.Adrestus.config.SocketConfigOptions;
import io.Adrestus.consensus.CachedConsensusState;
import io.Adrestus.core.Resourses.*;
import io.Adrestus.crypto.bls.BLS381.ECP;
import io.Adrestus.crypto.bls.BLS381.ECP2;
import io.Adrestus.crypto.bls.mapper.ECP2mapper;
import io.Adrestus.crypto.bls.mapper.ECPmapper;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.crypto.elliptic.mapper.CustomSerializerTreeMap;
import io.Adrestus.network.IPFinder;
import io.Adrestus.util.SerializationUtil;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.csp.ChannelSupplier;
import io.activej.csp.binary.BinaryChannelSupplier;
import io.activej.csp.binary.ByteBufsDecoder;
import io.activej.eventloop.Eventloop;
import io.activej.eventloop.net.SocketSettings;
import io.activej.net.SimpleServer;
import io.activej.promise.Promise;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static io.activej.promise.Promises.repeat;

public class BindServerCachedTask extends AdrestusTask {
    private static Logger LOG = LoggerFactory.getLogger(BindServerCachedTask.class);
    private static final ByteBufsDecoder<ByteBuf> DECODER = ByteBufsDecoder.ofVarIntSizePrefixedBytes();
    private final InetSocketAddress ADDRESS;

    private final SocketSettings settings;
    private SimpleServer server;
    private static SerializationUtil<CachedNetworkData> serialize;
    private Eventloop eventloop;

    public BindServerCachedTask() {
        this.ADDRESS = new InetSocketAddress(IPFinder.getLocal_address(), SocketConfigOptions.CACHED_DATA_PORT);
        this.settings = SocketSettings.create().withImplReadTimeout(Duration.ofSeconds(3)).withImplWriteTimeout(Duration.ofSeconds(3));
        List<SerializationUtil.Mapping> list = new ArrayList<>();
        list.add(new SerializationUtil.Mapping(ECP.class, ctx -> new ECPmapper()));
        list.add(new SerializationUtil.Mapping(ECP2.class, ctx -> new ECP2mapper()));
        list.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        list.add(new SerializationUtil.Mapping(TreeMap.class, ctx -> new CustomSerializerTreeMap()));
        this.serialize = new SerializationUtil<CachedNetworkData>(CachedNetworkData.class, list);
    }

    @SneakyThrows
    @Override
    public void execute() {
        eventloop = Eventloop.create().withCurrentThread();
        this.server = SimpleServer.create(
                        socket -> {
                            BinaryChannelSupplier bufsSupplier = BinaryChannelSupplier.of(ChannelSupplier.ofSocket(socket));
                            repeat(() ->
                                    bufsSupplier.decode(DECODER)
                                            .whenResult(x -> System.out.println(x))
                                            .then(() -> loadData())
                                            .then(socket::write)
                                            .map($ -> true))
                                    .whenComplete(socket::close);
                        })
                .withListenAddress(ADDRESS)
                .withSocketSettings(settings)
                .withAcceptOnce();
        server.listen();
        (new Thread() {
            public void run() {
                eventloop.run();
            }
        }).start();
    }

    private static @NotNull Promise<ByteBuf> loadData() {
        final CachedNetworkData cachedNetworkData = new CachedNetworkData(
                CachedConsensusState.getInstance().isValid(),
                CachedEpochGeneration.getInstance().getEpoch_counter(),
                CachedLatestBlocks.getInstance().getCommitteeBlock(),
                CachedLatestBlocks.getInstance().getTransactionBlock(),
                CachedLeaderIndex.getInstance().getCommitteePositionLeader(),
                CachedLeaderIndex.getInstance().getTransactionPositionLeader(),
                CachedSecurityHeaders.getInstance().getSecurityHeader(),
                CachedZoneIndex.getInstance().getZoneIndex());
        byte data_bytes[] = serialize.encode(cachedNetworkData);
        ByteBuf sizeBuf = ByteBufPool.allocate(data_bytes.length); // enough to serialize size 1024
        sizeBuf.writeVarInt(data_bytes.length);
        ByteBuf appendedBuf = ByteBufPool.append(sizeBuf, ByteBuf.wrapForReading(data_bytes));
        sizeBuf.recycle();
        return Promise.of(appendedBuf);
    }

    public void close() {
        this.eventloop.breakEventloop();
        this.server.close();
        this.server = null;
    }
}
