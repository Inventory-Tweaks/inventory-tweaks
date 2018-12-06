package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.util.Point;

import java.awt.*;
import java.net.URL;
import java.util.List;

/**
 * The inventory and chest settings menu.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiSettings extends InvTweaksGuiSettingsAbstract {
    private final static int ID_MIDDLE_CLICK = 1;
    private final static int ID_BEFORE_BREAK = 2;
    private final static int ID_SHORTCUTS = 3;
    private final static int ID_SHORTCUTS_HELP = 4;
    private final static int ID_AUTO_REFILL = 5;
    private final static int ID_MORE_OPTIONS = 6;
    private final static int ID_BUG_SORTING = 7;
    private final static int ID_EDITRULES = 100;
    private final static int ID_EDITTREE = 101;
    private final static int ID_MODDEDTREE = 103;
    private final static int ID_HELP = 102;
    private final static int ID_DISPLAY_TOOLTIP = 104;

    private static String labelMiddleClick;
    private static String labelShortcuts;
    private static String labelAutoRefill;
    private static String labelAutoRefillBeforeBreak;
    private static String labelMoreOptions;
    private static String labelBugSorting;
    private static String labelDisplayTooltip;

    @SuppressWarnings("unused")
    public InvTweaksGuiSettings(GuiScreen parentScreen_) {
        this(Minecraft.getMinecraft(), parentScreen_, InvTweaks.getConfigManager().getConfig());
    }

    public InvTweaksGuiSettings(Minecraft mc_, GuiScreen parentScreen_, InvTweaksConfig config_) {
        super(mc_, parentScreen_, config_);

        labelMiddleClick = I18n.format("invtweaks.settings.middleclick");
        labelShortcuts = I18n.format("invtweaks.settings.shortcuts");
        labelAutoRefill = I18n.format("invtweaks.settings.autorefill");
        labelAutoRefillBeforeBreak = I18n.format("invtweaks.settings.beforebreak");
        labelMoreOptions = I18n.format("invtweaks.settings.moreoptions");
        labelBugSorting = I18n.format("invtweaks.help.bugsorting");
        labelDisplayTooltip = I18n.format("invtweaks.settings.displaytooltip");
    }

    @Override
    public void initGui() {
        super.initGui();

        List<GuiButton> controlList = buttonList;
        @NotNull Point p = new Point();
        int i = 0;

        // Create large buttons

        moveToButtonCoords(1, p);
        controlList.add(new GuiButton(ID_EDITRULES, p.getX() + 55, height / 6 + 96,
                I18n.format("invtweaks.settings.rulesfile")));
        controlList.add(new GuiButton(ID_HELP, p.getX() + 55, height / 6 + 144,
                I18n.format("invtweaks.settings.onlinehelp")));

        moveToButtonCoords(11, p);
        controlList.add(new GuiButton(ID_EDITTREE, p.getX(), p.getY(), 150, 20,
                I18n.format("invtweaks.settings.treefile")));
        moveToButtonCoords(10, p);
        controlList.add(new GuiButton(ID_MODDEDTREE, p.getX(), p.getY(), 150, 20, 
                I18n.format("invtweaks.settings.moddedtreefile")));

        
        // Create settings buttons

        moveToButtonCoords(i++, p);
        controlList.add(new InvTweaksGuiTooltipButton(ID_SHORTCUTS_HELP, p.getX() + 130, p.getY(), 20, 20, "?",
                "Shortcuts help"));
        @NotNull InvTweaksGuiTooltipButton shortcutsBtn = new InvTweaksGuiTooltipButton(ID_SHORTCUTS, p.getX(), p.getY(), 130,
                20, computeBooleanButtonLabel(
                InvTweaksConfig.PROP_ENABLE_SHORTCUTS, labelShortcuts),
                I18n.format(
                        "invtweaks.settings.shortcuts.tooltip"));
        controlList.add(shortcutsBtn);

        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton beforeBreakBtn = new InvTweaksGuiTooltipButton(ID_BEFORE_BREAK, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_AUTO_REFILL_BEFORE_BREAK,
                        labelAutoRefillBeforeBreak),
                I18n.format(
                        "invtweaks.settings.beforebreak.tooltip"));
        controlList.add(beforeBreakBtn);

        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton autoRefillBtn = new InvTweaksGuiTooltipButton(ID_AUTO_REFILL, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_AUTO_REFILL,
                        labelAutoRefill), I18n
                .format(
                        "invtweaks.settings.autorefill.tooltip"));
        controlList.add(autoRefillBtn);

        moveToButtonCoords(i++, p);
        controlList.add(new InvTweaksGuiTooltipButton(ID_MORE_OPTIONS, p.getX(), p.getY(), labelMoreOptions,
                I18n.format(
                        "invtweaks.settings.moreoptions.tooltip")));

        controlList.add(new InvTweaksGuiTooltipButton(ID_BUG_SORTING, 5, this.height - 20, 100, 20,
                labelBugSorting, null, false));

        //noinspection UnusedAssignment
        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton middleClickBtn = new InvTweaksGuiTooltipButton(ID_MIDDLE_CLICK, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK,
                        labelMiddleClick),
                I18n.format(
                        "invtweaks.settings.middleclick.tooltip"));
        controlList.add(middleClickBtn);

        moveToButtonCoords(i++, p);
        @NotNull InvTweaksGuiTooltipButton displayTooltipBtn = new InvTweaksGuiTooltipButton(ID_DISPLAY_TOOLTIP, p.getX(), p.getY(),
                computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_TOOLTIP_PATH,
                        labelDisplayTooltip),
                I18n.format(
                        "invtweaks.settings.displaytooltip.tooltip"));
        controlList.add(displayTooltipBtn);

        // Check if links to files are supported, if not disable the buttons
        if(!Desktop.isDesktopSupported()) {
            controlList.stream().filter(InvTweaksObfuscation::isGuiButton).forEach(o -> {
                if(o.id >= ID_EDITRULES && o.id <= ID_HELP) {
                    o.enabled = false;
                }
            });
        }

        // Save control list
        buttonList = controlList;

    }

    @Override
    protected void actionPerformed(@NotNull GuiButton guibutton) {
        super.actionPerformed(guibutton);

        // GuiButton
        switch(guibutton.id) {
            // Toggle tooltip display.
            case ID_DISPLAY_TOOLTIP:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_TOOLTIP_PATH, labelDisplayTooltip);
                break;
            // Toggle middle click shortcut
            case ID_MIDDLE_CLICK:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK, labelMiddleClick);
                break;

            // Toggle auto-refill
            case ID_AUTO_REFILL:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_AUTO_REFILL, labelAutoRefill);
                break;

            // Toggle auto-refill before tool break
            case ID_BEFORE_BREAK:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_AUTO_REFILL_BEFORE_BREAK,
                        labelAutoRefillBeforeBreak);
                break;

            // Toggle shortcuts
            case ID_SHORTCUTS:
                toggleBooleanButton(guibutton, InvTweaksConfig.PROP_ENABLE_SHORTCUTS, labelShortcuts);
                break;

            // Shortcuts help
            case ID_SHORTCUTS_HELP:
                obf.displayGuiScreen(new InvTweaksGuiShortcutsHelp(mc, this, config));
                break;

            // More options screen
            case ID_MORE_OPTIONS:
                obf.displayGuiScreen(new InvTweaksGuiSettingsAdvanced(mc, parentScreen, config));
                break;

            // Sorting bug help screen
            case ID_BUG_SORTING:
                obf.displayGuiScreen(new InvTweaksGuiModNotWorking(mc, parentScreen, config));
                break;

            // Open rules configuration in external editor
            case ID_EDITRULES:
                try {
                    Desktop.getDesktop().open(InvTweaksConst.CONFIG_RULES_FILE);
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.rulesfile.error", e);
                }
                break;
                
            // Open tree configuration in external editor
            case ID_EDITTREE:
                try {
                    Desktop.getDesktop().open(InvTweaksConst.CONFIG_TREE_FILE);
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.treefile.error", e);
                }
                break;

            //Replace the current tree file with the modded tree file.
            case ID_MODDEDTREE:
                try {                    
                    InvTweaks.getConfigManager().ExtractModdedTreeFile();
                    InvTweaks.getConfigManager().makeSureConfigurationIsLoaded();
                } catch (Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.moddedtreefile.error", e);
                }
                break;
            // Open help in browser
            case ID_HELP:
                try {
                    Desktop.getDesktop().browse(new URL(InvTweaksConst.HELP_URL).toURI());
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.onlinehelp.error", e);
                }
                break;

        }

    }
}
