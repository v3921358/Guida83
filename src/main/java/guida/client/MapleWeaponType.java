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

public enum MapleWeaponType {

    NOT_A_WEAPON(0),
    BOW(3.4),
    CLAW(4),
    DAGGER(4),
    CROSSBOW(3.6),
    AXE1H(4.4),
    SWORD1H(4.0),
    BLUNT1H(4.4),
    AXE2H(4.8),
    SWORD2H(4.6),
    BLUNT2H(4.8),
    POLE_ARM(5.0),
    SPEAR(5.0),
    STAFF(4.4),
    WAND(4.4),
    KNUCKLE(4.8),
    GUN(3.6);

    private final double damageMultiplier;

    MapleWeaponType(double maxDamageMultiplier) {
        damageMultiplier = maxDamageMultiplier;
    }

    public double getMaxDamageMultiplier() {
        return damageMultiplier;
    }
}