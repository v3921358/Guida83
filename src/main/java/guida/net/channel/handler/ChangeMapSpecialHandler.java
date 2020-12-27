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
import guida.server.MaplePortal;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class ChangeMapSpecialHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (System.currentTimeMillis() - c.getPlayer().getLastWarpTime() < 2000) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        slea.readByte();
        String startwp = slea.readMapleAsciiString();
        slea.readByte();
        slea.readByte();

        MaplePortal portal = c.getPlayer().getMap().getPortal(startwp);
        c.getPlayer().setLastWarpTime(System.currentTimeMillis());
        if (c.getPlayer().getMap().isDojoMap() && c.getPlayer().getMap().getDojoStage() != 38) {
            if (portal != null) {
                if (c.getPlayer().getMap().countMobOnMap(9300216) > 0) {
                    c.getPlayer().getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(0, 7, (byte) c.getPlayer().getLevel()));
                    MapleMap map = c.getPlayer().getEventInstance().getMapInstance(c.getPlayer().getMap().getNextDojoMap());
                    c.getPlayer().changeMap(map, map.getPortal(0));
                } else {
                    c.sendPacket(MaplePacketCreator.serverNotice(5, "You haven't killed the boss yet. Please kill it before continuing."));
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
            } else {
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        } else {
            if (portal != null) {
                portal.enterPortal(c);
            } else {
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
    }
}