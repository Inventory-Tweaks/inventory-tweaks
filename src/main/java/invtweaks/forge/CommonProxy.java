package invtweaks.forge;

import invtweaks.InvTweaksConst;
import invtweaks.api.IItemTreeListener;
import invtweaks.api.InvTweaksAPI;
import invtweaks.api.SortingMethod;
import invtweaks.api.container.ContainerSection;
import invtweaks.integration.ItemListSorter;
import invtweaks.network.ITMessageToMessageCodec;
import invtweaks.network.ITPacketHandlerServer;
import invtweaks.network.packets.ITPacketLogin;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLEmbeddedChannel;
import net.minecraftforge.fml.common.network.FMLOutboundHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

public class CommonProxy implements InvTweaksAPI {
    protected static EnumMap<Side, FMLEmbeddedChannel> invtweaksChannel;
    @Nullable
    private static MinecraftServer server;

    public void preInit(FMLPreInitializationEvent e) {
    }

    public void init(FMLInitializationEvent e) {
        invtweaksChannel = NetworkRegistry.INSTANCE
                .newChannel(InvTweaksConst.INVTWEAKS_CHANNEL, new ITMessageToMessageCodec());
        invtweaksChannel.get(Side.SERVER).pipeline().addAfter("ITMessageToMessageCodec#0", "InvTweaks Handler Server", new ITPacketHandlerServer());

        MinecraftForge.EVENT_BUS.register(this);
    }

    public void postInit(FMLPostInitializationEvent e) {
        ItemListSorter.LinkJEITComparator();
    }

    public void serverAboutToStart(@NotNull FMLServerAboutToStartEvent e) {
        server = e.getServer();
    }

    public void serverStopped(FMLServerStoppedEvent e) {
        server = null;
    }

    public void setServerAssistEnabled(boolean enabled) {
    }

    public void setServerHasInvTweaks(boolean hasInvTweaks) {
    }

    /* Action values:
     * 0: Standard Click
     * 1: Shift-Click
     * 2: Move item to/from hotbar slot (Depends on current slot and hotbar slot being full or empty)
     * 3: Duplicate item (only while in creative)
     * 4: Drop item
     * 5: Spread items (Drag behavior)
     * 6: Merge all valid items with held item
     */
    @SideOnly(Side.CLIENT)
    public void slotClick(PlayerControllerMP playerController, int windowId, int slot, int data, ClickType action,
                          EntityPlayer player) {
    }

    public void sortComplete() {

    }

    @Override
    public void addOnLoadListener(IItemTreeListener listener) {

    }

    @Override
    public boolean removeOnLoadListener(IItemTreeListener listener) {
        return false;
    }

    @Override
    public void setSortKeyEnabled(boolean enabled) {
    }

    @Override
    public void setTextboxMode(boolean enabled) {
    }

    @Override
    public int compareItems(@NotNull ItemStack i, @NotNull ItemStack j) {
        return 0;
    }
    
    @Override
    public int compareItems(@NotNull ItemStack i, @NotNull ItemStack j, boolean onlyTreeSort) {
        return 0;
    }

    @Override
    public void sort(ContainerSection section, SortingMethod method) {
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(@NotNull PlayerEvent.PlayerLoggedInEvent e) {
        FMLEmbeddedChannel channel = invtweaksChannel.get(Side.SERVER);

        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(
                FMLOutboundHandler.OutboundTarget.PLAYER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(e.player);

        channel.writeOutbound(new ITPacketLogin());
    }

    @SuppressWarnings("unused")
    public void addServerScheduledTask(@NotNull Runnable task) {
        server.addScheduledTask(task);
    }

    public void addClientScheduledTask(Runnable task) {
    }
}
