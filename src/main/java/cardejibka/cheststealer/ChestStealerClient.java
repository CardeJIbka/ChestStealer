package cardejibka.cheststealer;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.*;
import org.lwjgl.glfw.GLFW;

public class ChestStealerClient implements ClientModInitializer {

    private static KeyMapping toggleKeyBinding;
    private static boolean isEnabled = false;
    private int currentSlot = 0;
    private int lastContainerId = -1;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        toggleKeyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cheststealer.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        while (toggleKeyBinding.consumeClick()) {
            isEnabled = !isEnabled;
            if (client.player != null) {
                Component status = Component.translatable("text.cheststealer." + (isEnabled ? "enabled" : "disabled"))
                        .withStyle(isEnabled ? ChatFormatting.GREEN : ChatFormatting.RED);

                Component message = Component.translatable("text.cheststealer.prefix")
                        .append(status);

                client.gui.setOverlayMessage(message, false);
            }
        }

        if (!isEnabled || client.player == null || client.player.containerMenu == null) {
            resetStealing();
            return;
        }

        AbstractContainerMenu menu = client.player.containerMenu;
        if (menu.containerId == 0 || isEnderChest(client)) {
            resetStealing();
            return;
        }

        if (menu.containerId != lastContainerId) {
            resetStealing();
            lastContainerId = menu.containerId;
        }

        if (menu instanceof ChestMenu chest) process(client, chest, chest.getRowCount() * 9);
        else if (menu instanceof ShulkerBoxMenu shulker) process(client, shulker, 27);
    }

    private void process(Minecraft client, AbstractContainerMenu menu, int maxSlots) {
        for (int i = 0; i < ConfigManager.itemsPerTick; i++) {
            if (currentSlot >= maxSlots) {
                resetStealing();
                break;
            }

            var slot = menu.getSlot(currentSlot);
            if (slot.getItem().isEmpty()) {
                currentSlot++;
                continue;
            }

            String itemId = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();

            if (!ConfigManager.isItemAllowed(itemId)) {
                currentSlot++;
                continue;
            }

            client.getConnection().send(new ServerboundContainerClickPacket(
                    menu.containerId, menu.getStateId(), (short) currentSlot, (byte) 0,
                    ContainerInput.QUICK_MOVE, new Int2ObjectOpenHashMap<>(),
                    HashedStack.create(slot.getItem(), null)
            ));

            menu.clicked(currentSlot, 0, ContainerInput.QUICK_MOVE, client.player);
            currentSlot++;
        }
    }

    private boolean isEnderChest(Minecraft client) {
        return client.screen instanceof AbstractContainerScreen<?> s &&
                s.getTitle().getContents() instanceof TranslatableContents tc &&
                "container.enderchest".equals(tc.getKey());
    }

    private void resetStealing() {
        currentSlot = 0;
        lastContainerId = -1;
    }
}