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

import guida.client.IItem;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Patrick
 */
public class UsePinkBoxHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final short slot = slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);
        if (toUse == null || toUse.getItemId() != itemId || toUse.getQuantity() < 1) {
            return;
        }
        if (itemId == 2022428) {
            // astaroth event rewards
            double random = Math.floor(Math.random() * 100.0 + 1);
            int prize = 0;
            short quantity = 1;
            if (random <= 2) {
                //grand prize
                prize = 1702136;
                c.getChannelServer().broadcastWorldMessage(c.getPlayer().getName() + " has won the grand prize, ice snow flower ring, from the Mysterious Box!");
            } else if (random <= 20) {
                //nice items
                double pickPrize = Math.floor(Math.random() * (3 - 1 + 1) + 1);
                prize = 2040757 + (int) pickPrize;
            } else if (random <= 25) {
                //chaos
                prize = 2049100;
            } else if (random <= 30) {
                prize = 2340000;
            } else if (random <= 40) {
                prize = 2022179;
                quantity = 3;
            } else if (random <= 50) {
                prize = 2022282;
                quantity = 2;
            } else {
                //crappy pots
                double pickPrize = Math.floor(Math.random() * (7 - 1 + 1) + 1);
                switch ((int) pickPrize) {
                    case 1 -> {
                        prize = 2000002;
                        quantity = 100;
                    }
                    case 2 -> {
                        prize = 2000006;
                        quantity = 100;
                    }
                    case 3 -> {
                        prize = 2022000;
                        quantity = 50;
                    }
                    case 4 -> {
                        prize = 2001000;
                        quantity = 50;
                    }
                    case 5 -> {
                        prize = 2001002;
                        quantity = 30;
                    }
                    case 6 -> {
                        prize = 2001001;
                        quantity = 30;
                    }
                    case 7 -> {
                        prize = 2020014;
                        quantity = 30;
                    }
                }
            }
            if (!c.getPlayer().getInventory(MapleInventoryType.USE).isFull()) {
                MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
                c.sendPacket(MaplePacketCreator.getShowItemGain(itemId, (short) -1, true));
                MapleInventoryManipulator.addById(c, prize, quantity, "Pink box");
                c.sendPacket(MaplePacketCreator.getShowItemGain(prize, quantity, true));
            } else {
                c.getPlayer().dropMessage(5, "Your USE inventory is full. Please remove some items before opening the Mysterious Box.");
            }
        }
    }
}