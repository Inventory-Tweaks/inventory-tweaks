package invtweaks;

import invtweaks.api.IItemTreeItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Representation of an item in the item tree.
 *
 * @author Jimeo Wan
 */
public class InvTweaksItemTreeItem implements IItemTreeItem {

    private String name;
    @Nullable
    private String id;
    private int damage;
    private NBTTagCompound extraData;
    private int order;
    private String path;

    /**
     * @param name_   The item name
     * @param id_     The item ID
     * @param damage_ The item variant or InvTweaksConst.DAMAGE_WILDCARD
     * @param order_  The item order while sorting
     */
    public InvTweaksItemTreeItem(String name_, String id_, int damage_, NBTTagCompound extraData_, int order_, String path_) {
        name = name_;
        id = InvTweaksObfuscation.getNamespacedID(id_);
        damage = damage_;
        extraData = extraData_;
        order = order_;
        path = path_;
    }

    @Override
    public String getName() {
        return name;
    }

    @Nullable
    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getDamage() {
        return damage;
    }

    @Override
    public NBTTagCompound getExtraData() {
        return extraData;
    }

    @Override
    public int getOrder() {
        return order;
    }
    
    @Override
    public String getPath() {
        return path;
    }

    /**
     * Warning: the item equality is not reflective. They are equal if "o" matches the item constraints (the opposite
     * can be false).
     */
    public boolean equals(@Nullable Object o) {
        if(o == null || !(o instanceof IItemTreeItem)) {
            return false;
        }
        @Nullable IItemTreeItem item = (IItemTreeItem) o;
        return Objects.equals(id, item.getId())
                && NBTUtil.areNBTEquals(extraData, item.getExtraData(), true)
                && (damage == InvTweaksConst.DAMAGE_WILDCARD || damage == item.getDamage());
    }

    public String toString() {
        return name;
    }

    @Override
    public int compareTo(@NotNull IItemTreeItem item) {
        return item.getOrder() - order;
    }

}
