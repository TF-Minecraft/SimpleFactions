package me.Plugins.SimpleFactions.Objects;

public class Modifier {
	String type;
	Double amount;
	private boolean persistent = false;
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Double getAmount() {
		return amount;
	}
	public void setAmount(Double amount) {
		this.amount = amount;
	}
	public boolean isPersistent() { return persistent; }
	public Modifier(String t, Double a, boolean persistent) {
		this.type = t;
		this.amount = a;
		this.persistent = persistent;
	}
}
