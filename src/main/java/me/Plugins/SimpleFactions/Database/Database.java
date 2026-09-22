package me.Plugins.SimpleFactions.Database;


import me.Plugins.SimpleFactions.War.battle.engine.core.Battle;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import me.Plugins.SimpleFactions.Army.Military;
import me.Plugins.SimpleFactions.Army.MilitaryExpansion;
import me.Plugins.SimpleFactions.Army.Regiment;
import me.Plugins.SimpleFactions.Guild.Branch.Branch;
import me.Plugins.SimpleFactions.Guild.loans.Loan;
import me.Plugins.SimpleFactions.Guild.upgrade.Upgrade;
import me.Plugins.SimpleFactions.Guild.upgrade.UpgradeExpansion;
import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Loaders.BranchLoader;
import me.Plugins.SimpleFactions.Loaders.RankLoader;
import me.Plugins.SimpleFactions.Loaders.TitleLoader;
import me.Plugins.SimpleFactions.Loaders.UpgradeLoader;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Managers.LogManager;
import me.Plugins.SimpleFactions.Managers.RelationManager;
import me.Plugins.SimpleFactions.Objects.Bank;
import me.Plugins.SimpleFactions.Objects.Faction;
import me.Plugins.SimpleFactions.Objects.Modifier;
import me.Plugins.SimpleFactions.Objects.PrestigeRank;
import me.Plugins.SimpleFactions.Tiers.Title;
import me.Plugins.SimpleFactions.War.core.War;
import me.Plugins.SimpleFactions.War.core.WarMapper;
import me.Plugins.SimpleFactions.War.resolution.WarReparationsObligation;
import me.Plugins.SimpleFactions.enums.Stance;
import me.Plugins.SimpleFactions.government.StabilityModifier;
import me.Plugins.SimpleFactions.laws.Law;
import me.Plugins.SimpleFactions.laws.LawGroup;

public class Database {

