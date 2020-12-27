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

//import java.awt.Point;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.server.movement.LifeMovementFragment;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;
import guida.tools.data.input.StreamUtil;

import java.util.List;

public class MovePetHandler extends AbstractMovementPacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int petId = slea.readInt();
        slea.readInt();
        /*Point startPos = */
        StreamUtil.readShortPoint(slea);
        final List<LifeMovementFragment> res = parseMovement(slea);
        if (res == null || res.isEmpty()) {
            return;
        }

        final MapleCharacter player = c.getPlayer();
        final int slot = player.getPetIndex(petId);
        if (player.inCS() && slot == -1) {
            return;
        } else if (slot == -1) {
            //log.warn("[h4x] {} ({}) trying to move a pet he/she does not own.", c.getPlayer().getName(), c.getPlayer().getId());
            return;
        }
        player.getPet(slot).updatePosition(res);
        player.getMap().broadcastMessage(player, MaplePacketCreator.movePet(player.getId(), petId, slot, res), false);
    }
}