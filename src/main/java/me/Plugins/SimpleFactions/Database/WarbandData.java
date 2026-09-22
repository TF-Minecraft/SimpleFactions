package me.Plugins.SimpleFactions.Database;

import java.util.ArrayList;
import java.util.List;

public class WarbandData {
	public String id;
	public String name;
	public String leaderId;
	public List<String> memberIds = new ArrayList<>();
	public List<String> invitedIds = new ArrayList<>();
	public boolean locked;
	public boolean faction;
	public String campaignSideId;
}
