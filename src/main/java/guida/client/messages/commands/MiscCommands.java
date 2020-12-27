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

import guida.client.Equip;
import guida.client.IItem;
import guida.client.Item;
import guida.client.ItemFlag;
import guida.client.MapleCharacter;
import guida.client.MapleCharacterUtil;
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.CommandProcessor;
import guida.client.messages.MessageCallback;
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.remote.WorldChannelInterface;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MaplePortal;
import guida.server.MapleSquadType;
import guida.server.MapleStatEffect;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleNPC;
import guida.server.life.MobSkill;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapItem;
import guida.server.maps.MapleMapObject;
import guida.server.maps.MapleReactor;
import guida.server.maps.MapleReactorFactory;
import guida.server.maps.MapleReactorStats;
import guida.server.playerinteractions.IMaplePlayerShop;
import guida.tools.MaplePacketCreator;
import guida.tools.ReadableMillisecondFormat;
import guida.tools.StringUtil;

import java.awt.Point;
import java.awt.Rectangle;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

public class MiscCommands implements Command {

    private static String semicolonStrip(String str) {
        return str.replace(Pattern.quote("{__SC}"), ";").replace(Pattern.quote("[__SC]"), "__SC");
    }

    public void execute(MapleClient c, final MessageCallback mc, String[] splitted) throws Exception {
        ChannelServer cserv = c != null ? c.getChannelServer() : null;
        if (splitted[0].equals("!spy")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            double var = victim.getJumpMod();
            double var2 = victim.getSpeedMod();
            int str = victim.getStr();
            int dex = victim.getDex();
            int intel = victim.getInt();
            int luk = victim.getLuk();
            int meso = victim.getMeso();
            int maxhp = victim.getCurrentMaxHp();
            int maxmp = victim.getCurrentMaxMp();
            int gmlev = victim.getGMLevel();
            mc.dropMessage("JumpMod is " + var + " and Speedmod is " + var2 + ".");
            mc.dropMessage("Players stats are: Str: " + str + ", Dex: " + dex + ", Int: " + intel + ", Luk: " + luk + ".");
            mc.dropMessage("Player has " + meso + " mesos.");
            mc.dropMessage("Max HP is " + maxhp + ", and max MP is " + maxmp + ".");
            mc.dropMessage("GM Level is " + gmlev + ".");
        } else if (splitted[0].equals("!giftnx")) {
            if (splitted.length < 4) {
                mc.dropMessage("Use !giftnx <player> <amount> <type> - with type being 'paypal', 'card' or 'maplepoint'.");
            } else {
                int type = 0; //invalid if it doesn't change
                String type1 = "";
                switch (splitted[3]) {
                    case "paypal" -> {
                        type = 1;
                        type1 = "PaypalNX";
                    }
                    case "card" -> {
                        type = 4;
                        type1 = "CardNX";
                    }
                    case "maplepoint" -> {
                        type = 2;
                        type1 = "MaplePoints";
                    }
                    default -> mc.dropMessage("Use !giftnx <player> <amount> <type> - with type being 'paypal', 'card' or 'maplepoint'.");
                }
                if (type == 1 || type == 2 || type == 4) {
                    MapleCharacter victim1 = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
                    int points = Integer.parseInt(splitted[2]);
                    victim1.modifyCSPoints(type, points);
                    mc.dropMessage(type1 + " has been gifted.");
                }
            }
        } else if (splitted[0].equals("!fame")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            int fame = Integer.parseInt(splitted[2]);
            victim.addFame(fame);
            victim.updateSingleStat(MapleStat.FAME, fame);
        } else if (splitted[0].equals("!heal")) {
            if (splitted.length == 1) {
                MapleCharacter player = c.getPlayer();
                player.setHp(player.getMaxHp());
                player.updateSingleStat(MapleStat.HP, player.getMaxHp());
                player.setMp(player.getMaxMp());
                player.updateSingleStat(MapleStat.MP, player.getMaxMp());
            } else if (splitted.length == 2) {
                MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
                if (player == null) {
                    mc.dropMessage("That player is either offline or doesn't exist");
                    return;
                }
                player.setHp(player.getMaxHp());
                player.updateSingleStat(MapleStat.HP, player.getMaxHp());
                player.setMp(player.getMaxMp());
                player.updateSingleStat(MapleStat.MP, player.getMaxMp());
                mc.dropMessage("Healed " + splitted[1]);
            }
        } else if (splitted[0].equals("!kill")) {
            for (String name : splitted) {
                if (!name.equals(splitted[0])) {
                    MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(name);
                    if (victim != null) {
                        victim.setHp(0);
                        victim.setMp(0);
                        victim.updateSingleStat(MapleStat.HP, 0);
                        victim.updateSingleStat(MapleStat.MP, 0);
                    }
                }
            }
        } else if (splitted[0].equals("!killmap")) {
            for (MapleCharacter victim : c.getPlayer().getMap().getCharacters()) {
                if (victim != null) {
                    victim.setHp(0);
                    victim.setMp(0);
                    victim.updateSingleStat(MapleStat.HP, 0);
                    victim.updateSingleStat(MapleStat.MP, 0);
                }
            }
        } else if (splitted[0].equals("!dcall")) {
            Collection<ChannelServer> csss = ChannelServer.getAllInstances();
            for (ChannelServer cservers : csss) {
                Collection<MapleCharacter> cmc = new LinkedHashSet<>(cservers.getPlayerStorage().getAllCharacters());
                for (MapleCharacter mch : cmc) {
                    if (!mch.isGM() && mch != null) {
                        try {
                            mch.getClient().disconnect();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } else if (splitted[0].equals("!healmap")) {
            for (MapleCharacter mch : c.getPlayer().getMap().getCharacters()) {
                mch.setHp(mch.getMaxHp());
                mch.setMp(mch.getMaxMp());
                mch.updateSingleStat(MapleStat.HP, mch.getMaxHp());
                mch.updateSingleStat(MapleStat.MP, mch.getMaxMp());
            }
        } else if (splitted[0].equals("!unstick")) {
            if (splitted[1].toLowerCase().startsWith("acc")) {
                Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE accounts SET loggedin = 0 WHERE name = ?");
                ps.setString(1, splitted[2]);
                ps.executeUpdate();
                ps.close();
                mc.dropMessage("The account '" + splitted[2] + "' has been unstuck.");
                return;
            }
            if (c != null && c.getPlayer().getGMLevel() == 1) {
                mc.dropMessage("You do not have the required priviledges to use this command.");
                return;
            }
            MapleCharacter victim = null;
            Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
            for (ChannelServer chan : cservs) {
                victim = chan.getPlayerStorage().getCharacterByName(splitted[1]);
                if (victim != null) {
                    break;
                }
            }
            if (victim != null) {
                if (!victim.isGM() || victim.isGM() && victim.getIdleTimer() > 10000) {
                    victim.saveToDB(true, true);
                    victim.unstick();
                }
                mc.dropMessage(victim.getName() + " has been unstuck.");
            } else {
                mc.dropMessage("This character does not exist or is currently not online.");
            }
        } else if (splitted[0].equals("!vac")) {
            for (MapleMapObject mmo : c.getPlayer().getMap().getAllMonsters()) {
                MapleMonster monster = (MapleMonster) mmo;
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.moveMonster(0, -1, 0, 0, 0, monster.getObjectId(), monster.getPosition(), c.getPlayer().getLastRes()));
                monster.setPosition(c.getPlayer().getPosition());
            }
        } else if (splitted[0].equals("!eventmap")) {
            c.getPlayer().getMap().setEvent(!c.getPlayer().getMap().hasEvent());
            mc.dropMessage(c.getPlayer().getMap().hasEvent() ? "Map set to event mode." : "Map set to regular mode.");
        } else if (splitted[0].equals("!clock")) {
            if (splitted.length < 2) {
                mc.dropMessage("Please include the time in seconds you'd like on the clock!");
                return;
            }

            int time = Integer.parseInt(splitted[1]);
            c.getPlayer().getMap().clearShownMapTimer();
            c.getPlayer().getMap().addMapTimer(time, time, new String[0], false, true, null);
        } else if (splitted[0].equals("!scmdclock")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax: !scmdclock [r]durationmin[-durationmax] <commands seperated by ;>");
                return;
            }
            // !scmdclock r30-60 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock 30-60 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock r30 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock 30 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            String time = splitted[1];
            boolean repeat = time.startsWith("r");
            int durationmin;
            int durationmax;
            if (time.contains("-")) {
                String work = repeat ? time.substring(1) : time;
                String[] w2 = work.split(Pattern.quote("-"));
                durationmin = Integer.parseInt(w2[0]);
                durationmax = Integer.parseInt(w2[1]);
            } else {
                String work = repeat ? time.substring(1) : time;
                int dur = Integer.parseInt(work);
                durationmin = dur;
                durationmax = dur;
            }
            String commandstr = StringUtil.joinStringFrom(splitted, 2);
            String[] commandsraw = commandstr.split(Pattern.quote(";"));
            String[] commands = new String[commandsraw.length];
            for (int x = 0; x < commandsraw.length; x++) {
                commands[x] = semicolonStrip(commandsraw[x]);
            }
            c.getPlayer().getMap().clearShownMapTimer();
            c.getPlayer().getMap().addMapTimer(durationmin, durationmax, commands, repeat, true, c.getPlayer().makeFakeCopy());
        } else if (splitted[0].equals("!hcmdclock")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax: !hcmdclock [r]durationmin[-durationmax] <commands seperated by ;>");
                return;
            }
            // !scmdclock r30-60 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock 30-60 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock r30 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            // !scmdclock 30 !cmd1 abcd;!cmd2 abcdef abcd;!cmd3
            String time = splitted[1];
            boolean repeat = time.startsWith("r");
            int durationmin;
            int durationmax;
            if (time.contains("-")) {
                String work = repeat ? time.substring(1) : time;
                String[] w2 = work.split(Pattern.quote("-"));
                durationmin = Integer.parseInt(w2[0]);
                durationmax = Integer.parseInt(w2[1]);
            } else {
                String work = repeat ? time.substring(1) : time;
                int dur = Integer.parseInt(work);
                durationmin = dur;
                durationmax = dur;
            }
            String commandstr = StringUtil.joinStringFrom(splitted, 2);
            String[] commandsraw = commandstr.split(Pattern.quote(";"));
            String[] commands = new String[commandsraw.length];
            for (int x = 0; x < commandsraw.length; x++) {
                commands[x] = semicolonStrip(commandsraw[x]);
            }
            c.getPlayer().getMap().addMapTimer(durationmin, durationmax, commands, repeat, false, c.getPlayer().makeFakeCopy());
        } else if (splitted[0].equals("!listmaptimer")) {
            String[] debug = c.getPlayer().getMap().mapTimerDebug();

            for (String s : debug) {
                mc.dropMessage(s);
            }
        } else if (splitted[0].equals("!rhctimer")) {
            if (splitted[1].equalsIgnoreCase("all")) {
                c.getPlayer().getMap().clearHiddenMapTimers();
            } else {

                int index = Integer.parseInt(splitted[1]);

                c.getPlayer().getMap().clearHiddenMapTimer(index);
            }

        } else if (splitted[0].equals("!rsctimer")) {

            c.getPlayer().getMap().clearShownMapTimer();
        } else if (splitted[0].equals("!warplevel")) {
            int warpMap = Integer.parseInt(splitted[1]);
            int minWarp = Integer.parseInt(splitted[2]);
            int maxWarp = Integer.parseInt(splitted[3]);
            MapleMap map2wa2 = c.getChannelServer().getMapFactory().getMap(warpMap);
            String warpmsg = "You will now be warped to " + map2wa2.getStreetName() + " : " + map2wa2.getMapName();
            MaplePacket warppk = MaplePacketCreator.serverNotice(6, warpmsg);
            for (MapleCharacter chr : c.getPlayer().getMap().getCharacters()) {
                if (chr.getLevel() >= minWarp && chr.getLevel() <= maxWarp) {
                    try {
                        chr.getClient().sendPacket(warppk);
                        chr.changeMap(map2wa2, map2wa2.getRandomSpawnPoint());
                    } catch (Exception ex) {
                        String errormsg = "There was a problem warping you. Please contact a GM.";
                        chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, errormsg));
                    }
                }

            }
        } else if (splitted[0].equals("!resetmap")) {
            MapleCharacter player = c.getPlayer();
            boolean custMap = splitted.length >= 2;
            int mapid = custMap ? Integer.parseInt(splitted[1]) : player.getMapId();
            MapleMap map = custMap ? player.getClient().getChannelServer().getMapFactory().getMap(mapid) : player.getMap();
            if (player.getClient().getChannelServer().getMapFactory().destroyMap(mapid)) {
                MapleMap newMap = player.getClient().getChannelServer().getMapFactory().getMap(mapid);
                MaplePortal newPor = newMap.getPortal(0);
                List<MapleCharacter> allChars = map.getCharacters();
                outerLoop:
                for (MapleCharacter m : allChars) {
                    for (int x = 0; x < 5; x++) {
                        try {
                            m.changeMap(newMap, newPor);
                            continue outerLoop;
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                    mc.dropMessage("Failed warping " + m.getName() + " to the new map. Skipping...");
                }
                map.clearShownMapTimer();
                map.clearHiddenMapTimers();
                mc.dropMessage("The map has been reset.");
            } else {
                mc.dropMessage("Unsuccessful reset!");
            }
        } else if (splitted[0].equalsIgnoreCase("!spawnrate")) {
            MapleMap map = c.getPlayer().getMap();
            if (splitted[1].equalsIgnoreCase("multi")) {
                if (map.isSpawnRateModified()) {
                    mc.dropMessage("The spawn rate for this map has already been modified. You may only reset it.");
                    return;
                }
                int delta = Integer.parseInt(splitted[2]);
                if (delta < 1) {
                    mc.dropMessage("You cannot multiply the spawnrate by anything less than one (use divide to decrease spawn)");
                    return;
                }
                if (delta > 5) {
                    mc.dropMessage("You cannot multiply the spawnrate by anything more than 5. That would cause the spawn to be too much.");
                    return;
                }
                map.setSpawnRateMulti(delta);
            } else if (splitted[1].equalsIgnoreCase("divide")) {
                if (map.isSpawnRateModified()) {
                    mc.dropMessage("The spawn rate for this map has already been modified. You may only reset it.");
                    return;
                }
                int delta = Integer.parseInt(splitted[2]);
                if (delta < 1) {
                    mc.dropMessage("You cannot divide the spawnrate by anything less than one (use multi to increase spawn)");
                    return;
                }
                map.setSpawnRateMulti(-delta);
            } else if (splitted[1].equalsIgnoreCase("reset")) {
                map.resetSpawnRate();
            } else {
                mc.dropMessage("Syntax: !spawnrate [multi/divide/reset] [delta]");
                mc.dropMessage("Multi speeds up the spawn up to 5 times.");
                mc.dropMessage("Divide slows down the spawn down with no limit.");
                mc.dropMessage("Reset resets the spawnrate as well as the spawn thread. Does not require delta.");
            }
        } else if (splitted[0].equalsIgnoreCase("!mute")) {
            if (splitted.length >= 3) {
                MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
                int time = Integer.parseInt(splitted[2]);
                Calendar unmuteTime = Calendar.getInstance();
                unmuteTime.add(Calendar.MINUTE, time);
                victim.setMuted(true);
                victim.setUnmuteTime(unmuteTime);
                mc.dropMessage(victim.getName() + " has been muted for " + time + " minutes.");
                victim.dropMessage("You have been muted for " + time + " minutes");

            } else {
                mc.dropMessage("!mute <player name> <minutes>");
            }
        } else if (splitted[0].equalsIgnoreCase("!unmute")) {
            if (splitted.length >= 2) {
                cserv.getPlayerStorage().getCharacterByName(splitted[1]).setMuted(false);
            } else {
                mc.dropMessage("Please enter the character name that you want to unmute.");
            }
        } else if (splitted[0].equalsIgnoreCase("!mutemap")) {
            MapleMap map = c.getPlayer().getMap();
            map.setMuted(!map.getMuted());
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, map.getMapName() + " has been " + (map.getMuted() ? "muted." : "unmuted.")));
        } else if (splitted[0].equalsIgnoreCase("!getbuffs")) {
            if (splitted.length < 2) {
                return;
            }
            String name = splitted[1];
            MapleCharacter chr = cserv.getPlayerStorage().getCharacterByName(name);
            List<MapleStatEffect> lmse = chr.getBuffEffects();
            mc.dropMessage(name + "'s buffs:");
            for (MapleStatEffect mse : lmse) {
                StringBuilder sb = new StringBuilder();
                sb.append(mse.isSkill() ? "SKILL: " : "ITEM: ");
                if (mse.isSkill()) {
                    sb.append(" ");
                    sb.append(mse.getRemark());
                    sb.append(" ");
                }
                sb.append(mse.isSkill() ? SkillFactory.getSkillName(mse.getSourceId()) : MapleItemInformationProvider.getInstance().getName(mse.getSourceId()));
                sb.append(" (");
                sb.append(mse.getSourceId());
                sb.append(") ");
                sb.append(mse.getBuffString());
                // SKILL: Level 1 Bless (910xxxx)
                mc.dropMessage(sb.toString());
            }
            mc.dropMessage(name + "'s buffs END.");
        } else if (splitted[0].equalsIgnoreCase("!toggleblock")) {
            if (splitted.length < 2) {
                mc.dropMessage("Syntax: !toggleblock exit/enter");
                return;
            }
            String type = splitted[1];
            if (type.equalsIgnoreCase("exit")) {
                c.getPlayer().getMap().setCanExit(!c.getPlayer().getMap().canExit());
                mc.dropMessage("Non-GMs may " + (c.getPlayer().getMap().canExit() ? "" : "not ") + "exit this map.");
            } else if (type.equalsIgnoreCase("enter")) {
                c.getPlayer().getMap().setCanEnter(!c.getPlayer().getMap().canEnter());
                mc.dropMessage("Non-GMs may " + (c.getPlayer().getMap().canEnter() ? "" : "not ") + "enter this map.");
            }
        } else if (splitted[0].equalsIgnoreCase("!damage")) {
            if (splitted.length < 2) {
                mc.dropMessage("Syntax: !damage enable/disable");
                return;
            }
            boolean op = splitted[1].equalsIgnoreCase("disable");
            for (MapleMapObject mmo : c.getPlayer().getMap().getAllMonsters()) {
                ((MapleMonster) mmo).setHpLock(op);
            }
            mc.dropMessage("All mobs are now " + (op ? "" : "not ") + "HP locked.");
        } else if (splitted[0].equalsIgnoreCase("!unfreezemap")) {
            for (MapleMapObject mmo : c.getPlayer().getMap().getAllMonsters()) {
                MapleMonster mm = (MapleMonster) mmo;
                if (mm.isFake() && mm.isMoveLocked()) {
                    mm.setMoveLocked(false);
                }
            }
            mc.dropMessage("All mobs are now not Move locked.");
        } else if (splitted[0].equalsIgnoreCase("!split")) {
            MapleMap wto = c.getChannelServer().getMapFactory().getMap(Integer.parseInt(splitted[1]));
            if (wto == null) {
                return;
            }
            MaplePortal wtof = wto.getPortal(Integer.parseInt(splitted[2]));
            MaplePortal wtot = wto.getPortal(Integer.parseInt(splitted[3]));
            if (wtof == null || wtot == null) {
                return;
            }
            boolean it = false;
            for (MapleCharacter cmc : c.getPlayer().getMap().getCharacters()) {
                if (it) {
                    cmc.changeMap(wto, wtot);
                } else {
                    cmc.changeMap(wto, wtof);
                }
                it = !it;
            }
        } else if (splitted[0].equalsIgnoreCase("!dropwave")) {
            int itemid = Integer.parseInt(splitted[1]);
            boolean mesos = itemid < 0;
            if (mesos) {
                itemid = -itemid;
                if (itemid < 1) {
                    itemid = 1;
                }
            }
            int quant = CommandProcessor.getNamedIntArg(splitted, 2, "quantity", 1);
            int margin = CommandProcessor.getNamedIntArg(splitted, 2, "dist", 20);
            if (margin < 20) {
                margin = 20;
            }
            MapleMap map = c.getPlayer().getMap();
            Point p = c.getPlayer().getPosition();
            margin = c.getPlayer().isFacingLeft() ? -margin : margin;
            if (mesos) {
                for (int x = 0; x < quant && x < 30; x++) {
                    map.spawnMesoDrop(itemid, p, c.getPlayer(), c.getPlayer(), true, (byte) 0);
                    p.translate(margin, 0);
                }
            } else {
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                Item toDrop = null;
                boolean eq = ii.getInventoryType(itemid) == MapleInventoryType.EQUIP;
                if (!eq) {
                    toDrop = new Item(itemid, (byte) 0, (short) 1);
                }
                for (int x = 0; x < quant && x < 30; x++) {
                    if (eq) {
                        toDrop = ii.randomizeStats((Equip) ii.getEquipById(itemid));
                    }
                    if (toDrop == null) {
                        break;
                    }
                    toDrop.setGMFlag();
                    toDrop.setOwner(c.getPlayer().getName());
                    map.spawnItemDrop(c.getPlayer(), c.getPlayer(), toDrop, p, true, true);
                    p.translate(margin, 0);
                }
            }
        } else if (splitted[0].equalsIgnoreCase("!killid")) {
            int mid = Integer.parseInt(splitted[1]);
            c.getPlayer().getMap().killMonster(mid, c.getPlayer());
        } else if (splitted[0].equalsIgnoreCase("!givemap")) {
            List<MapleCharacter> allChars = c.getPlayer().getMap().getCharacters();
            String type = splitted[1];
            if (type.equalsIgnoreCase("item")) {
                int itemid = Integer.parseInt(splitted[2]);
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                short quantity = (short) CommandProcessor.getOptionalIntArg(splitted, 3, 1);
                boolean pet = itemid >= 5000000 && itemid <= 5000100;
                for (MapleCharacter player : allChars) {
                    if (pet) {
                        if (quantity > 1) {
                            quantity = 1;
                        }
                        MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, "from !givemap", c.getPlayer().getName(), MaplePet.createPet(c.getPlayer().getId(), itemid), true);
                        return;
                    } else if (ii.isRechargable(itemid)) {
                        quantity = ii.getSlotMax(c, itemid);
                        MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, "Rechargable item created.", c.getPlayer().getName(), null, true);
                        return;
                    }
                    MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, player.getName() + "got from !givemap with quantity " + quantity, c.getPlayer().getName(), null, true);
                }
            } else if (type.equalsIgnoreCase("meso")) {
                int amt = Integer.parseInt(splitted[2]);

                for (MapleCharacter player : allChars) {
                    player.gainMeso(amt, true, true, true);
                }
            } else if (type.equalsIgnoreCase("exp")) {
                if (c.getPlayer().getGMLevel() < 10) { // Requested by Flame
                    mc.dropMessage("You gotta be a level 10 GM to use this one :P");
                    return;
                }
                int amt = Integer.parseInt(splitted[2]);

                for (MapleCharacter player : allChars) {
                    player.gainExp(amt, true, true, false, false);
                }
            } else if (type.equalsIgnoreCase("skill")) {
                for (MapleCharacter player : allChars) {
                    MapleItemInformationProvider.getInstance().getItemEffect(Integer.parseInt(splitted[2])).applyTo(player);
                }
            } else {
                mc.dropMessage("SYNTAX : !givemap item/meso/exp/skill <itemid/amt/amt/itemid> <quantity>");
            }
        } else if (splitted[0].equalsIgnoreCase("!area")) {
            int id = Integer.parseInt(splitted[1]);
            Rectangle area = c.getPlayer().getMap().getArea(id);
            if (area == null) {
                mc.dropMessage("Invalid areaID");
                return;
            }
            String type = splitted[2];
            boolean mark = type.equalsIgnoreCase("mark");
            List<MapleCharacter> cmc = mark ? null : c.getPlayer().getMap().getPlayersInRect(area);

            if (type.equalsIgnoreCase("item") && c.getPlayer().hasGMLevel(5)) {
                int itemid = Integer.parseInt(splitted[3]);
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                short quantity = (short) CommandProcessor.getOptionalIntArg(splitted, 4, 1);
                boolean pet = itemid >= 5000000 && itemid <= 5000100;
                for (MapleCharacter player : cmc) {
                    if (pet) {
                        if (quantity > 1) {
                            quantity = 1;
                        }
                        MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, "from !givemap", c.getPlayer().getName(), MaplePet.createPet(c.getPlayer().getId(), itemid), true);
                        return;
                    } else if (ii.isRechargable(itemid)) {
                        quantity = ii.getSlotMax(c, itemid);
                        MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, "Rechargable item created.", c.getPlayer().getName(), null, true);
                        return;
                    }
                    MapleInventoryManipulator.addById(player.getClient(), itemid, quantity, player.getName() + "got from !givemap with quantity " + quantity, c.getPlayer().getName(), null, true);
                }
            } else if (type.equalsIgnoreCase("meso")) {
                int amt = Integer.parseInt(splitted[3]);

                for (MapleCharacter player : cmc) {
                    player.gainMeso(amt, true, true, true);
                }
            } else if (type.equalsIgnoreCase("exp")) {
                int amt = Integer.parseInt(splitted[3]);

                for (MapleCharacter player : cmc) {
                    player.gainExp(amt, true, true, false, false);
                }
            } else if (type.equalsIgnoreCase("warp")) {
                int map = Integer.parseInt(splitted[3]);

                MapleMap target = cserv.getMapFactory().getMap(map);

                if (target == null) {
                    mc.dropMessage("You have entered an incorrect Map ID.");
                } else {
                    String warpmsg = "You will now be warped to " + target.getStreetName() + " : " + target.getMapName();
                    MaplePacket warppk = MaplePacketCreator.serverNotice(6, warpmsg);
                    MaplePortal targetPortal = null;
                    if (splitted.length > 4) {
                        try {
                            targetPortal = target.getPortal(Integer.parseInt(splitted[4]));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (targetPortal == null) {
                        targetPortal = target.getPortal(0);
                    }
                    for (MapleCharacter player : cmc) {
                        if (player.isGM()) {
                            continue;
                        }
                        player.getClient().sendPacket(warppk);
                        player.changeMap(target, targetPortal);
                    }
                }

            } else if (type.equalsIgnoreCase("clearwrong")) {
                int wrong = Integer.parseInt(splitted[3]);
                Rectangle area2 = c.getPlayer().getMap().getArea(wrong);
                if (area2 == null) {
                    mc.dropMessage("Invalid area2ID");
                    return;
                }
                List<MapleCharacter> wmc = c.getPlayer().getMap().getPlayersInRect(area2);
                for (MapleCharacter c2 : cmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("quest/party/clear"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("Party1/Clear"));
                }
                for (MapleCharacter c2 : wmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("quest/party/wrong_kor"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("Party1/Failed"));
                }

            } else if (type.equalsIgnoreCase("victorylose")) {
                int wrong = Integer.parseInt(splitted[3]);
                Rectangle area2 = c.getPlayer().getMap().getArea(wrong);
                if (area2 == null) {
                    mc.dropMessage("Invalid area2ID");
                    return;
                }
                List<MapleCharacter> wmc = c.getPlayer().getMap().getPlayersInRect(area2);
                for (MapleCharacter c2 : cmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("event/coconut/victory"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("Coconut/Victory"));
                }
                for (MapleCharacter c2 : wmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("event/coconut/lose"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("Coconut/Failed"));
                }

            } else if (type.equalsIgnoreCase("winlose")) {
                int wrong = Integer.parseInt(splitted[3]);
                Rectangle area2 = c.getPlayer().getMap().getArea(wrong);
                if (area2 == null) {
                    mc.dropMessage("Invalid area2ID");
                    return;
                }
                List<MapleCharacter> wmc = c.getPlayer().getMap().getPlayersInRect(area2);
                for (MapleCharacter c2 : cmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/win"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Win"));
                }
                for (MapleCharacter c2 : wmc) {
                    c2.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/lose"));
                    c2.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Lose"));
                }

            } else if (mark) {
                int xpos1 = area.x;
                int xpos2 = area.x + area.width;
                int ypos1 = area.y;
                int ypos2 = area.y + area.height;
                Point tl = new Point(xpos1, ypos1);
                Point bl = new Point(xpos1, ypos2);
                Point tr = new Point(xpos2, ypos1);
                Point br = new Point(xpos2, ypos2);
                MapleReactorStats mri = MapleReactorFactory.getReactor(2006000);
                MapleReactor tlr = new MapleReactor(mri, 2006000);
                MapleReactor blr = new MapleReactor(mri, 2006000);
                MapleReactor trr = new MapleReactor(mri, 2006000);
                MapleReactor brr = new MapleReactor(mri, 2006000);
                tlr.setDelay(-1);
                blr.setDelay(-1);
                trr.setDelay(-1);
                brr.setDelay(-1);
                tlr.setPosition(tl);
                blr.setPosition(bl);
                trr.setPosition(tr);
                brr.setPosition(br);
                c.getPlayer().getMap().spawnReactor(tlr);
                c.getPlayer().getMap().spawnReactor(trr);
                c.getPlayer().getMap().spawnReactor(blr);
                c.getPlayer().getMap().spawnReactor(brr);
            }
        } else if (splitted[0].equalsIgnoreCase("!dinvincibility")) {
            c.getPlayer().getMap().setCannotInvincible(!c.getPlayer().getMap().cannotInvincible());
            mc.dropMessage("Non-GMs can now " + (c.getPlayer().getMap().cannotInvincible() ? "no longer " : "") + "use Dark Sight, Smokescreen and Oak Barrel.");
        } else if (splitted[0].equalsIgnoreCase("!joinparty")) {
            MapleCharacter victim = null;
            if (c.getPlayer().getParty() != null) {
                c.getPlayer().dropMessage("You are already in a party.");
                return;
            }
            try {
                victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (victim == null) {
                c.getPlayer().dropMessage("This person is currently not in your channel, offline or does not exist.");
                return;
            }
            WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
            if (victim.getParty() != null) {
                if (victim.getParty().getMembers().size() < 6) {
                    MaplePartyCharacter partyplayer = new MaplePartyCharacter(c.getPlayer());
                    wci.updateParty(victim.getParty().getId(), PartyOperation.JOIN, partyplayer);
                    c.getPlayer().receivePartyMemberHP();
                    c.getPlayer().updatePartyMemberHP();
                } else {
                    c.getPlayer().dropMessage("The party you are trying to join is currently full.");
                }
            } else {
                c.getPlayer().dropMessage("This person does not have a party.");
            }
        } else if (splitted[0].equalsIgnoreCase("!joinguild")) {
            MapleCharacter victim = null;
            MapleCharacter player = c.getPlayer();
            if (c.getPlayer().getGuildId() != 0) {
                c.getPlayer().dropMessage("You are already in a guild.");
                return;
            }
            try {
                victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (victim == null) {
                player.dropMessage("This person is currently not in your channel, offline or does not exist.");
                return;
            }
            if (victim.getGuildId() != 0) {
                player.setGuildId(victim.getGuild().getId());
                player.setGuildRank(5);
                int s = c.getChannelServer().getWorldInterface().addGuildMember(player.getMGC());
                if (s == 0) {
                    player.dropMessage("The guild you are trying to join is currently full.");
                    player.setGuildId(0);
                    return;
                }
                c.sendPacket(MaplePacketCreator.showGuildInfo(player));
                player.saveGuildStatus();
                player.getMap().broadcastMessage(player, MaplePacketCreator.playerGuildName(player), false);
                player.getMap().broadcastMessage(player, MaplePacketCreator.playerGuildInfo(player), false);
            } else {
                player.dropMessage("This person does not have a guild.");
            }
        } else if (splitted[0].equalsIgnoreCase("!snailrush")) {
            Point maple = new Point(40, -88);
            Point story = new Point(40, 152);
            MapleMonster mob1 = MapleLifeFactory.getMonster(100100);
            mob1.setHpLock(true);
            MapleMonster mob2 = MapleLifeFactory.getMonster(100100);
            mob2.setHpLock(true);
            MapleMap m = c.getPlayer().getMap();
            m.spawnMonsterOnGroundBelow(mob1, maple);
            m.spawnMonsterOnGroundBelow(mob2, story);
            m.killMonster(9300091, c.getPlayer());
        } else if (splitted[0].equalsIgnoreCase("!playershop")) {
            String op = splitted[1];
            if (op.equalsIgnoreCase("closeall")) {
                for (IMaplePlayerShop imps : c.getPlayer().getMap().getPlayerShops()) {
                    imps.closeShop();
                    imps.makeAvailableAtFred();
                }
                return;
            }
            String victim = splitted[2];
            IMaplePlayerShop imps = c.getPlayer().getMap().getMaplePlayerShopByOwnerName(victim);
            if (imps == null) {
                mc.dropMessage("That character does not own a store.");
                return;
            }
            if (op.equalsIgnoreCase("close")) {
                imps.closeShop();
                imps.makeAvailableAtFred();
            } else if (op.equalsIgnoreCase("rename")) {
                String newdesc = StringUtil.joinStringFrom(splitted, 3);
                imps.setDescription(newdesc);
            }
        } else if (splitted[0].equals("!disbandsquad")) {
            int channel = Integer.parseInt(splitted[1]);
            ChannelServer chan = ChannelServer.getInstance(channel);
            String type = splitted[2].toLowerCase();
            MapleSquadType squad = null;
            if (type.startsWith("z")) {
                squad = MapleSquadType.ZAKUM;
            }
            if (type.startsWith("b")) {
                squad = MapleSquadType.BOSSQUEST;
            } else if (type.startsWith("h")) {
                squad = MapleSquadType.HORNTAIL;
            } else if (type.startsWith("u")) {
                squad = MapleSquadType.UNKNOWN;
            }
            if (squad != null) {
                chan.removeMapleSquad(cserv.getMapleSquad(squad), squad);
                mc.dropMessage("The " + squad.toString() + " squad for channel " + channel + " has been disposed.");
            } else {
                mc.dropMessage("Syntax : !disbandsquad <channel> <Zakum, BossQuest, Horntail, Unknown>");
            }
        } else if (splitted[0].equals("!weather")) {
            c.getPlayer().getMap().stopMapEffect();
            c.getPlayer().getMap().startMapEffect(StringUtil.joinStringFrom(splitted, 1), 5120002);
        } else if (splitted[0].equals("!oxquiz")) {
            if (splitted.length < 4) {
                c.getPlayer().dropMessage(5, "Synax : !oxquiz <q/a> <set> <id>");
                return;
            }
            boolean question = splitted[1].equals("q");
            int set = Integer.parseInt(splitted[2]);
            int id = Integer.parseInt(splitted[3]);
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.showOXQuiz(set, id, question));
        } else if (splitted[0].equals("!stopmap")) {
            MapleMap map = c.getPlayer().getMap();
            map.setCanMove(!map.canMove());
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, "You can " + (map.canMove() ? "now" : "not") + " move in this map."));
        } else if (splitted[0].equals("!toggleskill")) {
            MapleMap map = c.getPlayer().getMap();
            map.setAllowSkills(!map.canUseSkills());
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, "You can " + (map.canUseSkills() ? "now" : "not") + " use skills in this map."));
        } else if (splitted[0].equals("!eventitem")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax Error: !eventitem <itemid> <owner string>");
                mc.dropMessage("Example: !eventitem 1040002 JQ Winner");
                return;
            }
            int itemid = Integer.parseInt(splitted[1]);
            String owner = StringUtil.joinStringFrom(splitted, 2);
            MapleItemInformationProvider miip = MapleItemInformationProvider.getInstance();
            IItem item = miip.getEquipById(itemid);
            item.setOwner(owner);
            item.setGMFlag();
            MapleInventoryManipulator.addByItem(c, item, "", true);
        } else if (splitted[0].equals("!cygnusintro")) {
            c.getPlayer().startCygnusMovie();
        } else if (splitted[0].equals("!music")) {
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.musicChange(splitted[1]));
        } else if (splitted[0].equals("!itemvac")) {
            MapleMap map = c.getPlayer().getMap();
            for (MapleMapObject mmo : map.getAllItems()) {
                MapleMapItem item = (MapleMapItem) mmo;
                if (item.getItem() != null) {
                    IItem newItem = item.getItem().copy();
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.removeItemFromMap(item.getObjectId(), 2, c.getPlayer().getId()), item.getPosition());
                    c.getPlayer().getCheatTracker().pickupComplete();
                    c.getPlayer().getMap().removeMapObject(mmo);
                    item.setPickedUp(true);
                    map.spawnItemDrop(c.getPlayer(), c.getPlayer(), newItem, c.getPlayer().getPosition(), true, true);
                } else {
                    int meso = item.getMeso();
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.removeItemFromMap(item.getObjectId(), 2, c.getPlayer().getId()), item.getPosition());
                    c.getPlayer().getCheatTracker().pickupComplete();
                    c.getPlayer().getMap().removeMapObject(mmo);
                    item.setPickedUp(true);
                    c.getPlayer().getMap().spawnMesoDrop(meso, c.getPlayer().getPosition(), c.getPlayer(), c.getPlayer(), false, (byte) 0);
                }
            }
        } else if (splitted[0].equals("!listnpcs")) {
            for (MapleMapObject mmo : c.getPlayer().getMap().getAllNPCs()) {
                MapleNPC npc = (MapleNPC) mmo;
                c.sendPacket(MaplePacketCreator.serverNotice(6, "Object ID: " + npc.getObjectId() + " NPC Name(ID): " + npc.getName() + "(" + npc.getId() + ")"));
            }
        } else if (splitted[0].equals("!lastevent")) {
            if (ChannelServer.getInstance(1).getLastEvent() != 0) {
                mc.dropMessage(new ReadableMillisecondFormat(System.currentTimeMillis() - ChannelServer.getInstance(1).getLastEvent()).toString());
            } else {
                mc.dropMessage("Never");
            }
        } else if (splitted[0].equals("!newevent")) {
            ChannelServer.getInstance(1).setLastEvent(System.currentTimeMillis());
            mc.dropMessage("You may now procceed with your event.");
        } else if (splitted[0].equals("!dphide")) {
            MapleCharacter applyto = c.getPlayer();
            if (c.getPlayer().getGMLevel() != 1) {
                String target = splitted[1];
                applyto = c.getChannelServer().getPlayerStorage().getCharacterByName(target);
            }
            if (applyto == null || applyto.getGMLevel() > c.getPlayer().getGMLevel()) {
                mc.dropMessage("Invalid target.");
                return;
            }
            applyto.setHidden(false);
            SkillFactory.getSkill(9101004).getEffect(1).applyTo(applyto);
        } else if (splitted[0].equals("!namechange")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax Error : !namechange <Current IGN> <New IGN>");
                return;
            }
            try {
                if (c.getChannelServer().getWorldInterface().isConnected(splitted[1], false)) { //Jose wants them offline
                    mc.dropMessage("Please tell the player to logoff.");
                    return;
                }
            } catch (RemoteException e) {
                c.getChannelServer().reconnectWorld();
            }
            if (MapleCharacterUtil.exist(splitted[2], c.getWorld())) {
                mc.dropMessage("Someone already has this name.");
                return;
            } else if (!MapleCharacterUtil.isNameLegal(splitted[2])) {
                mc.dropMessage("This name isn't a valid name, make sure it's between 4-12 characters and isn't forbidden.");
                return;
            }
            Connection con = DatabaseConnection.getConnection();
            try (PreparedStatement ps = con.prepareStatement("UPDATE characters SET name = ? WHERE name = ?")) {
                ps.setString(1, splitted[2]); //New name
                ps.setString(2, splitted[1]); //Current name
                ps.executeUpdate();
                ps.close();
                mc.dropMessage("Name changed!");
            } catch (SQLException e) {
                mc.dropMessage("An error occured");
            }
        } else if (splitted[0].equals("!forceinto")) {
            if (splitted.length < 5) {
                mc.dropMessage("Syntax: !forceinto <trade/drop> <equip/use/setup/etc/cash> <slot> <quantity>");
                return;
            }

            MapleInventoryType ivType = MapleInventoryType.valueOf(splitted[2].toUpperCase());
            IItem item = c.getPlayer().getInventory(ivType).getItem(Short.parseShort(splitted[3]));
            if (item == null) {
                mc.dropMessage("No item in slot specified.");
                return;
            }
            short quantity = Short.parseShort(splitted[4]);
            if (splitted[1].equalsIgnoreCase("trade")) {
                if (c.getPlayer().getTrade() == null) {
                    mc.dropMessage("You are not in a trade.");
                    return;
                }
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

                byte targetSlot = c.getPlayer().getTrade().getNextFreeSlot();
                if (targetSlot == -1) {
                    mc.dropMessage("The trade is full.");
                    return;
                }
                if (quantity <= item.getQuantity() && quantity >= 0 || ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                    IItem tradeItem = item.copy();
                    if (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                        tradeItem.setQuantity(item.getQuantity());
                        MapleInventoryManipulator.removeFromSlot(c, ivType, item.getPosition(), item.getQuantity(), true);
                    } else {
                        tradeItem.setQuantity(quantity);
                        MapleInventoryManipulator.removeFromSlot(c, ivType, item.getPosition(), quantity, true);
                    }
                    tradeItem.setPosition(targetSlot);
                    c.getPlayer().getTrade().addItem(tradeItem);
                } else if (quantity < 0) {
                    mc.dropMessage("Quantity cannot be less than zero.");
                }
            } else if (splitted[1].equalsIgnoreCase("drop")) {
                MapleInventoryManipulator.drop(c, ivType, Byte.parseByte(splitted[3]), quantity, true);
            }
        } else if (splitted[0].equals("!itemflag")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax: !itemflag <equip/use/setup/etc/cash> <slot> [none/lock/spikes/cold_protection/untradeable/trade_once/no_scissor]");
                return;
            }
            MapleInventoryType ivType = MapleInventoryType.valueOf(splitted[1].toUpperCase());
            IItem item = c.getPlayer().getInventory(ivType).getItem(Short.parseShort(splitted[2]));
            if (item == null) {
                mc.dropMessage("Invalid item slot!");
                return;
            }
            if (splitted.length == 3) {
                StringBuilder flags = new StringBuilder();
                short flag = item.getFlag();
                for (ItemFlag itflg : ItemFlag.values()) {
                    if ((flag & itflg.getValue()) == itflg.getValue()) {
                        flags.append(itflg.name().toUpperCase()).append(" ");
                    }
                }
                if (item.isSSOneOfAKind()) {
                    flags.append("NO_SCISSOR");
                }
                if (flags.toString().length() == 0) {
                    mc.dropMessage("NONE");
                    return;
                }
                mc.dropMessage(flags.toString());
                return;
            }
            String flag = splitted[3].toUpperCase();
            boolean turnon = true;
            if (flag.equals("NO_SCISSOR")) {
                item.setSSOneOfAKind(!item.isSSOneOfAKind());
                turnon = item.isSSOneOfAKind();
            } else {
                ItemFlag itf = ItemFlag.valueOf(flag);
                if ((item.getFlag() & itf.getValue()) == itf.getValue()) {
                    item.setFlag((short) (item.getFlag() ^ itf.getValue()));
                    turnon = false;
                } else {
                    item.setFlag((short) (item.getFlag() | itf.getValue()));
                }
                c.sendPacket(MaplePacketCreator.getCharInfo(c.getPlayer()));
                c.getPlayer().getMap().removePlayer(c.getPlayer());
                c.getPlayer().getMap().addPlayer(c.getPlayer());
            }
            mc.dropMessage(turnon ? "The flag " + flag + " was set." : "The flag " + flag + " was unset.");
        } else if (splitted[0].equals("!seduce")) {
            if (splitted.length < 2) {
                mc.dropMessage("Syntax Error: !seduce <char name> [left/right/jump]");
                return;
            }
            MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim == null) {
                mc.dropMessage("The player is either offline or not on your channel");
                return;
            }
            int type = 1;
            if (splitted.length >= 3) {
                if (splitted[2].equalsIgnoreCase("right")) {
                    type = 2;
                }
                if (splitted[2].equalsIgnoreCase("jump")) {
                    type = 3;
                }
            }
            victim.giveDebuff(MapleDisease.SEDUCE, new MobSkill(128, type));
        } else if (splitted[0].equals("!seducemap")) {
            int type = 1;
            if (splitted.length >= 2) {
                if (splitted[2].equalsIgnoreCase("right")) {
                    type = 2;
                }
                if (splitted[2].equalsIgnoreCase("jump")) {
                    type = 3;
                }
            }
            for (MapleCharacter chrs : c.getPlayer().getMap().getCharacters()) {
                if (!chrs.isGM()) {
                    chrs.giveDebuff(MapleDisease.SEDUCE, new MobSkill(128, type));
                }
            }
        } else if (splitted[0].equals("!getpartymembers")) {
            if (splitted.length < 2) {
                mc.dropMessage("Syntax Error : !getpartymembers <victim name>");
                return;
            }
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim == null) {
                mc.dropMessage("Player is either offline or on another channel");
                return;
            } else if (victim.getParty() == null) {
                mc.dropMessage("Character is not in a party.");
                return;
            }
            StringBuilder sb = new StringBuilder("Members : ");
            for (MaplePartyCharacter chr : victim.getParty().getMembers()) {
                sb.append(chr.getName());
                sb.append(", ");
            }
            sb.deleteCharAt(sb.lastIndexOf(","));
            mc.dropMessage(sb.toString());
        } else if (splitted[0].equals("!donormedal")) {
            //               lith        perion      ellinna    henesys   kernin   nautilus  orbis
            List<String> jobs = Arrays.asList("beginner", "warrior", "magician", "bowman", "thief", "pirate", "cygnus");
            int[] medalid = {1142030, 1142016, 1142015, 1142014, 1142017, 1142019, 1142031};
            if (splitted.length < 2 || !jobs.contains(splitted[1].toLowerCase())) {
                mc.dropMessage("Syntax: !donormedal <beginner/warrior/magician/bowman/thief/pirate/cygnus> <donor name> ");
                return;
            }

            int itemid = medalid[jobs.indexOf(splitted[1].toLowerCase())];
            String charName = splitted[2];
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            Equip eqp = (Equip) ii.getEquipById(itemid);
            short stat = (short) 5;
            eqp.setStr(stat);
            eqp.setDex(stat);
            eqp.setInt(stat);
            eqp.setLuk(stat);
            eqp.setJump(stat);
            eqp.setSpeed(stat);

            eqp.setExpiration(new Timestamp(System.currentTimeMillis() + 30 * 86400000L));
            eqp.setOwner(charName);
            c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(c, eqp, "Donator item created for " + charName, false)));
        } else if (splitted[0].equals("!rclock")) {
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.removeMapTimer());
        } else if (splitted[0].equals("!shakescreen")) {
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.shakeScreen(Integer.parseInt(splitted[1]), Integer.parseInt(splitted[2])));
        } else if (splitted[0].equals("!cc")) {
            int channel = Integer.parseInt(splitted[1]);
            if (channel < 1 || channel > ChannelServer.getAllInstances().size()) {
                mc.dropMessage("Invalid channel id " + channel);
                return;
            }
            MapleClient.changeChannel(c, channel, true);
        } else if (splitted[0].equals("!doconfirm")) {
            if (splitted.length < 3) {
                mc.dropMessage("Syntax: !doconfirm <player> <1/2>");
                return;
            }
            final MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim == null || (victim.getGMLevel() >= c.getPlayer().getGMLevel() && victim.getObjectId() != c.getPlayer().getObjectId())) {
                mc.dropMessage("Unable to find player \"" + splitted[1] + "\"");
                return;
            }
            int cfmtype = Integer.parseInt(splitted[2]);
            if (cfmtype != 1 && cfmtype != 2) {
                mc.dropMessage("Only confirm types 1 and 2 are allowed");
                return;
            }
            Runnable rpass = () -> mc.dropMessage(victim.getName() + " has passed confirmation.");
            Runnable rfail = () -> mc.dropMessage(victim.getName() + " has failed confirmation.");
            victim.callConfirmationNpc(rpass, rfail, 9010000, cfmtype, "A GM requests confirmation.", "Please solve this:", "Please solve this:");
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("spy", "<player>", "Spies on the player", 4),
                new CommandDefinition("giftnx", "<player> <amount> <type>", "Gifts the specified NX to the player", 4),
                new CommandDefinition("fame", "<player> <fame>", "Sets the player's fame at the specified amount", 4),
                new CommandDefinition("heal", "[player]", "Heals you if player is not specified", 4),
                new CommandDefinition("kill", "<players>", "Kills the players specified", 4),
                new CommandDefinition("dcall", "", "DCs everyone.", 4),
                new CommandDefinition("healmap", "", "Heals the map", 4),
                new CommandDefinition("unstick", "<player>", "Unsticks the specified player", 1),
                new CommandDefinition("vac", "", "Vacs monsters to you.", 4),
                new CommandDefinition("eventmap", "", "Toggles event map status on the current map", 4),
                new CommandDefinition("clock", "[time]", "Shows a clock to everyone in the map", 4),
                new CommandDefinition("rclock", "", "Removes a clock shown to everyone in the map via !clock", 4),
                new CommandDefinition("shakescreen", "", "Shakes the screen", 4),
                new CommandDefinition("resetmap", "[mapid]", "Resets the specified mapid, or if not specified, the map you are on. Used to reset maps that crash when entered.", 4),
                new CommandDefinition("spawnrate", "See !spawnrate help", "Spawnrate control.", 5),
                new CommandDefinition("mute", "[player name] [minutes muted]", "Mutes player for the amount of minutes.", 1),
                new CommandDefinition("unmute", "[player name]", "Unmutes player", 1),
                new CommandDefinition("mutemap", "", "Mutes the map", 1),
                new CommandDefinition("killmap", "", "kills the map", 4),
                new CommandDefinition("getbuffs", "[player]", "Gets all the buffs of the specified player", 4),
                new CommandDefinition("toggleblock", "exit/enter", "Sets whether non-GMs may exit/enter this map.", 4),
                new CommandDefinition("damage", "enable/disable", "Sets whether damage to monsters is enabled.", 4),
                new CommandDefinition("unfreezemap", "", "Unfreezes movelocked monsters", 4),
                new CommandDefinition("split", "<mapid> <portal no 1> <portal no 2>", "Warps one half of the map to portal no 1 in mapid and the other to portal no 2 in mapid.", 4),
                new CommandDefinition("dropwave", "<itemid (negative value for mesos)> [quantity <quantity> (default 1)] [dist <distance between each item in pixels> (default 20)]", "Spawns a wave of items.", 5),
                new CommandDefinition("killid", "", "", 4),
                new CommandDefinition("givemap", "item/meso/exp/skill <itemid/amt/amt/itemid> <quantity>", "", 5),
                new CommandDefinition("area", "[too lengthy]", "See AngelSL for help.", 4), //
                new CommandDefinition("dinvincibility", "", "Toggles whether players can use Dark Sight, Oak Barrel or Smokescreen.", 4),
                new CommandDefinition("joinparty", "", "Join the player's party", 4),
                new CommandDefinition("joinguild", "", "Join the player's guild", 4),
                new CommandDefinition("snailrush", "", "", 4),
                new CommandDefinition("playershop", "<rename/close/closeall> <owner's name> [rename to]", "", 4),
                new CommandDefinition("disbandsquad", "<channel> <Zakum, BossQuest, Horntail, Unknown>", " Disbands MapleSquad", 4),
                new CommandDefinition("weather", "<text>", "Text", 4),
                new CommandDefinition("oxquiz", "<q/a> <set> <id>", "makes an ox quiz question", 4),
                new CommandDefinition("stopmap", "", "Sets movement on the map on/off", 4),
                new CommandDefinition("toggleskill", "", "Sets using skills on the map on/off", 4),
                new CommandDefinition("eventitem", "<itemid> <owner>", "Makes an event item", 4),
                new CommandDefinition("cygnusintro", "", "", 4),
                new CommandDefinition("music", "", "", 4),
                new CommandDefinition("itemvac", "", "Vacs items to you", 4),
                new CommandDefinition("listnpcs", "", "list all NPCs in the map", 4),
                new CommandDefinition("lastevent", "", "Checks when the last event was", 4),
                new CommandDefinition("newevent", "", "Sets the last event variable to the current time", 4),
                new CommandDefinition("dphide", "", "Dispel Hide on a target.", 1),
                new CommandDefinition("namechange", "<Character name> <New name>", "Changes the name of the character", 10),
                new CommandDefinition("forceinto", "", "Try It And See", 4),
                new CommandDefinition("itemflag", "", "Try It And See", 4),
                new CommandDefinition("seduce", "<victim name>", "seduces the victim ;]", 5),
                new CommandDefinition("seducemap", "", "Seduces the whole map", 5),
                new CommandDefinition("getpartymembers", "<victim name>", "Names all members of a party", 1),
                new CommandDefinition("scmdclock", "", "", 5),
                new CommandDefinition("hcmdclock", "", "", 5),
                new CommandDefinition("rhctimer", "", "", 5),
                new CommandDefinition("rsctimer", "", "", 5),
                new CommandDefinition("listmaptimer", "", "", 5),
                new CommandDefinition("warplevel", "", "", 5),
                new CommandDefinition("donormedal", "", "", 4),
                new CommandDefinition("cc", "", "", 4),
                new CommandDefinition("doconfirm", "", "", 4)
        };
    }
}