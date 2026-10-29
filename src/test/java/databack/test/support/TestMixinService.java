package databack.test.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinServiceBootstrap;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.service.MixinServiceAbstract;
import org.spongepowered.asm.service.mojang.LoggerAdapterLog4j2;

/// Mixin's service for tests: classes and their bytes come from the application class loader instead of
/// LaunchWrapper's. Selected through the `mixin.service` property ([TestMixins]).
public final class TestMixinService extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {

    private static volatile IMixinTransformerFactory transformerFactory;

    /// Selected through `mixin.bootstrapService`; there is nothing to set up.
    public static final class Bootstrap implements IMixinServiceBootstrap {

        @Override
        public String getName() {
            return "Databack test";
        }

        @Override
        public String getServiceClassName() {
            return TestMixinService.class.getName();
        }

        @Override
        public void bootstrap() {}
    }

    /// The transformer factory Mixin offered, once [org.spongepowered.asm.launch.MixinBootstrap#init] has run.
    static IMixinTransformerFactory transformerFactory() {
        return transformerFactory;
    }

    private static ClassLoader loader() {
        return TestMixinService.class.getClassLoader();
    }

    @Override
    public String getName() {
        return "Databack test";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void offer(IMixinInternal internal) {
        if (internal instanceof IMixinTransformerFactory) transformerFactory = (IMixinTransformerFactory) internal;
        super.offer(internal);
    }

    @Override
    protected ILogger createLogger(String name) {
        return new LoggerAdapterLog4j2(name);
    }

    @Override
    public IClassProvider getClassProvider() {
        return this;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return this;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.emptyList();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        return new ContainerHandleVirtual(getName());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return loader().getResourceAsStream(name);
    }

    @Override
    public URL[] getClassPath() {
        return new URL[0];
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, loader());
    }

    @Override
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader());
    }

    @Override
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
        return Class.forName(name, initialize, loader());
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
        return getClassNode(name, true, 0);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        return getClassNode(name, runTransformers, 0);
    }

    /// The class's untransformed bytes, read from the class path (there are no legacy transformers to run).
    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags)
        throws ClassNotFoundException, IOException {
        try (InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (in == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, readerFlags);
            return node;
        }
    }
}
