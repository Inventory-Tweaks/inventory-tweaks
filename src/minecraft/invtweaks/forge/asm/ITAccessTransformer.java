package invtweaks.forge.asm;

import cpw.mods.fml.common.asm.transformers.AccessTransformer;

import java.io.IOException;

public class ITAccessTransformer extends AccessTransformer {
    public ITAccessTransformer() throws IOException {
        super("invtweaks_at.cfg");
    }
}
