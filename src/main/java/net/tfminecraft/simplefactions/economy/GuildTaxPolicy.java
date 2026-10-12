package net.tfminecraft.simplefactions.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import net.tfminecraft.denareconomy.api.Denars;
import net.tfminecraft.denareconomy.api.TaxLeg;
import net.tfminecraft.denareconomy.api.TaxPolicy;
import net.tfminecraft.simplefactions.api.SimpleFactionsApi;
import net.tfminecraft.simplefactions.government.proposal.TaxTarget;
import net.tfminecraft.simplefactions.managers.FactionManager;
import net.tfminecraft.simplefactions.objects.Faction;
import net.tfminecraft.tlibs.character.CharacterRef;

/** Taxes are withheld inside the earner's posting and held until the realm-wide settlement. */
public final class GuildTaxPolicy implements TaxPolicy {
    @Override
    public Optional<TaxLeg> tax(CharacterRef earner, long grossCents) {
        if (!SimpleFactionsApi.ready()) throw new IllegalStateException("Factions are starting up");
        Faction realm = FactionManager.getByMember(earner.characterId());
        if (realm == null || realm.getBank() == null || grossCents <= 0) return Optional.empty();
        double rate = realm.getTaxRate(TaxTarget.CITIZENS, null, true);
        if (!Double.isFinite(rate) || rate < 0 || rate > 100) throw new IllegalStateException("Invalid citizen tax: " + rate);
        long cents = BigDecimal.valueOf(grossCents).multiply(BigDecimal.valueOf(rate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        return Optional.of(new TaxLeg(Denars.guildTax(realm.getOrCreateMainGuild().getIncarnationId()), cents));
    }
}
