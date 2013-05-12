package invtweaks;

/**
 * @author Jimeo Wan
 */
public enum InvTweaksShortcutType {

    MOVE_ALL_ITEMS,
    MOVE_EVERYTHING,
    MOVE_ONE_STACK,
    MOVE_ONE_ITEM,
    MOVE_UP,
    MOVE_DOWN,
    MOVE_TO_SPECIFIC_HOTBAR_SLOT,
    DROP;

    public static InvTweaksShortcutType fromConfigKey(String property) {
        if(InvTweaksConfig.PROP_SHORTCUT_ALL_ITEMS.equals(property)) {
            return MOVE_ALL_ITEMS;
        } else if(InvTweaksConfig.PROP_SHORTCUT_EVERYTHING.equals(property)) {
            return MOVE_EVERYTHING;
        } else if(InvTweaksConfig.PROP_SHORTCUT_DOWN.equals(property)) {
            return MOVE_DOWN;
        } else if(InvTweaksConfig.PROP_SHORTCUT_DROP.equals(property)) {
            return DROP;
        } else if(InvTweaksConfig.PROP_SHORTCUT_ONE_ITEM.equals(property)) {
            return MOVE_ONE_ITEM;
        } else if(InvTweaksConfig.PROP_SHORTCUT_UP.equals(property)) {
            return MOVE_UP;
        } else {
            return null;
        }
    }
}
