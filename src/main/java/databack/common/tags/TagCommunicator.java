package databack.common.tags;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import databack.common.debug.DebugOverlayEntry;
import databack.common.debug.DebugOverlayRegistry.DisplayMode;
import databack.common.interop.modern_block.ItemIdentity;
import databack.common.interop.registry.BlockIdentityRegistry;
import databack.common.interop.registry.ItemIdentityRegistry;
import databack.common.interop.registry.ProxyBlockRegistry;
import databack.common.interop.registry.ProxyItemRegistry;

@EventBusSubscriber(side = Side.CLIENT)
public class TagCommunicator {

    @DebugOverlayEntry(title = "databack.debug.block_id.title", description = "databack.debug.block_id.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateBlockId(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);

            ResourceLocation legacyId = ProxyBlockRegistry.INSTANCE.getIdForObject(block);

            event.left.add("Legacy Block Id: " + legacyId);
        }
    }

    @DebugOverlayEntry(title = "databack.debug.block_identity.title", description = "databack.debug.block_identity.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateBlockIdentity(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            int meta = block.getDamageValue(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);

            ResourceLocation legacyId = ProxyBlockRegistry.INSTANCE.getIdForObject(block);

            var blockIdentity = BlockIdentityRegistry.INSTANCE.getBlockIdentity(block, meta);

            if (!blockIdentity.identityId.equals(legacyId)) {
                event.left.add("Block Identity: " + blockIdentity.identityId);
            }

            if (blockIdentity.variant != null) {
                event.left.add("Block Variant: " + blockIdentity.variant);
            }
        }
    }

    @DebugOverlayEntry(title = "databack.debug.block_tags.title", description = "databack.debug.block_tags.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateBlockTags(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
            int meta = block.getDamageValue(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);

            var blockIdentity = BlockIdentityRegistry.INSTANCE.getBlockIdentity(block, meta);

            var tags = BuiltinTagRegistries.blocks().getTags(blockIdentity);

            if (!tags.isEmpty()) {
                event.left.add("Block Tags:");

                for (var tag : tags) {
                    event.left.add("- " + tag.toString());
                }
            }
        }
    }

    @DebugOverlayEntry(title = "databack.debug.entity_id.title", description = "databack.debug.entity_id.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateEntityId(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
            Class<? extends Entity> entity = hit.entityHit.getClass();

            event.left.add("Entity Id: " + BuiltinTagRegistries.entities().getIdForTarget(entity));
        }
    }

    @DebugOverlayEntry(title = "databack.debug.entity_tags.title", description = "databack.debug.entity_tags.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateEntityTags(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
            Class<? extends Entity> entity = hit.entityHit.getClass();

            var tags = BuiltinTagRegistries.entities().getTags(entity);

            if (!tags.isEmpty()) {
                event.left.add("Entity Tags:");

                for (var tag : tags) {
                    event.left.add("- " + tag.toString());
                }
            }
        }
    }

    @DebugOverlayEntry(title = "databack.debug.biome_id.title", description = "databack.debug.biome_id.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateBiomeId(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords((int) mc.renderViewEntity.posX, (int) mc.renderViewEntity.posZ);

        event.left.add("Biome Id: " + BuiltinTagRegistries.biomes().getIdForTarget(biome));
    }

    @DebugOverlayEntry(title = "databack.debug.biome_tags.title", description = "databack.debug.biome_tags.desc", defaultMode = DisplayMode.OFF)
    @SubscribeEvent
    public static void communicateBiomeTags(RenderGameOverlayEvent.Text event) {
        if (!Minecraft.getMinecraft().gameSettings.showDebugInfo) return;

        Minecraft mc = Minecraft.getMinecraft();

        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords((int) mc.renderViewEntity.posX, (int) mc.renderViewEntity.posZ);

        var tags = BuiltinTagRegistries.biomes().getTags(biome);

        if (!tags.isEmpty()) {
            event.left.add("Biome Tags:");

            for (var tag : tags) {
                event.left.add("- " + tag.toString());
            }
        }
    }

    @SubscribeEvent
    public static void communicateItemTags(ItemTooltipEvent event) {
        if (!event.showAdvancedItemTooltips) return;
        if (!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) return;

        Item item = event.itemStack.getItem();

        if (item == null) return;

        int damage = event.itemStack.getItemDamage();
        ItemIdentity itemIdentity = ItemIdentityRegistry.INSTANCE.getItemIdentity(item, damage);

        if (itemIdentity == null) return;

        event.toolTip.add("");
        event.toolTip.add("Legacy Item Id: " + ProxyItemRegistry.INSTANCE.getIdForObject(item));

        if (itemIdentity.variant != null) {
            event.toolTip.add("Item Identity: " + itemIdentity.identityId);
            event.toolTip.add("Item Variant: " + itemIdentity.variant);
        }

        var tags = BuiltinTagRegistries.items().getTags(itemIdentity);

        if (tags.isEmpty()) return;

        event.toolTip.add("Item Tags:");

        for (var tag : tags) {
            event.toolTip.add("- " + tag);
        }
    }
}
