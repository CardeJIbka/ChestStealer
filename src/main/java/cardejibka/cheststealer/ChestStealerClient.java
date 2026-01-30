package cardejibka.cheststealer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
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

    private static final KeyBinding.Category CHESTSTEALER_CATEGORY =
            new KeyBinding.Category(Identifier.of("cheststealer", "cheststealer"));

    @Override
    public void onInitializeClient() {
        toggleKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cheststealer.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CHESTSTEALER_CATEGORY
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

        if (handler instanceof GenericContainerScreenHandler container) {
            if (!isStealing) {
                startStealing();
                LOGGER.debug("Started smart auto-steal (slots: {})", container.getRows() * 9);
            }

            int chestSlots = container.getRows() * 9;
            long now = System.currentTimeMillis();

            if (currentSlot < chestSlots && now - lastClickTime >= DELAY_MS) {
                int nextNonEmptySlot = findNextNonEmptySlot(container, currentSlot, chestSlots);

                if (nextNonEmptySlot != -1) {
                    Slot slot = container.getSlot(nextNonEmptySlot);
                    client.interactionManager.clickSlot(
                            container.syncId,
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

            if (currentSlot >= chestSlots) {
                resetStealing();
                LOGGER.debug("Finished stealing from chest");
            }
        } else {
            resetStealing();
        }
    }

    private int findNextNonEmptySlot(GenericContainerScreenHandler container, int startSlot, int maxSlots) {
        for (int i = startSlot; i < maxSlots; i++) {
            if (!container.getSlot(i).getStack().isEmpty()) {
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