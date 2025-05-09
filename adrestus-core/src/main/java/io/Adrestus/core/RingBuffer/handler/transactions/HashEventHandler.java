package io.Adrestus.core.RingBuffer.handler.transactions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.Adrestus.core.RingBuffer.event.TransactionEvent;
import io.Adrestus.core.StatusType;
import io.Adrestus.core.Transaction;
import io.Adrestus.core.UnclaimedFeeRewardTransaction;
import io.Adrestus.crypto.HashUtil;
import io.Adrestus.crypto.elliptic.ECDSASignatureData;
import io.Adrestus.crypto.elliptic.mapper.BigDecimalSerializer;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.util.SerializationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HashEventHandler extends TransactionEventHandler {
    private static Logger LOG = LoggerFactory.getLogger(HashEventHandler.class);
    private SerializationUtil<Transaction> wrapper;
    private ObjectMapper mapper;

    public HashEventHandler() {
        List<SerializationUtil.Mapping> list = new ArrayList<>();
        list.add(new SerializationUtil.Mapping(BigDecimal.class, ctx -> new BigDecimalSerializer()));
        list.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        wrapper = new SerializationUtil<Transaction>(Transaction.class, list);
        mapper = new ObjectMapper();
    }

    @Override
    public void onEvent(TransactionEvent transactionEvent, long l, boolean b) throws Exception {
        try {
            //wrapper = new SerializationUtil<Transaction>(Transaction.class);
            Transaction transaction = transactionEvent.getTransaction();

            if (transaction.getStatus().equals(StatusType.BUFFERED) || transaction.getStatus().equals(StatusType.ABORT))
                return;

            if (transaction instanceof UnclaimedFeeRewardTransaction)
                return;

            Transaction cloneable = (Transaction) transaction.clone();

            if (transaction.getHash().length() != 64) {
                Optional.of("Transaction hashes length is not valid").ifPresent(val -> {
                    LOG.info(val);
                    transaction.infos(val);
                });
                transaction.setStatus(StatusType.ABORT);
            }

            cloneable.setHash("");
            cloneable.setSignature(new ECDSASignatureData());

            byte[] toHash = wrapper.encode(cloneable, 1024);
            String result_hash = HashUtil.sha256_bytetoString(toHash);

            if (!result_hash.equals(transaction.getHash())) {
                Optional.of("Transaction hashes does not match").ifPresent(val -> {
                    LOG.info(val);
                    transaction.infos(val);
                });
                transaction.setStatus(StatusType.ABORT);
            }
            //DONT DELETE THIS FIND A WAY TO FIX IT WHEN JS WALLET IS READY
//            if (transaction.getXAxis().toString().equals("0") && transaction.getYAxis().toString().equals("0")) {
//
//                byte[] toHash = wrapper.encode(cloneable, 1024);
//                String result_hash = HashUtil.sha256_bytetoString(toHash);
//
//                if (!result_hash.equals(transaction.getHash())) {
//                    Optional.of("Transaction hashes does not match").ifPresent(val -> {
//                        LOG.info(val);
//                        transaction.infos(val);
//                    });
//                    transaction.setStatus(StatusType.ABORT);
//                }
//            } else {
//                String jsonDataString = mapper.writeValueAsString(cloneable);
//                String result_hash = HashUtil.sha256(jsonDataString);
//
//                if (!result_hash.equals(transaction.getHash())) {
//                    Optional.of("Transaction hashes does not match").ifPresent(val -> {
//                        LOG.info(val);
//                        transaction.infos(val);
//                    });
//                    transaction.setStatus(StatusType.ABORT);
//                }
//
//            }

        } catch (NullPointerException ex) {
            LOG.info("Transaction is empty");
        }

    }
}
