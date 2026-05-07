package cardejibka.cheststealer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestStealerClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChestStealer");

    private static KeyMapping toggleKeyBinding;
    private static boolean isEnabled = false;

    private long lastClickTime = 0;
    private final long DELAY_MS = 0;
    private int currentSlot = 0;
    private boolean isStealing = false;
    private int lastContainerId = -1;

    @Override
    public void onInitializeClient() {
        toggleKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cheststealer.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        LOGGER.info("ChestStealer initialized | Toggle key: R");
    }

    private void onClientTick(Minecraft client) {
        while (toggleKeyBinding.consumeClick()) {
            isEnabled = !isEnabled;
            if (client.player != null) {
                Component status = Component.translatable("text.cheststealer." + (isEnabled ? "enabled" : "disabled"))
                        .withStyle(Style.EMPTY.withColor(isEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));

                client.player.sendSystemMessage(Component.literal("ChestStealer: ").append(status));
            }
        }

        if (!isEnabled || client.player == null || client.gameMode == null) {
            resetStealing();
            return;
        }

        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu == null || menu.containerId == 0) { // 0 = игрок инвентарь
            resetStealing();
            return;
        }

        if (menu.containerId != lastContainerId) {
            resetStealing();
            lastContainerId = menu.containerId;
        }

        if (menu instanceof ChestMenu chest) {
            processContainer(client, chest, chest.getRowCount() * 9);
        } else if (menu instanceof ShulkerBoxMenu shulker) {
            processContainer(client, shulker, 27);
        } else {
            resetStealing();
        }
    }

    private void processContainer(Minecraft client, AbstractContainerMenu menu, int containerSlots) {
        if (!isStealing) {
            startStealing();
            LOGGER.info("Started stealing from container ({} slots)", containerSlots);
        }

        long now = System.currentTimeMillis();
        if (currentSlot >= containerSlots) {
            resetStealing();
            LOGGER.info("Stealing finished");
            return;
        }

        if (now - lastClickTime < DELAY_MS) {
            return;
        }

        int slotId = findNextNonEmptySlot(menu, currentSlot, containerSlots);
        if (slotId == -1) {
            resetStealing();
            LOGGER.info("All slots empty — stealing finished");
            return;
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            resetStealing();
            return;
        }

        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                (short) slotId,
                (byte) 0,
                ContainerInput.QUICK_MOVE,
                new Int2ObjectOpenHashMap<>(),
                HashedStack.EMPTY
        );

        connection.send(packet);

        LOGGER.debug("Quick-moved slot {}", slotId);

        currentSlot = slotId + 1;
        lastClickTime = now;
    }

    private int findNextNonEmptySlot(AbstractContainerMenu menu, int start, int maxSlots) {
        for (int i = start; i < maxSlots; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) {
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