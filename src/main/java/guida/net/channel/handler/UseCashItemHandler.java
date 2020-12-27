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

package guida.net.channel.handler;

import guida.client.Equip;
import guida.client.ExpTable;
import guida.client.IItem;
import guida.client.ISkill;
import guida.client.ItemFlag;
import guida.client.MapleCharacter;
import guida.client.MapleCharacterUtil;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MaplePet;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.client.messages.ServernoticeMapleClientMessageCallback;
import guida.net.AbstractMaplePacketHandler;
import guida.net.world.remote.WorldLocation;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MaplePortal;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMist;
import guida.server.maps.MapleTVEffect;
import guida.server.playerinteractions.HiredMerchant;
import guida.server.playerinteractions.MaplePlayerShopItem;
import guida.tools.FileTimeUtil;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Rectangle;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

public class UseCashItemHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UseCashItemHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final short slot = slea.readShort();
        final int itemId = slea.readInt();
        final int itemType = itemId / 10000;
        c.getPlayer().resetAfkTimer();
        final IItem toUse = c.getPlayer().getInventory(ii.getInventoryType(itemId)).getItem(slot);
        if (toUse == null || toUse.getItemId() != itemId || toUse.getQuantity() < 1) {
            return;
        }
        try {
            switch (itemType) {
                case 504: //teleport rock
                    final byte rocktype = slea.readByte();
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    if (rocktype == 0) {
                        final int mapId = slea.readInt();
                        final MapleMap target = c.getChannelServer().getMapFactory().getMap(mapId);
                        final MaplePortal targetPortal = target.getPortal(0);
                        if (target.getForcedReturnId() == 999999999 && target.canVipRock() && c.getPlayer().getMap().canExit() && target.canEnter()) { //Makes sure this map doesn't have a forced return map
                            c.getPlayer().changeMap(target, targetPortal);
                        } else {
                            MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Not found)");
                            new ServernoticeMapleClientMessageCallback(1, c).dropMessage("Either the player could not be found or you were trying to teleport to an illegal location.");
                            c.sendPacket(MaplePacketCreator.enableActions());
                        }
                    } else {
                        final String name = slea.readMapleAsciiString();
                        final MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
                        if (victim != null) {
                            final MapleMap target = victim.getMap();
                            final WorldLocation loc = c.getChannelServer().getWorldInterface().getLocation(name);
                            final int mapid = victim.getMapId();
                            if (!(mapid >= 240050000 && mapid <= 240060200 || mapid < 100000000 || mapid >= 280010010 && mapid <= 280030000 || mapid >= 670000100 && mapid <= 670011000 || mapid >= 809020000 || mapid >= 101000100 && mapid <= 101000104 || mapid == 101000301 || mapid >= 105040310 && mapid <= 105040316 || mapid >= 108000100 && mapid <= 109080003 || mapid >= 190000000 && mapid <= 197010000 || mapid >= 200090000 && mapid <= 209080000 || mapid == 240000110 || mapid == 240000111 || mapid == 260000110) && c.getPlayer().getMap().canExit() && target.canEnter()) { //disallowed maps
                                if (c.getChannelServer().getMapFactory().getMap(loc.map).getForcedReturnId() == 999999999 && c.getChannelServer().getMapFactory().getMap(loc.map).canVipRock()) {//This doesn't allow tele to GM map, zakum and etc...
                                    if (!victim.isHidden() && !victim.hasGMLevel(2)) {
                                        if (itemId == 5041000) { //viprock
                                            c.getPlayer().changeMap(target, target.findClosestSpawnPoint(victim.getPosition()));
                                        } else if (mapid / 100000000 == c.getPlayer().getMapId() / 100000000) { //same continent
                                            c.getPlayer().changeMap(target, target.findClosestSpawnPoint(victim.getPosition()));
                                        } else {
                                            MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Not found)");
                                            new ServernoticeMapleClientMessageCallback(1, c).dropMessage("Either the player could not be found or you were trying to teleport to an illegal location.");
                                            c.sendPacket(MaplePacketCreator.enableActions());
                                        }
                                    } else {
                                        MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Not found)");
                                        new ServernoticeMapleClientMessageCallback(1, c).dropMessage("Either the player could not be found or you were trying to teleport to an illegal location.");
                                        c.sendPacket(MaplePacketCreator.enableActions());
                                    }
                                } else {
                                    MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Can't Teleport)");
                                    new ServernoticeMapleClientMessageCallback(1, c).dropMessage("You cannot teleport to this map.");
                                    c.sendPacket(MaplePacketCreator.enableActions());
                                }
                            } else {
                                MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Can't Teleport)");
                                c.getPlayer().dropMessage("The player you are trying to warp to is in a disallowed map.");
                                c.sendPacket(MaplePacketCreator.enableActions());
                            }
                        } else {
                            MapleInventoryManipulator.addById(c, itemId, (short) 1, "Teleport Rock Error (Not found)");
                            new ServernoticeMapleClientMessageCallback(1, c).dropMessage("Player could not be found in this channel.");
                            c.sendPacket(MaplePacketCreator.enableActions());
                        }
                    }
                    break;
                case 505: // AP/SP reset
                    if (itemId > 5050000) {
                        final MapleCharacter player = c.getPlayer();

                        final int SPTo = slea.readInt();
                        final int SPFrom = slea.readInt();

                        final ISkill skillSPTo = SkillFactory.getSkill(SPTo);
                        final ISkill skillSPFrom = SkillFactory.getSkill(SPFrom);

                        final int maxlevel = skillSPTo.isFourthJob() ? player.getMasterLevel(skillSPTo) : skillSPTo.getMaxLevel();
                        final int curLevel = player.getSkillLevel(skillSPTo);
                        final int curLevelSPFrom = player.getSkillLevel(skillSPFrom);
                        if (curLevel + 1 <= maxlevel && curLevelSPFrom > 0) {
                            player.changeSkillLevel(skillSPFrom, curLevelSPFrom - 1, skillSPFrom.isFourthJob() ? player.getMasterLevel(skillSPFrom) : 0);
                            if (skillSPTo.hasRequirements()) {
                                for (Entry<Integer, Integer> reqLevel : skillSPTo.getRequirements().entrySet()) {
                                    if (player.getSkillLevel(SkillFactory.getSkill(reqLevel.getKey())) < reqLevel.getValue()) {
                                        player.changeSkillLevel(skillSPFrom, curLevelSPFrom + 1, skillSPFrom.isFourthJob() ? player.getMasterLevel(skillSPFrom) : 0);
                                        c.disconnect();
                                        return;
                                    }
                                }
                            }
                            player.changeSkillLevel(skillSPTo, curLevel + 1, skillSPTo.isFourthJob() ? maxlevel : 0);
                        }
                    } else {
                        if (!c.getPlayer().canUseApReset()) {
                            c.getPlayer().dropMessage(1, "Your AP does not confirm to your level.");
                            c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                            return;
                        }
                        final List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(2);
                        final int APTo = slea.readInt();
                        final int APFrom = slea.readInt();
                        final MapleJob job = c.getPlayer().getJob();
                        ISkill improvingMaxHP;
                        ISkill improvingMaxMP;
                        switch (APFrom) {
                            case 64: // str
                                if (c.getPlayer().getStr() <= 4) {
                                    return;
                                }
                                c.getPlayer().setStr(c.getPlayer().getStr() - 1);
                                statupdate.add(new Pair<>(MapleStat.STR, c.getPlayer().getStr()));
                                break;
                            case 128: // dex
                                if (c.getPlayer().getDex() <= 4) {
                                    return;
                                }
                                c.getPlayer().setDex(c.getPlayer().getDex() - 1);
                                statupdate.add(new Pair<>(MapleStat.DEX, c.getPlayer().getDex()));
                                break;
                            case 256: // int
                                if (c.getPlayer().getInt() <= 4) {
                                    return;
                                }
                                c.getPlayer().setInt(c.getPlayer().getInt() - 1);
                                statupdate.add(new Pair<>(MapleStat.INT, c.getPlayer().getInt()));
                                break;
                            case 512: // luk
                                if (c.getPlayer().getLuk() <= 4) {
                                    return;
                                }
                                c.getPlayer().setLuk(c.getPlayer().getLuk() - 1);
                                statupdate.add(new Pair<>(MapleStat.LUK, c.getPlayer().getLuk()));
                                break;
                            case 2048: // HP
                                if (!isValidStats(c.getPlayer(), true)) {
                                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                    return;
                                }
                                int maxHP = c.getPlayer().getMaxHp();
                                if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                                    maxHP -= 12;
                                } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                                    if (job.isA(MapleJob.WARRIOR)) {
                                        improvingMaxHP = SkillFactory.getSkill(1000001);
                                    } else {
                                        improvingMaxHP = SkillFactory.getSkill(11000000);
                                    }
                                    final int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxHP -= 24;
                                    if (improvingMaxHPLevel >= 1) {
                                        maxHP -= improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                                    }
                                } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                                    maxHP -= 10;
                                } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                                    maxHP -= 20;
                                } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                                    if (job.isA(MapleJob.PIRATE)) {
                                        improvingMaxHP = SkillFactory.getSkill(5100000);
                                    } else {
                                        improvingMaxHP = SkillFactory.getSkill(15100000);
                                    }
                                    final int improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                                    maxHP -= 22;
                                    if (improvingMaxHPLevel >= 1) {
                                        maxHP -= improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                                    }
                                }
                                c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() - 1);
                                c.getPlayer().setMaxHp(maxHP);
                                c.getPlayer().setHp(maxHP);
                                statupdate.add(new Pair<>(MapleStat.HP, c.getPlayer().getMaxHp()));
                                statupdate.add(new Pair<>(MapleStat.MAXHP, c.getPlayer().getMaxHp()));
                                break;
                            case 8192: // MP
                                if (!isValidStats(c.getPlayer(), false)) {
                                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                    return;
                                }
                                int maxMP = c.getPlayer().getMaxMp();
                                if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                                    maxMP -= 8;
                                } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                                    maxMP -= 4;
                                } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                                    if (job.isA(MapleJob.MAGICIAN)) {
                                        improvingMaxMP = SkillFactory.getSkill(2000001);
                                    } else {
                                        improvingMaxMP = SkillFactory.getSkill(12000000);
                                    }
                                    final int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
                                    maxMP -= 21;
                                    if (improvingMaxMPLevel >= 1) {
                                        maxMP -= improvingMaxMP.getEffect(improvingMaxMPLevel).getY();
                                    }
                                } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                                    maxMP -= 12;
                                } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                                    maxMP -= 16;
                                }
                                c.getPlayer().setMpApUsed(c.getPlayer().getMpApUsed() - 1);
                                c.getPlayer().setMaxMp(maxMP);
                                c.getPlayer().setMp(maxMP);
                                statupdate.add(new Pair<>(MapleStat.MP, c.getPlayer().getMaxMp()));
                                statupdate.add(new Pair<>(MapleStat.MAXMP, c.getPlayer().getMaxMp()));
                                break;
                            default:
                                c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                return;
                        }
                        switch (APTo) {
                            case 64: // str
                                if (c.getPlayer().getStr() >= 999) {
                                    return;
                                }
                                c.getPlayer().setStr(c.getPlayer().getStr() + 1);
                                statupdate.add(new Pair<>(MapleStat.STR, c.getPlayer().getStr()));
                                break;
                            case 128: // dex
                                if (c.getPlayer().getDex() >= 999) {
                                    return;
                                }
                                c.getPlayer().setDex(c.getPlayer().getDex() + 1);
                                statupdate.add(new Pair<>(MapleStat.DEX, c.getPlayer().getDex()));
                                break;
                            case 256: // int
                                if (c.getPlayer().getInt() >= 999) {
                                    return;
                                }
                                c.getPlayer().setInt(c.getPlayer().getInt() + 1);
                                statupdate.add(new Pair<>(MapleStat.INT, c.getPlayer().getInt()));
                                break;
                            case 512: // luk
                                if (c.getPlayer().getLuk() >= 999) {
                                    return;
                                }
                                c.getPlayer().setLuk(c.getPlayer().getLuk() + 1);
                                statupdate.add(new Pair<>(MapleStat.LUK, c.getPlayer().getLuk()));
                                break;
                            case 2048: // hp
                                int maxHP = c.getPlayer().getMaxHp();
                                if (c.getPlayer().getHpApUsed() == 10000 || maxHP >= 30000) {
                                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                    return;
                                }
                                if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                                    maxHP += 8;
                                } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                                    maxHP += 20;
                                } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                                    maxHP += 6;
                                } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                                    maxHP += 16;
                                } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                                    maxHP += 18;
                                }
                                maxHP = Math.min(30000, maxHP);
                                c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() + 1);
                                c.getPlayer().setMaxHp(maxHP);
                                statupdate.add(new Pair<>(MapleStat.MAXHP, c.getPlayer().getMaxHp()));
                                break;
                            case 8192: // mp
                                int maxMP = c.getPlayer().getMaxMp();
                                if (c.getPlayer().getMpApUsed() == 10000 || maxMP >= 30000) {
                                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                    return;
                                }
                                if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                                    maxMP += 6;
                                } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                                    maxMP += 2;
                                } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                                    maxMP += 18;
                                } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                                    maxMP += 10;
                                } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                                    maxMP += 14;
                                }
                                maxMP = Math.min(30000, maxMP);
                                c.getPlayer().setMpApUsed(c.getPlayer().getMpApUsed() + 1);
                                c.getPlayer().setMaxMp(maxMP);
                                statupdate.add(new Pair<>(MapleStat.MAXMP, c.getPlayer().getMaxMp()));
                                break;
                            default:
                                c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                                return;
                        }
                        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
                    }
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 506: {
                    final int itemChangeType = itemId % 10;
                    IItem item = null;
                    int action;
                    switch (itemId / 1000 % 10) {
                        case 0:
                            if (itemChangeType == 0) { // Item tag.
                                final short equipSlot = slea.readShort();
                                action = slea.readInt();
                                if (action <= c.getLastAction() || equipSlot == 0) {
                                    c.sendPacket(MaplePacketCreator.enableActions());
                                    return;
                                }
                                c.setLastAction(action);
                                item = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(equipSlot);
                                item.setOwner(c.getPlayer().getName());
                            } else if (itemChangeType == 1) { // Sealing lock
                                final byte type = (byte) slea.readInt();
                                final short slot_ = (short) slea.readInt();
                                action = slea.readInt();
                                if (action <= c.getLastAction()) {
                                    c.sendPacket(MaplePacketCreator.enableActions());
                                    return;
                                }
                                c.setLastAction(action);
                                item = c.getPlayer().getInventory(MapleInventoryType.getByType(type)).getItem(slot_);
                                if (!ii.isCashItem(item.getItemId())) {
                                    item.setFlag((short) (item.getFlag() | ItemFlag.LOCK.getValue()));
                                    item.setExpiration(FileTimeUtil.getDefaultTimestamp());
                                }
                            } else if (itemChangeType == 2) { // Incubator, what does this do?
                                return;
                            }
                            break;
                        case 1:
                            final byte type = (byte) slea.readInt();
                            final short slot_ = (short) slea.readInt();
                            action = slea.readInt();
                            if (action <= c.getLastAction()) {
                                c.sendPacket(MaplePacketCreator.enableActions());
                                return;
                            }
                            c.setLastAction(action);
                            item = c.getPlayer().getInventory(MapleInventoryType.getByType(type)).getItem(slot_);
                            if (!ii.isCashItem(item.getItemId())) {
                                if ((item.getFlag() & ItemFlag.LOCK.getValue()) != 1) {
                                    item.setFlag((short) (item.getFlag() | ItemFlag.LOCK.getValue()));
                                    item.setExpiration(new Timestamp(System.currentTimeMillis() + ii.getProtectionTime(itemId) * 86400000L));
                                } else {
                                    item.setExpiration(new Timestamp(item.getExpiration().getTime() + ii.getProtectionTime(itemId) * 86400000L));
                                }
                            }
                            break;
                    }
                    if (item != null) {
                        final List<Pair<Short, IItem>> equipUpdate = new ArrayList<>(2);
                        equipUpdate.add(new Pair<>((short) 3, item));
                        equipUpdate.add(new Pair<>((short) 0, item));
                        c.sendPacket(MaplePacketCreator.modifyInventory(true, equipUpdate));
                    }
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                }
                case 507: {
                    final MapleCharacter player = c.getPlayer();
                    if (player.isMuted() || player.getMap().getMuted()) {
                        return;
                    }
                    String text = null;
                    boolean whisper;
                    int action;
                    final IItem medal = c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem((short) -49);
                    String medalName = "";
                    if (medal != null) {
                        medalName = "<" + ii.getName(medal.getItemId()) + "> ";
                    }
                    switch (itemId / 1000 % 10) {
                        case 1: // Megaphone
                            text = slea.readMapleAsciiString();
                            if (text.length() > 60) {
                                return;
                            }
                            if (player.getLevel() >= 10) {
                                player.getMap().broadcastMessage(MaplePacketCreator.serverNotice(2, player.getName() + " : " + text));
                            } else { // Client does it but ehh..
                                player.dropMessage("You may not use this until you're level 10.");
                            }
                            break;
                        case 2: // Super megaphone
                            text = slea.readMapleAsciiString();
                            if (text.length() > 60) {
                                return;
                            }
                            whisper = slea.readByte() == 1;
                            action = slea.readInt();
                            if (action <= c.getLastAction()) {
                                c.sendPacket(MaplePacketCreator.enableActions());
                                return;
                            }
                            c.setLastAction(action);
                            c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.serverNotice(3, c.getChannel(), medalName + c.getPlayer().getName() + " : " + text, whisper).getBytes(), true);
                            break;
                        case 3: // Heart megaphone
                        case 4: // Skull megaphone
                            log.info("Unhandled Megaphone Packet : " + slea.toString());
                            log.info("Megaphone ID: " + itemId);
                            break;
                        case 5: // Maple TV
                            final int tvType = itemId % 10;
                            boolean megassenger = false;
                            boolean ear = false;
                            MapleCharacter victim = null;

                            if (tvType != 1) { // 1 is the odd one out since it doesnt allow 2 players.
                                if (tvType >= 3) {
                                    megassenger = true;
                                    if (tvType == 3) {
                                        slea.readByte();
                                    }
                                    ear = 1 == slea.readByte();
                                } else if (tvType != 2) {
                                    slea.readByte();
                                }
                                if (tvType != 4) {
                                    victim = c.getChannelServer().getPlayerStorage().getCharacterByName(slea.readMapleAsciiString());
                                }
                            }
                            final List<String> messages = new LinkedList<>();
                            final StringBuilder builder = new StringBuilder();
                            for (int i = 0; i < 5; i++) {
                                final String message = slea.readMapleAsciiString();
                                if (megassenger) {
                                    builder.append(" ");
                                    builder.append(message);
                                }
                                messages.add(message);
                            }
                            slea.readInt();
                            if (megassenger) {
                                text = builder.toString();
                                if (text.length() <= 60) {
                                    c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.serverNotice(3, c.getChannel(), player.getName() + " : " + builder.toString(), ear).getBytes(), true);
                                }
                            }
                            if (!MapleTVEffect.isActive()) {
                                new MapleTVEffect(player, victim, messages, tvType);
                            } else {
                                player.dropMessage("MapleTV is already in use.");
                            }
                            break;
                        case 6: // Item Megaphone
                            int itemSmegaItemType = 0;
                            short itemSmegaItemPosition = 0;
                            IItem itemSmegaItem = null;
                            text = slea.readMapleAsciiString();
                            if (text.length() > 60) {
                                return;
                            }
                            whisper = slea.readByte() == 1;
                            final boolean showItem = slea.readByte() == 1;
                            if (showItem) {
                                itemSmegaItemType = slea.readInt();
                                itemSmegaItemPosition = (short) slea.readInt();
                                if (itemSmegaItemPosition < 0) {
                                    itemSmegaItemType = -1;
                                }
                                itemSmegaItem = c.getPlayer().getInventory(MapleInventoryType.getByType((byte) itemSmegaItemType)).getItem(itemSmegaItemPosition);
                            }
                            action = slea.readInt();
                            if (action <= c.getLastAction()) {
                                c.sendPacket(MaplePacketCreator.enableActions());
                                return;
                            }
                            c.setLastAction(action);
                            c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.itemSuperMegaphone(8, c.getChannel(), medalName + c.getPlayer().getName() + " : " + text, whisper, itemSmegaItem).getBytes(), true);
                            break;
                        case 7:
                            final byte numLines = slea.readByte();
                            final String[] message = {"", "", ""};
                            for (int i = 0; i < (numLines > 3 ? 3 : numLines); i++) {
                                message[i] = medalName + c.getPlayer().getName() + " : " + slea.readMapleAsciiString();
                            }
                            whisper = slea.readByte() == 1;
                            action = slea.readInt();
                            if (numLines > 3 || action <= c.getLastAction()) {
                                c.sendPacket(MaplePacketCreator.enableActions());
                                return;
                            }
                            c.setLastAction(action);
                            c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getTripleMegaphone(numLines, message, c.getChannel(), whisper).getBytes(), true);
                            break;
                    }
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                }
                case 509: //some note sending shit
                    // 49 00 04 00 D0 AA 4D 00 | 05 00 46 65 65 74 79 04 00 74 65 73 74 DA C6 C9 1D
                    final String sendTo = slea.readMapleAsciiString();
                    final String msg = slea.readMapleAsciiString();
                    try {
                        c.getPlayer().sendNote(sendTo, msg);
                    } catch (SQLException e) {
                        log.error("SAVING NOTE", e);
                    }
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 510: //something that plays music?
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.musicChange("Jukebox/Congratulation"));
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 512:
                    c.getPlayer().getMap().startMapEffect(ii.getMsg(itemId).replaceFirst("%s", c.getPlayer().getName()).replaceFirst("%s", slea.readMapleAsciiString()), itemId);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 517:
                    MaplePet pet = c.getPlayer().getPet(0);
                    if (pet == null) {
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    final String newName = slea.readMapleAsciiString();
                    if (newName.length() > 13 || MapleCharacterUtil.isBanned(newName)) {
                        return;
                    }
                    pet.setName(newName);
                    c.getPlayer().updatePet(pet);
                    c.sendPacket(MaplePacketCreator.enableActions());
                    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.updatePetNameTag(c.getPlayer(), newName, 0, pet.hasLabelRing()), true);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 520:
                    c.getPlayer().gainMeso(ii.getMeso(itemId), true, false, true);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    c.sendPacket(MaplePacketCreator.enableActions());
                    break;
                case 523:
                    final int iid = slea.readInt();
                    final List<Pair<HiredMerchant, MaplePlayerShopItem>> result = HiredMerchant.getMerchsByIId(iid, true);
                    c.sendPacket(MaplePacketCreator.sendMinerva(result, iid));
                    if (!result.isEmpty()) {
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                        c.getPlayer().minerving(true);
                    }
                    break;
                case 524: // pet intimacy food
                    for (int i = 0; i < 3; i++) {
                        MaplePet targetPet = c.getPlayer().getPet(i);
                        if (targetPet != null) {
                            if (targetPet.canConsume(itemId)) {
                                targetPet.setFullness(100);
                                final int closeGain = 100 * c.getChannelServer().getPetExpRate();
                                targetPet.setCloseness(Math.min(targetPet.getCloseness() + closeGain, 30000));
                                while (targetPet.getCloseness() >= ExpTable.getClosenessNeededForLevel(targetPet.getLevel() + 1)) {
                                    targetPet.setLevel(targetPet.getLevel() + 1);
                                    c.sendPacket(MaplePacketCreator.showOwnPetLevelUp((byte) 0, c.getPlayer().getPetIndex(targetPet)));
                                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.showPetLevelUp(c.getPlayer(), c.getPlayer().getPetIndex(targetPet)));
                                }
                                c.getPlayer().updatePet(targetPet);
                                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), (byte) 1, 0, true, true, targetPet.hasQuoteRing()), true);
                                MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                            }
                        } else {
                            c.sendPacket(MaplePacketCreator.enableActions());
                            break;
                        }
                    }
                    break;
                case 528:
                    if (itemId == 5281000) {
                        final Rectangle bounds = new Rectangle((int) c.getPlayer().getPosition().getX(), (int) c.getPlayer().getPosition().getY(), 1, 1);
                        final MapleMist mist = new MapleMist(bounds, c.getPlayer(), null);
                        c.getPlayer().getMap().spawnMist(mist, 10000, true);
                        c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getChatText(c.getPlayer().getId(), "Oh no, I farted!", false, 1));
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                        c.sendPacket(MaplePacketCreator.enableActions());
                    }
                    break;
                case 530:
                    ii.getItemEffect(itemId).applyTo(c.getPlayer());
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 533: // Duey quick delivery ticket
                    c.sendPacket(MaplePacketCreator.sendDuey((byte) 8, DueyActionHandler.loadItems(c.getPlayer())));
                    break;
                case 537:
                    if (c.getPlayer().isMuted() || c.getPlayer().getMap().getMuted()) {
                        c.getPlayer().dropMessage(5, c.getPlayer().isMuted() ? "You are " : "The map is " + "muted, therefore you are unable to talk.");
                        return;
                    }
                    final String text = slea.readMapleAsciiString();
                    c.getPlayer().setChalkboard(text);
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.useChalkboard(c.getPlayer(), false));
                    c.getPlayer().getClient().sendPacket(MaplePacketCreator.enableActions());
                    break;
                case 539:
                    if (c.getPlayer().isMuted() || c.getPlayer().getMap().getMuted()) {
                        return;
                    }
                    final List<String> lines = new LinkedList<>();
                    for (int i = 0; i < 4; i++) {
                        lines.add(slea.readMapleAsciiString());
                    }
                    c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.getAvatarMega(c.getPlayer(), c.getChannel(), itemId, lines, (slea.readByte() != 0)).getBytes(), true);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                case 552: {
                    final int invType = slea.readInt();
                    if (invType != 1) {
                        return;
                    }
                    final short slotApplied = (short) slea.readInt();
                    final int action = slea.readInt();
                    if (action <= c.getLastAction()) {
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    c.setLastAction(action);
                    final IItem equip = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(slotApplied);
                    if (equip.isSSOneOfAKind()) {
                        c.getPlayer().dropMessage(1, "You cannot use the Scissors of Karma on this item.");
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    if (ii.canTradeOnce(equip.getItemId())) {
                        equip.setFlag((short) (equip.getFlag() | ItemFlag.TRADE_ONCE.getValue()));
                        final List<Pair<Short, IItem>> equipUpdate = new ArrayList<>(2);
                        equipUpdate.add(new Pair<>((short) 3, equip));
                        equipUpdate.add(new Pair<>((short) 0, equip));
                        c.sendPacket(MaplePacketCreator.modifyInventory(true, equipUpdate));
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    }
                    break;
                }
                case 557:
                    final int invType = slea.readInt();
                    if (invType != 1) {
                        return;
                    }
                    final short slotApplied = (short) slea.readInt();
                    final int action = slea.readInt();
                    if (action <= c.getLastAction()) {
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    c.setLastAction(action);
                    final Equip applyTo = (Equip) c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(slotApplied);
                    if (applyTo.getViciousHammers() < 2 && applyTo.getItemId() != 1122000) {
                        applyTo.setUpgradeSlots((byte) (applyTo.getUpgradeSlots() + 1));
                        applyTo.setViciousHammers(applyTo.getViciousHammers() + 1);
                    }
                    c.sendPacket(MaplePacketCreator.sendHammerSlot(applyTo.getViciousHammers()));
                    c.getPlayer().setHammerSlot(slotApplied);
                    MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, itemId, 1, true, false);
                    break;
                default:
                    log.info("Unhandeled cash item; type = " + itemType);
                    break;
            }
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
            log.error("REMOTE ERROR", e);
        }
    }

    private boolean isValidStats(MapleCharacter chr, boolean isHP) {
        boolean isValid = false;
        final int level = chr.getLevel(), maxHP = chr.getMaxHp(), maxMP = chr.getMaxMp();
        switch (chr.getJob().getId()) {
            case 0:
            case 1000:
                if (isHP) {
                    isValid = maxHP >= level * 12 + 50;
                } else {
                    isValid = maxMP >= level * 10 + 2;
                }
                break;
            case 100:
            case 1100:
                if (isHP) {
                    isValid = maxHP >= level * 24 + 172;
                } else {
                    isValid = maxMP >= level * 4 + 59;
                }
                break;
            case 110:
            case 111:
            case 112:
            case 1110:
            case 1111:
            case 1112:
                if (isHP) {
                    isValid = maxHP >= level * 24 + 472;
                } else {
                    isValid = maxMP >= level * 4 + 59;
                }
                break;
            case 120:
            case 121:
            case 122:
            case 130:
            case 131:
            case 132:
                if (isHP) {
                    isValid = maxHP >= level * 24 + 172;
                } else {
                    isValid = maxMP >= level * 4 + 159;
                }
                break;
            case 200:
            case 1200:
                if (isHP) {
                    isValid = maxHP >= level * 10 + 64;
                } else {
                    isValid = maxMP >= level * 22 + 38;
                }
                break;
            case 210:
            case 211:
            case 212:
            case 220:
            case 221:
            case 222:
            case 230:
            case 231:
            case 232:
            case 1210:
            case 1211:
            case 1212:
                if (isHP) {
                    isValid = maxHP >= level * 10 + 64;
                } else {
                    isValid = maxMP >= level * 22 + 488;
                }
                break;
            case 300:
            case 400:
            case 1300:
            case 1400:
                if (isHP) {
                    isValid = maxHP >= level * 20 + 78;
                } else {
                    isValid = maxMP >= level * 14 - 3;
                }
                break;
            case 310:
            case 311:
            case 312:
            case 320:
            case 321:
            case 322:
            case 410:
            case 411:
            case 412:
            case 420:
            case 421:
            case 422:
            case 1310:
            case 1311:
            case 1312:
            case 1410:
            case 1411:
            case 1412:
                if (isHP) {
                    isValid = maxHP >= level * 20 + 378;
                } else {
                    isValid = maxMP >= level * 14 + 148;
                }
                break;
            case 500:
            case 1500:
                if (isHP) {
                    isValid = maxHP >= level * 22 + 80;
                } else {
                    isValid = maxMP >= level * 18 - 39;
                }
                break;
            case 510:
            case 511:
            case 512:
            case 520:
            case 521:
            case 522:
            case 1510:
            case 1511:
            case 1512:
                if (isHP) {
                    isValid = maxHP >= level * 22 + 380;
                } else {
                    isValid = maxMP >= level * 18 + 111;
                }
                break;
        }
        return isValid;
    }
}