package cardejibka.cheststealer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestStealerClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestStealer");
    private static KeyBinding toggleKeyBinding;
    private static boolean isEnabled = false;
    private long lastClickTime = 0;
    private final long DELAY_MS = 0;
    private int currentSlot = 0;
    private boolean isStealing = false;
    private int lastSyncId = -1;

    @Override
    public void onInitializeClient() {
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cheststealer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "category.cheststealer"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.debug("ChestStealer initialized | Toggle key: R (configurable) | Smart skipping enabled");
    }

    private void onClientTick(MinecraftClient client) {
        while (toggleKeyBinding.wasPressed()) {
            isEnabled = !isEnabled;
            if (client.player != null) {
                Text statusText = Text.translatable("text.cheststealer." + (isEnabled ? "enabled" : "disabled"))
                        .setStyle(Style.EMPTY.withColor(isEnabled ? Formatting.GREEN : Formatting.RED));
                client.player.sendMessage(
                        Text.literal("ChestStealer: ").append(statusText),
                        true
                );
            }
            LOGGER.debug("ChestStealer toggled: {}", isEnabled);
        }

        if (!isEnabled) {
            resetStealing();
            return;
        }

        if (client.player == null || client.player.currentScreenHandler == null) {
            resetStealing();
            return;
        }

        var handler = client.player.currentScreenHandler;
        if (handler.syncId != lastSyncId) {
            resetStealing();
            lastSyncId = handler.syncId;
        }

        int containerSlots;
        if (handler instanceof GenericContainerScreenHandler generic) {
            containerSlots = generic.getRows() * 9;
        } else if (handler instanceof ShulkerBoxScreenHandler) {
            containerSlots = 27;
        } else {
            resetStealing();
            return;
        }

        if (!isStealing) {
            startStealing();
            LOGGER.debug("Started smart auto-steal (slots: {})", containerSlots);
        }

        long now = System.currentTimeMillis();
        if (currentSlot < containerSlots && now - lastClickTime >= DELAY_MS) {
            int nextNonEmptySlot = findNextNonEmptySlot(handler, currentSlot, containerSlots);
            if (nextNonEmptySlot != -1) {
                Slot slot = handler.getSlot(nextNonEmptySlot);
                client.interactionManager.clickSlot(
                        handler.syncId,
                        nextNonEmptySlot,
                        0,
                        SlotActionType.QUICK_MOVE,
                        client.player
                );
                currentSlot = nextNonEmptySlot + 1;
                lastClickTime = now;
                LOGGER.debug("Quick-moved slot {} ({})", nextNonEmptySlot, slot.getStack().getItem().getName().getString());
            } else {
                resetStealing();
                LOGGER.debug("All remaining slots empty — finished stealing");
            }
        }

        if (currentSlot >= containerSlots) {
            resetStealing();
            LOGGER.debug("Finished stealing from chest");
        }
    }

    private int findNextNonEmptySlot(ScreenHandler handler, int startSlot, int maxSlots) {
        for (int i = startSlot; i < maxSlots; i++) {
            if (!handler.getSlot(i).getStack().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void startStealing() {
        isStealing = true;
        currentSlot = 0;
        lastClickTime = System.currentTimeMillis() - DELAY_MS;
    }

    private void resetStealing() {
        isStealing = false;
        currentSlot = 0;
    }
}