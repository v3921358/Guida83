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
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Matze
 */
public class TeleportRockHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final byte action = slea.readByte();
        final byte rocktype = slea.readByte();
        if (rocktype != 0 && rocktype != 1) {
            return;
        }
        int mapId;
        if (action == 0) {
            mapId = slea.readInt();
            c.getPlayer().deleteTeleportRockMap(mapId, rocktype);
        } else if (action == 1) {
            mapId = c.getPlayer().getMapId();
            if (mapId > 10000000 && mapId != 109010103 && mapId != 209000010 && c.getChannelServer().getMapFactory().getMap(mapId).canVipRock()) {
                c.getPlayer().addTeleportRockMap(mapId, rocktype);
            } else {
                c.getPlayer().dropMessage("You may not save this map.");
            }
        }
        c.sendPacket(MaplePacketCreator.refreshTeleportRockMapList(c.getPlayer(), rocktype));
    }
}