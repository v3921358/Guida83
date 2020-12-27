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

package guida.client.anticheat;

public enum CheatingOffense {

    FASTATTACK(1, 60000, 300),
    MOVE_MONSTERS,
    TUBI,
    FAST_HP_REGEN,
    FAST_MP_REGEN(1, 60000, 500),
    SAME_DAMAGE(10, 300000, 20),
    ATTACK_WITHOUT_GETTING_HIT,
    HIGH_MELEE_DAMAGE(10, 300000),
    HIGH_MAGIC_DAMAGE(10, 300000),
    ATTACK_FARAWAY_MONSTER(5),
    REGEN_HIGH_HP(50),
    REGEN_HIGH_MP(50),
    ITEMVAC(5),
    SHORT_ITEMVAC(2),
    USING_FARAWAY_PORTAL(30, 300000),
    FAST_TAKE_DAMAGE(1),
    FAST_MOVE(1, 300000),
    HIGH_JUMP(1, 300000),
    FAR_TELE(1, 300000),
    FAST_FALL(1, 300000),
    MISMATCHING_BULLETCOUNT(50),
    ETC_EXPLOSION(50, 300000),
    FAST_SUMMON_ATTACK,
    UNLIMITED_ATTACK(1, 300000),
    ATTACKING_WHILE_DEAD(10, 300000),
    USING_UNAVAILABLE_ITEM(10, 300000),
    FAMING_SELF(10, 300000, 1), // purely for marker reasons (appears in the database)
    FAMING_UNDER_15(10, 300000, 1),
    EXPLODING_NONEXISTANT,
    SUMMON_HIGH_DAMAGE(10, 300000),
    HEAL_ATTACKING_UNDEAD(1, 60000, 5),
    COOLDOWN_HACK(10, 300000, 10),
    MOB_INSTANT_DEATH_HACK(10, 300000, 5),
    ARAN_COMBO_HACK(1, 600000, 50);

    private final int points;
    private final long validityDuration;
    private final int autobancount;
    private boolean enabled;

    CheatingOffense() {
        this(1);
    }

    CheatingOffense(int points) {
        this(points, 60000);
    }

    CheatingOffense(int points, long validityDuration) {
        this(points, validityDuration, -1);
    }

    CheatingOffense(int points, long validityDuration, int autobancount) {
        this(points, validityDuration, autobancount, true);
    }

    CheatingOffense(int points, long validityDuration, int autobancount, boolean enabled) {
        this.points = points;
        this.validityDuration = validityDuration;
        this.autobancount = autobancount;
        this.enabled = enabled;
    }

    public int getPoints() {
        return points;
    }

    public long getValidityDuration() {
        return validityDuration;
    }

    public boolean shouldAutoban(int count) {
        if (autobancount == -1) {
            return false;
        }
        return count > autobancount;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
