package invtweaks;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/**
 * Icon-size button, which get drawns in a specific way to fit its small size.
 *
 * @author Jimeo Wan
 */
public class InvTweaksGuiIconButton extends InvTweaksGuiTooltipButton {

    protected InvTweaksConfigManager cfgManager;

    private boolean useCustomTexture;

    public InvTweaksGuiIconButton(InvTweaksConfigManager cfgManager,
                                  int id, int x, int y, int w, int h,
                                  String displayString, String tooltip, boolean useCustomTexture) {
        super(id, x, y, w, h, displayString, tooltip);
        this.cfgManager = cfgManager;
        this.useCustomTexture = useCustomTexture;
    }

    public void drawButton(Minecraft minecraft, int i, int j) {
        super.drawButton(minecraft, i, j);

        // Draw background (use the 4 corners of the texture to fit best its small size)
        int k = getHoverState(isMouseOverButton(i, j));
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        if(useCustomTexture) {
            minecraft.renderEngine.bindTexture("/gui/button10px.png");
            drawTexturedModalRect(xPosition, yPosition, (k - 1) * 10, 0, width, height);
        } else {
            minecraft.renderEngine.bindTexture("/gui/gui.png");
            drawTexturedModalRect(xPosition, yPosition, 1, 46 + k * 20 + 1, width / 2, height / 2);
            drawTexturedModalRect(xPosition, yPosition + height / 2, 1, 46 + k * 20 + 20 - height / 2 - 1, width / 2,
                                  height / 2);
            drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2 - 1, 46 + k * 20 + 1, width / 2,
                                  height / 2);
            drawTexturedModalRect(xPosition + width / 2, yPosition + height / 2, 200 - width / 2 - 1,
                                  46 + k * 20 + 19 - height / 2, width / 2,
                                  height / 2);
        }

    }

}