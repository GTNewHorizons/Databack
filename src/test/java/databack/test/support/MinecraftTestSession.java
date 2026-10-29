package databack.test.support;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/// Bootstraps Minecraft ([MinecraftBootstrap]) once per test JVM, before any test class is loaded (registered in
/// `META-INF/services`).
public final class MinecraftTestSession implements LauncherSessionListener {

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        MinecraftBootstrap.init();
    }
}
