package invtweaks.forge;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.IPickupNotifier;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import invtweaks.InvTweaks;
import invtweaks.InvTweaksConfig;
import invtweaks.InvTweaksConst;
import invtweaks.InvTweaksItemTreeLoader;
import invtweaks.api.IItemTreeListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.packet.Packet250CustomPayload;

public class ClientProxy extends CommonProxy implements IPickupNotifier {
    private InvTweaks instance;
    private ForgeClientTick clientTick;
    public boolean serverSupportEnabled = false;
    public boolean serverSupportDetected = false;

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        InvTweaks.log = e.getModLog();
    }

    @Override
    public void init(FMLInitializationEvent e) {
        Minecraft mc = FMLClientHandler.instance().getClient();
        // Instantiate mod core
        instance = new InvTweaks(mc);
        clientTick = new ForgeClientTick(instance);
        TickRegistry.registerTickHandler(clientTick, Side.CLIENT);
        GameRegistry.registerPickupHandler(this);
    }

    @Override
    public void postInit(FMLPostInitializationEvent e) {
        if(!InvTweaks.getConfigManager().getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_FORGE_ITEMTREE)
                     .equals(InvTweaksConfig.VALUE_FALSE)) {
            InvTweaksItemTreeLoader.addOnLoadListener(new ForgeItemTreeListener());
        }
    }

    @Override
    public void notifyPickup(EntityItem item, EntityPlayer player) {
        instance.setItemPickupPending(true);
    }

    @Override
    public void setServerAssistEnabled(boolean enabled) {
        serverSupportEnabled = serverSupportDetected && enabled;
        InvTweaks.log
                 .info("Server has support: " + serverSupportDetected + " support enabled: " + serverSupportEnabled);
    }

    @Override
    public void setServerHasInvTweaks(boolean hasInvTweaks) {
        serverSupportDetected = hasInvTweaks;
        serverSupportEnabled = hasInvTweaks &&
                !InvTweaks.getConfigManager().getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SERVER_ITEMSWAP)
                          .equals(InvTweaksConfig.VALUE_FALSE);
        InvTweaks.log.info("Server has support: " + hasInvTweaks + " support enabled: " + serverSupportEnabled);
    }

    @Override
    public void slotClick(PlayerControllerMP playerController,
                          int windowId, int slot, int data,
                          int action, EntityPlayer player) {
        //int modiferKeys = (shiftHold) ? 1 : 0 /* XXX Placeholder */;
        if(serverSupportEnabled) {
            player.openContainer.slotClick(slot, data, action, player);

            ByteArrayDataOutput packetData = ByteStreams.newDataOutput();
            packetData.writeByte(InvTweaksConst.PACKET_CLICK);
            packetData.writeInt(slot);
            packetData.writeInt(data);
            packetData.writeInt(action);

            Packet250CustomPayload packet = PacketDispatcher.getPacket("InventoryTweaks", packetData.toByteArray());
            PacketDispatcher.sendPacketToServer(packet);
        } else {
            playerController.windowClick(windowId, slot, data, action, player);
        }
    }

    @Override
    public void sortComplete() {
        if(serverSupportEnabled) {
            Packet250CustomPayload pkt =
                    new Packet250CustomPayload("InventoryTweaks", new byte[]{InvTweaksConst.PACKET_SORTCOMPLETE});
            PacketDispatcher.sendPacketToServer(pkt);
        }
    }

    @Override
    public void addOnLoadListener(IItemTreeListener listener) {
        InvTweaksItemTreeLoader.addOnLoadListener(listener);
    }

    @Override
    public boolean removeOnLoadListener(IItemTreeListener listener) {
        return InvTweaksItemTreeLoader.removeOnLoadListener(listener);
    }

    @Override
    public void setSortKeyEnabled(boolean enabled) {
        instance.setSortKeyEnabled(enabled);
    }
}
