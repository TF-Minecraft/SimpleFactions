package me.Plugins.SimpleFactions.enums;

public enum Rules {
    CAN_HAVE_VASSALS("Can Have Vassals", true),
    CAN_MAKE_FEDERATION("Can Form Federation", false),
    LEADER_CAN_BE_ON_COUNCIL("Leader can be Councilmember", true),
    LEADER_ELECTIONS("Leader Elections", false), //Implemented
    HAS_COUNCIL("Has Council", false), //Implemented
    APPOINTED_COUNCIL("Leader Appoints Council", false), //Implemented
    WEALTH_BASED_COUNCIL("Wealth-Based Council", false), //Implemented
    ELECTED_COUNCIL("Elected Council", false), //Implemented
    CITIZEN_TAX("Can Collect Citizen Taxes", true), //Implemented
    VASSAL_TAX("Can Collect Vassal Taxes", true), //Implemented
    GUILD_TAX("Can Collect Guild Taxes", true), //Implemented
    DIVIDEND_TAX("Can Collect Dividend Taxes", true), //Implemented
    TARIFFS("Can Impose Tariffs", true), //Implemented
    VASSAL_VOTING_RIGHTS("Vassals Have Voting Rights", true), //Implemented
    CAN_FAVOUR("Can Favour Guilds and Vassals", true),
    CAN_REPRESS("Can Repress Guilds and Vassals", true),
    CLOSED_BORDERS("Leader Invites New Members", false), //Implemented
    OPEN_BORDERS("Guilds Can Invite New Members", false), //Implemented
    CAN_RECRUIT_PROFESSIONAL_ARMY("Can Recruit Professional Army", true),
    NO_COUNCIL("No Council", false); //Implemented

    private final String display;
    private final boolean absentTrue;

    Rules(String display, boolean absentTrue) {
        this.display = display;
        this.absentTrue = absentTrue;
    }

    public String getDisplay() {
        return display;
    }

    public boolean trueIfAbsent() {
        return absentTrue;
    }
}

