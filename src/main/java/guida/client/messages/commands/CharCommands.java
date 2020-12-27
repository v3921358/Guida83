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
import guida.client.ISkill;
import guida.client.Item;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MaplePet;
import guida.client.MapleRing;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.net.MaplePacket;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.TimerManager;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleNPC;
import guida.server.maps.MapleMapObject;
import guida.server.playerinteractions.MapleShopFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import java.sql.Timestamp;

import static guida.client.messages.CommandProcessor.getOptionalIntArg;

public class CharCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        MapleCharacter player = c.getPlayer();
        switch (splitted[0]) {
            case "!maxstats":
                player.setMaxHp(30000);
                player.setMaxMp(30000);
                player.setStr(Short.MAX_VALUE);
                player.setDex(Short.MAX_VALUE);
                player.setInt(Short.MAX_VALUE);
                player.setLuk(Short.MAX_VALUE);
                player.updateSingleStat(MapleStat.MAXHP, 30000);
                player.updateSingleStat(MapleStat.MAXMP, 30000);
                player.updateSingleStat(MapleStat.STR, Short.MAX_VALUE);
                player.updateSingleStat(MapleStat.DEX, Short.MAX_VALUE);
                player.updateSingleStat(MapleStat.INT, Short.MAX_VALUE);
                player.updateSingleStat(MapleStat.LUK, Short.MAX_VALUE);
                break;
            case "!minstats":
                player.setMaxHp(50);
                player.setMaxMp(5);
                player.setStr(4);
                player.setDex(4);
                player.setInt(4);
                player.setLuk(4);
                player.updateSingleStat(MapleStat.MAXHP, 50);
                player.updateSingleStat(MapleStat.MAXMP, 5);
                player.updateSingleStat(MapleStat.STR, 4);
                player.updateSingleStat(MapleStat.DEX, 4);
                player.updateSingleStat(MapleStat.INT, 4);
                player.updateSingleStat(MapleStat.LUK, 4);
                break;
            case "!maxhp":
                if (splitted.length < 2) {
                    mc.dropMessage("!maxhp <Desired Max HP>");
                } else {
                    int stat = Integer.parseInt(splitted[1]);
                    player.setMaxHp(stat);
                    player.setHp(stat);
                    player.updateSingleStat(MapleStat.HP, stat);
                    player.updateSingleStat(MapleStat.MAXHP, stat);
                }
                break;
            case "!maxmp":
                if (splitted.length < 2) {
                    mc.dropMessage("!maxmp <Desired Max MP>");
                } else {
                    int stat = Integer.parseInt(splitted[1]);
                    player.setMaxMp(stat);
                    player.setMp(stat);
                    player.updateSingleStat(MapleStat.MP, stat);
                    player.updateSingleStat(MapleStat.MAXMP, stat);
                }
                break;
            case "!hp":
                if (splitted.length < 2) {
                    mc.dropMessage("!hp <Desired MP>");
                } else {
                    int stat = Integer.parseInt(splitted[1]);
                    player.setHp(stat);
                    player.updateSingleStat(MapleStat.HP, stat);
                    c.getPlayer().checkBerserk();
                }
                break;
            case "!mp": {
                int stat = Integer.parseInt(splitted[1]);
                player.setMp(stat);
                player.updateSingleStat(MapleStat.MP, stat);
                break;
            }
            case "!sstr": {
                int stat = Integer.parseInt(splitted[1]);
                player.setStr(stat);
                player.updateSingleStat(MapleStat.STR, stat);
                break;
            }
            case "!sdex": {
                int stat = Integer.parseInt(splitted[1]);
                player.setDex(stat);
                player.updateSingleStat(MapleStat.DEX, stat);
                break;
            }
            case "!sint": {
                int stat = Integer.parseInt(splitted[1]);
                player.setInt(stat);
                player.updateSingleStat(MapleStat.INT, stat);
                break;
            }
            case "!sluk": {
                int stat = Integer.parseInt(splitted[1]);
                player.setLuk(stat);
                player.updateSingleStat(MapleStat.LUK, stat);
                break;
            }
            case "!skill": {
                ISkill skill = SkillFactory.getSkill(Integer.parseInt(splitted[1]));
                if (skill == null) {
                    mc.dropMessage("Please enter a valid skill ID.");
                    return;
                }
                int level = getOptionalIntArg(splitted, 2, 1);
                int masterlevel = getOptionalIntArg(splitted, 3, -1);
                if (level > skill.getMaxLevel()) {
                    level = skill.getMaxLevel();
                }
                if (skill.isFourthJob()) {
                    if (masterlevel > skill.getMaxLevel()) {
                        masterlevel = skill.getMaxLevel();
                    }
                } else {
                    masterlevel = -1;
                }
                player.changeSkillLevel(skill, level, masterlevel);
                break;
            }
            case "!sp":
                int sp = Integer.parseInt(splitted[1]);
                if (sp + player.getRemainingSp() > Short.MAX_VALUE) {
                    sp = Short.MAX_VALUE;
                }
                player.setRemainingSp(sp);
                player.updateSingleStat(MapleStat.AVAILABLESP, player.getRemainingSp());
                break;
            case "!ap":
                int ap = Integer.parseInt(splitted[1]);
                if (ap + player.getRemainingAp() > Short.MAX_VALUE) {
                    ap = Short.MAX_VALUE;
                }
                player.setRemainingAp(ap);
                player.updateSingleStat(MapleStat.AVAILABLEAP, player.getRemainingAp());
                break;
            case "!job":
                int jobId = Integer.parseInt(splitted[1]);
                if (MapleJob.getById(jobId) != null) {
                    player.changeJob(MapleJob.getById(jobId));
                }
                break;
            case "!whereami":
                mc.dropMessage("You are on map " + player.getMapId() + ".");
                break;
            case "!shop":
                MapleShopFactory sfact = MapleShopFactory.getInstance();
                int shopId = Integer.parseInt(splitted[1]);
                if (sfact.getShop(shopId) != null) {
                    sfact.getShop(shopId).sendShop(c);
                }
                break;
            case "!meso":
                if (splitted.length > 1) {
                    if (splitted[1].equalsIgnoreCase("max")) {
                        player.gainMeso(Integer.MAX_VALUE - player.getMeso(), true, false, true);
                    } else if (StringUtil.isValidIntegerString(splitted[1])) {
                        if (Integer.MAX_VALUE - (player.getMeso() + Integer.parseInt(splitted[1])) >= 0) {
                            player.gainMeso(Integer.parseInt(splitted[1]), true, false, true);
                        } else {
                            player.gainMeso(Integer.MAX_VALUE - player.getMeso(), true, false, true);
                        }
                    } else {
                        mc.dropMessage("Please enter a valid meso amount.");
                    }
                } else {
                    mc.dropMessage("Please enter a meso amount.");
                }
                break;
            case "!levelup":
                if (player.getLevel() < 200) {
                    player.levelUp();
                    player.setExp(0);
                } else {
                    mc.dropMessage("You are already level 200.");
                }
                break;
            case "!item":
                if (splitted.length > 1) {
                    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    if (!StringUtil.isValidIntegerString(splitted[1]) || ii.getName(Integer.parseInt(splitted[1])) == null) {
                        mc.dropMessage("Please enter a valid Item ID.");
                        return;
                    }
                    int itemId = Integer.parseInt(splitted[1]);
                    short quantity = 1;
                    if (ii.isPet(itemId)) {
                        MapleInventoryManipulator.addById(c, itemId, quantity, player.getName() + "used !item with quantity 1", player.getName(), MaplePet.createPet(c.getPlayer().getId(), itemId), true);
                    } else if (ii.isRechargable(itemId)) {
                        MapleInventoryManipulator.addById(c, itemId, ii.getSlotMax(c, itemId), "Rechargable item created.", player.getName(), null, true);
                    } else if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                        MapleInventoryManipulator.addById(c, itemId, quantity, player.getName() + "used !item with quantity 1", player.getName(), null, true);
                    } else {
                        quantity = (short) getOptionalIntArg(splitted, 2, 1);
                        if (quantity > 0) {
                            MapleInventoryManipulator.addById(c, itemId, quantity, player.getName() + "used !item with quantity " + quantity, player.getName(), null, true);
                        } else {
                            c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.removeItem(c, ii.getInventoryType(itemId), itemId, -quantity, false)));
                        }
                    }
                    c.sendPacket(MaplePacketCreator.getShowItemGain(itemId, quantity, true));
                } else {
                    mc.dropMessage("Please enter the Item ID of the item you want.");
                }
                break;
            case "!eitem":
                if (splitted.length > 1) {
                    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    if (!StringUtil.isValidIntegerString(splitted[1]) || ii.getName(Integer.parseInt(splitted[1])) == null) {
                        mc.dropMessage("Please enter a valid Item ID.");
                        return;
                    }
                    int itemId = Integer.parseInt(splitted[1]);
                    int days = getOptionalIntArg(splitted, 2, 1);
                    IItem item;
                    if (ii.isPet(itemId)) {
                        item = new Item(itemId, (byte) 0, (short) 1).copy();
                        item.setPet(MaplePet.createPet(c.getPlayer().getId(), itemId));
                    } else if (ii.isRechargable(itemId)) {
                        item = new Item(itemId, (byte) 0, ii.getSlotMax(c, itemId)).copy();
                    } else if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                        item = ii.getEquipById(itemId);
                    } else {
                        item = new Item(itemId, (byte) 0, (short) 1).copy();
                    }
                    item.setOwner(player.getName());
                    item.setExpiration(new Timestamp(System.currentTimeMillis() + days * 86400000L));
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(c, item, "", false)));
                    c.sendPacket(MaplePacketCreator.getShowItemGain(itemId, item.getQuantity(), true));
                } else {
                    mc.dropMessage("Please enter the Item ID of the item you want and the number of days you want the item to last.");
                }
                break;
            case "!drop":
                if (splitted.length > 1) {
                    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    if (!StringUtil.isValidIntegerString(splitted[1]) || ii.getName(Integer.parseInt(splitted[1])) == null) {
                        mc.dropMessage("Please enter a valid Item ID.");
                        return;
                    }
                    int itemId = Integer.parseInt(splitted[1]);
                    short quantity = (short) getOptionalIntArg(splitted, 2, 1);
                    IItem toDrop;
                    if (ii.getInventoryType(itemId) == MapleInventoryType.EQUIP) {
                        toDrop = ii.randomizeStats((Equip) ii.getEquipById(itemId));
                    } else {
                        toDrop = new Item(itemId, (byte) 0, quantity);
                    }
                    toDrop.setGMFlag();
                    //toDrop.log("Created by " + player.getName() + " using !drop. Quantity: " + quantity, false);
                    toDrop.setOwner(player.getName());
                    player.getMap().spawnItemDrop(player, player, toDrop, player.getPosition(), true, true);
                } else {
                    mc.dropMessage("Please enter the Item ID of the item you want.");
                }
                break;
            case "!level": {
                if (splitted.length < 2) {
                    mc.dropMessage("!level <Desired Level>");
                } else {
                    int level;
                    try {
                        level = Integer.parseInt(splitted[1]);
                    } catch (Exception e) {
                        mc.dropMessage("Please enter a valid level from 1 to 200.");
                        return;
                    }
                    if (level >= 1 && level <= 200) {
                        c.getPlayer().setLevel(level);
                        c.getPlayer().levelUp();
                        if (level >= 200) {
                            c.getPlayer().setExp(0);
                            player.updateSingleStat(MapleStat.EXP, 0);
                        } else {
                            int newexp = c.getPlayer().getExp();
                            if (newexp < 0) {
                                c.getPlayer().gainExp(-newexp, false, false);
                            }
                        }
                    } else {
                        mc.dropMessage("Please enter a level between 1 and 200.");
                    }
                }
                break;
            }
            case "!maxlevel":
                while (player.getLevel() < 200) {
                    player.levelUp();
                }
                player.gainExp(-player.getExp(), false, false);
                break;
            case "!ring":
                int itemId = Integer.parseInt(splitted[1]);
                String partnerName = splitted[2];
                String message = splitted[3];
                int ret = MapleRing.createRing(itemId, player, player.getClient().getChannelServer().getPlayerStorage().getCharacterByName(partnerName), message);
                if (ret == -1) {
                    mc.dropMessage("Error - Make sure the person you are attempting to create a ring with is online.");
                }
                break;
            case "!ariantpq":
                if (splitted.length < 2) {
                    player.getMap().ariantPQStart();
                } else {
                    c.sendPacket(MaplePacketCreator.updateAriantPQRanking(splitted[1], 5, false));
                }
                break;
            case "!scoreboard":
                player.getMap().broadcastMessage(MaplePacketCreator.showAriantScoreBoard());
                break;
            case "!clearinvent":
                if (splitted.length < 2) {
                    mc.dropMessage("Please specify which tab to clear. If you want to clear all, use '!clearinvent all'.");
                } else {
                    String type = splitted[1];
                    boolean pass = false;
                    if (type.equals("equip") || type.equals("all")) {
                        pass = true;
                        c.getPlayer().getInventory(MapleInventoryType.EQUIP).list().clear();
                    }
                    if (type.equals("use") || type.equals("all")) {
                        pass = true;
                        c.getPlayer().getInventory(MapleInventoryType.USE).list().clear();
                    }
                    if (type.equals("etc") || type.equals("all")) {
                        pass = true;
                        c.getPlayer().getInventory(MapleInventoryType.ETC).list().clear();
                    }
                    if (type.equals("etc") || type.equals("all")) {
                        pass = true;
                        c.getPlayer().getInventory(MapleInventoryType.SETUP).list().clear();
                    }
                    if (type.equals("cash") || type.equals("all")) {
                        pass = true;
                        c.getPlayer().getInventory(MapleInventoryType.CASH).list().clear();
                    }
                    if (pass) {
                        mc.dropMessage("Your inventory has been cleared!");
                    } else {
                        mc.dropMessage("!clearinvent " + type + " does not exist!");
                    }
                }
                break;
            case "!godmode":
                player.setGodmode(!player.hasGodmode());
                mc.dropMessage("You are now " + (player.hasGodmode() ? "" : "not ") + "in godmode.");
                break;
            case "!eventlevel":
                int minlevel = Integer.parseInt(splitted[1]);
                int maxlevel = Integer.parseInt(splitted[2]);
                int map = Integer.parseInt(splitted[3]);
                int minutes = getOptionalIntArg(splitted, 4, 5);
                if (splitted.length < 4) {
                    mc.dropMessage("Syntax Error: !eventlevel <minlevel> <maxlevel> <mapid> <minutes>");
                    return;
                }
                c.getChannelServer().startEvent(minlevel, maxlevel, map);
                final MapleNPC npc = MapleLifeFactory.getNPC(9201093);
                npc.setPosition(c.getPlayer().getPosition());
                npc.setCy(c.getPlayer().getPosition().y);
                npc.setRx0(c.getPlayer().getPosition().x + 50);
                npc.setRx1(c.getPlayer().getPosition().x - 50);
                npc.setFh(c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition()).getId());
                npc.setCustom(true);
                c.getPlayer().getMap().addMapObject(npc);
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
                MaplePacket msgpacket = MaplePacketCreator.serverNotice(6, "The NPC " + npc.getName() + " will be in " + c.getPlayer().getMap().getMapName() + " for " + minutes + " minutes(s). Please talk to it to be warped to the Event (Must be in between level " + minlevel + " and " + maxlevel + ")");
                c.getChannelServer().getWorldInterface().broadcastMessage(c.getPlayer().getName(), msgpacket.getBytes());
                final MapleCharacter playerr = c.getPlayer();
                TimerManager.getInstance().schedule(() -> {
                    for (MapleMapObject npcmo : playerr.getMap().getAllNPCs()) {
                        MapleNPC fnpc = (MapleNPC) npcmo;
                        if (fnpc.isCustom() && fnpc.getId() == npc.getId()) {
                            playerr.getMap().removeMapObject(fnpc.getObjectId());
                        }
                    }
                }, minutes * 60 * 1000L);
                break;
            case "!setmarried":
                MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
                MapleCharacter partner = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[2]);
                if (victim != null && partner != null) {
                    victim.setMarried(Integer.parseInt(splitted[3]));
                    victim.setPartnerId(partner.getId());
                } else {
                    mc.dropMessage("Make sure both players are online and on the same channel as you.");
                }
                break;
            case "!togglewhitetext":
                player.setWhiteText(!player.getWhiteText());
                mc.dropMessage("You will now type in " + (player.getWhiteText() ? "white" : "normal") + " text.");
                break;
            case "!maxskills":
                mc.dropMessage("Maxing. ... ");
                player.maxSkills();
                mc.dropMessage("Maxed skills");
                break;
            case "!dojoenergy":
                c.getPlayer().getDojo().setEnergy(10000);
                c.sendPacket(MaplePacketCreator.setDojoEnergy(10000));
                break;
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("maxstats", "", "", 4),
                new CommandDefinition("minstats", "", "", 4),
                new CommandDefinition("maxhp", "", "", 4),
                new CommandDefinition("maxmp", "", "", 4),
                new CommandDefinition("hp", "", "", 4),
                new CommandDefinition("mp", "", "", 4),
                new CommandDefinition("sstr", "", "", 4),
                new CommandDefinition("sdex", "", "", 4),
                new CommandDefinition("sint", "", "", 4),
                new CommandDefinition("sluk", "", "", 4),
                new CommandDefinition("skill", "", "", 4),
                new CommandDefinition("sp", "", "", 4),
                new CommandDefinition("ap", "", "", 4),
                new CommandDefinition("godmode", "", "", 4),
                new CommandDefinition("job", "", "", 4),
                new CommandDefinition("whereami", "", "", 4),
                new CommandDefinition("shop", "", "", 4),
                new CommandDefinition("meso", "", "", 4),
                new CommandDefinition("levelup", "", "", 4),
                new CommandDefinition("item", "", "", 5),
                new CommandDefinition("eitem", "", "", 5),
                new CommandDefinition("drop", "", "", 5),
                new CommandDefinition("level", "", "", 4),
                new CommandDefinition("maxlevel", "", "", 4),
                new CommandDefinition("ring", "", "", 4),
                new CommandDefinition("ariantpq", "", "", 5),
                new CommandDefinition("scoreboard", "", "", 5),
                new CommandDefinition("playernpc", "", "", 5),
                new CommandDefinition("clearinvent", "<all, equip, use, etc, setup, cash>", "Clears the desired inventory", 4),
                new CommandDefinition("eventlevel", "<minlevel> <maxlevel> <mapid> <minutes>", "Spawns NPC to warp to an event", 4),
                new CommandDefinition("setmarried", "", "", 5),
                new CommandDefinition("togglewhitetext", "", "", 1),
                new CommandDefinition("maxskills", "", "", 4),
                new CommandDefinition("dojoenergy", "", "Fills your dojo energy", 4)
        };
    }
}