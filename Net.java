package com.multiverse.mod;

import com.multiverse.mod.client.ClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MultiverseMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AbilityPacket.class, AbilityPacket::encode, AbilityPacket::decode, AbilityPacket::handle);
        CHANNEL.registerMessage(id++, EscapePacket.class, EscapePacket::encode, EscapePacket::decode, EscapePacket::handle);
        CHANNEL.registerMessage(id++, GrabPromptPacket.class, GrabPromptPacket::encode, GrabPromptPacket::decode, GrabPromptPacket::handle);
    }

    /** Client -> server: ability key pressed (true) or released (false). Slot 4 = fly toggle. */
    public record AbilityPacket(int slot, boolean pressed) {
        public static void encode(AbilityPacket m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.slot);
            buf.writeBoolean(m.pressed);
        }
        public static AbilityPacket decode(FriendlyByteBuf buf) {
            return new AbilityPacket(buf.readVarInt(), buf.readBoolean());
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p != null) HomelanderPowers.handle(p, slot, pressed);
            });
            c.setPacketHandled(true);
        }
    }

    /** Client -> server: one letter typed while held in a grab. */
    public record EscapePacket(char letter) {
        public static void encode(EscapePacket m, FriendlyByteBuf buf) { buf.writeChar(m.letter); }
        public static EscapePacket decode(FriendlyByteBuf buf) { return new EscapePacket(buf.readChar()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> {
                ServerPlayer p = c.getSender();
                if (p != null) HomelanderPowers.escape(p, letter);
            });
            c.setPacketHandled(true);
        }
    }

    /** Server -> client: letter sequence to type while grabbed. Empty string clears it. */
    public record GrabPromptPacket(String letters) {
        public static void encode(GrabPromptPacket m, FriendlyByteBuf buf) { buf.writeUtf(m.letters); }
        public static GrabPromptPacket decode(FriendlyByteBuf buf) { return new GrabPromptPacket(buf.readUtf()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientState.onGrabPrompt(letters)));
            c.setPacketHandled(true);
        }
    }
}
