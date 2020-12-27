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

import guida.net.AbstractMaplePacketHandler;
import guida.server.maps.AnimatedMapleMapObject;
import guida.server.movement.AbsoluteLifeMovement;
import guida.server.movement.BasicMovement;
import guida.server.movement.ChairMovement;
import guida.server.movement.ChangeEquipSpecialAwesome;
import guida.server.movement.JumpDownMovement;
import guida.server.movement.LifeMovement;
import guida.server.movement.LifeMovementFragment;
import guida.server.movement.RelativeLifeMovement;
import guida.server.movement.TeleportMovement;
import guida.tools.data.input.LittleEndianAccessor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMovementPacketHandler extends AbstractMaplePacketHandler {

    protected List<LifeMovementFragment> parseMovement(LittleEndianAccessor lea) {
        final List<LifeMovementFragment> res = new ArrayList<>();
        short x = 0, y = 0, unk = 0, foothold = 0, curx = 0, cury = 0;
        byte stance = 0;
        int numCommands = lea.readByte();
        for (byte i = 0; i < numCommands; i++) {
            final short command = lea.readByte();
            switch (command) {
                case 0: // normal move
                case 5:
                case 17: { // Float
                    x = lea.readShort();
                    y = lea.readShort();
                    curx = x;
                    cury = y;
                    final int xwobble = lea.readShort();
                    final int ywobble = lea.readShort();
                    unk = lea.readShort();
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    if (x == y && stance == foothold) {
                        return null;
                    }
                    final AbsoluteLifeMovement alm = new AbsoluteLifeMovement(command, new Point(x, y), stance, foothold);
                    alm.setPixelsPerSecond(new Point(xwobble, ywobble));
                    alm.setUnk(unk);
                    res.add(alm);
                    break;
                }
                case 1:
                case 2:
                case 6: // fj
                case 12:
                case 13: // Shot-jump-back thing
                case 16: // Float
                case 18:
                case 19:
                case 20:
                case 22: {
                    x = lea.readShort();
                    y = lea.readShort();
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    if (x == y && stance == foothold) {
                        return null;
                    }
                    final RelativeLifeMovement rlm = new RelativeLifeMovement(command, new Point(x, y), stance, foothold);
                    res.add(rlm);
                    break;
                }
                case 3:
                case 4: // teleport
                case 7: // assaulter
                case 8: // assassinate
                case 9: // rush
                case 14: {
                    x = lea.readShort();
                    y = lea.readShort();

                    unk = lea.readShort();
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    if (x == y && stance == foothold) {
                        return null;
                    }
                    final TeleportMovement tm = new TeleportMovement(command, new Point(x, y), stance, foothold, curx, cury);
                    curx = x;
                    cury = y;
                    tm.setUnk(unk);
                    res.add(tm);
                    break;
                }
                case 10: { // change equip
                    res.add(new ChangeEquipSpecialAwesome(lea.readByte()));
                    break;
                }
                case 11: { // chair
                    x = lea.readShort();
                    y = lea.readShort();
                    curx = x;
                    cury = y;
                    unk = lea.readShort();
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    if (x == y && stance == foothold) {
                        return null;
                    }
                    final ChairMovement cm = new ChairMovement(command, new Point(x, y), stance, foothold);
                    cm.setUnk(unk);
                    res.add(cm);
                    break;
                }
                case 15: { // jump down
                    x = lea.readShort();
                    y = lea.readShort();
                    curx = x;
                    cury = y;
                    final int xwobble = lea.readShort();
                    final int ywobble = lea.readShort();
                    unk = lea.readShort();
                    final int unk2 = lea.readShort();
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    if (x == y && stance == foothold) {
                        return null;
                    }
                    final JumpDownMovement jdm = new JumpDownMovement(command, new Point(x, y), stance, foothold);
                    jdm.setPixelsPerSecond(new Point(xwobble, ywobble));
                    jdm.setUnk(unk);
                    jdm.setUnk2(unk2);
                    res.add(jdm);
                    break;
                }
                case 21: {
                    stance = lea.readByte();
                    foothold = lea.readShort();
                    final BasicMovement bm = new BasicMovement(command, stance, foothold);
                    res.add(bm);
                    break;
                }
            }
        }
        /*if (numCommands != res.size()) {
			log.warn("numCommands ({}) does not match the number of deserialized movement commands ({})", numCommands, res.size());
		}*/
        return res;
    }

    protected void updatePosition(List<LifeMovementFragment> movement, AnimatedMapleMapObject target, int yoffset) {
        for (LifeMovementFragment move : movement) {
            if (move instanceof LifeMovement) {
                if (move instanceof AbsoluteLifeMovement) {
                    final Point position = move.getPosition();
                    position.y += yoffset;
                    target.setPosition(position);
                }
                target.setStance((byte) ((LifeMovement) move).getStance());
            }
        }
    }
}