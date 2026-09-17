package databack.client.debug;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.factory.ClientGUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SortableListWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import databack.Databack;
import databack.common.debug.DebugOverlayRegistry;
import databack.common.debug.DebugOverlayRegistry.DisplayMode;

@SideOnly(Side.CLIENT)
public class DebugOverlayScreen {

    // IDrawable that explicitly enables blending before drawing the translucent background.
    // MUI2 doesn't guarantee GL_BLEND is on when drawing panel backgrounds, so we set it ourselves.
    private static final IDrawable DARK_BG = (context, x, y, width, height, theme) -> {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GuiDraw.drawRect(x, y, width, height, 0x80000000);
        // Leave blend enabled — MUI2 widget rendering expects it for text and button draws.
    };

    private static final int MAX_LIST_W   = 500;
    private static final int MAX_LIST_H   = 320;
    private static final int ROW_H        = 20;
    private static final int MODE_BTN_W   = 100;
    private static final int BOTTOM_BAR_W = 120 + 4 + 130 + 4 + 100; // 358
    private static final int H_MARGIN     = 40; // minimum pixels between list edge and screen edge
    private static final int V_MARGIN     = 100; // reserved for title + bottom bar

    public static void open() {
        ClientGUI.open(create());
    }

    private static ModularScreen create() {
        DebugOverlayRegistry.applySavedOrder();

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int listW = Math.min(MAX_LIST_W, sr.getScaledWidth()  - H_MARGIN);
        int listH = Math.min(MAX_LIST_H, sr.getScaledHeight() - V_MARGIN);
        int nameW = listW - MODE_BTN_W - 6;

        ModularPanel panel = new ModularPanel("databack:debug_overlay")
            .full().invisible().background(DARK_BG);

        SortableListWidget<String> list = new SortableListWidget<String>()
            .top(50).height(listH)
            .width(listW).horizontalCenter()
            .onChange(DebugOverlayRegistry::reorder);

        for (String id : DebugOverlayRegistry.getKnownHandlers()) {
            list.child(buildRow(id, listW, nameW));
        }

        panel
            .child(new TextWidget<>(IKey.str("Debug Screen Options")).color(0xFFDDDDDD).top(20).horizontalCenter())
            .child(list)
            .child(buildBottomBar(panel).bottom(20).horizontalCenter());

        return new ModularScreen(Databack.MODID, panel);
    }

    private static SortableListWidget.Item<String> buildRow(String handlerId, int listW, int nameW) {
        String langKey = DebugOverlayRegistry.getTitle(handlerId);

        TextWidget<?> nameWidget = new TextWidget<>(langKey == null ? IKey.dynamic(() -> nameWithColor(handlerId)) : IKey.lang(langKey))
            .color(0xFFDDDDDD)
            .left(2).width(nameW).height(ROW_H);

        String descKey = DebugOverlayRegistry.getDescription(handlerId);
        if (descKey != null) {
            nameWidget.tooltip(t -> t.addLine(IKey.dynamic(() -> StatCollector.translateToLocal(descKey))));
        }

        Flow row = Flow.row()
            .widthRel(1f).height(ROW_H)
            .child(nameWidget)
            .child(
                new ButtonWidget<>()
                    .overlay(IKey.dynamic(() -> modeName(handlerId)))
                    .size(MODE_BTN_W, ROW_H - 2)
                    .right(2)
                    .tooltip(t -> t
                        .addLine(IKey.str("\u00a7fOff\u00a7r: disabled entirely"))
                        .addLine(IKey.str("\u00a7fIn Overlay\u00a7r: shown when F3 is open"))
                        .addLine(IKey.str("\u00a7fAlways\u00a7r: shown even without F3")))
                    .onMouseTapped(btn -> {
                        DebugOverlayRegistry.cycleMode(handlerId);
                        return true;
                    })
            );

        return new DragHandleItem(handlerId, nameW)
            .height(ROW_H)
            .child(row);
    }

    /**
     * SortableListWidget.Item that only accepts drags when the cursor is in the
     * name/handle portion (left of nameW), so clicks on the mode button pass through.
     *
     * getContext().getMouseX() returns the X in the widget's own local coordinate space
     * because the matrix is applied before onDragStart is called.
     */
    private static class DragHandleItem extends SortableListWidget.Item<String> {

        private final int nameW;

        DragHandleItem(String id, int nameW) {
            super(id);
            this.nameW = nameW;
        }

        @Override
        public boolean onDragStart(int mouseButton) {
            if (getContext().getMouseX() > nameW) return false;
            return super.onDragStart(mouseButton);
        }
    }

    private static String nameWithColor(String handlerId) {
        DisplayMode mode = DebugOverlayRegistry.getMode(handlerId);
        String name = DebugOverlayRegistry.getDisplayName(handlerId);
        if (mode == DisplayMode.OFF) return "\u00a7c" + name;   // red overrides IKey color
        if (mode == DisplayMode.ALWAYS) return "\u00a7a" + name; // green overrides IKey color
        return name; // IKey color (0xFFDDDDDD off-white) applies
    }

    private static String modeName(String handlerId) {
        DisplayMode mode = DebugOverlayRegistry.getMode(handlerId);
        if (mode == DisplayMode.OFF) return "\u00a7cOff";
        if (mode == DisplayMode.ALWAYS) return "\u00a7aAlways";
        return "In Overlay";
    }

    private static Flow buildBottomBar(ModularPanel panel) {
        return Flow.row()
            .height(20).width(BOTTOM_BAR_W)
            .child(
                new ButtonWidget<>()
                    .overlay(IKey.str("Default"))
                    .size(120, 20)
                    .onMouseTapped(btn -> {
                        DebugOverlayRegistry.setAll(DisplayMode.IN_OVERLAY);
                        return true;
                    })
            )
            .child(
                new ButtonWidget<>()
                    .overlay(IKey.str("Performance"))
                    .size(130, 20)
                    .marginLeft(4)
                    .onMouseTapped(btn -> {
                        DebugOverlayRegistry.setAll(DisplayMode.OFF);
                        return true;
                    })
            )
            .child(
                new ButtonWidget<>()
                    .overlay(IKey.str("Done"))
                    .size(100, 20)
                    .marginLeft(4)
                    .onMouseTapped(btn -> {
                        panel.closeIfOpen();
                        return true;
                    })
            );
    }
}
