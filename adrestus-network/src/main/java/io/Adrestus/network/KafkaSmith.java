package io.Adrestus.network;

import java.util.ArrayList;

public interface KafkaSmith {

    void manufactureKafkaComponent(KafkaKingdomType kafkaKingdomType);

    <T extends IKafkaComponent> T getKafkaComponent(KafkaKingdomType type);

    void updateLeaderHost(KafkaKingdomType type, String leader_host, String currentIP, int partition, boolean isClose);

    void shutDownGracefully();
}
