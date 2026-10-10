package com.civillagers.gametest.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The gametest mod on NeoForge: finds the same test methods Fabric API finds and registers them the
 * way Fabric API does, so every node runs one list of tests under one set of ids.
 *
 * <ul>
 *   <li><b>Discovery.</b> The test classes are the {@code fabric-gametest} entrypoints in this mod's
 *       own {@code fabric.mod.json}, read as plain JSON. A class added there runs on every node.</li>
 *   <li><b>Ids.</b> {@code civillagers_gametest:<class>_<method>} in snake case, Fabric API's
 *       rule.</li>
 *   <li><b>Defaults.</b> Fabric API's: an empty 8x8x8 structure, 20 ticks, required, no rotation, one
 *       attempt, padding 1. NeoForge ships no empty structure, so this mod has its own. The
 *       environment is an empty one registered here, the same definition as vanilla's
 *       {@code minecraft:default}, which the registration event gives no way to look up.</li>
 *   <li><b>Before 1.21.5</b> a test is a {@code TestFunction} in vanilla's static registry rather
 *       than a registry entry, and there are no environments or padding. NeoForge only turns methods
 *       carrying vanilla's own annotation into one, and takes the structure's namespace from a
 *       NeoForge annotation on the test class, so the harness builds the functions itself.</li>
 * </ul>
 *
 * <p>This is test harness, not a loader seam: nothing in the mod calls it, and no test body changes
 * for it. See {@code src/gametest/java/AGENTS.md}.
 */
@Mod(CivillagersGameTests.MOD_ID)
public final class CivillagersGameTests {
    /** NeoForge mod ids cannot contain a hyphen, so both loaders use this one. */
    static final String MOD_ID = "civillagers_gametest";
    /**
     * Where to write the JUnit report before 1.21.5, whose server has no {@code --report} option. Set
     * by build.neoforge.gradle.kts.
     */
    static final String REPORT_PROPERTY = "civillagers.gametest.report";

    private static final int MAX_TICKS = 20;
    private static final int PADDING = 1;

    private final List<TestMethod> tests;

    public CivillagersGameTests(IEventBus modBus, ModContainer container) {
        tests = findTests(container);
        //? if >=1.21.5 {
        modBus.addListener(this::registerFunctions);
        //?}
        modBus.addListener(this::registerTests);
    }

