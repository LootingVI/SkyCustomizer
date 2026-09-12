package de.flori.skycustomizer.config;

import de.flori.skycustomizer.discord.DiscordPlaceholders;
import de.flori.skycustomizer.features.DiscordRichPresence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

final class DiscordPlaceholderSmoke {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void runtime(Minecraft mc) throws Exception {
        var cfg = SkyCustomizerConfig.get();
        var original = cfg.items;
        boolean enabled = cfg.enabled;
        try {
            cfg.enabled = true;
            cfg.items = new SkyCustomizerConfig.ItemConfig();
            cfg.items.itemTypeNames.put("minecraft:iron_sword", "&#55FFFFTest Blade");
            var main = stack(Items.IRON_SWORD, 1, 250);
            main.setDamageValue(10);
            var values = new LinkedHashMap<String, String>();
            var hand = DiscordRichPresence.class.getDeclaredMethod("hand", Map.class, ItemStack.class, ItemStack.class);
            hand.setAccessible(true);
            hand.invoke(null, values, main, stack(Items.STONE, 32, 0));
            check(values.get("item").equals("Test Blade"), "Live item name includes local rename");
            check(values.get("item_id").equals("minecraft:iron_sword"), "Registry item ID");
            check(values.get("item_durability").equals((main.getMaxDamage() - 10) + "/" + main.getMaxDamage()), "Actual remaining durability");
            check(values.get("offhand_count").equals("32"), "Offhand stack size");
            hand.invoke(null, values, ItemStack.EMPTY, ItemStack.EMPTY);
            check(values.get("item").equals("Empty") && values.get("item_count").equals("0"), "Empty hand is not air or a stale item");
            hand.invoke(null, values, stack(Items.STONE, 1, 0), ItemStack.EMPTY);
            check(values.get("item_durability").equals("N/A"), "No durability on building blocks");
            var snapshot = DiscordRichPresence.class.getDeclaredMethod("variables", Minecraft.class, SkyCustomizerConfig.class, SkyCustomizerConfig.DiscordConfig.class);
            snapshot.setAccessible(true);
            var settings = new SkyCustomizerConfig.DiscordConfig();
            values = new LinkedHashMap<>((Map<String, String>) snapshot.invoke(null, mc, cfg, settings));
            check(DiscordPlaceholders.render("{player}", values, settings).equals(mc.getUser().getName()), "Live username is never the preview name");
            check(!values.containsKey("coords") && !values.containsKey("server"), "Opt-in data is not collected");
            check(!values.containsKey("item") && !values.containsKey("health"), "No invented equipment or health in menus");
        } finally { cfg.items = original; cfg.enabled = enabled; }
    }

    static void insertion(Minecraft mc) throws Exception {
        var screen = (FeatureEditorScreen) mc.screen;
        var field = field(screen, "State");
        field.setValue("Using OLD here");
        field.moveCursorTo(6, false);
        field.moveCursorTo(9, true);
        screen.checkpoint();
        screen.mouseClicked(new MouseButtonEvent(field.getX() + field.getWidth() + 20, field.getY(), new MouseButtonInfo(0, 0)), false);
        check(mc.screen instanceof DiscordPlaceholderScreen, "Text field opens placeholder browser");
        var browser = (DiscordPlaceholderScreen) mc.screen;
        field(browser, "Search placeholders").setValue("{item}");
        var choose = DiscordPlaceholderScreen.class.getDeclaredMethod("choose");
        choose.setAccessible(true); choose.invoke(browser);
        check(mc.screen == screen, "Insertion returns to the same editor draft");
        check(field(screen, "State").getValue().equals("Using {item} here"), "Replace selection at cursor without replacing the full template");
        screen.undo(false);
        check(field(screen, "State").getValue().equals("Using OLD here"), "Placeholder insertion can be undone");
        screen.undo(true);
        check(field(screen, "State").getValue().equals("Using {item} here"), "Placeholder insertion can be redone");
        System.out.println("[SkyCustomizer] DISCORD_PLACEHOLDERS_UI_PASSED: " + checks + " checks");
    }

    static void search(Screen screen, String text) { field(screen, "Search placeholders").setValue(text); }

    private static ItemStack stack(Item item, int count, int durability) {
        var components = DataComponentMap.builder().set(DataComponents.ITEM_NAME, Component.literal("Test item"))
                .set(DataComponents.MAX_STACK_SIZE, durability > 0 ? 1 : 64);
        if (durability > 0) components.set(DataComponents.MAX_DAMAGE, durability).set(DataComponents.DAMAGE, 0);
        return new ItemStack(new Holder.Direct<>(item, components.build()), count);
    }

    private static EditBox field(Screen screen, String name) {
        return screen.children().stream().filter(child -> child instanceof EditBox box && box.getMessage().getString().equals(name))
                .map(child -> (EditBox) child).findFirst().orElseThrow();
    }

    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
}
