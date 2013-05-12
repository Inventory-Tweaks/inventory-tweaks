package invtweaks.forge;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;
import invtweaks.InvTweaksConst;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.network.INetworkManager;
import net.minecraft.network.packet.Packet250CustomPayload;

import java.util.ArrayList;

public class PacketHandler implements IPacketHandler {
    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player) {
        switch(packet.data[0]) {
            case InvTweaksConst.PACKET_LOGIN:
                onLoginPacket(manager, packet.data, player);
                break;
            case InvTweaksConst.PACKET_CLICK:
                onClickPacket(manager, packet.data, player);
                break;
            case InvTweaksConst.PACKET_SORTCOMPLETE:
                onSortCompletePacket(manager, packet.data, player);
                break;
        }
    }

    private void onLoginPacket(INetworkManager manager, byte[] data, Player player) {
        if(data.length == 2 && data[1] == InvTweaksConst.PROTOCOL_VERSION) {
            InvTweaksMod.proxy.setServerHasInvTweaks(true);
        }
    }

    @SuppressWarnings("unchecked")
    private void onClickPacket(INetworkManager manager, byte[] data, Player player) {
        EntityPlayerMP realPlayer = (EntityPlayerMP) player;
        ByteArrayDataInput packetData = ByteStreams.newDataInput(data);
        packetData.skipBytes(1);
        int slot = packetData.readInt();
        int clickData = packetData.readInt();
        int action = packetData.readInt();

        realPlayer.openContainer.slotClick(slot, clickData, action, realPlayer);
    }

    private void onSortCompletePacket(INetworkManager manager, byte[] data, Player player) {
        EntityPlayerMP realPlayer = (EntityPlayerMP) player;
        ArrayList arraylist = new ArrayList();

        for(int i = 0; i < realPlayer.openContainer.inventorySlots.size(); ++i) {
            arraylist.add(((Slot) realPlayer.openContainer.inventorySlots.get(i)).getStack());
        }

        realPlayer.sendContainerAndContentsToPlayer(realPlayer.openContainer, arraylist);
    }
}
