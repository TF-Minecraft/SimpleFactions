package net.tfminecraft.simplefactions.war.wounds;

/**
 * One regiment's share of a force. Law slots are granted by laws and are full again after every
 * battle; paid slots carry their wounds until the war ends.
 */
public record ForcePart(String regimentId, boolean militia, int lawSlots, int paidSlots) {
	public ForcePart {
		lawSlots = Math.max(0, lawSlots);
		paidSlots = Math.max(0, paidSlots);
	}

	public int slots() {
		return (int) Math.min(Integer.MAX_VALUE, (long) lawSlots + paidSlots);
	}
}
