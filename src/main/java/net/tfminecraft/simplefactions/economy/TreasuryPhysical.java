package net.tfminecraft.simplefactions.economy;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.TaxLeg;
import net.tfminecraft.denareconomy.api.TaxPolicy;
import net.tfminecraft.denareconomy.loaders.MessageLoader;
import net.tfminecraft.denareconomy.managers.MoneyManager;
import net.tfminecraft.simplefactions.guild.Guild;
import net.tfminecraft.simplefactions.identity.Members;
import net.tfminecraft.tlibs.character.CharacterRef;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Records physical boundaries; an interrupted delivery is retained for staff, never redelivered. */
public final class TreasuryPhysical {
    private TreasuryPhysical() {}
    public static boolean material(Player player, CharacterRef earner, ItemStack item, double gross, Guild guild) {
        var session = Members.session(player).orElse(null);
        if (!EconomyRuntime.ready() || !Denars.taxReady() || session == null || !session.ref().equals(earner)) return false;
        long cents = Money.cents(gross);
        if (cents <= 0) return false;
        TaxPolicy policy = Bukkit.getServicesManager().load(TaxPolicy.class);
        Optional<TaxLeg> tax = policy == null ? Optional.empty() : policy.tax(earner, cents);
        long withheld = tax.map(TaxLeg::cents).orElse(0L);
        if (withheld < 0 || withheld > cents) throw new IllegalArgumentException("Invalid material tax");
        String inventory = inventory(player), source = encode(item);
        int slot = player.getInventory().getHeldItemSlot();
        JsonObject snapshot = snapshot("material", player, inventory);
        snapshot.addProperty("slot", slot);
        snapshot.addProperty("beforeItem", source);
        snapshot.addProperty("afterItem", "empty");
        snapshot.addProperty("grossCents", cents);
        snapshot.addProperty("withheldCents", withheld);
        snapshot.addProperty("taxAccount", tax.map(TaxLeg::account).orElse(null));
        TreasuryActions.Plan plan = new TreasuryActions.Plan(TreasuryActions.newId(), "Material bank deposit", player, guild)
                .transfer(Denars.MINT, Denars.bank(earner), cents - withheld);
        if (withheld > 0) plan.transfer(Denars.MINT, tax.orElseThrow().account(), withheld);
        boolean deposited = plan.physical(snapshot, intent -> {
            if (!Members.isCurrent(session) || !inventory.equals(inventory(player)) || !source.equals(encode(item))
                    || slot != player.getInventory().getHeldItemSlot() || !source.equals(encode(player.getInventory().getItem(slot))))
                throw new IOException("Material source changed after posting: " + intent.id());
            item.setAmount(0);
            player.getInventory().setItem(slot, null);
            player.saveData();
        }).commit(player);
        if (deposited) {
            MessageLoader.send(player, "money.earned", "amount", MoneyManager.denars(cents - withheld));
            if (withheld > 0) MessageLoader.send(player, "money.tax", "tax", MoneyManager.denars(withheld));
        }
        return deposited;
    }
    public static boolean withdraw(Player player, Guild guild, double amount, List<ItemStack> items) {
        var session = Members.session(player).orElse(null);
        if (!EconomyRuntime.ready() || session == null) return false;
        String before = inventory(player);
        List<ItemStack> captured = items.stream().map(ItemStack::clone).toList();
        JsonObject snapshot = snapshot("force-withdraw", player, before);
        snapshot.addProperty("delivery", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(captured)));
        snapshot.addProperty("world", player.getWorld().getUID().toString());
        snapshot.addProperty("x", player.getLocation().getX());
        snapshot.addProperty("y", player.getLocation().getY());
        snapshot.addProperty("z", player.getLocation().getZ());
        snapshot.addProperty("cents", Money.cents(amount));
        return new TreasuryActions.Plan(TreasuryActions.newId(), "Staff physical treasury withdrawal", player, guild)
                .transfer(TreasuryActions.account(guild), Denars.MINT, Money.cents(amount))
                .physical(snapshot, intent -> {
                    if (!Members.isCurrent(session) || !before.equals(inventory(player)))
                        throw new IOException("Withdrawal inventory changed after posting: " + intent.id());
                    for (ItemStack item : captured) for (ItemStack leftover : player.getInventory().addItem(item).values())
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                    player.saveData();
                }).commit(player);
    }
    private static JsonObject snapshot(String kind, Player player, String before) {
        JsonObject data = new JsonObject();
        data.addProperty("kind", kind);
        data.addProperty("account", player.getUniqueId().toString());
        data.addProperty("inventoryBefore", before);
        return data;
    }
    private static String inventory(Player player) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
    }
    private static String encode(ItemStack item) {
        return item == null || item.getType().isAir() ? "empty" : Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
}
