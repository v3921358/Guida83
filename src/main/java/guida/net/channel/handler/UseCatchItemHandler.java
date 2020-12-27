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
import guida.server.life.MapleMonster;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Pat
 */
public class UseCatchItemHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // 4A 00
        // B9 F4 8B 00 // unknown
        // 01 00 // success??
        // 32 A3 22 00 // itemid
        // 38 37 2B 00 // monsterid
        slea.readInt();
        slea.readShort();
        final int itemid = slea.readInt();
        final int monsterid = slea.readInt();

        final MapleMonster mob = c.getPlayer().getMap().getMonsterByOid(monsterid);
        if (mob != null) {
            if (mob.getHp() <= mob.getMaxHp() / 2) {
                if (itemid == 2270002) {
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.catchMonster(monsterid, itemid, (byte) 1));
                }
                mob.getMap().killMonster(mob);
                c.getPlayer().setAPQScore(c.getPlayer().getAPQScore() + 1);
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.updateAriantPQRanking(c.getPlayer().getName(), c.getPlayer().getAPQScore(), false));
            } else {
                c.sendPacket(MaplePacketCreator.serverNotice(5, "You cannot catch the monster as it is too strong."));
            }
        }
    }
}