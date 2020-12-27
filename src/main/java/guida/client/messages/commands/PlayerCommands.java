/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 * The following is the original copyright notice:
 *
 *     This file is part of the OdinMS Maple Story Server
 *     Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 *                        Matthias Butz <matze@odinms.de>
 *                        Jan Christian Meyer <vimes@odinms.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.client.messages.commands;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleStat;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.net.channel.ChannelServer;
import guida.scripting.npc.NPCScriptManager;
import guida.server.MapleAchievements;
import guida.server.MapleItemInformationProvider;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleMonsterInformationProvider;
import guida.server.maps.MapleMapObject;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.ReadableMillisecondFormat;
import guida.tools.StringUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerCommands implements Command {
    private final Map<String, ArrayList<String>> whoDropsCache = new HashMap<>();

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        MapleCharacter player = c.getPlayer();
        if (splitted[0].equalsIgnoreCase("@str")) {
            int up = 0;
            try {
                up = Integer.parseInt(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (player.getRemainingAp() <= 0) {
                mc.dropMessage("You do not have any AP left to use.");
            } else if (up < 1 || player.getRemainingAp() < up) {
                mc.dropMessage("Please enter a valid amount of AP you wish to add to STR. You have " + player.getRemainingAp() + " AP left.");
            } else if (player.getStr() == 999) {
                mc.dropMessage("You have reached the maximum amount of points on this stat.");
            } else if (player.getStr() + up > 999) {
                mc.dropMessage("You are limited to having 999 points on each stat and still allowed to add " + (999 - player.getStr()) + " more AP.");
            } else {
                player.setStr(player.getStr() + up);
                player.setRemainingAp(player.getRemainingAp() - up);
                player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
                player.updateSingleStat(MapleStat.STR, player.getStr());
            }
        } else if (splitted[0].equalsIgnoreCase("@dex")) {
            int up = 0;
            try {
                up = Integer.parseInt(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (player.getRemainingAp() <= 0) {
                mc.dropMessage("You do not have any AP left to use.");
            } else if (up < 1 || player.getRemainingAp() < up) {
                mc.dropMessage("Please enter a valid amount of AP you wish to add to DEX. You have " + player.getRemainingAp() + " AP left.");
            } else if (player.getDex() == 999) {
                mc.dropMessage("You have reached the maximum amount of points on this stat.");
            } else if (player.getDex() + up > 999) {
                mc.dropMessage("You are limited to having 999 points on each stat and still allowed to add " + (999 - player.getDex()) + " more AP.");
            } else {
                player.setDex(player.getDex() + up);
                player.setRemainingAp(player.getRemainingAp() - up);
                player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
                player.updateSingleStat(MapleStat.DEX, player.getDex());
            }
        } else if (splitted[0].equalsIgnoreCase("@int")) {
            int up = 0;
            try {
                up = Integer.parseInt(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (player.getRemainingAp() <= 0) {
                mc.dropMessage("You do not have any AP left to use.");
            } else if (up < 1 || player.getRemainingAp() < up) {
                mc.dropMessage("Please enter a valid amount of AP you wish to add to INT. You have " + player.getRemainingAp() + " AP left.");
            } else if (player.getInt() == 999) {
                mc.dropMessage("You have reached the maximum amount of points on this stat.");
            } else if (player.getInt() + up > 999) {
                mc.dropMessage("You are limited to having 999 points on each stat and still allowed to add " + (999 - player.getInt()) + " more AP.");
            } else {
                player.setInt(player.getInt() + up);
                player.setRemainingAp(player.getRemainingAp() - up);
                player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
                player.updateSingleStat(MapleStat.INT, player.getInt());
            }
        } else if (splitted[0].equalsIgnoreCase("@luk")) {
            int up = 0;
            try {
                up = Integer.parseInt(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (player.getRemainingAp() <= 0) {
                mc.dropMessage("You do not have any AP left to use.");
            } else if (up < 1 || player.getRemainingAp() < up) {
                mc.dropMessage("Please enter a valid amount of AP you wish to add to LUK. You have " + player.getRemainingAp() + " AP left.");
            } else if (player.getLuk() == 999) {
                mc.dropMessage("You have reached the maximum amount of points on this stat.");
            } else if (player.getLuk() + up > 999) {
                mc.dropMessage("You are limited to having 999 points on each stat and still allowed to add " + (999 - player.getLuk()) + " more AP.");
            } else {
                player.setLuk(player.getLuk() + up);
                player.setRemainingAp(player.getRemainingAp() - up);
                player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
                player.updateSingleStat(MapleStat.LUK, player.getLuk());
            }
        } else if (splitted[0].equalsIgnoreCase("@command") || splitted[0].equalsIgnoreCase("@commands")) {
            mc.dropMessage("Use @help to access the list of player commands for Guida.");
        } else if (splitted[0].equalsIgnoreCase("@help")) {
            if (splitted.length < 2) {
                mc.dropMessage("There're 3 pages of player commands - please use '@help 1' to access page 1, and so on.");
            } else {
                switch (splitted[1]) {
                    case "1" -> {
                        mc.dropMessage("--- PAGE ONE [1] --- The player commands are as listed and explained below:");
                        mc.dropMessage("@help <no.> shows this list with <no.> being the page number.");
                        mc.dropMessage("@str, @int, @dex & @luk help you add stats faster.");
                        mc.dropMessage("@whodrops <item name> lets you find what monsters drop an item.");
                        mc.dropMessage("@channel lists everyone currently online in your channel.");
                        mc.dropMessage("@servertime shows the current server time.");
                        mc.dropMessage("@uptime shows the current server up time.");
                    }
                    case "2" -> {
                        mc.dropMessage("--- PAGE TWO [2] --- The player commands are as listed and explained below:");
                        mc.dropMessage("@gmlist lists all official GMs of Guida.");
                        mc.dropMessage("@idle lets you check how long someone has been idle for.");
                        mc.dropMessage("@unfreeze fixes your character if it is suddenly unable to use skills.");
                        mc.dropMessage("@iseventmap lets you check whether or not the map you're on is an event map or not.");
                    }
                    case "3" -> {
                        mc.dropMessage("--- PAGE THREE [3] --- The player commands are as listed and explained below:");
                        mc.dropMessage("@mail lets you send offline messages to other players. They cost 25,000 mesos per message.");
                        mc.dropMessage("@pqlist lists all the currently working PQs and their level requirements.");
                        mc.dropMessage("@togglesmega lets you turn smega's on and off.");
                        mc.dropMessage("@achievements shows you what achievements you have done.");
                        mc.dropMessage("@dispose fixes your character if it is suddenly unable to talk to NPCs");
                    }
                    default -> mc.dropMessage("Invalid page number. Use @help <pagenumber> to use the help - there are 3 pages.");
                }
            }
        } else if (splitted[0].equalsIgnoreCase("@gmlist")) {
            mc.dropMessage("Guida GMs: " + c.getChannelServer().getGMList());
        } else if (splitted[0].equalsIgnoreCase("@togglesmega")) {
            c.getPlayer().setSmegaEnabled(!c.getPlayer().getSmegaEnabled());
            String onOff = c.getPlayer().getSmegaEnabled() ? "on" : "off";
            mc.dropMessage("Your smegas have been turned " + onOff + ".");
        } else if (splitted[0].equalsIgnoreCase("@smega")) {
            String onOff = c.getPlayer().getSmegaEnabled() ? "on" : "off";
            mc.dropMessage("Your smegas are currently " + onOff + ". To change it, use @togglesmega.");
        } else if (splitted[0].equalsIgnoreCase("@iseventmap")) {
            mc.dropMessage(c.getPlayer().getMap().hasEvent() ? "Event mode is currently on in this map. This means you won't lose experience when dying." : "Map has event mode off. You WILL lose experience when dying.");
        } else if (splitted[0].equalsIgnoreCase("@idle")) {
            if (splitted.length > 1) {
                for (ChannelServer cservv : ChannelServer.getAllInstances()) {
                    for (MapleCharacter character : cservv.getPlayerStorage().getAllCharacters()) {
                        if (character.getName().equalsIgnoreCase(splitted[1])) {
                            if (c.getPlayer().isGM() && character.isGM() || !character.isGM()) {
                                mc.dropMessage(splitted[1] + " has been idle for " + new ReadableMillisecondFormat(character.getIdleTimer()).toString() + ".");
                            }
                            break;
                        }
                    }
                }
            }
        } else if (splitted[0].equalsIgnoreCase("@achievements")) {
            mc.dropMessage("Your finished achievements:");
            for (Integer i : c.getPlayer().getFinishedAchievements()) {
                String name = MapleAchievements.getInstance().getById(i).getName();
                String personalName = name.replace("#pp", player.getPossessivePronoun());
                mc.dropMessage(personalName + " - " + MapleAchievements.getInstance().getById(i).getReward() + " NX");
            }
        } else if (splitted[0].equalsIgnoreCase("@unfreeze")) {
            c.sendPacket(MaplePacketCreator.enableActions());
        } else if (splitted[0].equalsIgnoreCase("@pqlist")) {
            mc.dropMessage("List of working PQs: Kerning PQ (Level limit: 21-40), Ludi PQ (Level limit: 35-65), Zakum PQ(Level limit: 50+), BossQuest (Go to Martin in Orbis!), Ludi Maze PQ (Level limit: 51 - 85)");
        } else if (splitted[0].equalsIgnoreCase("@monsterhp") || splitted[0].equalsIgnoreCase("@bosshp")) {
            boolean boss = splitted[0].equalsIgnoreCase("@bosshp");
            for (MapleMapObject monstermo : c.getPlayer().getMap().getAllMonsters()) {
                MapleMonster monster = (MapleMonster) monstermo;
                if (!boss || monster.isBoss()) {
                    mc.dropMessage(monster.getName() + ": " + monster.getHp() + " / " + monster.getMaxHp() + " (" + monster.getHpPercentage() + "%) HP" + (monster.isHpLocked() ? " (HP Locked)" : ""));
                }
            }
        } else if (splitted[0].equals("@battleshiphp")) {
            mc.dropMessage("Your battleship has " + c.getPlayer().getBattleShipHP() + " HP left");
        } else if (splitted[0].equals("@dispose")) {
            NPCScriptManager.getInstance().dispose(c);
            if (c.getCM() != null) {
                c.getCM().dispose();
            }
            if (c.getQM() != null) {
                c.getQM().dispose();
            }
            mc.dropMessage("You should be able to talk to NPCs now.");
        } else if (splitted[0].equals("@servertime")) {
            long timeInMillis = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(timeInMillis);
            Date date = cal.getTime();
            mc.dropMessage(date.toString());
        } else if (splitted[0].equals("@whodrops")) {
            if (splitted.length > 1) {
                String itemNameOrig = StringUtil.joinStringFrom(splitted, 1);
                String itemName = itemNameOrig.toLowerCase();
                ArrayList<String> names = whoDropsCache.get(itemName);
                if (!whoDropsCache.containsKey(itemName)) {
                    List<Pair<Integer, String>> matchingNames = new ArrayList<>();
                    for (Pair<Integer, String> item : MapleItemInformationProvider.getInstance().getAllItems()) {
                        if (item.getRight().equalsIgnoreCase(itemName)) {
                            itemNameOrig = item.getRight();
                            matchingNames.add(item);
                        }
                    }
                    if (!matchingNames.isEmpty()) {
                        names = new ArrayList<>();
                        for (Pair<Integer, String> name : matchingNames) {
                            List<Pair<Integer, Integer>> mobs = MapleMonsterInformationProvider.getInstance().whoDrops(name.getLeft());
                            if (mobs.isEmpty()) {
                                continue;
                            }
                            names.ensureCapacity(names.size() + mobs.size());
                            names.add(String.format("%s (%d) is dropped by %d monsters:", name.getRight(), name.getLeft(), mobs.size()));
                            for (Pair<Integer, Integer> mob : mobs) {
                                names.add(String.format("* %s, with a chance of 1 in %d",
                                        (mob.getLeft() == -1 ? "All monsters" : MapleLifeFactory.getMonster(mob.getLeft()).getName()),
                                        mob.getRight()));
                            }
                        }
                        if (names.isEmpty()) {
                            names.add(itemNameOrig + " is not dropped by any monster.");
                        }
                        whoDropsCache.put(itemName, names);
                    } else {
                        whoDropsCache.put(itemName, null);
                    }
                }

                if (names == null) {
                    mc.dropMessage("Item \"" + itemNameOrig + "\" not found.");
                } else {
                    for (String res : names) {
                        mc.dropMessage(res);
                    }
                }
            } else {
                mc.dropMessage(splitted[0] + " must be followed by an item name.");
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("str", "", "Add your stats in one go.", 0),
                new CommandDefinition("dex", "", "Add your stats in one go.", 0),
                new CommandDefinition("int", "", "Add your stats in one go.", 0),
                new CommandDefinition("luk", "", "Add your stats in one go.", 0),
                new CommandDefinition("gmlist", "", "Lists current Guida GMs.", 0),
                new CommandDefinition("help", "", "Lists player commands.", 0),
                new CommandDefinition("command", "", "Lists player commands.", 0),
                new CommandDefinition("commands", "", "Lists player commands.", 0),
                new CommandDefinition("togglesmega", "", "Turns smegas on & off", 0),
                new CommandDefinition("iseventmap", "", "Check if a map is currently having an event or not.", 0),
                new CommandDefinition("idle", "", "Check for how long a person has been idle. Enter the name in the first argument.", 0),
                new CommandDefinition("unfreeze", "", "Use this if you can't use your skills anymore.", 0),
                new CommandDefinition("pqlist", "", "List of working PQs", 0),
                new CommandDefinition("achievements", "", "Shows the achievements you have finished so far", 0),
                new CommandDefinition("monsterhp", "", "Shows the HP of the monsters in your map", 0),
                new CommandDefinition("bosshp", "", "Shows the HP of the boss monsters in your map", 0),
                new CommandDefinition("dispose", "", "Fixes your char if you can't talk to NPCs", 0),
                new CommandDefinition("dj", "", "DJ command to let the server know a DJ is streaming", 0),
                new CommandDefinition("servertime", "", "Displays the server time.", 0),
                new CommandDefinition("whodrops", "", "Gets a list of what monsters drop an item by name.", 0)
        };
    }
}
