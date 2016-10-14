package invtweaks.integration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import invtweaks.InvTweaksGuiTooltipButton;
import mezz.jei.api.BlankModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

@JEIPlugin
public class JeiPlugin extends BlankModPlugin {
	@Override
	public void register(@Nonnull IModRegistry registry) {
		registry.addAdvancedGuiHandlers(new IAdvancedGuiHandler<GuiContainer>() {
			@Nonnull
			@Override
			public Class<GuiContainer> getGuiContainerClass() {
				return GuiContainer.class;
			}

			@Nullable
			@Override
			public List<Rectangle> getGuiExtraAreas(GuiContainer guiContainer) {
				List<Rectangle> areas = new ArrayList<>();

				for (GuiButton button : guiContainer.buttonList) {
					if (button instanceof InvTweaksGuiTooltipButton) {
						Rectangle rectangle = new Rectangle(button.xPosition, button.yPosition, button.width, button.height);
						areas.add(rectangle);
					}
				}

				return areas;
			}
		});
	}
}
