package net.tfminecraft.simplefactions.vehicles.maintenance;

import java.util.UUID;

import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.denareconomy.enums.Accounts;
import net.tfminecraft.simplefactions.utils.MemberAccounts;

public final class DenarEconomyPlayerBank {
    /** Payer IDs may be character IDs (vehicle registry and fees); money reaches their account. */
    public interface PlayerBank {
        double getBankBalance(UUID playerUuid);

        boolean withdrawFromBank(UUID playerUuid, double amount);

        boolean depositToBank(UUID playerUuid, double amount);

        /** Account id for this member key (or legacy name), whether or not they are logged in. */
        UUID resolve(String member);
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
        public UUID resolve(String member) {
            return MemberAccounts.accountId(member);
        }

        @Override
        public double getBankBalance(UUID playerUuid) {
            return OfflineModifier.balance(MemberAccounts.accountOf(playerUuid), Accounts.BANK);
        }

        @Override
        public boolean withdrawFromBank(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(MemberAccounts.accountOf(playerUuid), Accounts.BANK, -amount);
        }

        @Override
        public boolean depositToBank(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(MemberAccounts.accountOf(playerUuid), Accounts.BANK, amount);
        }

        @Override
        public double getPouchBalance(UUID playerUuid) {
            return OfflineModifier.balance(MemberAccounts.accountOf(playerUuid), Accounts.POUCH);
        }

        @Override
        public boolean withdrawFromPouch(UUID playerUuid, double amount) {
            return amount > 0.0 && OfflineModifier.apply(MemberAccounts.accountOf(playerUuid), Accounts.POUCH, -amount);
        }
    }
}
