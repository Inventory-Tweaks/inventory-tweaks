package invtweaks.forge;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import invtweaks.InvTweaks;
import net.minecraft.client.Minecraft;

import java.util.EnumSet;

public class ForgeClientTick implements ITickHandler {
    private InvTweaks instance;

    public ForgeClientTick(InvTweaks inst) {
        instance = inst;
    }

    /**
     * Called at the "start" phase of a tick
     * <p/>
     * Multiple ticks may fire simultaneously- you will only be called once with all the firing ticks
     *
     * @param type
     * @param tickData
     */
    @Override
    public void tickStart(EnumSet<TickType> type, Object... tickData) {
    }

    /**
     * Called at the "end" phase of a tick
     * <p/>
     * Multiple ticks may fire simultaneously- you will only be called once with all the firing ticks
     *
     * @param type
     * @param tickData
     */
    @Override
    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        Minecraft mc = FMLClientHandler.instance().getClient();
        if(mc.theWorld != null) {
            if(mc.currentScreen != null) {
                instance.onTickInGUI(mc.currentScreen);
            } else {
                instance.onTickInGame();
            }
        }
    }

    /**
     * Returns the list of ticks this tick handler is interested in receiving at the minute
     */
    @Override
    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT, TickType.RENDER);
    }

    /**
     * A profiling label for this tick handler
     */
    @Override
    public String getLabel() {
        return "Client Tick";
    }
}
