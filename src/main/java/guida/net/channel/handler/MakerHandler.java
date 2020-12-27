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
import guida.client.IItem;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.SkillFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MakerItemFactory;
import guida.server.MakerItemFactory.MakerItemCreateEntry;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.Randomizer;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jay Estrella and PurpleMadness
 */
public class MakerHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final int type = slea.readInt();
        switch (type) {
            case 1 -> {
                final int toCreate = slea.readInt();
                int itemToGet = toCreate;
                final boolean stimulator = slea.readByte() == 1;
                List<Integer> gems = new ArrayList<>();
                final int numGems = slea.readInt();
                for (int i = 0; i < numGems; i++) {
                    int gem = slea.readInt();
                    if (!gems.contains(gem) && gem / 10000 == 425) {
                        gems.add(gem);
                    }
                }
                if (toCreate / 10000 == 425) { // are we making a gem :o
                    if (toCreate % 10 == 0) { //ii.getName(toCreate).startsWith("Basic")) {
                        boolean promoted = false;
                        if (Math.ceil(Math.random() * 100.0) > 85) { //15%
                            itemToGet++; // make it intermediate
                            promoted = true;
                        }
                        if (promoted && Math.ceil(Math.random() * 100.0) > 95) { //5%
                            c.getPlayer().finishAchievement(50);
                            itemToGet++; // make it advanced
                        }
                    } else if (toCreate % 10 == 1) { //ii.getName(toCreate).startsWith("Intermediate")) {
                        if (Math.ceil(Math.random() * 100.0) > 90) { //10%
                            itemToGet++; // make it advanced
                        }
                    }
                }
                MakerItemCreateEntry recipe = MakerItemFactory.getItemCreateEntry(toCreate);
                if (canCreate(c, recipe) && !c.getPlayer().getInventory(ii.getInventoryType(toCreate)).isFull()) {
                    final List<Pair<Short, IItem>> allChanges = new ArrayList<>();
                    final List<Pair<Integer, Integer>> itemAmount = new ArrayList<>();
                    for (Pair<Integer, Integer> p : recipe.getReqItems()) {
                        final int toRemove = p.getLeft();
                        final int count = p.getRight();
                        allChanges.addAll(MapleInventoryManipulator.removeItem(c, ii.getInventoryType(toRemove), toRemove, count, false));
                        itemAmount.add(new Pair<>(toRemove, -count));
                    }
                    if (stimulator && c.getPlayer().haveItem(recipe.getStimulator(), 1, false, false)) {
                        IItem create = ii.randomizeStats(applyGems((Equip) ii.getEquipById(toCreate), gems), true);
                        allChanges.addAll(MapleInventoryManipulator.removeItem(c, MapleInventoryType.ETC, recipe.getStimulator(), 1, false));
                        itemAmount.add(new Pair<>(recipe.getStimulator(), -1));
                        for (Integer gem : gems) {
                            allChanges.addAll(MapleInventoryManipulator.removeItem(c, MapleInventoryType.ETC, gem, 1, false));
                            itemAmount.add(new Pair<>(gem, -1));
                        }
                        if (Math.ceil(Math.random() * 100.0) > 10) {
                            allChanges.addAll(MapleInventoryManipulator.addByItem(c, create, "Creating item using Maker Skill", false));
                            itemAmount.add(new Pair<>(create.getItemId(), 1));
                        } else {
                            c.sendPacket(MaplePacketCreator.serverNotice(1, "The item was destroyed in the process."));
                        }
                    } else {
                        if (ii.getInventoryType(toCreate) != MapleInventoryType.EQUIP) {
                            allChanges.addAll(MapleInventoryManipulator.addByItemId(c, itemToGet, recipe.getRewardAmount(), "Creating item using Maker Skill", false));
                            itemAmount.add(new Pair<>(itemToGet, (int) recipe.getRewardAmount()));
                        } else {
                            IItem create = ii.randomizeStats(applyGems((Equip) ii.getEquipById(toCreate), gems));
                            allChanges.addAll(MapleInventoryManipulator.addByItem(c, create, "Creating item using Maker Skill", false));
                            itemAmount.add(new Pair<>(toCreate, (int) recipe.getRewardAmount()));
                            for (Integer gem : gems) {
                                allChanges.addAll(MapleInventoryManipulator.removeItem(c, MapleInventoryType.ETC, gem, 1, false));
                                itemAmount.add(new Pair<>(gem, -1));
                            }
                        }
                    }
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, allChanges));
                    c.sendPacket(MaplePacketCreator.getShowItemGain(itemAmount));
                } else {
                    c.disconnect(); // Should only occur if there's a packet editor!
                }
            }
            case 3 -> {
                final int itemId = slea.readInt();
                if (c.getPlayer().haveItem(itemId, 100, false, false)) {
                    if (getMonsterCrystal(itemId) != 0) {
                        int monsterCrystal = getMonsterCrystal(itemId);
                        MapleInventoryManipulator.removeById(c, MapleInventoryType.ETC, itemId, 100, false, false);
                        c.sendPacket(MaplePacketCreator.getShowItemGain(itemId, (short) -1, true));
                        MapleInventoryManipulator.addById(c, monsterCrystal, (short) 1, "Creating item using Maker Skill");
                        c.sendPacket(MaplePacketCreator.getShowItemGain(monsterCrystal, (short) 1, true));
                    }
                }
            }
            case 4 -> {
                final int disassembleId = slea.readInt();
                final int quantity = slea.readInt();
                final short position = slea.readShort();
                int reqLv = ii.getReqLevel(disassembleId);
                double maxCrystals = 5.0;
                if (ii.isWeapon(disassembleId)) {
                    maxCrystals = maxCrystals + 2.0; //make weapons slightly more rewarding to disassemble
                }
                int toMake = (int) Math.ceil(Math.random() * maxCrystals);
                int monsterCrystal = getMonsterCrystalByLevel(reqLv);
                final IItem toUse = c.getPlayer().getInventory(ii.getInventoryType(disassembleId)).getItem(position);
                if (toUse == null || toUse.getItemId() != disassembleId || toUse.getQuantity() < 1) {
                    return;
                }
                if (monsterCrystal != 0) {
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.EQUIP, position, quantity, false, false);
                    c.sendPacket(MaplePacketCreator.getShowItemGain(disassembleId, (short) -1, true));
                    MapleInventoryManipulator.addById(c, monsterCrystal, (short) toMake, "Creating item using Maker Skill");
                    c.sendPacket(MaplePacketCreator.getShowItemGain(monsterCrystal, (short) toMake, true));
                }
            }
        }
    }

    private boolean canCreate(MapleClient c, MakerItemCreateEntry recipe) {
        return hasItems(c, recipe) && hasMesos(c, recipe) && hasLevel(c, recipe) && hasSkill(c, recipe);
    }

    private boolean hasItems(MapleClient c, MakerItemCreateEntry recipe) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (Pair<Integer, Integer> p : recipe.getReqItems()) {
            int count = p.getRight();
            int itemId = p.getLeft();

            if (c.getPlayer().getInventory(ii.getInventoryType(itemId)).countById(itemId) < count) {
                return false;
            }
        }

        return true;
    }

    private boolean hasMesos(MapleClient c, MakerItemCreateEntry recipe) {
        return c.getPlayer().getMeso() >= recipe.getCost();
    }

    private boolean hasLevel(MapleClient c, MakerItemCreateEntry recipe) {
        return c.getPlayer().getLevel() >= recipe.getReqLevel();
    }

    private boolean hasSkill(MapleClient c, MakerItemCreateEntry recipe) {
        if (c.getPlayer().getJob().getId() >= 1000) // KoC Maker skill.
        {
            return c.getPlayer().getSkillLevel(SkillFactory.getSkill(10001007)) >= recipe.getReqSkillLevel();
        } else {
            return c.getPlayer().getSkillLevel(SkillFactory.getSkill(1007)) >= recipe.getReqSkillLevel();
        }
    }

    private int getMonsterCrystal(int etcId) {
        int monsterCrystal = switch (etcId - 4000000) {
//basic 1
            case 123, 24, 86, 32, 167, 107, 95, 87, 73, 370, 371, 96, 109, 113, 99, 13, 67, 88, 35, 59, 103, 108, 153, 115, 43, 26, 29, 104, 154, 100, 105, 110, 116, 117, 276, 23, 222, 159, 158, 114, 31, 76, 120, 278, 290, 277, 111, 101, 157, 155, 58, 118, 78, 156, 112, 204, 14, 89, 178, 102, 60, 169, 45, 62, 90, 205, 44, 36, 125, 91, 170, 48, 286 -> 4260000;
//basic 2
            case 81, 33, 61, 70, 72, 171, 71, 126, 51, 41, 22, 298, 55, 283, 284, 172, 69, 206, 25, 288, 52, 177, 75, 285, 50, 382, 223 -> 4260001;
//basic 3
            case 128, 92, 282, 207, 143, 57, 93, 295, 49, 176, 129, 289, 144, 56, 296, 145, 226, 227, 28 -> 4260002;
//intermediate 1
            case 236, 79, 260, 208, 74, 130, 229, 230, 46, 53, 146, 237, 261, 131, 231, 238, 54 -> 4260003;
//intermediate 2
            case 239, 240, 132, 241, 147, 179, 133, 242, 148, 80, 232, 233, 234 -> 4260004;
//intermediate 3
            case 134, 149, 264, 265, 268, 135, 150, 225, 266, 180 -> 4260005;
//advanced 1
            case 269, 270, 448, 181, 267, 272, 449, 450, 271, 274 -> 4260006;
//advanced 2
            case 273, 452, 453 -> 4260007;
//advanced 3
            case 454, 455, 457, 458 -> 4260008;
            default -> 0;
        };
        if (monsterCrystal != 4260008 && monsterCrystal != 0) {
            if (Math.ceil(Math.random() * 100.0) <= 10) { //10% chance on higher crystal
                monsterCrystal++;
            }
        }
        return monsterCrystal;
    }

    private int getMonsterCrystalByLevel(int level) {
        int monsterCrystal = 0;
        if (level > 30 && level <= 50) {
            monsterCrystal = 4260000;
        } else if (level > 50 && level <= 60) {
            monsterCrystal = 4260001;
        } else if (level > 60 && level <= 70) {
            monsterCrystal = 4260002;
        } else if (level > 70 && level <= 80) {
            monsterCrystal = 4260003;
        } else if (level > 80 && level <= 90) {
            monsterCrystal = 4260004;
        } else if (level > 90 && level <= 100) {
            monsterCrystal = 4260005;
        } else if (level > 100 && level <= 110) {
            monsterCrystal = 4260006;
        } else if (level > 110 && level <= 120) {
            monsterCrystal = 4260007;
        } else if (level > 120) {
            monsterCrystal = 4260008;
        }

        if (monsterCrystal != 4260008 && monsterCrystal != 0) {
            if (Math.ceil(Math.random() * 100.0) <= 10) { //10% chance on higher crystal
                monsterCrystal++;
            }
        }
        return monsterCrystal;
    }

    private Equip applyGems(Equip eq, List<Integer> gems) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final String[] allProps = {"PAD", "MAD", "ACC", "EVA", "Speed", "Jump", "MaxHP", "MaxMP", "STR", "INT", "LUK", "DEX", "ReqLevel", "randOption", "randStat"};
        for (int gem : gems) {
            for (int i = 0; i < allProps.length; i++) {
                int stat = ii.getGemStatbyName(gem, allProps[i]);
                if (stat == 0) {
                    continue;
                }
                switch (i) {
                    case 0:
                        eq.setWatk((short) (eq.getWatk() + stat));
                        break;
                    case 1:
                        eq.setMatk((short) (eq.getMatk() + stat));
                        break;
                    case 2:
                        eq.setAcc((short) (eq.getAcc() + stat));
                        break;
                    case 3:
                        eq.setAvoid((short) (eq.getAvoid() + stat));
                        break;
                    case 4:
                        eq.setSpeed((short) (eq.getSpeed() + stat));
                        break;
                    case 5:
                        eq.setJump((short) (eq.getJump() + stat));
                        break;
                    case 6:
                        eq.setHp((short) (eq.getHp() + stat));
                        break;
                    case 7:
                        eq.setMp((short) (eq.getMp() + stat));
                        break;
                    case 8:
                        eq.setStr((short) (eq.getStr() + stat));
                        break;
                    case 9:
                        eq.setInt((short) (eq.getInt() + stat));
                        break;
                    case 10:
                        eq.setLuk((short) (eq.getLuk() + stat));
                        break;
                    case 11:
                        eq.setDex((short) (eq.getDex() + stat));
                        break;
                    case 12:
                        //eq.setReqLevel((short) (eq.getReqLevel() + stat));
                        break;
                    case 13: {
                        final List<Integer> statPool = new ArrayList<>(4);
                        if (eq.getWatk() > 0) {
                            statPool.add(0);
                        }
                        if (eq.getMatk() > 0) {
                            statPool.add(1);
                        }
                        if (eq.getSpeed() > 0) {
                            statPool.add(2);
                        }
                        if (eq.getJump() > 0) {
                            statPool.add(3);
                        }
                        if (statPool.isEmpty()) {
                            break;
                        }
                        int increase = Math.ceil(Math.random() * 100.0) <= 50 ? -1 : 1;
                        switch (statPool.get(Randomizer.nextInt(statPool.size()))) {
                            case 0 -> eq.setWatk((short) (eq.getWatk() + stat * increase));
                            case 1 -> eq.setMatk((short) (eq.getMatk() + stat * increase));
                            case 2 -> eq.setSpeed((short) (eq.getSpeed() + stat * increase));
                            case 3 -> eq.setJump((short) (eq.getJump() + stat * increase));
                        }
                        break;
                    }
                    case 14: {
                        final List<Integer> statPool = new ArrayList<>(6);
                        if (eq.getStr() > 0) {
                            statPool.add(0);
                        }
                        if (eq.getDex() > 0) {
                            statPool.add(1);
                        }
                        if (eq.getInt() > 0) {
                            statPool.add(2);
                        }
                        if (eq.getLuk() > 0) {
                            statPool.add(3);
                        }
                        if (eq.getAcc() > 0) {
                            statPool.add(4);
                        }
                        if (eq.getAvoid() > 0) {
                            statPool.add(5);
                        }
                        if (statPool.isEmpty()) {
                            break;
                        }
                        int increase = Math.ceil(Math.random() * 100.0) <= 50 ? -1 : 1;
                        switch (statPool.get(Randomizer.nextInt(statPool.size()))) {
                            case 0 -> eq.setStr((short) (eq.getStr() + stat * increase));
                            case 1 -> eq.setDex((short) (eq.getDex() + stat * increase));
                            case 2 -> eq.setInt((short) (eq.getInt() + stat * increase));
                            case 3 -> eq.setLuk((short) (eq.getLuk() + stat * increase));
                            case 4 -> eq.setAcc((short) (eq.getAcc() + stat * increase));
                            case 5 -> eq.setAvoid((short) (eq.getAvoid() + stat * increase));
                        }
                        break;
                    }
                }
                break;
            }
        }
        return eq;
    }
}