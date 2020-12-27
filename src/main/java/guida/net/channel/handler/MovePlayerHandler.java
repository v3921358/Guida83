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
import guida.server.movement.AbsoluteLifeMovement;
import guida.server.movement.LifeMovementFragment;
import guida.server.movement.TeleportMovement;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Point;
import java.util.List;

public class MovePlayerHandler extends AbstractMovementPacketHandler {

    private static void checkMovementSpeed(MapleCharacter chr, List<LifeMovementFragment> moves) {
        final double playerSpeedMod = chr.getSpeedMod() + 0.05;
        final double playerJumpMod = chr.getJumpMod() + 0.05;
        for (LifeMovementFragment lmf : moves) {
            if (lmf.getClass() == AbsoluteLifeMovement.class) {
                final AbsoluteLifeMovement alm = (AbsoluteLifeMovement) lmf;
                final double speedMod = Math.abs(alm.getPixelsPerSecond().x) / 130.0;
                double jumpMod = alm.getPixelsPerSecond().y * -1; // negative jumpmod = falling!
                if (alm.getUnk() == 0) {
                    continue;
                }
                if (speedMod > playerSpeedMod) {
                    chr.getCheatTracker().registerOffense(CheatingOffense.FAST_MOVE);
                }

                if (jumpMod < 0) {
                    jumpMod *= -1.0;
                    // maple terminal velocity is 670 px/sec, movement updated every sec.
                    if (jumpMod > 680.0) {
                        chr.getCheatTracker().registerOffense(CheatingOffense.FAST_FALL);
                    }
                } else {
                    jumpMod /= 555.0; // this is really lenient considering we don't account for gravity
                    if (jumpMod > playerJumpMod) {
                        chr.getCheatTracker().registerOffense(CheatingOffense.HIGH_JUMP);
                    }
                }

            } else if (lmf.getClass() == TeleportMovement.class) {
                TeleportMovement tmf = (TeleportMovement) lmf;
                Point oldpos = tmf.getOldPos();
                Point newpos = tmf.getPosition();
                double dist = oldpos.distance(newpos);
                if (dist > 300.0) {
                    chr.getCheatTracker().registerOffense(CheatingOffense.FAR_TELE);
                }
            }
        }
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        c.getPlayer().resetBuffAndAttackCounts();
        slea.readByte();
        slea.readInt();
        final int oid = slea.readInt();
        // log.trace("Movement command received: unk1 {} unk2 {}", new Object[] { unk1, unk2 });
        final List<LifeMovementFragment> res = parseMovement(slea);
        MapleCharacter player = c.getPlayer();
        player.setLastRes(res);
        player.resetAfkTimer();
        // TODO more validation of input data
        if (res != null) {
            if (slea.available() != 18) {
                // A few skills causes this
                //log.warn(c.getPlayer().getName() + ": slea.available != 18 (movement parsing error)");
                return;
            }
            if (!player.isHidden()) {
                player.getMap().broadcastMessage(player, MaplePacketCreator.movePlayer(player.getId(), oid, res), false);
            }
            if (CheatingOffense.FAST_MOVE.isEnabled() || CheatingOffense.HIGH_JUMP.isEnabled()) {
                checkMovementSpeed(player, res);
            }
            updatePosition(res, player, 0);
            player.getMap().movePlayer(player, player.getPosition());
        }
    }
}