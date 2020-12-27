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

package guida.server.movement;

import java.awt.Point;

public abstract class AbstractLifeMovement implements LifeMovement {

    private final Point position;
    private final int stance;
    private final int foothold;
    private final int type;

    public AbstractLifeMovement(int type, Point position, int stance, int foothold) {
        super();
        this.type = type;
        this.position = position;
        this.stance = stance;
        this.foothold = foothold;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public int getFoothold() {
        return foothold;
    }

    @Override
    public int getStance() {
        return stance;
    }

    @Override
    public Point getPosition() {
        return position;
    }
}