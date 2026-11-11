package net.tfminecraft.simplefactions.war.wounds;

/** Where a force's soldiers come from, which decides who carries its wounds and how they heal. */
public enum ForceKind {
	/** A fighting faction's own regiments. */
	REGIMENTS,
	/** A subject's soldiers lent to its nearest fighting overlord. */
	LEVY,
	/** A non-fighting subject's whole militia, defending its own province. */
	MILITIA,
	/** A mercenary company on contract. Its wounds heal over time and it never loses slots. */
	MERCENARY
}
