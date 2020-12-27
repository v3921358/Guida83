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
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Danny
 */
public class ViciousHammerHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (c.getPlayer().getHammerSlot() == null) {
            c.disconnect();
            return;
        }
        final short slot = c.getPlayer().getHammerSlot();
        final IItem item = c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(slot);
        c.sendPacket(MaplePacketCreator.sendHammerEnd());
        final List<Pair<Short, IItem>> equipUpdate = new ArrayList<>(2);
        equipUpdate.add(new Pair<>((short) 3, item));
        equipUpdate.add(new Pair<>((short) 0, item));
        c.sendPacket(MaplePacketCreator.modifyInventory(true, equipUpdate));
        c.getPlayer().setHammerSlot(null);
    }
}