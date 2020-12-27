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
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.npc.NPCScriptManager;
import guida.server.MapleItemInformationProvider;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Jay Estrella
 */
public final class ScriptedItemHandler extends AbstractMaplePacketHandler {

    public final void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        slea.readInt(); // trash stamp
        final short itemSlot = slea.readShort();
        final int itemId = slea.readInt();
        final int npcId = ii.getScriptedItemNpc(itemId);
        final IItem item = c.getPlayer().getInventory(ii.getInventoryType(itemId)).getItem(itemSlot);
        if (item == null || item.getItemId() != itemId || item.getQuantity() < 1 || npcId == 0) {
            return;
        }
        NPCScriptManager.getInstance().start(c, npcId);
    }
}