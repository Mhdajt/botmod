package com.botmod;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;

/** Network-less connection. Packets are dropped. Annotations intentionally omitted on overrides so
 *  a signature change in a new MC version doesn't break compilation (the no-op just won't apply). */
public class BotConnection extends Connection {
    public final EmbeddedChannel embedded;

    public BotConnection() {
        super(PacketFlow.SERVERBOUND);
        // Connection is a Netty handler; channelActive() assigns its internal channel.
        this.embedded = new EmbeddedChannel(this);
    }

    public void send(Packet<?> packet) { }
    public boolean isConnected() { return true; }

    /** Safety valve in case some send() path isn't overridden: don't let outbound queue grow. */
    public void drain() {
        embedded.outboundMessages().clear();
        embedded.inboundMessages().clear();
    }
}
