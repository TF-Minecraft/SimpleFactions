package net.tfminecraft.simplefactions.managers.holder;

/** Marks a goal picker as choosing a war's second demand rather than declaring a war. */
public record WarDemandDraft(int warId, boolean attackerSide) {}
