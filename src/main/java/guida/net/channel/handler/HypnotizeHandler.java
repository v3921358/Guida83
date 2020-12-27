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
import guida.client.status.MonsterStatus;
import guida.net.AbstractMaplePacketHandler;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleMap;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class HypnotizeHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        int mobfrom = slea.readInt();
        int playerid = slea.readInt();
        int mobto = slea.readInt();
        slea.readByte();
        int damage = slea.readInt();
        slea.readByte();
        slea.readInt();

        MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterById(playerid);
        MapleMap map = player.getMap();
        MapleMonster mobFrom = map.getMonsterByOid(mobfrom);
        MapleMonster mobTo = map.getMonsterByOid(mobto);
        if (mobFrom != null && mobTo != null && mobFrom.containsStatus(MonsterStatus.HYPNOTIZED)) {
            map.damageMonster(player, mobTo, damage);
        }
    }
}