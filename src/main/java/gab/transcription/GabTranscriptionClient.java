package gab.transcription;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;

/**
 * GABtranscription — атрибут свап для списа (1.21.11).
 *
 * Q (клавіша "викинути предмет") працює як свап ТІЛЬКИ коли в руці:
 * меч, їжа, булава, сокира, заряд вітру або перла.
 * В інших випадках Q кидає предмет як зазвичай.
 *
 * Мод не шле жодних "лівих" пакетів: це звичайний вибір слота хотбару
 * і звичайний клік атаки, з паузами між діями як у людини.
 */
public class GabTranscriptionClient implements ClientModInitializer {

    /** Через скільки тіків після свапу тиснемо атаку. */
    private static final int ATTACK_AFTER_TICKS = 1;
    /** Через скільки тіків повертаємо старий слот. */
    private static final int RESTORE_AFTER_TICKS = 3;
    /** Пауза між свапами (тіки), щоб не спамити. */
    private static final int COOLDOWN_TICKS = 8;

    private int returnSlot = -1;
    private int spearSlot = -1;
    private int ticksSinceSwap = 0;
    private int cooldown = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            resetState();
            return;
        }

        // Свап уже йде — доводимо до кінця
        if (returnSlot != -1) {
            handleSwapped(client, player);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
        }

        if (client.currentScreen != null || player.isSpectator() || !player.isAlive()) {
            return;
        }

        // Працює тільки з дозволених предметів у руці
        if (!isAllowedHeld(player.getMainHandStack())) {
            return;
        }

        // Спис має лежати в хотбарі, інакше Q кидає предмет як звичайно
        PlayerInventory inventory = player.getInventory();
        int slot = findSpearSlot(inventory);
        if (slot == -1) {
            return;
        }

        boolean pressed = false;
        while (client.options.dropKey.wasPressed()) {
            pressed = true;
        }
        if (!pressed || cooldown > 0) {
            return;
        }

        returnSlot = inventory.getSelectedSlot();
        spearSlot = slot;
        ticksSinceSwap = 0;
        inventory.setSelectedSlot(slot);
    }

    private void handleSwapped(MinecraftClient client, ClientPlayerEntity player) {
        // Поки спис в руці — Q не має його викинути
        while (client.options.dropKey.wasPressed()) {
            // з'їдаємо натискання
        }

        ticksSinceSwap++;

        if (ticksSinceSwap == ATTACK_AFTER_TICKS && client.currentScreen == null) {
            // Звичайний клік атаки — далі все робить ванільна логіка
            KeyBinding attack = client.options.attackKey;
            KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(attack.getBoundKeyTranslationKey()));
        }

        if (ticksSinceSwap >= RESTORE_AFTER_TICKS) {
            PlayerInventory inventory = player.getInventory();
            // Якщо гравець сам встиг перемкнути слот — не чіпаємо
            if (inventory.getSelectedSlot() == spearSlot) {
                inventory.setSelectedSlot(returnSlot);
            }
            resetState();
            cooldown = COOLDOWN_TICKS;
        }
    }

    private void resetState() {
        returnSlot = -1;
        spearSlot = -1;
        ticksSinceSwap = 0;
    }

    private static boolean isAllowedHeld(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.isIn(ItemTags.SWORDS)
                || stack.isIn(ItemTags.AXES)
                || stack.isOf(Items.MACE)
                || stack.isOf(Items.WIND_CHARGE)
                || stack.isOf(Items.ENDER_PEARL)
                || stack.contains(DataComponentTypes.FOOD);
    }

    private static int findSpearSlot(PlayerInventory inventory) {
        for (int i = 0; i < 9; i++) {
            if (isSpear(inventory.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSpear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_spear");
    }
}
