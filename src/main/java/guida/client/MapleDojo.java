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

package guida.client;

/**
 * @author Patrick
 */
public class MapleDojo {

    int energy = 0;
    int stage = 1;
    int points;
    int belt;

    public MapleDojo(int startpoints, int startbelt) {
        points = startpoints;
        belt = startbelt;
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int newEnergy) {
        energy = Math.min(Math.max(0, newEnergy), 10000);
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int newStage) {
        stage = newStage;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int newPoints) {
        points = newPoints;
    }

    public int getBelt() {
        return belt;
    }

    public void setBelt(int newbelt) {
        belt = newbelt;
    }
}