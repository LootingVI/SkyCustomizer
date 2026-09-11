package de.flori.skycustomizer.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Map;
import java.util.WeakHashMap;

public final class ItemIdentity {
    public record Identity(String key, String originalName, boolean unique) {}
    private record Cached(CustomData data, Component customName, Component itemName, Identity identity) {}
    private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();
    private ItemIdentity() {}

    public static Component originalName(ItemStack stack) {
        Component custom = stack.getCustomName();
        return custom != null ? custom : stack.getItemName();
    }

    public static synchronized Identity of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        Component name = stack.getCustomName();
        Component itemName = stack.getItemName();
        Cached cached = CACHE.get(stack);
        if (cached != null && cached.data == data && cached.customName == name && cached.itemName == itemName) return cached.identity;
        String uuid = "", skyblockId = "";
        if (data != null && !data.isEmpty()) {
            var tag = data.copyTag();
            var attributes = tag.getCompound("ExtraAttributes").orElse(tag);
            uuid = attributes.getStringOr("uuid", "");
            skyblockId = attributes.getStringOr("id", "");
        }
        String rawName = (name != null ? name : itemName).getString();
        String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        Identity identity = fromParts(uuid, skyblockId, vanillaId, rawName);
        if (CACHE.size() >= 512) CACHE.clear();
        CACHE.put(stack, new Cached(data, name, itemName, identity));
        return identity;
    }

    public static Identity fromParts(String uuid, String skyblockId, String vanillaId, String rawName) {
        if (uuid != null && !uuid.isBlank()) return new Identity("uuid:" + uuid, rawName, true);
        if (skyblockId != null && !skyblockId.isBlank()) return new Identity("skyblock:" + skyblockId, rawName, false);
        return new Identity("item:" + vanillaId + "|" + rawName, rawName, false);
    }
}
