package databack.client.debug;

import org.lwjgl.input.Keyboard;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.relauncher.Side;

@EventBusSubscriber(side = Side.CLIENT)
public class DebugOverlayKeyHandler {

    @SubscribeEvent
    public static void onKey(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState()
            && Keyboard.getEventKey() == Keyboard.KEY_F6
            && Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            DebugOverlayScreen.open();
        }
    }
}
