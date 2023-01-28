package io.Adrestus.core.RingBuffer.handler.blocks;

import io.Adrestus.core.*;
import io.Adrestus.core.RingBuffer.event.AbstractBlockEvent;
import io.Adrestus.crypto.elliptic.mapper.StakingData;
import io.Adrestus.p2p.kademlia.repository.KademliaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DuplicateEventHandler implements BlockEventHandler<AbstractBlockEvent>, DisruptorBlockVisitor {
    private static Logger LOG = LoggerFactory.getLogger(DuplicateEventHandler.class);

    @Override
    public void onEvent(AbstractBlockEvent blockEvent, long l, boolean b) throws Exception {
        try {
            AbstractBlock block = blockEvent.getBlock();
            block.accept(this);
        } catch (NullPointerException ex) {
            LOG.info("Block is empty");
        }
    }

    @Override
    public void visit(CommitteeBlock committeeBlock) {
        List<StakingData> duplicates =
                committeeBlock
                        .getStakingMap()
                        .keySet()
                        .stream()
                        .collect(Collectors.groupingBy(Function.identity()))
                        .entrySet()
                        .stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        List<KademliaData> duplicate_address = committeeBlock
                .getStakingMap()
                .values()
                .stream()
                .filter(e -> Collections.frequency(committeeBlock.getStakingMap().values().stream().collect(Collectors.toList()), e) > 1)
                .collect(Collectors.toList());

        if (!duplicates.isEmpty() || !duplicate_address.isEmpty()) {
            LOG.info("Committee Block contains duplicate stakes of users");
            committeeBlock.setStatustype(StatusType.ABORT);
        }
    }

    @Override
    public void visit(TransactionBlock transactionBlock) {
        List<Transaction> duplicates =
                transactionBlock
                        .getTransactionList()
                        .stream()
                        .collect(Collectors.groupingBy(Function.identity()))
                        .entrySet()
                        .stream()
                        .filter(e -> e.getValue().size() > 1)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            LOG.info("Block contains duplicate transactions abort");
            transactionBlock.setStatustype(StatusType.ABORT);
        }
    }
}
