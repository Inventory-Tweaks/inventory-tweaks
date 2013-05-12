package invtweaks;

public class ShortcutSpecification {
    public enum Action {
        MOVE,
        DROP
    }

    public enum Target {
        UP,
        DOWN,
        HOTBAR_SLOT,
        UNSPECIFIED
    }

    public enum Scope {
        EVERYTHING,
        ALL_ITEMS,
        ONE_STACK,
        ONE_ITEM
    }

    private Action action;
    private Target target;
    private Scope scope;

    public ShortcutSpecification(Action a, Target t, Scope s) {
        action = a;
        target = t;
        scope = s;
    }

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }
}
