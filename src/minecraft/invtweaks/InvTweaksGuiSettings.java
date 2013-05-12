package invtweaks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.Point;

import java.awt.*;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.logging.Logger;


/**
 * The inventory and chest settings menu.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiSettings extends InvTweaksGuiSettingsAbstract {

    private static final Logger log = InvTweaks.log;

    private final static int ID_MIDDLE_CLICK = 1;
    private final static int ID_BEFORE_BREAK = 2;
    private final static int ID_SHORTCUTS = 3;
    private final static int ID_SHORTCUTS_HELP = 4;
    private final static int ID_AUTO_REFILL = 5;
    private final static int ID_MORE_OPTIONS = 6;
    private final static int ID_SORTING_KEY = 7;
    private final static int ID_BUG_SORTING = 8;
    private final static int ID_EDITRULES = 100;
    private final static int ID_EDITTREE = 101;
    private final static int ID_HELP = 102;

    private static String labelMiddleClick;
    private static String labelShortcuts;
    private static String labelAutoRefill;
    private static String labelAutoRefillBeforeBreak;
    private static String labelMoreOptions;
    private static String labelBugSorting;

    private InvTweaksGuiTooltipButton sortMappingButton;
    private boolean sortMappingEdition = false;


    public InvTweaksGuiSettings(Minecraft mc, GuiScreen parentScreen, InvTweaksConfig config) {
        super(mc, parentScreen, config);

        labelMiddleClick = InvTweaksLocalization.get("invtweaks.settings.middleclick");
        labelShortcuts = InvTweaksLocalization.get("invtweaks.settings.shortcuts");
        labelAutoRefill = InvTweaksLocalization.get("invtweaks.settings.autorefill");
        labelAutoRefillBeforeBreak = InvTweaksLocalization.get("invtweaks.settings.beforebreak");
        labelMoreOptions = InvTweaksLocalization.get("invtweaks.settings.moreoptions");
        labelBugSorting = InvTweaksLocalization.get("invtweaks.help.bugsorting");
    }

    public void initGui() {
        super.initGui();

        List<Object> controlList = obf.getControlList(this);
        Point p = new Point();
        int i = 0;

        // Create large buttons

        moveToButtonCoords(1, p);
        controlList.add(new GuiButton(ID_EDITRULES, p.getX() + 55, obf.getWindowHeight(this) / 6 + 96,
                                      InvTweaksLocalization.get("invtweaks.settings.rulesfile")));
        controlList.add(new GuiButton(ID_EDITTREE, p.getX() + 55, obf.getWindowHeight(this) / 6 + 120,
                                      InvTweaksLocalization.get("invtweaks.settings.treefile")));
        controlList.add(new GuiButton(ID_HELP, p.getX() + 55, obf.getWindowHeight(this) / 6 + 144,
                                      InvTweaksLocalization.get("invtweaks.settings.onlinehelp")));

        // Create settings buttons

        moveToButtonCoords(i++, p);
        controlList.add(new InvTweaksGuiTooltipButton(ID_SHORTCUTS_HELP,
                                                      p.getX() + 130, p.getY(), 20, 20, "?", "Shortcuts help"));
        String shortcuts = config.getProperty(InvTweaksConfig.PROP_ENABLE_SHORTCUTS);
        InvTweaksGuiTooltipButton shortcutsBtn =
                new InvTweaksGuiTooltipButton(ID_SHORTCUTS, p.getX(), p.getY(), 130, 20, computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_SHORTCUTS, labelShortcuts),
                                              InvTweaksLocalization.get("invtweaks.settings.shortcuts.tooltip"));
        controlList.add(shortcutsBtn);
        if(shortcuts.equals(InvTweaksConfig.VALUE_CI_COMPATIBILITY)) {
            // Convenient Inventory compatibility: shortcuts not available
            obf.setEnabled(shortcutsBtn, false);
            shortcutsBtn.setTooltip(shortcutsBtn.getTooltip() + "\n(" +
                                            InvTweaksLocalization.get("invtweaks.settings.disableci.tooltip") + ")");
        }

        moveToButtonCoords(i++, p);
        sortMappingButton = new InvTweaksGuiTooltipButton(ID_SORTING_KEY, p.getX(), p.getY(),
                                                          InvTweaksLocalization.get("invtweaks.settings.key") + " " +
                                                                  config.getProperty(
                                                                          InvTweaksConfig.PROP_KEY_SORT_INVENTORY));
        controlList.add(sortMappingButton);

        moveToButtonCoords(i++, p);
        InvTweaksGuiTooltipButton beforeBreakBtn =
                new InvTweaksGuiTooltipButton(ID_BEFORE_BREAK, p.getX(), p.getY(), computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_AUTO_REFILL_BEFORE_BREAK, labelAutoRefillBeforeBreak),
                                              InvTweaksLocalization.get("invtweaks.settings.beforebreak.tooltip"));
        controlList.add(beforeBreakBtn);

        moveToButtonCoords(i++, p);
        InvTweaksGuiTooltipButton autoRefillBtn =
                new InvTweaksGuiTooltipButton(ID_AUTO_REFILL, p.getX(), p.getY(), computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_AUTO_REFILL, labelAutoRefill),
                                              InvTweaksLocalization.get("invtweaks.settings.autorefill.tooltip"));
        controlList.add(autoRefillBtn);

        moveToButtonCoords(i++, p);
        controlList.add(new InvTweaksGuiTooltipButton(ID_MORE_OPTIONS, p.getX(), p.getY(), labelMoreOptions,
                                                      InvTweaksLocalization
                                                              .get("invtweaks.settings.moreoptions.tooltip")));

        controlList
                .add(new InvTweaksGuiTooltipButton(ID_BUG_SORTING, 5, this.height - 20, 100, 20, labelBugSorting, null,
                                                   false));

        String middleClick = config.getProperty(InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK);
        moveToButtonCoords(i++, p);
        InvTweaksGuiTooltipButton middleClickBtn =
                new InvTweaksGuiTooltipButton(ID_MIDDLE_CLICK, p.getX(), p.getY(), computeBooleanButtonLabel(
                        InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK, labelMiddleClick),
                                              InvTweaksLocalization.get("invtweaks.settings.middleclick.tooltip"));
        controlList.add(middleClickBtn);
        if(middleClick.equals(InvTweaksConfig.VALUE_CI_COMPATIBILITY)) {
            // Convenient Inventory compatibility: middle click not available
            obf.setEnabled(middleClickBtn, false);
            middleClickBtn.setTooltip(middleClickBtn.getTooltip() + "\n(" +
                                              InvTweaksLocalization.get("invtweaks.settings.disableci.tooltip"));
        }

        // Check if links to files are supported, if not disable the buttons
        if(!Desktop.isDesktopSupported()) {
            for(Object o : controlList) {
                if(obf.isGuiButton(o)) {
                    GuiButton guiButton = obf.asGuiButton(o);
                    if(obf.getId(guiButton) >= ID_EDITRULES && obf.getId(guiButton) <= ID_HELP) {
                        obf.setEnabled(guiButton, false);
                    }
                }
            }
        }

        // Save control list
        obf.setControlList(this, controlList);

    }

    protected void actionPerformed(GuiButton guibutton) {
        super.actionPerformed(guibutton);

        switch(obf.getId(guibutton)) {

            // Switch sorting key
            case ID_SORTING_KEY:
                sortMappingButton.displayString = InvTweaksLocalization.get("invtweaks.settings.key") + " > ??? <";
                sortMappingEdition = true;
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
                    Desktop.getDesktop().open(new File(InvTweaksConst.CONFIG_RULES_FILE));
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.rulesfile.error", e);
                }
                break;

            // Open tree configuration in external editor
            case ID_EDITTREE:
                try {
                    Desktop.getDesktop().open(new File(InvTweaksConst.CONFIG_TREE_FILE));
                } catch(Exception e) {
                    InvTweaks.logInGameErrorStatic("invtweaks.settings.treefile.error", e);
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

    protected void keyTyped(char c, int keyCode) {
        if(sortMappingEdition) {
            String keyName = Keyboard.getKeyName(keyCode);
            config.setProperty(InvTweaksConfig.PROP_KEY_SORT_INVENTORY, keyName);
            sortMappingButton.displayString = InvTweaksLocalization.get("invtweaks.settings.key") + " " + keyName;
        }
        super.keyTyped(c, keyCode);
    }

}
