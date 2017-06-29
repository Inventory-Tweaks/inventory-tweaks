package invtweaks;

import invtweaks.forge.InvTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.Point;

import java.awt.*;
import java.util.List;

/**
 * The inventory and chest advanced settings menu.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiSettingsAdvanced extends InvTweaksGuiSettingsAbstract {
    private final static int ID_SORT_ON_PICKUP = 1;
    private final static int ID_AUTO_EQUIP_ARMOR = 2;
    private final static int ID_ENABLE_SOUNDS = 3;
    private final static int ID_CHESTS_BUTTONS = 4;
    private final static int ID_SERVER_ASSIST = 5;
    private final static int ID_EDITSHORTCUTS = 100;

    private static String labelChestButtons;
    private static String labelSortOnPickup;
    private static String labelEquipArmor;
    private static String labelEnableSounds;
    private static String labelServerAssist;

    public InvTweaksGuiSettingsAdvanced(Minecraft mc_, GuiScreen parentScreen_, InvTweaksConfig config_) {
        super(mc_, parentScreen_, config_);

        labelSortOnPickup = I18n.format("invtweaks.settings.advanced.sortonpickup");
        labelEquipArmor = I18n.format("invtweaks.settings.advanced.autoequip");
        labelEnableSounds = I18n.format("invtweaks.settings.advanced.sounds");
        labelChestButtons = I18n.format("invtweaks.settings.chestbuttons");
        labelServerAssist = I18n.format("invtweaks.settings.advanced.serverassist");
    }

    @Override
    public void initGui() {
        super.initGui();

        List<GuiButton> controlList = buttonList;
        @NotNull Point p = new Point();
        int i = 0;

        // Create large buttons

        moveToButtonCoords(1, p);
        controlList.add(new GuiButton(ID_EDITSHORTCUTS, p.getX() + 55, height / 6 + 144,
                I18n.format("invtweaks.settings.advanced.mappingsfile")));

        // Create settings buttons

        i += 2;
        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton sortOnPickupBtn = new InvTweaksGuiTooltipButton(ID_SORT_ON_PICKUP, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_SORTING_ON_PICKUP,
                        labelSortOnPickup),
                I18n.format(
                        "invtweaks.settings.advanced.sortonpickup.tooltip"));
        controlList.add(sortOnPickupBtn);

        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton enableSoundsBtn = new InvTweaksGuiTooltipButton(ID_ENABLE_SOUNDS, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_SOUNDS,
                        labelEnableSounds),
                I18n.format(
                        "invtweaks.settings.advanced.sounds.tooltip"));
        controlList.add(enableSoundsBtn);

        moveToButtonCoords(i++, p);
        controlList.add(new InvTweaksGuiTooltipButton(ID_CHESTS_BUTTONS, p.getX(), p.getY(),
                computeBooleanButtonLabel(InvTweaksConfig.PROP_SHOW_CHEST_BUTTONS,
                        labelChestButtons), I18n
                .format(
                        "invtweaks.settings.chestbuttons.tooltip")));

        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton autoEquipArmorBtn = new InvTweaksGuiTooltipButton(ID_AUTO_EQUIP_ARMOR, p.getX(),
                p.getY(), computeBooleanButtonLabel(
                InvTweaksConfig.PROP_ENABLE_AUTO_EQUIP_ARMOR, labelEquipArmor), I18n.format(
                "invtweaks.settings.advanced.autoequip.tooltip"));
        controlList.add(autoEquipArmorBtn);

        //noinspection UnusedAssignment
        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton serverAssistBtn = new InvTweaksGuiTooltipButton(ID_SERVER_ASSIST, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_SERVER_ITEMSWAP,
                        labelServerAssist),
                I18n.format(
                        "invtweaks.settings.advanced.serverassist.tooltip"));
        controlList.add(serverAssistBtn);

        // Check if links to files are supported, if not disable the buttons
        if(!Desktop.isDesktopSupported()) {
            controlList.stream().forEach(button -> {
                if(button.id == ID_EDITSHORTCUTS) {
                    button.enabled = false;
                }
            });
        }

        // Save control list
        buttonList = controlList;

    }

    @Override
    public void drawScreen(int i, int j, float f) {
        super.drawScreen(i, j, f);

        int x = width / 2;
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.settings.pvpwarning.pt1"),
                x, 40, 0x999999);
        drawCenteredString(obf.getFontRenderer(), I18n.format("invtweaks.settings.pvpwarning.pt2"),
                x, 50, 0x999999);
    }

    @Override
    protected void actionPerformed(@NotNull GuiButton guibutton) {

        // GuiButton
        switch(guibutton.id) {

            // Toggle auto-refill sound
            case ID_SORT_ON_PICKUP:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_SORTING_ON_PICKUP, labelSortOnPickup);
                break;

            // Toggle shortcuts
            case ID_AUTO_EQUIP_ARMOR:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_AUTO_EQUIP_ARMOR, labelEquipArmor);
                break;

            // Toggle sounds
            case ID_ENABLE_SOUNDS:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_SOUNDS, labelEnableSounds);
                break;

            // Toggle chest buttons
            case ID_CHESTS_BUTTONS:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_SHOW_CHEST_BUTTONS, labelChestButtons);
                break;

            // Toggle server assistance
            case ID_SERVER_ASSIST:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_SERVER_ITEMSWAP, labelServerAssist);
                InvTweaksMod.proxy.setServerAssistEnabled(!InvTweaks.getConfigManager().getConfig().getProperty(
                        InvTweaksConfig.PROP_ENABLE_SERVER_ITEMSWAP).equals(InvTweaksConfig.VALUE_FALSE));
                break;


            // Open shortcuts mappings in external editor
            case ID_EDITSHORTCUTS:
                try {
                    Desktop.getDesktop().open(InvTweaksConst.CONFIG_PROPS_FILE);
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.advanced.mappingsfile.error", e);
                }
                break;

            // Back to main settings screen
            case ID_DONE:
                obf.displayGuiScreen(new InvTweaksGuiSettings(mc, parentScreen, config));

        }

    }

}
