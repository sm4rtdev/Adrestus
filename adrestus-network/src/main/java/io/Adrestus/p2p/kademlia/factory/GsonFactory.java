package io.Adrestus.p2p.kademlia.factory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.Adrestus.p2p.kademlia.common.NettyBigIntegerExternalNode;
import io.Adrestus.p2p.kademlia.model.FindNodeAnswer;
import io.Adrestus.p2p.kademlia.node.Node;
import io.Adrestus.p2p.kademlia.node.external.ExternalNode;
import io.Adrestus.p2p.kademlia.protocol.message.*;
import io.Adrestus.p2p.kademlia.repository.KademliaData;
import io.Adrestus.p2p.kademlia.repository.KademliaDataDeserializer;
import io.Adrestus.p2p.kademlia.serialization.*;

import java.io.Serializable;

public interface GsonFactory {
    Gson gson();

    class DefaultGsonFactory<K extends Serializable, V extends Serializable> implements GsonFactory {

        public GsonBuilder gsonBuilder() {
            GsonBuilder gsonBuilder = new GsonBuilder();
            return gsonBuilder
                    .enableComplexMapKeySerialization()
                    .serializeNulls()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .registerTypeAdapter(KademliaMessage.class, new KademliaMessageDeserializer<K, V>())
                    .registerTypeAdapter(DHTLookupKademliaMessage.DHTLookup.class, new DHTLookUpDeserializer<K>())
                    .registerTypeAdapter(DHTLookupResultKademliaMessage.DHTLookupResult.class, new DHTLookUpResultDeserializer<K, V>())
                    .registerTypeAdapter(DHTStoreKademliaMessage.DHTData.class, new DHTStoreDeserializer<K, V>())
                    .registerTypeAdapter(DHTStoreResultKademliaMessage.DHTStoreResult.class, new DHTStoreResultDeserializer<K>())
                    .registerTypeAdapter(ExternalNode.class, new ExternalNodeDeserializer())
                    .registerTypeAdapter(FindNodeAnswer.class, new FindNodeAnswerDeserializer())
                    .registerTypeAdapter(NettyBigIntegerExternalNode.class, new NodeDeserializer())
                    .registerTypeAdapter(Node.class, new NodeSerializer())
                    .registerTypeAdapter(KademliaData.class, new KademliaDataDeserializer());
        }

        @Override
        public Gson gson() {
            return gsonBuilder().create();
        }
    }

}
