package net.minestom.event.trait;

import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;

/**
 * Represents any event called about an {@link ItemStack}.
 */
public interface ItemEvent extends Event {
    ItemStack getItemStack();
}
