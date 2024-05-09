package io.Adrestus.core;

import io.Adrestus.IMemoryTreePool;

import java.util.ArrayList;
import java.util.List;

public class UnclaimedFeeRewardTransactionTreePoolEntry implements TransactionTreePoolEntries<UnclaimedFeeRewardTransaction> {
    private ArrayList<UnclaimedFeeRewardTransaction> transactionList;

    @Override
    public void ForgeEntriesBuilder(IMemoryTreePool memoryTreePool) {
        try {
            UnclaimedFeeRewardTransaction unclaimedFeeRewardTransaction = (UnclaimedFeeRewardTransaction) transactionList.get(0);
            memoryTreePool.depositUnclaimedReward(unclaimedFeeRewardTransaction.getRecipientAddress(), unclaimedFeeRewardTransaction.getAmount());
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void InventEntriesBuilder(IMemoryTreePool memoryTreePool,int blockHeight) {
        try {
            UnclaimedFeeRewardTransaction unclaimedFeeRewardTransaction = (UnclaimedFeeRewardTransaction) transactionList.get(0);
            memoryTreePool.depositUnclaimedReward(unclaimedFeeRewardTransaction.getRecipientAddress(), unclaimedFeeRewardTransaction.getAmount());
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void SetArrayList(ArrayList<UnclaimedFeeRewardTransaction> transactionList) {
        this.transactionList = new ArrayList<>(transactionList);
    }

    @Override
    public void Clear() {
        this.transactionList.clear();
        this.transactionList = null;
    }
}
