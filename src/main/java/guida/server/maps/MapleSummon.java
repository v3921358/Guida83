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

package guida.server.maps;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.SkillFactory;
import guida.tools.MaplePacketCreator;

import java.awt.Point;

/**
 * @author Jan
 */
public class MapleSummon extends AbstractAnimatedMapleMapObject {

    private final MapleCharacter owner;
    private final int skillLevel;
    private final int skill;
    private final SummonMovementType movementType;
    private int hp;

    public MapleSummon(MapleCharacter owner, int skill, Point pos, SummonMovementType movementType) {
        super();
        this.owner = owner;
        this.skill = skill;
        skillLevel = owner.getSkillLevel(SkillFactory.getSkill(skill));
        if (skillLevel == 0) {
            throw new RuntimeException("Trying to create a summon for a char without the skill");
        }
        this.movementType = movementType;
        setPosition(pos);
    }

    public void sendSpawnData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.spawnSpecialMapObject(this, skillLevel, false));
    }

    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.removeSpecialMapObject(this, true));
    }

    public MapleCharacter getOwner() {
        return owner;
    }

    public int getSkill() {
        return skill;
    }

    public int getHP() {
        return hp;
    }

    public void addHP(int delta) {
        hp += delta;
    }

    public SummonMovementType getMovementType() {
        return movementType;
    }

    public boolean isPuppet() {
        return skill == 3111002 || skill == 3211002 || skill == 5211001 || skill == 13111004;
    }

    public boolean isSummon() {
        switch (skill) {
            case 3211005: // golden eagle
            case 3111005: // golden hawk
            case 2311006: // summon dragon
            case 3221005: // frostprey
            case 3121006: // phoenix
            case 5211002: // bird - pirate
            case 1321007: // beholder
            case 2121005: // elquines
            case 2221005: // ifrit
            case 2321003: // bahamut
            case 11001004: // Soul Master "Soul"
            case 12001004: // Flame Wizard "Flame"
            case 13001004: // Windbreaker "Storm"
            case 14001005: // Nightwalker "Darkness"
            case 15001004: // Thunder guy "Lightning"
            case 12111004: // Flame Wizard ifrit
                return true;
            default:
                return false;
        }
    }

    public int getSkillLevel() {
        return skillLevel;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.SUMMON;
    }
}