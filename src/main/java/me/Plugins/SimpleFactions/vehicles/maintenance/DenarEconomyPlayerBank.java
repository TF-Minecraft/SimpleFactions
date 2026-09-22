package me.Plugins.SimpleFactions.vehicles.maintenance;

import java.util.UUID;

import net.tfminecraft.DenarEconomy.DenarEconomy;
import net.tfminecraft.DenarEconomy.Enum.Accounts;

public final class DenarEconomyPlayerBank {
    public interface PlayerBank {
        double getBankBalance(UUID playerUuid);

        boolean withdrawFromBank(UUID playerUuid, double amount);

        boolean depositToBank(UUID playerUuid, double amount);
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
        public double getBankBalance(UUID playerUuid) {
            if (playerUuid == null) {
                return 0.0;
            }
            DenarEconomy.getPlayerManager().get(playerUuid);
            return DenarEconomy.getMoneyManager().getBalance(Accounts.BANK, playerUuid);
        }

        @Override
        public boolean withdrawFromBank(UUID playerUuid, double amount) {
            return withdraw(playerUuid, amount, Accounts.BANK);
        }

        @Override
        public boolean depositToBank(UUID playerUuid, double amount) {
            if (playerUuid == null || amount <= 0.0) {
                return false;
            }
            DenarEconomy.getPlayerManager().get(playerUuid);
            DenarEconomy.getMoneyManager().changeBal(playerUuid.toString(), amount, Accounts.BANK);
            return true;
        }

        @Override
        public double getPouchBalance(UUID playerUuid) {
            if (playerUuid == null) {
                return 0.0;
            }
            DenarEconomy.getPlayerManager().get(playerUuid);
            return DenarEconomy.getMoneyManager().getBalance(Accounts.POUCH, playerUuid);
        }

        @Override
        public boolean withdrawFromPouch(UUID playerUuid, double amount) {
            return withdraw(playerUuid, amount, Accounts.POUCH);
        }

        private boolean withdraw(UUID playerUuid, double amount, Accounts account) {
            if (playerUuid == null || amount <= 0.0) {
                return false;
            }
            double balance = DenarEconomy.getMoneyManager().getBalance(account, playerUuid);
            if (balance < amount) {
                return false;
            }
            DenarEconomy.getMoneyManager().changeBal(playerUuid.toString(), -amount, account);
            return true;
        }
    }
}
