package invtweaks;

public enum InvTweaksConfigSortingRuleType {

    RECTANGLE(1),
    ROW(2),
    COLUMN(3),
    SLOT(4);

    private int lowestPriority;
    private int highestPriority;

    InvTweaksConfigSortingRuleType(int priorityLevel) {
        lowestPriority = priorityLevel * 1000000;
        highestPriority = (priorityLevel + 1) * 1000000 - 1;
    }

    // Used for computing rule priorities
    public int getLowestPriority() {
        return lowestPriority;
    }

    // Used for computing lock levels
    public int getHighestPriority() {
        return highestPriority;
    }
}