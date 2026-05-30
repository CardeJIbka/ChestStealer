package cardejibka.cheststealer;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = ChestStealer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ChestStealerClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("ChestStealer");

    public static KeyMapping toggleKeyBinding;
    private static boolean isEnabled = false;

    private static long lastClickTime = 0;
    private static final long DELAY_MS = 0;
    private static int currentSlot = 0;
    private static boolean isStealing = false;
    private static int lastContainerId = -1;

    private static final String ENDER_CHEST_KEY = "container.enderchest";

    public static void init() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(TickHandler.class);
        LOGGER.info("ChestStealer initialized | Toggle key: R");
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        toggleKeyBinding = new KeyMapping(
                "key.cheststealer.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                "key.categories.misc"
        );
        event.register(toggleKeyBinding);
    }

    @Mod.EventBusSubscriber(modid = ChestStealer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class TickHandler {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft client = Minecraft.getInstance();

            while (toggleKeyBinding != null && toggleKeyBinding.consumeClick()) {
                isEnabled = !isEnabled;
                if (client.player != null) {
                    Component status = Component.translatable(
                                    "text.cheststealer." + (isEnabled ? "enabled" : "disabled"))
                            .withStyle(Style.EMPTY.withColor(
                                    isEnabled ? ChatFormatting.GREEN : ChatFormatting.RED));

                    Component message = Component.literal("ChestStealer: ").append(status);
                    client.gui.setOverlayMessage(message, false);
                }
            }

            if (!isEnabled || client.player == null || client.gameMode == null) {
                resetStealing();
                return;
            }

            AbstractContainerMenu menu = client.player.containerMenu;
            if (menu == null || menu.containerId == 0) {
                resetStealing();
                return;
            }

            if (menu.containerId != lastContainerId) {
                resetStealing();
                lastContainerId = menu.containerId;
            }

            if (isEnderChestOpen(client)) {
                resetStealing();
                return;
            }

            if (menu instanceof ChestMenu chest) {
                processContainer(client, chest, chest.getRowCount() * 9);
            } else if (menu instanceof ShulkerBoxMenu shulker) {
                processContainer(client, shulker, 27);
            } else {
                resetStealing();
            }
        }
    }

    private static boolean isEnderChestOpen(Minecraft client) {
        if (client.screen instanceof AbstractContainerScreen<?> screen) {
            Component title = screen.getTitle();
            return title.getContents() instanceof TranslatableContents tc
                    && ENDER_CHEST_KEY.equals(tc.getKey());
        }
        return false;
    }

    private static void processContainer(Minecraft client, AbstractContainerMenu menu, int containerSlots) {
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

        if (now - lastClickTime < DELAY_MS) return;

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

        Int2ObjectMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();

        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                slotId,
                1,
                ClickType.QUICK_MOVE,
                ItemStack.EMPTY,
                changedSlots
        );

        connection.send(packet);

        connection.send(packet);
        LOGGER.debug("Quick-moved slot {}", slotId);

        currentSlot = slotId + 1;
        lastClickTime = now;
    }

    private static int findNextNonEmptySlot(AbstractContainerMenu menu, int start, int maxSlots) {
        for (int i = start; i < maxSlots; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) return i;
        }
        return -1;
    }

    private static void startStealing() {
        isStealing = true;
        currentSlot = 0;
        lastClickTime = System.currentTimeMillis() - DELAY_MS;
    }

    private static void resetStealing() {
        isStealing = false;
        currentSlot = 0;
    }
}