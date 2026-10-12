package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.UUID;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.simplefactions.economy.Money;
import net.tfminecraft.simplefactions.identity.Members;

/** Read-only projections. Every payment is posted through a durable vehicle intent. */
public final class DenarEconomyPlayerBank {
    public interface PlayerBank {
        double getBankBalance(UUID character);
        UUID resolve(String member);
    }
    public interface PlayerPouch { double getPouchBalance(UUID character); }
    public static final Impl INSTANCE = new Impl();
    private DenarEconomyPlayerBank() {}

    public static final class Impl implements PlayerBank, PlayerPouch {
        private Impl() {}
        @Override public UUID resolve(String member) {
            String key = Members.characterId(member) != null ? member : Members.ofAccountName(member);
            return Members.ref(key).map(ref -> ref.characterId()).orElse(null);
        }
        @Override public double getBankBalance(UUID character) {
            return Members.ref(Members.key(character)).map(ref -> Money.denars(Denars.balance(Denars.bank(ref)))).orElse(0.0);
        }
        @Override public double getPouchBalance(UUID character) {
            return Members.ref(Members.key(character)).map(ref -> Money.denars(Denars.balance(Denars.pouch(ref)))).orElse(0.0);
        }
    }
}
