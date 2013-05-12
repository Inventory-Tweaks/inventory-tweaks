package invtweaks;

import invtweaks.api.ContainerSection;
import net.minecraft.client.Minecraft;

/**
 * Chest sorting button
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiSortingButton extends InvTweaksGuiIconButton {

    private final ContainerSection section = ContainerSection.CHEST;

    private int algorithm;
    private int rowSize;

    public InvTweaksGuiSortingButton(InvTweaksConfigManager cfgManager,
                                     int id, int x, int y, int w, int h,
                                     String displayString, String tooltip,
                                     int algorithm, int rowSize, boolean useCustomTexture) {
        super(cfgManager, id, x, y, w, h, displayString, tooltip, useCustomTexture);
        this.algorithm = algorithm;
        this.rowSize = rowSize;
    }

    public void drawButton(Minecraft minecraft, int i, int j) {
        super.drawButton(minecraft, i, j);

        // Display symbol
        int textColor = getTextColor(i, j);
        if(displayString.equals("h")) {
            drawRect(xPosition + 3, yPosition + 3, xPosition + width - 3, yPosition + 4, textColor);
            drawRect(xPosition + 3, yPosition + 6, xPosition + width - 3, yPosition + 7, textColor);
        } else if(displayString.equals("v")) {
            drawRect(xPosition + 3, yPosition + 3, xPosition + 4, yPosition + height - 3, textColor);
            drawRect(xPosition + 6, yPosition + 3, xPosition + 7, yPosition + height - 3, textColor);
        } else {
            drawRect(xPosition + 3, yPosition + 3, xPosition + width - 3, yPosition + 4, textColor);
            drawRect(xPosition + 5, yPosition + 4, xPosition + 6, yPosition + 5, textColor);
            drawRect(xPosition + 4, yPosition + 5, xPosition + 5, yPosition + 6, textColor);
            drawRect(xPosition + 3, yPosition + 6, xPosition + width - 3, yPosition + 7, textColor);
        }
    }

    /**
     * Sort container
     */
    public boolean mousePressed(Minecraft minecraft, int i, int j) {
        if(super.mousePressed(minecraft, i, j)) {
            try {
                new InvTweaksHandlerSorting(
                        minecraft, cfgManager.getConfig(),
                        section, algorithm, rowSize).sort();
            } catch(Exception e) {
                InvTweaks.logInGameErrorStatic("invtweaks.sort.chest.error", e);
                e.printStackTrace();
            }
            return true;
        } else {
            return false;
        }

    }

}
