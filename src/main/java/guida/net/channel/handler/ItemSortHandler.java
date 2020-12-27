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
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Xterminator
 */
public class ItemSortHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        final byte invTypeId = slea.readByte();
        if (invTypeId < 1 || invTypeId > 5) {
            return;
        }
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.itemSortComplete(false, invTypeId));
            return;
        }
        c.setLastAction(action);
        final MapleInventoryType invType = MapleInventoryType.getByType(invTypeId);
        final MapleInventory inv = c.getPlayer().getInventory(invType);
        final List<Integer> items = new ArrayList<>();
        for (IItem item : inv.list()) {
            if (!items.contains(item.getItemId())) {
                items.add(item.getItemId());
            }
        }
        if (items.size() > 1) {
            Collections.sort(items);
        }
        short currentSlot = 1;
        final List<Pair<Short, IItem>> allChanges = new ArrayList<>();
        for (int itemId : items) {
            for (IItem item : inv.listById(itemId)) {
                if (item.getPosition() != currentSlot) {
                    allChanges.addAll(MapleInventoryManipulator.move(c, invType, item.getPosition(), currentSlot));
                }
                currentSlot++;
            }
        }
        c.sendPacket(MaplePacketCreator.modifyInventory(true, allChanges));
        c.sendPacket(MaplePacketCreator.itemSortComplete(!allChanges.isEmpty(), invType.getType()));
    }
}