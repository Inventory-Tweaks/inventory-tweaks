package invtweaks.forge;

import cpw.mods.fml.common.network.IConnectionHandler;
import cpw.mods.fml.common.network.Player;
import invtweaks.InvTweaksConst;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.NetLoginHandler;
import net.minecraft.network.packet.NetHandler;
import net.minecraft.network.packet.Packet1Login;
import net.minecraft.network.packet.Packet250CustomPayload;
import net.minecraft.server.MinecraftServer;

public class ConnectionHandler implements IConnectionHandler {
    @Override
    public void playerLoggedIn(Player player, NetHandler netHandler, INetworkManager manager) {
        Packet250CustomPayload pkt = new Packet250CustomPayload("InventoryTweaks",
                                                                new byte[]{InvTweaksConst.PACKET_LOGIN,
                                                                           InvTweaksConst.PROTOCOL_VERSION});
        manager.addToSendQueue(pkt);
    }

    @Override
    public void clientLoggedIn(NetHandler clientHandler, INetworkManager manager, Packet1Login login) {
    }

    @Override
    public String connectionReceived(NetLoginHandler netHandler, INetworkManager manager) {
        // Do nothing
        return "";
    }

    @Override
    public void connectionOpened(NetHandler netClientHandler, String server, int port, INetworkManager manager) {
        // Do nothing
    }

    @Override
    public void connectionOpened(NetHandler netClientHandler, MinecraftServer server, INetworkManager manager) {
        // Do nothing
    }

    @Override
    public void connectionClosed(INetworkManager manager) {
        InvTweaksMod.proxy.setServerHasInvTweaks(false);
    }
}
