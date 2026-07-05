package com.blockforge.item;

/**
 * Player inventory: slots 0-8 are the hotbar, 9-35 the backpack.
 */
public final class Inventory {

    public static final int SIZE = 36;
    public static final int HOTBAR = 9;

    public final ItemStack[] slots = new ItemStack[SIZE];

    /**
     * Adds a stack, merging into existing stacks first (hotbar first),
     * then filling empty slots. Returns the leftover count (0 = all added).
     */
    public int add(ItemStack stack) {
        if (stack == null || stack.count <= 0) return 0;
        // merge pass
        if (stack.item.maxStack > 1 && stack.item.durability == 0) {
            for (int i = 0; i < SIZE && stack.count > 0; i++) {
                ItemStack s = slots[i];
                if (s != null && s.canMergeWith(stack)) {
                    int room = s.item.maxStack - s.count;
                    int take = Math.min(room, stack.count);
                    s.count += take;
                    stack.count -= take;
                }
            }
        }
        // empty slot pass
        for (int i = 0; i < SIZE && stack.count > 0; i++) {
            if (slots[i] == null) {
                int take = Math.min(stack.item.maxStack, stack.count);
                slots[i] = new ItemStack(stack.item, take, stack.damage);
                stack.count -= take;
            }
        }
        return stack.count;
    }

    /** Total count of an item across all slots. */
    public int countOf(Item item) {
        int n = 0;
        for (ItemStack s : slots) {
            if (s != null && s.item == item) n += s.count;
        }
        return n;
    }

    /** Removes up to n of an item; returns how many were actually removed. */
    public int remove(Item item, int n) {
        int removed = 0;
        for (int i = 0; i < SIZE && removed < n; i++) {
            ItemStack s = slots[i];
            if (s != null && s.item == item) {
                int take = Math.min(s.count, n - removed);
                s.count -= take;
                removed += take;
                if (s.count <= 0) slots[i] = null;
            }
        }
        return removed;
    }

    public void clear() {
        java.util.Arrays.fill(slots, null);
    }
}
