package invtweaks;

import org.lwjgl.input.Keyboard;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


/**
 * @author Jimeo Wan
 */
public class InvTweaksShortcutMapping {

    private static final Logger log = InvTweaks.log;

    private List<Integer> keysToHold = new LinkedList<Integer>();

    public InvTweaksShortcutMapping(int keyCode) {
        keysToHold.add(keyCode);
    }

    public InvTweaksShortcutMapping(int... keyCodes) {
        for(int keyCode : keyCodes) {
            keysToHold.add(keyCode);
        }
    }

    public InvTweaksShortcutMapping(String keyName) {
        this(new String[]{keyName});
    }

    public InvTweaksShortcutMapping(String... keyNames) {
        for(String keyName : keyNames) {
            // - Accept both KEY_### and ###, in case someone
            //   takes the LWJGL Javadoc at face value
            // - Accept LALT & RALT instead of LMENU & RMENU
            keyName = keyName.trim().replace("KEY_", "").replace("ALT", "MENU");
            keysToHold.add(Keyboard.getKeyIndex(keyName));
        }
    }

    public boolean isTriggered(Map<Integer, Boolean> pressedKeys) {
        for(Integer keyToHold : keysToHold) {
            if(keyToHold != Keyboard.KEY_LCONTROL) {
                if(!pressedKeys.get(keyToHold)) {
                    return false;
                }
            }
            // AltGr also activates LCtrl, make sure the real LCtrl has been pressed
            else if(!pressedKeys.get(keyToHold) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
                return false;
            }
        }
        return true;
    }

    public List<Integer> getKeyCodes() {
        return this.keysToHold;
    }
}
