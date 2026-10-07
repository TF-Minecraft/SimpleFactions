package net.tfminecraft.simplefactions.testsupport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.tfminecraft.simplefactions.SimpleFactions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.banner.Pattern;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;

/**
 * Scoped Paper boundary for GUI tests. Production holders and menu builders remain real.
 * Inventories retain their contents, and item amounts, metadata and PDC survive independent
 * item/meta copies. Only the GUI API used here is simulated; this is not a replacement for a server
 * integration test.
 */
@SuppressWarnings({"deprecation", "unchecked"})
public final class GuiTestFixture implements AutoCloseable {
  public final SimpleFactions plugin = mock(SimpleFactions.class);
  public final Server server = mock(Server.class);
  public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
  public final World world = mock(World.class);
  public final List<Runnable> tasks = new ArrayList<>();
  public final List<Runnable> repeatingTasks = new ArrayList<>();
  public final List<Runnable> asyncTasks = new ArrayList<>();

  private final SimpleFactions previousPlugin = SimpleFactions.plugin;
  private final Map<ItemStack, ItemState> items = new IdentityHashMap<>();
  private final Map<ItemMeta, MetaState> metas = new IdentityHashMap<>();
  private final Map<Inventory, String> titles = new IdentityHashMap<>();
  private final MockedConstruction<ItemStack> itemConstruction;
  private final MockedStatic<Bukkit> bukkit;

