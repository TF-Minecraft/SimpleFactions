package me.Plugins.SimpleFactions.keys;

import org.bukkit.NamespacedKey;

import me.Plugins.SimpleFactions.SimpleFactions;

public class Keys {
    public static final NamespacedKey BRANCH_ID = new NamespacedKey(SimpleFactions.plugin, "branch_id");
    public static final NamespacedKey BOOLEAN_FLAG = new NamespacedKey(SimpleFactions.plugin, "boolean_flag");
    public static final NamespacedKey STRING_KEY = new NamespacedKey(SimpleFactions.plugin, "string_key");
    public static final NamespacedKey SECONDARY_STRING_KEY = new NamespacedKey(SimpleFactions.plugin, "secondary_string_key");
    public static final NamespacedKey INT = new NamespacedKey(SimpleFactions.plugin, "int_key");
    public static final NamespacedKey LONG = new NamespacedKey(SimpleFactions.plugin, "long_key");
    /**
     * Marks a book as a mercenary contract and carries its negotiation stage. Kept
     * off {@link #INT} so a signed book is never mistaken for a loan stage.
     */
    public static final NamespacedKey CONTRACT_STAGE = new NamespacedKey(SimpleFactions.plugin, "contract_stage");
    /** The offered contract a stage 3 agreement book belongs to. */
    public static final NamespacedKey CONTRACT_ID = new NamespacedKey(SimpleFactions.plugin, "contract_id");
    /** Queue tile payload for cancel confirmation ({@code type:ownerId:detail}). */
    public static final NamespacedKey QUEUE_CANCEL = new NamespacedKey(SimpleFactions.plugin, "queue_cancel");
}
