package io.Adrestus.core.RewardMechanism;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.Adrestus.Trie.PatriciaTreeTransactionType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.groupingBy;

public class RewardStorageCalculator implements RewardHandler {

    @Override
    public boolean canHandleRequest(Request req) {
        return req.getRequestType() == RequestType.REWARD_STORAGE_CALCULATOR;
    }

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public void handle(Request req) {
        req.markHandled();

        if(CachedRewardMapData.getInstance().getEffective_stakes_map().isEmpty())
            return;

        Multimap<String, DelegateObject> delegate_rewards = ArrayListMultimap.create();
        for (Map.Entry<String, RewardObject> entry : CachedRewardMapData.getInstance().getEffective_stakes_map().entrySet()) {
            req.getMemoryTreePool().deposit(PatriciaTreeTransactionType.UNCLAIMED_FEE_REWARD,entry.getKey(),entry.getValue().getReal_reward(),0);
            for (Map.Entry<String,  DelegateObject> entry2 : entry.getValue().getDelegate_stake().entrySet()) {
                delegate_rewards.put(entry2.getKey(),entry2.getValue());
            }
        }

        for (Map.Entry<String, Collection<DelegateObject>> entry : delegate_rewards.asMap().entrySet()) {
            Double sum = entry.getValue()
                    .stream()
                    .map(DelegateObject::getReward)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            req.getMemoryTreePool().deposit(PatriciaTreeTransactionType.UNCLAIMED_FEE_REWARD,entry.getKey(),sum,0);
        }
        delegate_rewards.clear();
        delegate_rewards=null;
    }

    @Override
    public String name() {
        return "REWARD_STORAGE_CALCULATOR";
    }
}
