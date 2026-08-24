package databack.common.tags;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

@EventBusSubscriber(side = Side.CLIENT)
public class TagCommunicator {

    @SubscribeEvent
    public static void communicateHitTags(RenderGameOverlayEvent.Text event) {
        if (event.left.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();

        MovingObjectPosition hit = mc.objectMouseOver;

        if (hit != null && hit.typeOfHit == MovingObjectType.BLOCK) {
            Block block = mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);

            event.left.add("");
            event.left.add("Block Id: " + BuiltinTagRegistries.blocks().getIdForTarget(block));

            var tags = BuiltinTagRegistries.blocks().getTags(block);

            if (!tags.isEmpty()) {
                event.left.add("Block Tags:");

                for (var tag : tags) {
                    event.left.add("- " + tag.toString());
                }
            }
        }

        if (hit != null && hit.typeOfHit == MovingObjectType.ENTITY) {
            Class<? extends Entity> entity = hit.entityHit.getClass();

            event.left.add("");
            event.left.add("Entity Id: " + BuiltinTagRegistries.entities().getIdForTarget(entity));

            var tags = BuiltinTagRegistries.entities().getTags(entity);

            if (!tags.isEmpty()) {
                event.left.add("Entity Tags:");

                for (var tag : tags) {
                    event.left.add("- " + tag.toString());
                }
            }
        }
    }

    @SubscribeEvent
    public static void communicateBiomeTags(RenderGameOverlayEvent.Text event) {
        if (event.left.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();

        BiomeGenBase biome = mc.theWorld.getBiomeGenForCoords((int) mc.renderViewEntity.posX, (int) mc.renderViewEntity.posZ);

        event.left.add("");
        event.left.add("Biome Id: " + BuiltinTagRegistries.biomes().getIdForTarget(biome));

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

        var tags = BuiltinTagRegistries.items().getTags(item);

        if (tags.isEmpty()) return;

        event.toolTip.add("");
        event.toolTip.add("Item Id: " + BuiltinTagRegistries.items().getIdForTarget(item));

        event.toolTip.add("Tags:");

        for (var tag : tags) {
            event.toolTip.add("- " + tag);
        }
    }
}
