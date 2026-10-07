package net.tfminecraft.simplefactions.testsupport;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Keyed;
import org.bukkit.block.BlockType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;

/**
 * Stands in for the server's registries, which Paper finds through {@link java.util.ServiceLoader}.
 * Without it the first {@code Sound} constant a test touches fails to initialise, and every later
 * test in the same JVM gets {@code NoClassDefFoundError}. Sound, enchantment, banner-pattern and
 * menu/attribute identifiers and block air classification resolve; all other registries and server-backed operations remain unavailable.
 */
public final class TestRegistryAccess implements RegistryAccess {

  private static final Map<NamespacedKey, Sound> SOUNDS = new ConcurrentHashMap<>();
  private static final Map<NamespacedKey, Enchantment> ENCHANTMENTS = new ConcurrentHashMap<>();
  private static final Map<NamespacedKey, PatternType> PATTERNS = new ConcurrentHashMap<>();

  private static final Map<NamespacedKey, BlockType> BLOCKS = new ConcurrentHashMap<>();

  private static final Map<NamespacedKey, MenuType> MENUS = new ConcurrentHashMap<>();
  private static final Map<NamespacedKey, Attribute> ATTRIBUTES = new ConcurrentHashMap<>();

  @Override
  @Deprecated
  public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
    return registry(
        Sound.class.equals(type),
        Enchantment.class.equals(type),
        PatternType.class.equals(type),
        MenuType.class.equals(type), BlockType.class.equals(type), Attribute.class.equals(type));
  }

  @Override
  public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
    return registry(
        RegistryKey.SOUND_EVENT.equals(key),
        RegistryKey.ENCHANTMENT.equals(key),
        RegistryKey.BANNER_PATTERN.equals(key),
        RegistryKey.MENU.equals(key), RegistryKey.BLOCK.equals(key), RegistryKey.ATTRIBUTE.equals(key));
  }

  @SuppressWarnings("unchecked")
  private static <T extends Keyed> Registry<T> registry(
      boolean sounds, boolean enchantments, boolean patterns, boolean menus, boolean blocks, boolean attributes) {
    return (Registry<T>)
        Proxy.newProxyInstance(
            Registry.class.getClassLoader(),
            new Class<?>[] {Registry.class},
            (self, method, args) ->
                switch (method.getName()) {
                  case "equals" -> self == args[0];
                  case "hashCode" -> System.identityHashCode(self);
                  case "toString" -> sounds ? "TestRegistry[sounds]" : "TestRegistry[unavailable]";
                  case "getKey" -> ((Keyed) args[0]).getKey();
                  case "get", "getOrThrow" -> {
                    if (attributes)
                      yield ATTRIBUTES.computeIfAbsent(
                          (NamespacedKey) args[0], TestRegistryAccess::attribute);
                    if (blocks) {
                      Class.forName(BlockType.class.getName(), true, BlockType.class.getClassLoader());
                      NamespacedKey blockKey = NamespacedKey.fromString(args[0].toString());
                      yield method.getName().equals("getOrThrow")
                          ? BLOCKS.computeIfAbsent(blockKey, TestRegistryAccess::block)
                          : BLOCKS.get(blockKey);
                    }
                    if (menus)
                      yield MENUS.computeIfAbsent(
                          (NamespacedKey) args[0], TestRegistryAccess::menu);
                    if (patterns)
                      yield method.getName().equals("get")
                          ? PATTERNS.get((NamespacedKey) args[0])
                          : registerPattern((NamespacedKey) args[0]);
                    if (enchantments)
                      yield ENCHANTMENTS.computeIfAbsent(
                          (NamespacedKey) args[0], TestRegistryAccess::enchantment);
                    if (!sounds)
                      throw new IllegalStateException(
                          "This registry is unavailable without a server");
                    yield SOUNDS.computeIfAbsent(
                        (NamespacedKey) args[0], TestRegistryAccess::sound);
                  }
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  private static Attribute attribute(NamespacedKey key) {
    return (Attribute) Proxy.newProxyInstance(Attribute.class.getClassLoader(),
        new Class<?>[] {Attribute.class}, (self, method, args) -> switch (method.getName()) {
          case "equals" -> self == args[0];
          case "hashCode" -> key.hashCode();
          case "toString" -> key.toString();
          case "getKey", "key" -> key;
          default -> throw new UnsupportedOperationException("Attribute identifier only: " + method.getName());
        });
  }

  private static BlockType block(NamespacedKey key) {
    return (BlockType) Proxy.newProxyInstance(BlockType.class.getClassLoader(),
        new Class<?>[] {BlockType.Typed.class}, (self, method, args) -> switch (method.getName()) {
          case "equals" -> self == args[0];
          case "hashCode" -> key.hashCode();
          case "toString" -> key.toString();
          case "getKey", "key" -> key;
          case "typed" -> self;
          case "isAir" -> java.util.Set.of("air", "cave_air", "void_air").contains(key.getKey());
          default -> throw new UnsupportedOperationException("Block air classification only: " + method.getName());
        });
  }

  private static MenuType menu(NamespacedKey key) {
    return (MenuType)
        Proxy.newProxyInstance(
            MenuType.class.getClassLoader(),
            new Class<?>[] {MenuType.Typed.class},
            (self, method, args) ->
                switch (method.getName()) {
                  case "equals" -> self == args[0];
                  case "hashCode" -> key.hashCode();
                  case "toString" -> key.toString();
                  case "getKey", "key" -> key;
                  case "typed" -> self;
                  case "getInventoryViewClass" -> InventoryView.class;
                  default ->
                      throw new UnsupportedOperationException(
                          "Menu identifier only: " + method.getName());
                });
  }

  public static PatternType registerPattern(NamespacedKey key) {
    return PATTERNS.computeIfAbsent(
        key,
        k ->
            org.mockito.Mockito.mock(
                PatternType.class,
                invocation ->
                    switch (invocation.getMethod().getName()) {
                      case "getKey", "key" -> k;
                      case "getIdentifier" -> k.getKey();
                      default -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
                    }));
  }

  private static Enchantment enchantment(NamespacedKey key) {
    return org.mockito.Mockito.mock(
        Enchantment.class,
        invocation ->
            switch (invocation.getMethod().getName()) {
              case "getKey", "key" -> key;
              case "getName" -> key.getKey();
              default -> org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
  }

  private static Sound sound(NamespacedKey key) {
    return (Sound)
        Proxy.newProxyInstance(
            Sound.class.getClassLoader(),
            new Class<?>[] {Sound.class},
            (self, method, args) ->
                switch (method.getName()) {
                  case "equals" -> self == args[0];
                  case "hashCode" -> key.hashCode();
                  case "toString" -> "Sound[" + key + "]";
                  case "getKey", "key" -> key;
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }
}
