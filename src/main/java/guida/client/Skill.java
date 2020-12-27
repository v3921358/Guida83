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

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.server.MapleStatEffect;
import guida.server.life.Element;

import java.util.HashMap;
import java.util.Map;

public class Skill implements ISkill {

    private final int id;
    private final Map<Integer, Integer> requiredSkillLevels = new HashMap<>(5);
    private MapleStatEffect[] effects;
    private Element element;
    private int animationTime;
    private boolean charge, hit, finisher, summon, keyDownAttack;

    private Skill(int id) {
        super();
        this.id = id;
    }

    public static Skill loadFromData(int id, MapleData data) {
        Skill ret = new Skill(id);
        boolean isBuff = false;
        int def = -1;
        int skillType = DataUtil.toInt(data.resolve("skillType"), def);
        String elem = DataUtil.toString(data.resolve("elemAttr"), null);
        ret.element = elem != null ? Element.getFromChar(elem.charAt(0)) : Element.NEUTRAL;
        // unfortunatly this is only set for a few skills so we have to do some more to figure out if it's a buff
        MapleData effect = data.getChild("effect");
        if (skillType != -1) {
            if (skillType == 2) {
                isBuff = true;
            }
        } else {
            MapleData action = data.getChild("action");
            MapleData hit = data.getChild("hit");
            MapleData ball = data.getChild("ball");
            isBuff = effect != null && hit == null && ball == null;
            isBuff |= action != null && DataUtil.toString(action.resolve("0"), "").equals("alert2");
            switch (id) {
                case 1121001: // Monster Magnet
                case 1121006: // Rush
                case 1221001: // Monster Magnet
                case 1221007: // Rush
                case 1311005: // Sacrifice
                case 1321001: // Monster Magnet
                case 1321003: // Rush
                case 2111002: // Explosion
                case 2111003: // Poison Mist
                case 2121001: // Big Bang
                case 2221001: // Big Bang
                case 2301002: // Heal
                case 2321001: // Big Bang
                case 3110001: // Mortal Blow
                case 3210001: // Mortal Blow
                case 4101005: // Drain
                case 4111003: // Shadow Web
                case 4201004: // Steal
                case 4221006: // Smokescreen
                case 5121010: // Time Leap
                case 5201006: // Recoil Shot
                case 9101000: // Heal + Dispel
                case 14111001: // Shadow Web
                    isBuff = false;
                    break;
                case 1320008: // Aura of the Beholder
                case 1320009: // Hex of the Beholder
                case 4121003: // Taunt
                case 4221003: // Taunt
                case 5111005: // Transformation
                case 5121003: // Super Transformation
                case 5211001: // Octopus
                case 5211002: // Gaviota
                case 5211006: // Homing Beacon
                case 5220002: // Wrath of the Octopi
                case 5220011: // Bullseye
                case 13111005: // Eagle Eye
                case 15111002: // Transformation
                case 21000000: // Aran Combo
                case 21101003: // Body Pressure
                    isBuff = true;
                    break;
            }
        }
        MapleData levels = data.getChild("level");
        ret.effects = new MapleStatEffect[levels.getChildCount()];
        for (MapleData level : levels) {
            ret.effects[Integer.parseInt(level.getName()) - 1] = MapleStatEffect.loadSkillEffectFromData(level, id, isBuff, level.getName());
        }
        ret.charge = data.getChild("keydown") != null;
        ret.hit = data.getChild("hit") != null;
        ret.keyDownAttack = data.getChild("keydownend") != null;
        ret.finisher = data.getChild("finish") != null;
        ret.summon = data.getChild("summon") != null;
        MapleData reqDataRoot = data.getChild("req");
        if (reqDataRoot != null) {
            for (MapleData reqData : reqDataRoot) {
                ret.requiredSkillLevels.put(Integer.parseInt(reqData.getName()), DataUtil.toInt(reqData, 1));
            }
        }
        ret.animationTime = 0;
        if (effect != null) {
            for (MapleData effectEntry : effect) {
                ret.animationTime += DataUtil.toInt(effectEntry.resolve("delay"), 0);
            }
        }
        return ret;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public MapleStatEffect getEffect(int level) {
        if (level < 1 || level > effects.length) {
            return null;
        }
        return effects[level - 1];
    }

    @Override
    public int getMaxLevel() {
        return effects.length;
    }

    @Override
    public boolean canBeLearnedBy(MapleJob job) {
        int jid = job.getId();
        int skillForJob = id / 10000;

        return jid == skillForJob // same job

                || (skillForJob / 100 % 10 == 0 // OR (is Beginner skill
                && skillForJob / 1000 == jid / 1000) // AND same character type)

                || (jid / 100 == skillForJob / 100 // OR (same class
                && (skillForJob / 10 % 10 == 0 // AND (is unspecialised skill
                || (jid / 10 % 10 == skillForJob / 10 % 10  // OR (same specialisation
                && jid % 10 >= skillForJob % 10))) // AND player's advancement is greater than skill advancement)))

                || jid / 100 == 9; // OR GM
    }

    @Override
    public boolean isFourthJob() {
        return id / 10000 % 10 == 2;
    }

    @Override
    public Element getElement() {
        return element;
    }

    @Override
    public int getAnimationTime() {
        return animationTime;
    }

    @Override
    public boolean hasRequirements() {
        return !requiredSkillLevels.isEmpty();
    }

    @Override
    public Map<Integer, Integer> getRequirements() {
        return requiredSkillLevels;
    }

    @Override
    public boolean isBeginnerSkill() {
        boolean output = false;
        String idString = String.valueOf(id);
        if (idString.length() == 4 || idString.length() == 1) {
            output = true;
        }

        return output;
    }

    @Override
    public boolean hasCharge() {
        return charge;
    }

    @Override
    public boolean isSummon() {
        return summon;
    }

    @Override
    public boolean isAttack() {
        return hit;
    }

    @Override
    public boolean isKeyDownAttack() {
        return keyDownAttack;
    }

    @Override
    public boolean isFinisher() {
        return finisher;
    }
}