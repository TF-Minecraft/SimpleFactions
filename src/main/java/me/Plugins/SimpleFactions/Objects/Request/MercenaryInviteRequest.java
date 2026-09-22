package me.Plugins.SimpleFactions.Objects.Request;

import me.Plugins.SimpleFactions.mercenary.company.MercenaryCompany;

/** An offer of a company slot, held against the invited player. */
public class MercenaryInviteRequest extends Request {
    private static final long EXPIRY_MILLIS = 60000;

    private final MercenaryCompany company;

    public MercenaryInviteRequest(MercenaryCompany company) {
        super(company.getGuild());
        this.company = company;
        this.time = System.currentTimeMillis() + EXPIRY_MILLIS;
    }

    public MercenaryCompany getCompany() {
        return company;
    }
}
