package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.NotNull;

/**
 * A help menu for the NoCheatPlus conflict.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiModNotWorking extends InvTweaksGuiSettingsAbstract {
    public InvTweaksGuiModNotWorking(Minecraft mc_, GuiScreen parentScreen_, InvTweaksConfig config_) {
        super(mc_, parentScreen_, config_);
    }

    @Override
    public void drawScreen(int i, int j, float f) {
        super.drawScreen(i, j, f);

        int x = width / 2;
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.help.bugsorting.pt1"), x,
                80, 0xBBBBBB);
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.help.bugsorting.pt2"), x,
                95, 0xBBBBBB);
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.help.bugsorting.pt3"), x,
                110, 0xBBBBBB);
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.help.bugsorting.pt4"), x,
                150, 0xFFFF99);
    }

    @Override
    protected void actionPerformed(@NotNull GuiButton guibutton) {
        // GuiButton
        switch(guibutton.id) {
            // Back to main settings screen
            case ID_DONE:
                obf.displayGuiScreen(new InvTweaksGuiSettings(mc, parentScreen, config));
        }
    }

}
