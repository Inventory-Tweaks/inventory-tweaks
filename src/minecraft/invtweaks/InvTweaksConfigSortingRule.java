package invtweaks;

import java.awt.*;
import java.util.logging.Logger;


/**
 * Stores a sorting rule, as a target plus a keyword. The target is provided as an array of preferred slots (ex: target
 * "1", i.e. first column, is stored as [0, 9, 18, 27])
 *
 * @author Jimeo Wan
 */
public class InvTweaksConfigSortingRule implements Comparable<InvTweaksConfigSortingRule> {

    private static final Logger log = InvTweaks.log;

    private String constraint;
    private int[] preferredPositions;
    private String keyword;
    private InvTweaksConfigSortingRuleType type;
    private int priority;
    private int containerSize;
    private int containerRowSize;

    public InvTweaksConfigSortingRule(InvTweaksItemTree tree, String constraint,
                                      String keyword, int containerSize, int containerRowSize) {

        this.keyword = keyword;
        this.constraint = constraint;
        this.containerSize = containerSize;
        this.containerRowSize = containerRowSize;
        this.type = getRuleType(constraint, containerRowSize);
        this.preferredPositions = getRulePreferredPositions(constraint);

        // Compute priority
        // 1st criteria : the rule type
        // 2st criteria : the keyword category depth
        // 3st criteria : the item order in a same category

        priority = type.getLowestPriority() + 100000 +
                tree.getKeywordDepth(keyword) * 1000 - tree.getKeywordOrder(keyword);

    }

    public InvTweaksConfigSortingRuleType getType() {
        return type;
    }

    /**
     * @return An array of preferred positions (from the most to the less preferred).
     */
    public int[] getPreferredSlots() {
        return preferredPositions;
    }

    public String getKeyword() {
        return keyword;
    }

    /**
     * Raw constraint name, for debug purposes
     */
    public String getRawConstraint() {
        return constraint;
    }

    /**
     * @return rule priority (for rule sorting)
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Compares rules priority : positive value means 'this' is of greater priority than o
     */
    public int compareTo(InvTweaksConfigSortingRule o) {
        return getPriority() - o.getPriority();
    }

    public int[] getRulePreferredPositions(String constraint) {
        // TODO Caching
        return InvTweaksConfigSortingRule.getRulePreferredPositions(
                constraint, containerSize, containerRowSize);
    }

    public static int[] getRulePreferredPositions(String constraint,
                                                  int containerSize, int containerRowSize) {

        int[] result = null;
        int containerColumnSize = containerSize / containerRowSize;

        // Rectangle rules
        if(constraint.length() >= 5) {

            boolean vertical = false;
            if(constraint.contains("v")) {
                vertical = true;
                constraint = constraint.replaceAll("v", "");
            }
            String[] elements = constraint.split("-");
            if(elements.length == 2) {

                int[] slots1 = getRulePreferredPositions(elements[0],
                                                         containerSize, containerRowSize);
                int[] slots2 = getRulePreferredPositions(elements[1],
                                                         containerSize, containerRowSize);
                if(slots1.length == 1 && slots2.length == 1) {

                    int slot1 = slots1[0], slot2 = slots2[0];

                    Point point1 = new Point(slot1 % containerRowSize, slot1 / containerRowSize),
                            point2 = new Point(slot2 % containerRowSize, slot2 / containerRowSize);

                    result = new int[(Math.abs(point2.y - point1.y) + 1) *
                            (Math.abs(point2.x - point1.x) + 1)];
                    int resultIndex = 0;

                    // Swap coordinates for vertical ordering
                    if(vertical) {
                        for(Point p : new Point[]{point1, point2}) {
                            int buffer = p.x;
                            p.x = p.y;
                            p.y = buffer;
                        }
                    }

                    int y = point1.y;
                    while((point1.y < point2.y) ? y <= point2.y : y >= point2.y) {
                        int x = point1.x;
                        while((point1.x < point2.x) ? x <= point2.x : x >= point2.x) {
                            result[resultIndex++] = (vertical)
                                                    ? index(containerRowSize, x, y) : index(containerRowSize, y, x);
                            x += (point1.x < point2.x) ? 1 : -1;
                        }
                        y += (point1.y < point2.y) ? 1 : -1;
                    }

                    if(constraint.contains("r")) {
                        reverseArray(result);
                    }

                }
            }
        } else {

            // Default values
            int column = -1, row = -1;
            boolean reverse = false;

            // Extract chars
            for(int i = 0; i < constraint.length(); i++) {
                char c = constraint.charAt(i);
                if(c >= '1' && c - '1' <= containerRowSize) {
                    // 1 column = 0, 9 column = 8
                    column = c - '1';
                } else if(c >= 'a' && c - 'a' <= containerColumnSize) {
                    // A row = 0, D row = 3, H row = 7
                    row = c - 'a';
                } else if(c == 'r') {
                    reverse = true;
                }
            }

            // Tile case
            if(column != -1 && row != -1) {
                result = new int[]{index(containerRowSize, row, column)};
            }
            // Row case
            else if(row != -1) {
                result = new int[containerRowSize];
                for(int i = 0; i < containerRowSize; i++) {
                    result[i] = index(containerRowSize, row,
                                      reverse ? containerRowSize - 1 - i : i);
                }
            }
            // Column case
            else {
                result = new int[containerColumnSize];
                for(int i = 0; i < containerColumnSize; i++) {
                    result[i] = index(containerRowSize,
                                      reverse ? i : containerColumnSize - 1 - i, column);
                }
            }
        }

        return result;
    }

    public static InvTweaksConfigSortingRuleType getRuleType(String constraint, int rowSize) {

        InvTweaksConfigSortingRuleType result = InvTweaksConfigSortingRuleType.SLOT;

        if(constraint.length() == 1 || (constraint.length() == 2 && constraint.contains("r"))) {
            constraint = constraint.replace("r", "");
            // Column rule
            if(constraint.charAt(0) - '1' <= rowSize && constraint.charAt(0) >= '1') {
                result = InvTweaksConfigSortingRuleType.COLUMN;
            }
            // Row rule
            else {
                result = InvTweaksConfigSortingRuleType.ROW;
            }
        }
        // Rectangle rule
        else if(constraint.length() > 4) {
            // Special case: rectangle rule on a single column
            if(constraint.charAt(1) == constraint.charAt(4)) {
                result = InvTweaksConfigSortingRuleType.COLUMN;
            }
            // Special case: rectangle rule on a single row
            else if(constraint.charAt(0) == constraint.charAt(3)) {
                result = InvTweaksConfigSortingRuleType.ROW;
            }
            // Usual case
            else {
                result = InvTweaksConfigSortingRuleType.RECTANGLE;
            }
        }

        return result;

    }

    public String toString() {
        return constraint + " " + keyword;
    }

    private static int index(int rowSize, int row, int column) {
        return row * rowSize + column;
    }

    private static void reverseArray(int[] data) {
        int left = 0;
        int right = data.length - 1;
        while(left < right) {
            int temp = data[left];
            data[left] = data[right];
            data[right] = temp;
            left++;
            right--;
        }
    }


}
