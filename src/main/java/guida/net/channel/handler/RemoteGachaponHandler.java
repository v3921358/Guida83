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

import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.npc.NPCScriptManager;
import guida.server.MapleItemInformationProvider;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Xterminator
 */
public class RemoteGachaponHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int itemId = slea.readInt();
        if (itemId == 5451000) {
            for (int i = 1; i <= 5; i++) {
                if (c.getPlayer().getInventory(MapleInventoryType.getByType((byte) i)).getNextFreeSlot() == -1) {
                    c.getPlayer().dropMessage(1, "Please make sure you have space in all your inventories.");
                    return;
                }
            }
            if (c.getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(5451000)).countById(itemId) > 0) {
                c.getPlayer().setUsingRemoteGachaponTicket(true);
                final int id = slea.readInt();
                if (id >= 0 && id <= 9) {
                    switch (id) {
                        case 8 -> NPCScriptManager.getInstance().start(c, 9100109);
                        case 9 -> NPCScriptManager.getInstance().start(c, 9100117);
                        default -> NPCScriptManager.getInstance().start(c, 9100100 + id);
                    }
                }
            }
        }
    }
}