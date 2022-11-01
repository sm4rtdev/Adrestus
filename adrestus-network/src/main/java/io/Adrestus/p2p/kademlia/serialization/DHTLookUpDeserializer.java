package io.Adrestus.p2p.kademlia.serialization;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import io.Adrestus.p2p.kademlia.common.NettyExternalNode;
import io.Adrestus.p2p.kademlia.common.NettyConnectionInfo;
import io.Adrestus.p2p.kademlia.protocol.message.DHTLookupKademliaMessage;

import java.io.Serializable;
import java.lang.reflect.Type;

public class DHTLookUpDeserializer<K extends Serializable> implements JsonDeserializer<DHTLookupKademliaMessage.DHTLookup<Long, NettyConnectionInfo, K>> {

    @Override
    public DHTLookupKademliaMessage.DHTLookup<Long, NettyConnectionInfo, K> deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        DHTLookupKademliaMessage.DHTLookup<Long, NettyConnectionInfo, K> dhtLookup = new DHTLookupKademliaMessage.DHTLookup<>();
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        JsonObject requesterJsonObject = jsonObject.getAsJsonObject("requester");
        dhtLookup.setRequester(jsonDeserializationContext.deserialize(requesterJsonObject, NettyExternalNode.class));
        dhtLookup.setCurrentTry(jsonObject.get("current_try").getAsInt());
        dhtLookup.setKey(jsonDeserializationContext.deserialize(jsonObject.get("key"), new TypeToken<K>() {
        }.getType()));
        return dhtLookup;
    }
}
