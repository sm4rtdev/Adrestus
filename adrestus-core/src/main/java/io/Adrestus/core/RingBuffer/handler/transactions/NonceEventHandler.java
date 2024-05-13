package io.Adrestus.core.RingBuffer.handler.transactions;

import io.Adrestus.TreeFactory;
import io.Adrestus.Trie.PatriciaTreeNode;
import io.Adrestus.core.*;
import io.Adrestus.core.Resourses.CachedZoneIndex;
import io.Adrestus.core.RingBuffer.event.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;

public class NonceEventHandler extends TransactionEventHandler implements TransactionUnitVisitor {
    private static Logger LOG = LoggerFactory.getLogger(NonceEventHandler.class);

    @Override
    public void onEvent(TransactionEvent transactionEvent, long l, boolean b) throws Exception {
        Transaction transaction = transactionEvent.getTransaction();
        if (transaction.getStatus().equals(StatusType.BUFFERED) || transaction.getStatus().equals(StatusType.ABORT))
            return;
        transaction.accept(this);

    }

    @Override
    public void visit(RegularTransaction regularTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(regularTransaction.getFrom()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie is empty we add From address");
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(regularTransaction.getFrom(), new PatriciaTreeNode(0, 0));
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(regularTransaction.getFrom()).get();
        } catch (NullPointerException ex) {
            LOG.info("RegularTransaction is empty");
            regularTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != regularTransaction.getNonce()) {
            LOG.info("RegularTransaction nonce is not valid");
            regularTransaction.setStatus(StatusType.ABORT);
        }
    }

    @Override
    public void visit(RewardsTransaction rewardsTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(rewardsTransaction.getRecipientAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie is empty we add RecipientAddress address");
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(rewardsTransaction.getRecipientAddress(), new PatriciaTreeNode(0, 0));
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(rewardsTransaction.getRecipientAddress()).get();
        } catch (NullPointerException ex) {
            LOG.info("RewardsTransaction is empty");
            rewardsTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != rewardsTransaction.getNonce()) {
            LOG.info("RewardsTransaction nonce is not valid");
            rewardsTransaction.setStatus(StatusType.ABORT);
        }
    }

    @Override
    public void visit(StakingTransaction stakingTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(stakingTransaction.getValidatorAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie is empty we add ValidatorAddress");
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(stakingTransaction.getValidatorAddress(), new PatriciaTreeNode(0, 0));
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(stakingTransaction.getValidatorAddress()).get();
        } catch (NullPointerException ex) {
            LOG.info("StakingTransaction is empty");
            stakingTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != stakingTransaction.getNonce()) {
            LOG.info("StakingTransaction nonce is not valid");
            stakingTransaction.setStatus(StatusType.ABORT);
        }
    }

    @Override
    public void visit(DelegateTransaction delegateTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(delegateTransaction.getDelegatorAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie is empty we add DelegatorAddress");
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(delegateTransaction.getDelegatorAddress(), new PatriciaTreeNode(0, 0));
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(delegateTransaction.getDelegatorAddress()).get();
        } catch (NullPointerException ex) {
            LOG.info("DelegateTransaction is empty");
            delegateTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != delegateTransaction.getNonce()) {
            LOG.info("DelegateTransaction nonce is not valid");
            delegateTransaction.setStatus(StatusType.ABORT);
        }
    }

    @Override
    public void visit(UnclaimedFeeRewardTransaction unclaimedFeeRewardTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(unclaimedFeeRewardTransaction.getRecipientAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie is empty we add UnclaimedFeeRewardTransaction");
            TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).store(unclaimedFeeRewardTransaction.getRecipientAddress(), new PatriciaTreeNode(0, 0));
            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(unclaimedFeeRewardTransaction.getRecipientAddress()).get();
        } catch (NullPointerException ex) {
            LOG.info("UnclaimedFeeRewardTransaction is empty");
            unclaimedFeeRewardTransaction.setStatus(StatusType.ABORT);
            return;
        }
    }

    @Override
    public void visit(UnDelegateTransaction unDelegateTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(unDelegateTransaction.getDelegatorAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie UndelegatingTransaction is empty abort");
            unDelegateTransaction.setStatus(StatusType.ABORT);
            return;
        } catch (NullPointerException ex) {
            LOG.info("UndelegatingTransaction is empty");
            unDelegateTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != unDelegateTransaction.getNonce()) {
            LOG.info("DelegateTransaction nonce is not valid");
            unDelegateTransaction.setStatus(StatusType.ABORT);
        }
    }

    @Override
    public void visit(UnstakingTransaction unstakingTransaction) {
        PatriciaTreeNode patriciaTreeNode = null;
        try {

            patriciaTreeNode = TreeFactory.getMemoryTree(CachedZoneIndex.getInstance().getZoneIndex()).getByaddress(unstakingTransaction.getValidatorAddress()).get();

        } catch (NoSuchElementException ex) {
            LOG.info("State trie UnstakingTransaction is empty abort");
            unstakingTransaction.setStatus(StatusType.ABORT);
        } catch (NullPointerException ex) {
            LOG.info("UnstakingTransaction is empty");
            unstakingTransaction.setStatus(StatusType.ABORT);
            return;
        }

        if (patriciaTreeNode.getNonce() + 1 != unstakingTransaction.getNonce()) {
            LOG.info("StakingTransaction nonce is not valid");
            unstakingTransaction.setStatus(StatusType.ABORT);
        }
    }
}
