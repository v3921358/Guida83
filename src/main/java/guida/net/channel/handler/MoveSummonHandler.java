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
import guida.server.maps.MapleSummon;
import guida.server.movement.LifeMovementFragment;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;
import guida.tools.data.input.StreamUtil;

import java.awt.Point;
import java.util.Collection;
import java.util.List;

public class MoveSummonHandler extends AbstractMovementPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int oid = slea.readInt();
        final Point startPos = StreamUtil.readShortPoint(slea);
        final List<LifeMovementFragment> res = parseMovement(slea);
        final MapleCharacter player = c.getPlayer();
        final Collection<MapleSummon> summons = player.getSummons().values();
        MapleSummon summon = null;
        for (MapleSummon sum : summons) {
            if (sum.getObjectId() == oid) {
                summon = sum;
            }
        }
        if (summon != null && res != null && !res.isEmpty()) {
            updatePosition(res, summon, 0);
            player.getMap().broadcastMessage(player, MaplePacketCreator.moveSummon(player.getId(), oid, startPos, res), summon.getPosition());
        }
    }
}