  public GuiTestFixture() {
    when(world.getName()).thenReturn("world");
    when(plugin.getName()).thenReturn("SimpleFactions");
    when(plugin.namespace()).thenReturn("simplefactions");
    when(plugin.getServer()).thenReturn(server);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("GuiTestFixture"));
    when(server.getScheduler()).thenReturn(scheduler);
    when(server.createInventory(nullable(InventoryHolder.class), anyInt(), anyString()))
        .thenAnswer(
            call -> inventory(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    when(scheduler.runTask(any(), any(Runnable.class)))
        .thenAnswer(call -> schedule(tasks, call.getArgument(1)));
    when(scheduler.runTaskAsynchronously(any(), any(Runnable.class)))
        .thenAnswer(call -> schedule(asyncTasks, call.getArgument(1)));
    when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong()))
        .thenAnswer(call -> schedule(tasks, call.getArgument(1)));
    when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong()))
        .thenAnswer(call -> schedule(repeatingTasks, call.getArgument(1)));
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getServer).thenReturn(server);
    bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    Logger logger = plugin.getLogger();
    bukkit.when(Bukkit::getLogger).thenReturn(logger);
    bukkit.when(() -> Bukkit.getWorld(anyString())).thenReturn(world);
    bukkit
        .when(() -> Bukkit.createInventory(nullable(InventoryHolder.class), anyInt(), anyString()))
        .thenAnswer(
            call -> inventory(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    itemConstruction =
        mockConstruction(
            ItemStack.class,
            (item, context) -> {
              Object first =
                  context.arguments().isEmpty() ? Material.AIR : context.arguments().get(0);
              ItemState state;
              if (first instanceof ItemStack source) {
                state = state(source).copy();
              } else {
                state = new ItemState((Material) first);
                if (context.arguments().size() > 1)
                  state.amount = (Integer) context.arguments().get(1);
              }
              bind(item, state);
            });
    SimpleFactions.plugin = plugin;
  }

  private BukkitTask schedule(List<Runnable> queue, Runnable runnable) {
    queue.add(runnable);
    BukkitTask task = mock(BukkitTask.class);
    when(task.getTaskId()).thenReturn(queue.size());
    return task;
  }

  /** Runs the currently queued main-thread tasks; tasks queued by them wait for the next call. */
  public void runTasks() {
    List<Runnable> pending = new ArrayList<>(tasks);
    tasks.clear();
    pending.forEach(Runnable::run);
  }

  public NamespacedKey key(String name) {
    return new NamespacedKey(plugin, name);
  }

  public String data(ItemStack item, String key) {
    return item.getItemMeta().getPersistentDataContainer().get(key(key), PersistentDataType.STRING);
  }

  public Inventory inventory(InventoryHolder holder, int size, String title) {
    ItemStack[] contents = new ItemStack[size];
    Inventory inventory = mock(Inventory.class, call -> inventoryCall(call, contents, holder));
    titles.put(inventory, title);
    return inventory;
  }

  private Object inventoryCall(InvocationOnMock call, ItemStack[] contents, InventoryHolder holder)
      throws Throwable {
    return switch (call.getMethod().getName()) {
      case "getSize" -> contents.length;
      case "getType" -> holder instanceof Player ? InventoryType.PLAYER : InventoryType.CHEST;
      case "getHolder" -> holder;
      case "getItem" -> contents[(Integer) call.getArgument(0)];
      case "setItem" -> {
        contents[(Integer) call.getArgument(0)] = call.getArgument(1);
        yield null;
      }
      case "getContents", "getStorageContents" -> contents.clone();
      case "addItem" -> {
        ItemStack[] incoming = (ItemStack[]) call.getRawArguments()[0];
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        for (int index = 0; index < incoming.length; index++) {
          ItemStack item = incoming[index];
          if (item == null || item.getAmount() <= 0 || item.getType() == Material.AIR) continue;
          int remaining = item.getAmount();
          int maximum = item.getMaxStackSize();
          for (ItemStack present : contents) {
            if (present != null && present.isSimilar(item) && present.getAmount() < maximum) {
              int moved = Math.min(remaining, maximum - present.getAmount());
              present.setAmount(present.getAmount() + moved);
              remaining -= moved;
            }
          }
          for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            if (contents[slot] == null || contents[slot].getType() == Material.AIR) {
              ItemStack inserted = item.clone();
              inserted.setAmount(Math.min(remaining, maximum));
              contents[slot] = inserted;
              remaining -= inserted.getAmount();
            }
          }
          if (remaining > 0) {
            ItemStack leftover = item.clone();
            leftover.setAmount(remaining);
            leftovers.put(index, leftover);
          }
        }
        yield leftovers;
      }
      case "setContents", "setStorageContents" -> {
        ItemStack[] values = call.getArgument(0);
        if (values.length > contents.length)
          throw new IllegalArgumentException("Inventory is too small");
        Arrays.fill(contents, null);
        System.arraycopy(values, 0, contents, 0, values.length);
        yield null;
      }
      case "clear" -> {
        if (call.getArguments().length == 0) Arrays.fill(contents, null);
        else contents[(Integer) call.getArgument(0)] = null;
        yield null;
      }
      case "isEmpty" -> Arrays.stream(contents).allMatch(item -> item == null || item.isEmpty());
      case "firstEmpty" -> {
        int slot = 0;
        while (slot < contents.length && contents[slot] != null && !contents[slot].isEmpty())
          slot++;
        yield slot == contents.length ? -1 : slot;
      }
      case "getMaxStackSize" -> 64;
      default -> Answers.RETURNS_DEFAULTS.answer(call);
    };
  }

  public Player player(String name) {
    Player player = mock(Player.class);
    when(player.getName()).thenReturn(name);
    when(player.getUniqueId())
        .thenReturn(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
    when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    when(player.isOnline()).thenReturn(true);
    ItemStack[] contents = new ItemStack[41];
    PlayerInventory bottom =
        mock(PlayerInventory.class, call -> inventoryCall(call, contents, player));
    when(player.getInventory()).thenReturn(bottom);
    InventoryView[] current = {view(player, inventory(null, 0, "Crafting"), bottom)};
    when(player.getOpenInventory()).thenAnswer(call -> current[0]);
    when(player.openInventory(any(Inventory.class)))
        .thenAnswer(call -> current[0] = view(player, call.getArgument(0), bottom));
    doAnswer(
            call -> {
              current[0] = view(player, inventory(null, 0, "Crafting"), bottom);
              return null;
            })
        .when(player)
        .closeInventory();
    return player;
  }

  private InventoryView view(Player player, Inventory top, PlayerInventory bottom) {
    InventoryView view = mock(InventoryView.class);
    when(view.getPlayer()).thenReturn(player);
    when(view.getTopInventory()).thenReturn(top);
    when(view.getType()).thenAnswer(call -> top.getType());
    when(view.getBottomInventory()).thenReturn(bottom);
    when(view.getTitle()).thenReturn(titles.get(top));
    when(view.getOriginalTitle()).thenReturn(titles.get(top));
    when(view.countSlots()).thenAnswer(call -> top.getSize() + bottom.getSize());
    when(view.getInventory(anyInt()))
        .thenAnswer(
            call -> {
              int slot = call.getArgument(0);
              return slot < 0 ? null : slot < top.getSize() ? top : bottom;
            });
    when(view.convertSlot(anyInt()))
        .thenAnswer(
            call -> {
              int slot = call.getArgument(0);
              return slot < top.getSize() ? slot : slot - top.getSize();
            });
    when(view.getItem(anyInt()))
        .thenAnswer(
            call -> {
              int slot = call.getArgument(0);
              Inventory owner = view.getInventory(slot);
              return owner == null ? null : owner.getItem(view.convertSlot(slot));
            });
    doAnswer(
            call -> {
              int slot = call.getArgument(0);
              Inventory owner = view.getInventory(slot);
              if (owner != null) owner.setItem(view.convertSlot(slot), call.getArgument(1));
              return null;
            })
        .when(view)
        .setItem(anyInt(), nullable(ItemStack.class));
    return view;
  }

  public InventoryClickEvent click(Player player, int rawSlot) {
    return new InventoryClickEvent(
        player.getOpenInventory(),
        rawSlot < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER,
        rawSlot,
        ClickType.LEFT,
        InventoryAction.PICKUP_ALL);
  }

  private ItemState state(ItemStack item) {
    ItemState state = items.get(item);
    if (state == null)
      throw new IllegalArgumentException("Create GUI items inside GuiTestFixture's scope");
    return state;
  }

  private void bind(ItemStack item, ItemState state) {
    items.put(item, state);
    when(item.getType()).thenAnswer(call -> state.type);
    doAnswer(
            call -> {
              state.type = call.getArgument(0);
              return null;
            })
        .when(item)
        .setType(any());
    when(item.getAmount()).thenAnswer(call -> state.amount);
    when(item.getMaxStackSize()).thenReturn(64);
    doAnswer(
            call -> {
              state.amount = call.getArgument(0);
              return null;
            })
        .when(item)
        .setAmount(anyInt());
    when(item.getItemMeta()).thenAnswer(call -> meta(state.meta.copy(), state.type));
    when(item.setItemMeta(nullable(ItemMeta.class)))
        .thenAnswer(
            call -> {
              ItemMeta supplied = call.getArgument(0);
              if (supplied != null && !metas.containsKey(supplied))
                throw new IllegalArgumentException("Use item.getItemMeta() for GUI metadata");
              state.meta = supplied == null ? new MetaState() : metas.get(supplied).copy();
              return true;
            });
    when(item.hasItemMeta()).thenAnswer(call -> !state.meta.empty());
    when(item.isEmpty())
        .thenAnswer(
            call ->
                (state.type == Material.AIR
                        || state.type == Material.CAVE_AIR
                        || state.type == Material.VOID_AIR)
                    || state.amount <= 0);
    when(item.getEnchantments()).thenAnswer(call -> Map.copyOf(state.meta.enchantments));
    when(item.containsEnchantment(any()))
        .thenAnswer(call -> state.meta.enchantments.containsKey(call.getArgument(0)));
    when(item.getEnchantmentLevel(any()))
        .thenAnswer(call -> state.meta.enchantments.getOrDefault(call.getArgument(0), 0));
    doAnswer(
            call -> {
              state.meta.enchantments.put(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(item)
        .addUnsafeEnchantment(any(), anyInt());
    when(item.clone()).thenAnswer(call -> new ItemStack(item));
    when(item.isSimilar(nullable(ItemStack.class)))
        .thenAnswer(
            call -> {
              ItemState other = items.get((ItemStack) call.getArgument(0));
              return other != null && state.type == other.type && state.meta.same(other.meta);
            });
  }

  private ItemMeta meta(MetaState state, Material material) {
    Class<? extends ItemMeta> type =
        material.name().endsWith("_BANNER")
            ? BannerMeta.class
            : material == Material.PLAYER_HEAD
                ? SkullMeta.class
                : material == Material.WRITTEN_BOOK || material == Material.WRITABLE_BOOK
                    ? BookMeta.class
                    : ItemMeta.class;
    ItemMeta meta =
        mock(
            type,
            call ->
                switch (call.getMethod().getName()) {
                  case "getDisplayName" -> state.name == null ? "" : state.name;
                  case "hasDisplayName" -> state.name != null;
                  case "setDisplayName" -> {
                    state.name = call.getArgument(0);
                    state.display =
                        state.name == null
                            ? null
                            : LegacyComponentSerializer.legacySection().deserialize(state.name);
                    yield null;
                  }
                  case "displayName" -> {
                    if (call.getArguments().length == 0) yield state.display;
                    state.display = call.getArgument(0);
                    state.name =
                        state.display == null
                            ? null
                            : LegacyComponentSerializer.legacySection().serialize(state.display);
                    yield null;
                  }
                  case "getLore" -> state.lore == null ? null : new ArrayList<>(state.lore);
                  case "hasLore" -> state.lore != null && !state.lore.isEmpty();
                  case "setLore" -> {
                    List<String> lore = call.getArgument(0);
                    state.lore = lore == null ? null : new ArrayList<>(lore);
                    state.componentLore =
                        lore == null
                            ? null
                            : lore.stream()
                                .<Component>map(
                                    LegacyComponentSerializer.legacySection()::deserialize)
                                .toList();
                    yield null;
                  }
                  case "lore" -> {
                    if (call.getArguments().length == 0)
                      yield state.componentLore == null
                          ? null
                          : new ArrayList<>(state.componentLore);
                    List<Component> lore = call.getArgument(0);
                    state.componentLore = lore == null ? null : List.copyOf(lore);
                    state.lore =
                        lore == null
                            ? null
                            : lore.stream()
                                .map(LegacyComponentSerializer.legacySection()::serialize)
                                .toList();
                    yield null;
                  }
                  case "getPages" -> new ArrayList<>(state.pages);
                  case "getPageCount" -> state.pages.size();
                  case "hasPages" -> !state.pages.isEmpty();
                  case "getPage" -> state.pages.get((Integer) call.getArgument(0) - 1);
                  case "setPage" -> {
                    state.pages.set((Integer) call.getArgument(0) - 1, call.getArgument(1));
                    yield null;
                  }
                  case "setPages", "addPage" -> {
                    if (call.getMethod().getName().equals("setPages")) state.pages.clear();
                    Object pages = call.getRawArguments()[0];
                    state.pages.addAll(
                        pages instanceof List<?> list
                            ? (List<String>) list
                            : Arrays.asList((String[]) pages));
                    yield null;
                  }
                  case "getTitle" -> state.title;
                  case "hasTitle" -> state.title != null;
                  case "setTitle" -> {
                    state.title = call.getArgument(0);
                    yield true;
                  }
                  case "getAuthor" -> state.author;
                  case "hasAuthor" -> state.author != null;
                  case "setAuthor" -> {
                    state.author = call.getArgument(0);
                    yield null;
                  }
                  case "getOwningPlayer" -> state.owner;
                  case "hasOwner" -> state.owner != null;
                  case "setOwningPlayer" -> {
                    state.owner = call.getArgument(0);
                    yield true;
                  }
                  case "getPersistentDataContainer" -> container(state.data);
                  case "getEnchants" -> Map.copyOf(state.enchantments);
                  case "hasEnchants" -> !state.enchantments.isEmpty();
                  case "hasEnchant" -> state.enchantments.containsKey(call.getArgument(0));
                  case "getEnchantLevel" -> state.enchantments.getOrDefault(call.getArgument(0), 0);
                  case "addEnchant" -> {
                    Integer previous =
                        state.enchantments.put(call.getArgument(0), call.getArgument(1));
                    yield !Objects.equals(previous, call.getArgument(1));
                  }
                  case "removeEnchant" -> state.enchantments.remove(call.getArgument(0)) != null;
                  case "getItemFlags" -> Set.copyOf(state.flags);
                  case "hasItemFlag" -> state.flags.contains(call.getArgument(0));
                  case "addItemFlags", "removeItemFlags" -> {
                    var flags = Arrays.asList((org.bukkit.inventory.ItemFlag[]) call.getRawArguments()[0]);
                    if (call.getMethod().getName().equals("addItemFlags")) state.flags.addAll(flags);
                    else state.flags.removeAll(flags);
                    yield null;
                  }
                  case "clone" -> meta(state.copy(), material);
                  case "hasCustomModelData" -> state.model != null;
                  case "getCustomModelData" -> state.model;
                  case "setCustomModelData" -> {
                    state.model = call.getArgument(0);
                    state.modelFloats =
                        state.model == null ? List.of() : List.of(state.model.floatValue());
                    yield null;
                  }
                  case "getCustomModelDataComponent" -> modelComponent(state.modelFloats);
                  case "setCustomModelDataComponent" -> {
                    CustomModelDataComponent component = call.getArgument(0);
                    state.modelFloats =
                        component == null ? List.of() : List.copyOf(component.getFloats());
                    state.model =
                        state.modelFloats.isEmpty()
                            ? null
                            : state.modelFloats.getFirst().intValue();
                    yield null;
                  }
                  case "getPatterns" -> new ArrayList<>(state.patterns);
                  case "numberOfPatterns" -> state.patterns.size();
                  case "getPattern" -> state.patterns.get((Integer) call.getArgument(0));
                  case "addPattern" -> {
                    state.patterns.add(call.getArgument(0));
                    yield null;
                  }
                  case "removePattern" ->
                      state.patterns.remove((int) (Integer) call.getArgument(0));
                  case "setPattern" -> {
                    state.patterns.set(call.getArgument(0), call.getArgument(1));
                    yield null;
                  }
                  case "setPatterns" -> {
                    state.patterns.clear();
                    state.patterns.addAll(call.getArgument(0));
                    yield null;
                  }
                  default -> Answers.RETURNS_DEFAULTS.answer(call);
                });
    metas.put(meta, state);
    return meta;
  }

  private CustomModelDataComponent modelComponent(List<Float> initial) {
    List<Float> values = new ArrayList<>(initial);
    return mock(
        CustomModelDataComponent.class,
        call ->
            switch (call.getMethod().getName()) {
              case "getFloats" -> List.copyOf(values);
              case "setFloats" -> {
                values.clear();
                values.addAll(call.getArgument(0));
                yield null;
              }
              default -> Answers.RETURNS_DEFAULTS.answer(call);
            });
  }

  private PersistentDataContainer container(Map<NamespacedKey, StoredValue> data) {
    return mock(
        PersistentDataContainer.class,
        call ->
            switch (call.getMethod().getName()) {
              case "set" -> {
                data.put(
                    call.getArgument(0),
                    new StoredValue(call.getArgument(1), copyValue(call.getArgument(2))));
                yield null;
              }
              case "get", "getOrDefault" -> {
                StoredValue stored = data.get((NamespacedKey) call.getArgument(0));
                Object value =
                    stored != null && stored.type().equals(call.getArgument(1))
                        ? copyValue(stored.value())
                        : null;
                yield value != null
                    ? value
                    : call.getArguments().length == 3 ? call.getArgument(2) : null;
              }
              case "has" -> {
                StoredValue stored = data.get((NamespacedKey) call.getArgument(0));
                yield stored != null
                    && (call.getArguments().length == 1
                        || stored.type().equals(call.getArgument(1)));
              }
              case "getKeys" -> new java.util.HashSet<>(data.keySet());
              case "isEmpty" -> data.isEmpty();
              case "remove" -> {
                data.remove((NamespacedKey) call.getArgument(0));
                yield null;
              }
              default -> Answers.RETURNS_DEFAULTS.answer(call);
            });
  }

  private static Object copyValue(Object value) {
    if (value instanceof byte[] bytes) return bytes.clone();
    if (value instanceof int[] ints) return ints.clone();
    if (value instanceof long[] longs) return longs.clone();
    return value;
  }

  private record StoredValue(PersistentDataType<?, ?> type, Object value) {}

  private static final class MetaState {
    String name;
    Component display;
    List<String> lore;
    List<Component> componentLore;
    OfflinePlayer owner;
    String title;
    String author;
    final List<String> pages = new ArrayList<>();
    Integer model;
    final Map<NamespacedKey, StoredValue> data = new LinkedHashMap<>();
    final Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
    final Set<org.bukkit.inventory.ItemFlag> flags = new java.util.HashSet<>();
    List<Float> modelFloats = List.of();
    final List<Pattern> patterns = new ArrayList<>();

    MetaState copy() {
      MetaState copy = new MetaState();
      copy.name = name;
      copy.display = display;
      copy.lore = lore == null ? null : new ArrayList<>(lore);
      copy.componentLore = componentLore == null ? null : List.copyOf(componentLore);
      copy.owner = owner;
      copy.title = title;
      copy.author = author;
      copy.pages.addAll(pages);
      copy.model = model;
      copy.enchantments.putAll(enchantments);
      copy.flags.addAll(flags);
      copy.modelFloats = List.copyOf(modelFloats);
      copy.patterns.addAll(patterns);
      data.forEach(
          (key, value) ->
              copy.data.put(key, new StoredValue(value.type(), copyValue(value.value()))));
      return copy;
    }

    boolean same(MetaState other) {
      return Objects.equals(name, other.name)
          && Objects.equals(display, other.display)
          && Objects.equals(lore, other.lore)
          && Objects.equals(componentLore, other.componentLore)
          && Objects.equals(owner, other.owner)
          && Objects.equals(title, other.title)
          && Objects.equals(author, other.author)
          && pages.equals(other.pages)
          && Objects.equals(model, other.model)
          && enchantments.equals(other.enchantments)
          && flags.equals(other.flags)
          && modelFloats.equals(other.modelFloats)
          && patterns.equals(other.patterns)
          && data.keySet().equals(other.data.keySet())
          && data.entrySet().stream()
              .allMatch(
                  entry -> {
                    StoredValue value = other.data.get(entry.getKey());
                    return entry.getValue().type().equals(value.type())
                        && Objects.deepEquals(entry.getValue().value(), value.value());
                  });
    }

    boolean empty() {
      return name == null
          && (lore == null || lore.isEmpty())
          && model == null
          && owner == null
          && title == null
          && author == null
          && pages.isEmpty()
          && enchantments.isEmpty()
          && flags.isEmpty()
          && patterns.isEmpty()
          && data.isEmpty();
    }
  }

  private static final class ItemState {
    Material type;
    int amount = 1;
    MetaState meta = new MetaState();

    ItemState(Material type) {
      this.type = type;
    }

    ItemState copy() {
      ItemState copy = new ItemState(type);
      copy.amount = amount;
      copy.meta = meta.copy();
      return copy;
    }
  }

  @Override
  public void close() {
    try {
      itemConstruction.close();
    } finally {
      try {
        bukkit.close();
      } finally {
        SimpleFactions.plugin = previousPlugin;
      }
    }
  }
}
