package net.tfminecraft.simplefactions.utils;

import java.util.UUID;

import net.tfminecraft.denareconomy.accounts.OfflineModifier;
import net.tfminecraft.simplefactions.identity.Members;

/**
 * The account that money for a member reaches until the economy keys by
 * character. Member keys map to the account playing that character; any
 * other string is still looked up as a Minecraft name.
 */
public final class MemberAccounts {
    private MemberAccounts() {}

    public static UUID accountId(String member) {
        if (member == null || member.isBlank()) return null;
        if (Members.characterId(member) != null) return Members.accountId(member).orElse(null);
        return OfflineModifier.playerId(member);
    }

    /** The account behind a character ID; any other UUID is taken to be an account already. */
    public static UUID accountOf(UUID id) {
        if (id == null) return null;
        return Members.accountId(Members.key(id)).orElse(id);
    }
}
