package net.tfminecraft.simplefactions.identity.death;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A death's original rights, fixed successors, obligations and acknowledged cleanup steps. */
final class DeathJournal {
    UUID deathId;
    UUID ownerId;
    UUID characterId;
    long at;
    List<Step> steps = new ArrayList<>();
    List<Claim> claims = new ArrayList<>();
    Map<String, Map<String, Long>> payments = new LinkedHashMap<>();
    boolean complete;

    static final class Step {
        String id;
        String kind;
        JsonObject plan;
        boolean done;
        Step(String id, String kind, JsonObject plan) {
            this.id = id;
            this.kind = kind;
            this.plan = plan;
        }
    }

    static final class Claim {
        String cleanupStep;
        OwedPayment payment;
        long paid;
        Claim(String cleanupStep, OwedPayment payment) {
            this.cleanupStep = cleanupStep;
            this.payment = payment;
        }
    }
}
