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
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xterminator
 */
public class SlotMergeHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        final byte invTypeId = slea.readByte();
        if (invTypeId < 1 || invTypeId > 5 || c.getPlayer().isSlotMerging()) {
            return;
        }
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.slotMergeComplete(false, invTypeId));
            return;
        }
        c.setLastAction(action);
        c.getPlayer().setSlotMerging(true);
        final MapleInventoryType invType = MapleInventoryType.getByType(invTypeId);
        final MapleInventory inv = c.getPlayer().getInventory(invType);
        final List<Integer> items = new ArrayList<>(inv.list().size());
        for (IItem item : inv.list()) {
            if (invTypeId == 1) {
                break;
            }
            if (!items.contains(item.getItemId())) {
                items.add(item.getItemId());
            }
        }
        final List<Pair<Short, IItem>> allChanges = new ArrayList<>();
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (int item : items) {
            List<Short> stack = inv.findAllById(item);
            if (stack.size() > 1 && !ii.isThrowingStar(item) && !ii.isShootingBullet(item)) {
                for (short itemPos : stack) {
                    if (inv.getItem(itemPos) != null && inv.getItem(itemPos).getQuantity() != ii.getSlotMax(c, item)) {
                        if (inv.getItem(stack.get(stack.size() - 1)) != null) {
                            final short position = stack.get(stack.size() - 1);
                            if (position == itemPos) {
                                continue;
                            }
                            allChanges.addAll(MapleInventoryManipulator.move(c, invType, position, itemPos));
                        }
                    }
                }
            }
        }
        c.sendPacket(MaplePacketCreator.modifyInventory(true, allChanges));
        c.sendPacket(MaplePacketCreator.slotMergeComplete(!allChanges.isEmpty(), invType.getType()));
        c.getPlayer().setSlotMerging(false);
    }
}