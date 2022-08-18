package io.Adrestus.core.RingBuffer.handler.transactions;

import io.Adrestus.core.RingBuffer.event.TransactionEvent;
import io.Adrestus.core.Transaction;
import io.Adrestus.core.StatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionFeeEventHandler extends TransactionEventHandler {
    private static Logger LOG = LoggerFactory.getLogger(TransactionFeeEventHandler.class);
    private static final double FEES = 10;
    private static final double PERCENT = 100;

    @Override
    public void onEvent(TransactionEvent transactionEvent, long l, boolean b) throws Exception {
        try {
            Transaction transaction = transactionEvent.getTransaction();
            if (transaction.getAmountWithTransactionFee() != (FEES / PERCENT) * transaction.getAmount()) {
                LOG.info("Transaction fee calculator is incorrect");
                transaction.setStatus(StatusType.ABORT);
            }
        } catch (NullPointerException ex) {
            LOG.info("Transaction is empty");
        }
    }
}
