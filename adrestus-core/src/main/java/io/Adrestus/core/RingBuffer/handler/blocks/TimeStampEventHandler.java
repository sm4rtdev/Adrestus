package io.Adrestus.core.RingBuffer.handler.blocks;

import io.Adrestus.core.AbstractBlock;
import io.Adrestus.core.CommitteeBlock;
import io.Adrestus.core.Resourses.CachedLatestBlocks;
import io.Adrestus.core.RingBuffer.event.AbstractBlockEvent;
import io.Adrestus.core.StatusType;
import io.Adrestus.core.TransactionBlock;
import io.Adrestus.util.GetTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.ParseException;


public class TimeStampEventHandler implements BlockEventHandler<AbstractBlockEvent>, DisruptorBlockVisitor {
    private static Logger LOG = LoggerFactory.getLogger(TimeStampEventHandler.class);

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
        try {
            Timestamp current = GetTime.GetTimestampFromString(committeeBlock.getHeaderData().getTimestamp());
            Timestamp cached = GetTime.GetTimestampFromString(CachedLatestBlocks.getInstance().getCommitteeBlock().getHeaderData().getTimestamp());
            if (!cached.before(current)) {
                LOG.info("CommitteeBlock timestamp is not valid");
                committeeBlock.setStatustype(StatusType.ABORT);
                return;
            }
        } catch (Exception e) {
            LOG.info("TransactionBlock timestamp  have not valid form abort");
            committeeBlock.setStatustype(StatusType.ABORT);
            return;
        }
    }

    @Override
    public void visit(TransactionBlock transactionBlock) {
        try {
            Timestamp current = GetTime.GetTimestampFromString(transactionBlock.getHeaderData().getTimestamp());
            Timestamp cached = GetTime.GetTimestampFromString(CachedLatestBlocks.getInstance().getTransactionBlock().getHeaderData().getTimestamp());

            if (!cached.before(current)) {
                LOG.info("TransactionBlock timestamp is not valid");
                transactionBlock.setStatustype(StatusType.ABORT);
                return;
//                if (!cached.before(GetTime.GetTimeStampWithDelay(current))) {
//                    LOG.info("TransactionBlock timestamp is not valid");
//                    transactionBlock.setStatustype(StatusType.ABORT);
//                    return;
//                }
            }
        } catch(Exception e) {
            LOG.info("TransactionBlock timestamp  have not valid form abort");
            transactionBlock.setStatustype(StatusType.ABORT);
            return;
        }
    }
}
