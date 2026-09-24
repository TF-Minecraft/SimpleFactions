package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.UUID;

import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;

public final class DenarEconomyPlayerBank {
    public interface PlayerBank {
        double getBankBalance(UUID playerUuid);

        boolean withdrawFromBank(UUID playerUuid, double amount);

        boolean depositToBank(UUID playerUuid, double amount);

        /** Account id for this name, whether or not they are logged in. */
        UUID resolve(String playerName);
    }

    public interface PlayerPouch {
        double getPouchBalance(UUID playerUuid);

        boolean withdrawFromPouch(UUID playerUuid, double amount);
    }

    public static final Impl INSTANCE = new Impl();

    private DenarEconomyPlayerBank() {}

    public static final class Impl implements PlayerBank, PlayerPouch {
        private Impl() {}

        @Override
        public UUID resolve(String playerName) {
            return OfflineModifier.playerId(playerName);
        }

        @Override
        public double getBankBalance(UUID playerUuid) {
            return OfflineModifier.balance(playerUuid, Accounts.BANK);
        }

        @Override
        public boolean withdrawFromBank(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(playerUuid, Accounts.BANK, -amount);
        }

        @Override
        public boolean depositToBank(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(playerUuid, Accounts.BANK, amount);
        }

        @Override
        public double getPouchBalance(UUID playerUuid) {
            return OfflineModifier.balance(playerUuid, Accounts.POUCH);
        }

        @Override
        public boolean withdrawFromPouch(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(playerUuid, Accounts.POUCH, -amount);
        }
    }
}
