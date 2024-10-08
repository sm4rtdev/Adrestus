package io.Adrestus.core.RewardMechanism;

import io.Adrestus.TreeFactory;
import io.Adrestus.Trie.PatriciaTreeNode;
import io.Adrestus.config.StakingConfiguration;
import io.Adrestus.util.CustomBigDecimal;
import io.Adrestus.util.bytes.Bytes53;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EffectiveStakeCalculator implements RewardHandler {
    @Override
    public boolean canHandleRequest(Request req) {
        return req.getRequestType() == RequestType.EFFECTIVE_STAKE;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public void handle(Request req) {
        req.markHandled();
        List<String> adresses = TreeFactory.getMemoryTree(0).Keyset(Bytes53.ZERO, Integer.MAX_VALUE).stream().collect(Collectors.toList());
        int stake_counter = 0;
        BigDecimal total_stake;
        List<BigDecimal> values=new ArrayList<>();
        for (String address : adresses) {
            PatriciaTreeNode patriciaTreeNode = TreeFactory.getMemoryTree(0).getByaddress(address).get();
            if (patriciaTreeNode.getStaking_amount().compareTo(BigDecimal.ZERO)>0) {
                stake_counter++;
                values.add(patriciaTreeNode.getStaking_amount());
            }
        }
        total_stake = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        for (String address : adresses) {
            PatriciaTreeNode patriciaTreeNode = TreeFactory.getMemoryTree(0).getByaddress(address).get();
            if (patriciaTreeNode.getStaking_amount().compareTo(BigDecimal.ZERO)>0) {
                double effective_stake = Math.max(Math.min((1 + StakingConfiguration.C) * total_stake / stake_counter, patriciaTreeNode.getStaking_amount()), (1 - StakingConfiguration.C) * total_stake / stake_counter);
                CachedRewardMapData.getInstance().getEffective_stakes_map().put(address, new RewardObject(CustomBigDecimal.valueOf(effective_stake), BigDecimal.ZERO));
            }
        }
    }

    @Override
    public String name() {
        return "EffectiveStakeCommand";
    }
}
