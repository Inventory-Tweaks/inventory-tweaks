package invtweaks.network;

import invtweaks.network.packets.ITPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;

public class ITPacketHandlerServer extends SimpleChannelInboundHandler<ITPacket> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ITPacket msg) throws Exception {
        final NetHandlerPlayServer handler = (NetHandlerPlayServer) ctx.channel().attr(NetworkRegistry.NET_HANDLER).get();
        handler.playerEntity.getServer().addScheduledTask(() -> msg.handle(handler));
    }
}
