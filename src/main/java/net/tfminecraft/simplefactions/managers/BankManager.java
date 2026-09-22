package net.tfminecraft.simplefactions.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import net.tfminecraft.simplefactions.objects.Bank;
import net.tfminecraft.simplefactions.utils.Formatter;

public class BankManager implements Listener{
	public HashMap<Player, Long> cooldown = new HashMap<>();
	public static List<Bank> banks = new ArrayList<Bank>();
	public List<Player> isWithdrawing = new ArrayList<Player>();
	Formatter format = new Formatter();
	public Boolean hasBank(Chunk c) {
		for(Bank b : banks) {
			if(b.getChunk().equals(c)) return true;
		}
		return false;
	}
}
