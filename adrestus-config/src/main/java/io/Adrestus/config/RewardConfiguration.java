package io.Adrestus.config;

import java.math.RoundingMode;

public class RewardConfiguration {
    public static final int TRANSACTION_REWARD_PER_BLOCK = 7;
    public static final int VDF_REWARD = 20;
    public static final int VRF_REWARD = 15;
    public static final int TRANSACTION_LEADER_BLOCK_REWARD = 10;
    public static final int BLOCK_REWARD_HEIGHT = 3;
    public static final RoundingMode ROUNDING = RoundingMode.DOWN;
    public static final int DECIMAL_PRECISION = 6;
}
