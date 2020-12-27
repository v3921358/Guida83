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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.anticheat.CheatingOffense;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MaplePortal;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Point;

/**
 * @author Xterminator
 */
public class InnerPortalHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        slea.skip(1);
        final MaplePortal portal = c.getPlayer().getMap().getPortal(slea.readMapleAsciiString());
        final int toX = slea.readShort();
        final int toY = slea.readShort();
        //slea.readShort(); // Original X pos
        //slea.readShort(); // Original Y pos
        MapleCharacter chr = c.getPlayer();

        if (portal == null) {
            c.disconnect();
            return;
        } else if (portal.getPosition().distanceSq(chr.getPosition()) > 22500) {
            chr.getCheatTracker().registerOffense(CheatingOffense.USING_FARAWAY_PORTAL);
        }
        chr.getMap().movePlayer(chr, new Point(toX, toY));
    }
}