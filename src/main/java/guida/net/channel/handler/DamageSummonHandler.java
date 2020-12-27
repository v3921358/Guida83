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

import guida.client.MapleBuffStat;
import guida.client.MapleClient;
import guida.net.AbstractMaplePacketHandler;
import guida.net.MaplePacketHandler;
import guida.server.maps.MapleSummon;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class DamageSummonHandler extends AbstractMaplePacketHandler implements MaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // Ramon 93 00 69 00 00 00 FF 32 02 00 00 98 40 7C 00 01 -> 562 dmg
        // Ramon 93 00 8E 00 00 00 FF 00 00 00 00 05 87 01 00 00 -> miss

        slea.readInt();
        final int unkByte = slea.readByte();
        final int damage = slea.readInt();
        final int monsterIdFrom = slea.readInt();
        slea.readByte(); // stance

        for (MapleSummon summon : c.getPlayer().getSummons().values()) {
            if (summon.isPuppet() && summon.getOwner() == c.getPlayer()) { // We can only have one puppet(AFAIK O.O) so this check is safe.
                summon.addHP(-damage);
                if (summon.getHP() <= 0) {
                    c.getPlayer().cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
                }
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.damageSummon(c.getPlayer().getId(), summon.getSkill(), damage, unkByte, monsterIdFrom), summon.getPosition());
                break;
            }
        }
    }
}