    //? if >=1.21.5 {
    private void registerFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION,
                helper -> tests.forEach(test -> helper.register(test.id(), test::run)));
    }

    private void registerTests(RegisterGameTestsEvent event) {
        // `var`, because TestEnvironmentDefinition only takes a type parameter from 26.1 on.
        var environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(MOD_ID, "default"));
        // Test padding arrived with 26.1; before it the runner spaces tests out itself. 26.3 put the
        // dimension a test runs in on the test rather than on the runner.
        //? if >=26.3 {
        var data = new net.minecraft.gametest.framework.TestData<>(environment,
                        net.minecraft.world.level.Level.OVERWORLD,
                        Identifier.fromNamespaceAndPath(MOD_ID, "empty"), MAX_TICKS, 0, true, Rotation.NONE,
                        false, 1, 1, false, PADDING);
        //?} elif >=26.1 {
        /*var data = new net.minecraft.gametest.framework.TestData<>(environment,
                        Identifier.fromNamespaceAndPath(MOD_ID, "empty"), MAX_TICKS, 0, true, Rotation.NONE,
                        false, 1, 1, false, PADDING);
        *///?} else {
        /*var data = new net.minecraft.gametest.framework.TestData<>(environment,
                        Identifier.fromNamespaceAndPath(MOD_ID, "empty"), MAX_TICKS, 0, true, Rotation.NONE,
                        false, 1, 1, false);
        *///?}
        for (TestMethod test : tests) {
            event.registerTest(test.id(), new net.minecraft.gametest.framework.FunctionGameTestInstance(
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, test.id()),
                    data));
        }
    }
    //?} else {
    /*private void registerTests(RegisterGameTestsEvent event) {
        String report = System.getProperty(REPORT_PROPERTY);
        if (report != null) {
            try {
                net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(
                        new net.minecraft.gametest.framework.JUnitLikeTestReporter(new java.io.File(report)));
            } catch (javax.xml.parsers.ParserConfigurationException e) {
                throw new IllegalStateException("Cannot write the gametest report to " + report, e);
            }
        }
        String structure = MOD_ID + ":empty";
        for (TestMethod test : tests) {
            // Vanilla's default batch, and Fabric API's defaults for everything else.
            net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions().add(
                    new net.minecraft.gametest.framework.TestFunction("defaultBatch", test.id().toString(), structure,
                            Rotation.NONE, MAX_TICKS, 0L, true, false, 1, 1, false, test::run));
        }
    }
    *///?}

    private static List<TestMethod> findTests(ModContainer container) {
        JsonObject manifest;
        try (InputStream in = openManifest(container)) {
            if (in == null) throw new IllegalStateException(MOD_ID + " has no fabric.mod.json to read its test classes from");
            manifest = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<TestMethod> tests = new ArrayList<>();
        for (var entry : manifest.getAsJsonObject("entrypoints").getAsJsonArray("fabric-gametest")) {
            TestClass testClass = new TestClass(load(entry.getAsString()));
            List<TestMethod> found = new ArrayList<>();
            for (Class<?> type = testClass.type(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(GameTest.class)) found.add(new TestMethod(testClass, validate(method)));
                }
            }
            if (found.isEmpty()) throw new IllegalStateException("No @GameTest methods in " + entry.getAsString());
            tests.addAll(found);
        }
        return tests;
    }

    /** This mod's {@code fabric.mod.json}, or null. FML hands out a path before NeoForge 21.9 and a stream after. */
    private static InputStream openManifest(ModContainer container) throws IOException {
        //? if >=1.21.9 {
        return container.getModInfo().getOwningFile().getFile().getContents().openFile("fabric.mod.json");
        //?} else {
        /*java.nio.file.Path path = container.getModInfo().getOwningFile().getFile().findResource("fabric.mod.json");
        return java.nio.file.Files.exists(path) ? java.nio.file.Files.newInputStream(path) : null;
        *///?}
    }

    /**
     * Loads a test class without initializing it. Test classes keep the mod's items in static fields,
     * and this runs while the mod is constructed, before NeoForge lets anything register.
     */
    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, CivillagersGameTests.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot find the test class " + className, e);
        }
    }

    /** Fabric API's own checks, so a test that would not run there does not run here either. */
    private static Method validate(Method method) {
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class
                || !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                || method.getReturnType() != void.class) {
            throw new IllegalStateException("Test method " + method.getDeclaringClass().getName() + "#" + method.getName()
                    + " must be public, not static, return void and take one GameTestHelper");
        }
        return method;
    }

    /** One instance per test class, created on its first test, the way Fabric API shares an entrypoint. */
    private static final class TestClass {
        private final Class<?> type;
        private Object instance;

        TestClass(Class<?> type) {
            this.type = type;
        }

        Class<?> type() {
            return type;
        }

        synchronized Object instance() {
            if (instance == null) {
                try {
                    instance = type.getDeclaredConstructor().newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot create the test class " + type.getName(), e);
                }
            }
            return instance;
        }
    }

    private record TestMethod(TestClass testClass, Method method) {
        Identifier id() {
            String name = (testClass.type().getSimpleName() + "_" + method.getName())
                    .replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            return Identifier.fromNamespaceAndPath(MOD_ID, name);
        }

        void run(GameTestHelper helper) {
            try {
                method.invoke(testClass.instance(), helper);
            } catch (InvocationTargetException e) {
                // A failed assertion has to reach the runner as itself, or it reports an error instead.
                if (e.getTargetException() instanceof RuntimeException failure) throw failure;
                throw new RuntimeException("Test method " + method.getName() + " threw", e.getTargetException());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