	public int getTimer() {
		try {
			File file = new File("plugins/SimpleFactions/Cache", "data.json");
			if (!file.exists()) return 0;

			TimerData data = JsonUtil.readJson(file, TimerData.class);
			return data != null ? data.time : 0;

		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	public int getDay() {
		try {
			File file = new File("plugins/SimpleFactions/Cache", "data.json");
			if (!file.exists()) return 0;

			TimerData data = JsonUtil.readJson(file, TimerData.class);
			return data != null ? data.day : 0;

		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	public void saveTimer(int time, int day) {
		try {
			File folder = new File("plugins/SimpleFactions/Cache");
			if (!folder.exists()) folder.mkdirs();

			File file = new File(folder, "data.json");

			TimerData data = new TimerData();
			data.time = time;
			data.day = day;

			JsonUtil.writeJson(file, data);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    /* =====================================================
     * FACTIONS
     * ===================================================== */

    public void loadFactions() {
        Bukkit.getLogger().info("[SimpleFactions] Loading factions (Gson)");

        File folder = new File("plugins/SimpleFactions/Data");
        if (!folder.exists()) folder.mkdirs();

        // Suppressed for the whole load: each faction and guild calls updateWealth, which
        // cascades into a full-server prestige pass over a partial list. FactionManager.run
        // recomputes once everything is in.
        FactionManager.loading = true;
        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (!file.getName().endsWith(".json")) continue;

            try {
                FactionData data = JsonUtil.readJson(file, FactionData.class);
                if (data == null || data.id == null) continue;

                // --- Provinces ---
                List<Integer> provinces = new ArrayList<>();
                for (Number n : data.provinces) provinces.add(n.intValue());

                // --- Titles ---
                List<Title> titles = new ArrayList<>();
                for (String tid : data.titles) {
                    Title t = TitleLoader.getById(tid);
                    if (t != null) titles.add(t);
                }

                int capital = data.capital != null ? data.capital : -1;
                int extraCap = data.extraNodeCapacity != null ? data.extraNodeCapacity.intValue() : 0;

                double citizenTax = data.citizenTax != null ? data.citizenTax : 5.0;
                double guildTax = data.guildTax != null ? data.guildTax : 5.0;
                double vassalTax = data.vassalTax != null ? data.vassalTax : 5.0;
                double dividendTax = data.dividendTax != null ? data.dividendTax : 5.0;
                double tariffs = data.tariffs != null ? data.tariffs : 5.0;
                
                // Deep copy specific taxes
                HashMap<String, HashMap<String, Double>> specificTaxes = new HashMap<>();
                if (data.specificTaxes != null) {
                    for (HashMap.Entry<String, HashMap<String, Double>> entry : data.specificTaxes.entrySet()) {
                        if (entry.getValue() != null) {
                            specificTaxes.put(entry.getKey(), new HashMap<>(entry.getValue()));
                        }
                    }
                }

                Faction f = new Faction(
                        data.id,
                        data.rgb,
                        provinces,
                        titles,
                        data.leader,
                        data.name,
                        data.rulerTitle,
                        data.banner,
                        data.government,
                        data.culture,
                        data.religion,
                        extraCap,
                        loadModifiers(data.prestigeModifiers),
                        citizenTax,
                        guildTax,
                        vassalTax,
                        dividendTax,
                        tariffs,
                        specificTaxes,
                        capital,
                        data.laws,
                        data.governmentData
                );

                // --- Rank / founding ---
                // Rank is derived state that only climbs one level per updatePrestige, so it
                // has to be restored rather than re-derived from a cold ladder on every boot.
                if (data.rank != null) {
                    PrestigeRank restored = RankLoader.getByString(data.rank);
                    if (restored != null) f.setRank(restored);
                }
                f.setFoundedAt(data.foundedAt != null ? data.foundedAt : System.currentTimeMillis()/1000L);

                if (data.settlements != null) {
                    f.getSettlementHandler().load(data.settlements);
                }

                if (data.installations != null) {
                    f.getInstallationHandler().load(data.installations);
                }

                if (data.installationQueue != null) {
                    f.getInstallationHandler().loadConstruction(data.installationQueue);
                }

                // --- Relations ---
                if (data.relations == null) {
                    LogManager.relations("JSON %s relations=null", f.getId());
                } else {
                    LogManager.relations("JSON %s relations=%s", f.getId(), data.relations);
                    for (String r : data.relations) {
                        FactionManager.addDBRelation(f, r);
                    }
                }

                if(data.tradeRelations != null) {
                    for (String r : data.tradeRelations) {
                        FactionManager.addDBTradeRelation(f, r);
                    }
                }

                if(data.treatyRelations != null) {
                    for (String r : data.treatyRelations) {
                        FactionManager.addDBTreatyRelation(f, r);
                    }
                }

                // --- Tier ---
                if (data.tierIndex != null) {
                    f.getTier().setIndex(data.tierIndex.intValue());
                }

                // --- Military ---
                Military m = f.getMilitary();
                for (String s : data.military) {
                    String[] split = s.split("\\.");
                    m.getRegiment(split[0]).setCurrentSlots(Integer.parseInt(split[1]));
                }

                for (String s : data.militaryQueue) {
                    String[] split = s.split("\\.");
                    m.addQueueItem(m.getRegiment(split[0]), Integer.parseInt(split[1]));
                }

                // --- Guild ---
                if (data.guilds != null) {
                    for (GuildData gd : data.guilds) {

                        if(gd.loans != null) {
                            for (LoanData ld : gd.loans) {
                                FactionManager.addDBLoan(ld);
                            }
                        }

                        Guild g = new Guild(gd, f);

                        // --- Bank ---
                        if ("true".equalsIgnoreCase(gd.bank)) {
                            Chunk c = Bukkit.getWorld(gd.world)
                                    .getChunkAt(gd.xPos.intValue(), gd.zPos.intValue());
                            g.setBank(new Bank(g, gd.balance != null ? gd.balance : 0.0, c));
                            g.updateWealth();
                        }

                        // --- Upgrade Queue ---
                        if (gd.upgradeQueue != null) {
                            for (UpgradeExpansionData ued : gd.upgradeQueue) {
                                Upgrade upgrade = g.getUpgrade(ued.upgrade);
                                if (upgrade != null) {
                                    g.addQueuedUpgrade(upgrade, ued.timeLeft);
                                }
                            }
                        }

                        f.getGuildHandler().addGuild(g);
                    }
                }

                if (data.warReparationsObligations != null) {
                    for (WarReparationsObligationData obligationData : data.warReparationsObligations) {
                        if (obligationData == null
                                || obligationData.payeeFactionId == null
                                || obligationData.payeeFactionId.isBlank()) {
                            continue;
                        }
                        double percent = obligationData.incomePercent != null ? obligationData.incomePercent : 0.0;
                        int days = obligationData.daysRemaining != null ? obligationData.daysRemaining : 0;
                        if (percent <= 0 || days <= 0) {
                            continue;
                        }
                        f.addWarReparationsObligation(new WarReparationsObligation(
                                obligationData.payeeFactionId, percent, days));
                    }
                }

                FactionManager.factions.add(f);
                f.updateWealth();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FactionManager.loading = false;
    }

    /* =====================================================
     * SAVE
     * ===================================================== */

    public void saveFaction(Faction f) {
        try {
            File file = new File("plugins/SimpleFactions/Data", f.getId() + ".json");

            FactionData data = new FactionData();
            data.id = f.getId();
            data.name = f.getName();
            data.rgb = f.getRGB();
            data.leader = f.getLeader();
            data.rulerTitle = f.getRulerTitle();
            data.government = f.getGovernmentString();
            data.culture = f.getCulture();
            data.religion = f.getReligion();

            data.citizenTax = f.getTaxHandler().getCitizenTax();
            data.guildTax = f.getTaxHandler().getGuildTax();
            data.vassalTax = f.getTaxHandler().getVassalTax();
            data.dividendTax = f.getTaxHandler().getDividendTax();
            data.tariffs = f.getTaxHandler().getTariffs();
            data.specificTaxes = serializeSpecificTaxes(f.getTaxHandler());
            data.capital = f.getCapital();
            data.extraNodeCapacity = (double) f.getExtraNodeCapacity();

            data.banner = new ArrayList<>(f.getBannerPatterns());
            
            // --- Government ---
            data.governmentData = f.getGovernment().serialize();

            data.settlements = f.getSettlementHandler().serialize();
            data.installations = f.getInstallationHandler().serialize();
            data.installationQueue = f.getInstallationHandler().serializeConstruction();

            for (int p : f.getProvinces()) data.provinces.add(p);
            for (Title t : f.getTitles()) data.titles.add(t.getId());

            // --- Military ---
            for (Regiment r : f.getMilitary().getRegiments()) {
                if (!r.isLevy()) {
                    data.military.add(r.getId() + "." + r.getCurrentSlots());
                }
            }

            for (MilitaryExpansion e : f.getMilitary().getQueue()) {
                data.militaryQueue.add(e.getRegiment().getId() + "." + e.getTimeLeft());
            }

            // --- Relations ---
            f.getRelations().forEach((id, rel) ->
                    data.relations.add(id + "(" + rel.getType().getId() + "."
                            + rel.getAttitude().getId() + "." + rel.getOpinion() + ")"));
            LogManager.relations("SAVE %s relations=%s", f.getId(), data.relations);
            // --- Trade Relations ---
            f.getDiplomacyHandler().getTradeRelations().forEach((id, rel) ->
                    data.tradeRelations.add(id + "(" + rel.getId() + ")"));
            f.getDiplomacyHandler().getTreatyRelations().forEach((id, rel) -> {
                if (rel != null && rel.isTreaty() && !rel.isClearTreaty()) {
                    data.treatyRelations.add(id + "(" + rel.getId() + ")");
                }
            });

            // --- Modifiers ---
            f.getPrestigeModifiers().forEach(m -> {
                if(m.isPersistent())
                    data.prestigeModifiers.add(m.getType() + "(" + m.getAmount() + ")");
            });

            // --- Rank / founding ---
            data.rank = f.getRank() != null ? f.getRank().getId() : null;
            data.foundedAt = f.getFoundedAt();

            // --- Guild ---
            for (Guild g : f.getGuildHandler().getGuilds()) {

                GuildData gd = new GuildData();
                gd.id = g.getId();
                gd.name = g.getOwnName();
                gd.leader = g.getLeader();
                gd.rgb = g.getRGB();
                gd.type = g.getType().getId();
                gd.capital = g.getCapital();
                gd.members = new ArrayList<>(g.getMembers());
                gd.banner = new ArrayList<>(g.getBannerPatterns());
                gd.stance = g.isBase() ? g.getFaction().getOverlord() != null ? 
                            g.getStance(g.getFaction().getOverlord()).name() : 
                            g.getStance(g.getFaction()).name() : 
                            g.getStance(g.getFaction()).name();
                gd.creditScore = g.getLoanHandler().getCreditScore();
                gd.repressed = g.isRepressed();
                gd.favoured = g.isFavoured();
                gd.dividendPercent = g.getDividendPercent();
                gd.dividendEligible = g.getDividendEligibleSnapshot();
                gd.casinoProfit = g.getLedger().getCasinoProfit();
                Map<String, Double> citizenTaxes = g.getLedger().getCitizenTaxesCopy();
                gd.citizenTaxes = citizenTaxes.isEmpty() ? null : citizenTaxes;
                gd.company = g.getCompany() != null ? g.getCompany().serialize() : null;

                // --- Bank ---
                if (g.getBank() != null) {
                    Bank b = g.getBank();
                    gd.bank = "true";
                    gd.world = b.getChunk().getWorld().getName();
                    gd.xPos = (double) b.getChunk().getX();
                    gd.zPos = (double) b.getChunk().getZ();
                    gd.balance = b.getWealth();
                } else {
                    gd.bank = "false";
                }
                // --- Modifiers ---
				g.getWealthModifiers().forEach(m -> {
                    if(m.isPersistent())
                        gd.wealthModifiers.add(m.getType() + "(" + m.getAmount() + ")");
                });

                gd.pillageHits = new ArrayList<>();
                if (g.getPillageHits() != null) {
                    for (StabilityModifier modifier : g.getPillageHits()) {
                        if (modifier == null) {
                            continue;
                        }
                        StabilityModifierData modifierData = new StabilityModifierData();
                        modifierData.name = modifier.getName();
                        modifierData.modifier = modifier.getModifier();
                        modifierData.decay = modifier.getDecay();
                        gd.pillageHits.add(modifierData);
                    }
                }

                for (Map.Entry<Integer, Branch> e : g.getBranches().entrySet()) {
                    Branch b = e.getValue();
                    GuildBranchData bd = new GuildBranchData();
                    bd.id = b.getId();
                    bd.level = b.getLevel();
                    gd.branches.add(bd);
                }

                for (Upgrade u : g.getUpgrades()) {
                    GuildBranchData bd = new GuildBranchData();
                    bd.id = u.getId();
                    bd.level = u.getLevel();
                    gd.upgrades.add(bd);
                }

                for (UpgradeExpansion e : g.getUpgradeQueue()) {
                    UpgradeExpansionData ued = new UpgradeExpansionData();
                    ued.upgrade = e.getUpgrade().getId();
                    ued.timeLeft = e.getTimeLeft();
                    gd.upgradeQueue.add(ued);
                }

                for(Loan loan : g.getLoanHandler().getLoansGiven()) {
                    gd.loans.add(new LoanData(loan));
                }

                data.guilds.add(gd);
            }


            // --- Laws ---
            for (LawGroup group : f.getLawHandler().getGroupList()) {
                Law current = group.getCurrent();
                if (current != null) {
                    data.laws.add(group.getId() + ":" + current.getId());
                }
            }

            data.overlord = RelationManager.getOverlord(f);
            data.tierIndex = (double) f.getTier().getIndex();

            data.warReparationsObligations = new ArrayList<>();
            for (WarReparationsObligation obligation : f.getWarReparationsObligations()) {
                if (obligation == null || !obligation.isActive()) {
                    continue;
                }
                WarReparationsObligationData obligationData = new WarReparationsObligationData();
                obligationData.payeeFactionId = obligation.getPayeeFactionId();
                obligationData.incomePercent = obligation.getIncomePercent();
                obligationData.daysRemaining = obligation.getDaysRemaining();
                data.warReparationsObligations.add(obligationData);
            }

            JsonUtil.writeJson(file, data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /* =====================================================
     * HELPERS
     * ===================================================== */

    public static List<Modifier> loadModifiers(List<String> raw) {
        List<Modifier> list = new ArrayList<>();
        for (String s : raw) {
            String type = s.substring(0, s.indexOf("("));
            double amt = Double.parseDouble(s.substring(s.indexOf("(") + 1, s.indexOf(")")));
            list.add(new Modifier(type, amt, true));
        }
        return list;
    }

    private HashMap<String, HashMap<String, Double>> serializeSpecificTaxes(me.Plugins.SimpleFactions.Objects.Handler.TaxHandler taxHandler) {
        HashMap<String, HashMap<String, Double>> result = new HashMap<>();
        
        HashMap<me.Plugins.SimpleFactions.government.proposal.TaxTarget, HashMap<String, Double>> specificTaxes = taxHandler.getSpecificTaxes();
        for (Map.Entry<me.Plugins.SimpleFactions.government.proposal.TaxTarget, HashMap<String, Double>> entry : specificTaxes.entrySet()) {
            result.put(entry.getKey().name(), new HashMap<>(entry.getValue()));
        }
        
        return result;
    }

    public void deleteFaction(Faction f) {
        File file = new File("plugins/SimpleFactions/Data", f.getId() + ".json");
        if (file.exists()) file.delete();
    }

	public void saveWar(War war) {
		try {
			File folder = new File("plugins/SimpleFactions/Wars");
			if (!folder.exists()) folder.mkdirs();

			File file = new File(folder, "war_" + war.getId() + ".json");
			WarData data = WarMapper.toData(war);
			JsonUtil.writeJson(file, data);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public List<War> loadWars() {
		List<War> wars = new ArrayList<>();
		File folder = new File("plugins/SimpleFactions/Wars");

		if (!folder.exists() || !folder.isDirectory()) return wars;

		File[] files = folder.listFiles();
		if (files == null) return wars;

		for (File file : files) {
			if (!file.getName().endsWith(".json")) continue;

			try {
				WarData data = JsonUtil.readJson(file, WarData.class);
				if (data == null) continue;

				War war = WarMapper.fromData(data);
				if (war != null) {
					wars.add(war);
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return wars;
	}

	public void deleteWar(War war) {
		File file = new File("plugins/SimpleFactions/Wars", "war_" + war.getId() + ".json");
		if (file.exists()) file.delete();
	}

	public void saveBattle(me.Plugins.SimpleFactions.War.battle.engine.core.Battle battle) {
		if (battle == null) {
			return;
		}
		try {
			File folder = new File("plugins/SimpleFactions/Battles");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			File file = new File(folder, "battle_" + battle.getId() + ".json");
			me.Plugins.SimpleFactions.Database.BattleData data =
					me.Plugins.SimpleFactions.War.battle.persistence.BattleMapper.toData(battle);
			JsonUtil.writeJson(file, data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void saveWarband(me.Plugins.SimpleFactions.War.battle.warband.Warband warband) {
		if (warband == null) {
			return;
		}
		try {
			File folder = new File("plugins/SimpleFactions/Warbands");
			if (!folder.exists()) {
				folder.mkdirs();
			}
			File file = new File(folder, "warband_" + warband.getId() + ".json");
			me.Plugins.SimpleFactions.Database.WarbandData data =
					me.Plugins.SimpleFactions.War.battle.persistence.WarbandMapper.toData(warband);
			JsonUtil.writeJson(file, data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void deleteBattleFile(String battleId) {
		if (battleId == null) {
			return;
		}
		File file = new File("plugins/SimpleFactions/Battles", "battle_" + battleId + ".json");
		if (file.exists()) {
			file.delete();
		}
	}

	public void deleteWarbandFile(String warbandId) {
		if (warbandId == null) {
			return;
		}
		File file = new File("plugins/SimpleFactions/Warbands", "warband_" + warbandId + ".json");
		if (file.exists()) {
			file.delete();
		}
	}

	public java.util.List<me.Plugins.SimpleFactions.War.battle.warband.Warband> loadWarbands() {
		java.util.List<me.Plugins.SimpleFactions.War.battle.warband.Warband> warbands = new ArrayList<>();
		File folder = new File("plugins/SimpleFactions/Warbands");
		if (!folder.exists() || !folder.isDirectory()) {
			return warbands;
		}
		File[] files = folder.listFiles();
		if (files == null) {
			return warbands;
		}
		for (File file : files) {
			if (!file.getName().endsWith(".json")) {
				continue;
			}
			try {
				me.Plugins.SimpleFactions.Database.WarbandData data =
						JsonUtil.readJson(file, me.Plugins.SimpleFactions.Database.WarbandData.class);
				me.Plugins.SimpleFactions.War.battle.warband.Warband warband =
						me.Plugins.SimpleFactions.War.battle.persistence.WarbandMapper.fromData(data);
				if (warband != null) {
					warbands.add(warband);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return warbands;
	}

	public java.util.List<me.Plugins.SimpleFactions.War.battle.engine.core.Battle> loadBattles() {
		java.util.List<me.Plugins.SimpleFactions.War.battle.engine.core.Battle> loaded = new ArrayList<>();
		File folder = new File("plugins/SimpleFactions/Battles");
		if (!folder.exists() || !folder.isDirectory()) {
			return loaded;
		}
		File[] files = folder.listFiles();
		if (files == null) {
			return loaded;
		}
		for (File file : files) {
			if (!file.getName().endsWith(".json")) {
				continue;
			}
			try {
				me.Plugins.SimpleFactions.Database.BattleData data =
						JsonUtil.readJson(file, me.Plugins.SimpleFactions.Database.BattleData.class);
				me.Plugins.SimpleFactions.War.battle.engine.core.Battle battle =
						me.Plugins.SimpleFactions.War.battle.persistence.BattleMapper.fromData(data);
				if (battle != null) {
					loaded.add(battle);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return loaded;
	}
}
