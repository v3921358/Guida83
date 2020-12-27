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
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;

/**
 * @author iamSTEVE
 */
public class TreasureChestHandler extends AbstractMaplePacketHandler {

    private final int[] goldrewards = {1402037, 2290107, 4032016, 2022121, 1302059, 1442002, 4032015, 2049100, 1472053, 2290095, 4001040, 2340000, 1382050, 2290116, 1092049, 1102041, 2290096, 4001039, 4001038, 1432018, 2290085};
    private final int[] goldrates = {1, 2, 3, 3, 2, 4, 4, 3, 3, 3, 3, 4, 2, 3, 4, 3, 2, 1, 4, 4, 2, 1};
    private final int[] silverrewards = {1002452, 1002455, 1102082, 1302049, 2340000, 1102041, 2000004, 1452019, 4001116, 4001041, 1022060, 2040813, 1002587, 1402044, 2040013, 2000005, 2101013, 1442046, 1422031, 2022121, 1332054};
    private final int[] silverrates = {3, 3, 3, 2, 1, 1, 4, 3, 2, 2, 1, 3, 4, 3, 2, 2, 1, 2, 1, 1, 3};

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final short slot = slea.readShort();
        final int itemid = slea.readInt();

        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final IItem toUse = c.getPlayer().getInventory(MapleInventoryType.ETC).getItem(slot);
        if (toUse != null && toUse.getQuantity() > 0) {
            if (toUse.getItemId() != itemid) {
                return;
            }
            int type = -1;
            if (toUse.getItemId() == 4280000) { //gold
                type = 1;
            } else if (toUse.getItemId() == 4280001) { //silver
                type = 2;
            }
            final ArrayList<Integer> allRewards = getReward(type);
            final int reward = allRewards.get((int) Math.floor(Math.random() * allRewards.size()));
            final int amount = getAmount(reward);
            final IItem rewarditem = ii.getEquipById(reward);
            final MapleInventoryType itype = ii.getInventoryType(reward);
            if (!MapleInventoryManipulator.checkSpace(c, reward, amount, "")) {
                c.sendPacket(MaplePacketCreator.serverNotice(1, "Your inventory is full. Please remove an item from your " + itype.name() + " inventory."));
                return;
            }
            final boolean randStats = itype.equals(MapleInventoryType.EQUIP) && !ii.isThrowingStar(reward) && !ii.isShootingBullet(reward);
            if (c.getPlayer().getInventory(MapleInventoryType.CASH).countById(5490000) > 0) {
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.ETC, (byte) slot, (short) 1, true);
                MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, 5490000, 1, true, false);
                if (randStats) {
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(c, ii.randomizeStats((Equip) rewarditem), "Received from a gachapon box", false)));
                } else {
                    MapleInventoryManipulator.addById(c, reward, (short) amount, "Received from a gachapon box", null, null);
                }
                c.sendPacket(MaplePacketCreator.getShowItemGain(toUse.getItemId(), (short) -1, true));
                c.sendPacket(MaplePacketCreator.getShowItemGain(5490000, (short) -1, true));
                c.sendPacket(MaplePacketCreator.getShowItemGain(reward, (short) amount, true));
            } else {
                c.getPlayer().dropMessage(5, "You do not have the master key.");
            }
        }
    }

    private int getAmount(int itemid) {
        switch (itemid) {
            case 2000004:
                return 300; // Elixir
            case 2000005:
                return 100; // Power Elixir
            default:
                return 1;
        }
    }

    private ArrayList<Integer> getReward(int type) {
        int[] rewardsCopy, ratesCopy;
        final ArrayList<Integer> returnArray = new ArrayList<>(30);

        if (type == 1) { // gold
            rewardsCopy = goldrewards;
            ratesCopy = goldrates;
        } else { // silver
            rewardsCopy = silverrewards;
            ratesCopy = silverrates;
        }

        for (int i = 0; i < rewardsCopy.length; i++) {
            returnArray.add(rewardsCopy[i]);
            for (int j = 1; j < ratesCopy[i]; j++) {
                returnArray.add(rewardsCopy[i]);
            }
        }
        return returnArray;
    }
}