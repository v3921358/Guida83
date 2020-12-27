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
import guida.server.maps.MapleMap;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class AutoAggroHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // A0 00 7C 00 00 00 0C 00 00 00
        final int oid = slea.readInt();
        final MapleMap map = c.getPlayer().getMap();
        final MapleMonster monster = map.getMonsterByOid(oid);
        if (monster != null && monster.getController() != null) {
            if (!monster.isControllerHasAggro()) {
                if (map.getCharacterById(monster.getController().getId()) == null) {
                    monster.switchController(c.getPlayer(), true);
                } else {
                    monster.switchController(monster.getController(), true);
                }
            } else {
                if (map.getCharacterById(monster.getController().getId()) == null) {
                    monster.switchController(c.getPlayer(), true);
                }
            }
        } else if (monster != null && monster.getController() == null) {
            monster.switchController(c.getPlayer(), true);
        }
    }
}