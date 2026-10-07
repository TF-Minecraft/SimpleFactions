package net.tfminecraft.simplefactions.keys;

import org.bukkit.NamespacedKey;

import net.tfminecraft.simplefactions.SimpleFactions;

public class Keys {
    private Keys() {}

    public static final NamespacedKey BRANCH_ID = new NamespacedKey(SimpleFactions.plugin, "branch_id");
    /** Latest async branch income preview written onto an upgrade or downgrade button. */
    public static final NamespacedKey BRANCH_PREVIEW = new NamespacedKey(SimpleFactions.plugin, "branch_preview");
    /** Latest async law, tax, favour, or treaty income preview. */
    public static final NamespacedKey ECONOMY_PREVIEW = new NamespacedKey(SimpleFactions.plugin, "economy_preview");
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
    /** The one-time id of a stage 3 loan agreement book; the accepted loan takes it. */
    public static final NamespacedKey LOAN_OFFER = new NamespacedKey(SimpleFactions.plugin, "loan_offer");
    /** Queue tile payload for cancel confirmation ({@code type:ownerId:detail}). */
    public static final NamespacedKey QUEUE_CANCEL = new NamespacedKey(SimpleFactions.plugin, "queue_cancel");
}
