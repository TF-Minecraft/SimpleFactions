package net.tfminecraft.simplefactions.database;

import java.util.ArrayList;
import java.util.List;

public class WarbandData {
	public String id;
	public java.util.Map<java.util.UUID, net.tfminecraft.tlibs.character.CharacterRef> memberCharacters = new java.util.LinkedHashMap<>();
	public java.util.Map<java.util.UUID, net.tfminecraft.tlibs.character.CharacterRef> invitedCharacters = new java.util.LinkedHashMap<>();
	public String name;
	public String leaderId;
	public List<String> memberIds = new ArrayList<>();
	public List<String> invitedIds = new ArrayList<>();
	public boolean locked;
	public boolean faction;
	public String campaignSideId;
}
