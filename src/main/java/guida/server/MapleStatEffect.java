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

package guida.server;

import guida.client.IItem;
import guida.client.ISkill;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleDisease;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MapleStat;
import guida.client.SkillCooldown;
import guida.client.SkillFactory;
import guida.client.status.MonsterStatus;
import guida.client.status.MonsterStatusEffect;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleDoor;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapObject;
import guida.server.maps.MapleMapObjectType;
import guida.server.maps.MapleMist;
import guida.server.maps.MapleSummon;
import guida.server.maps.SummonMovementType;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Matze
 * @author Frz
 */
public class MapleStatEffect implements Serializable {

    static final long serialVersionUID = 9179541993413738569L;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleStatEffect.class);
    private short watk, matk, wdef, mdef, acc, avoid, hands, speed, jump;
    private short hp, mp;
    private double hpR, mpR;
    private short mpCon, hpCon;
    private int duration;
    private boolean overTime;
    private int sourceid;
    private int moveTo;
    private boolean skill;
    private List<Pair<MapleBuffStat, Integer>> statups;
    private Map<MonsterStatus, Integer> monsterStatus;
    private int x, y, z;
    private double prop;
    private int itemCon, itemConNo;
    private int fixDamage;
    private int damage, attackCount, bulletCount, bulletConsume;
    private Point lt, rb;
    private int mobCount;
    private int moneyCon;
    private int cooldown;
    private int morphId;
    private int itemConsume;
    private List<MapleDisease> cureDebuffs;
    private int mastery, range;
    private String remark;
    private short thaw;

    public MapleStatEffect() {
    }

    public static MapleStatEffect loadSkillEffectFromData(MapleData source, int skillid, boolean overtime, String lvl) {
        return loadFromData(source, skillid, true, overtime, "Level " + lvl);
    }

    public static MapleStatEffect loadItemEffectFromData(MapleData source, int itemid) {
        return loadFromData(source, itemid, false, false, "");
    }

    private static void addBuffStatPairToListIfNotZero(List<Pair<MapleBuffStat, Integer>> list, MapleBuffStat buffstat, Integer val) {
        if (val != 0) {
            list.add(new Pair<>(buffstat, val));
        }
    }

    private static MapleStatEffect loadFromData(MapleData source, int sourceid, boolean skill, boolean overTime, String remarrk) {
        MapleStatEffect ret = new MapleStatEffect();
        int def1 = -1;
        ret.duration = DataUtil.toInt(source.resolve("time"), def1);
        ret.hp = (short) DataUtil.toInt(source.resolve("hp"), 0);
        ret.hpR = DataUtil.toInt(source.resolve("hpR"), 0) / 100.0;
        ret.mp = (short) DataUtil.toInt(source.resolve("mp"), 0);
        ret.mpR = DataUtil.toInt(source.resolve("mpR"), 0) / 100.0;
        ret.mpCon = (short) DataUtil.toInt(source.resolve("mpCon"), 0);
        ret.hpCon = (short) DataUtil.toInt(source.resolve("hpCon"), 0);
        int iprop = DataUtil.toInt(source.resolve("prop"), 100);
        ret.prop = iprop / 100.0;
        ret.attackCount = DataUtil.toInt(source.resolve("attackCount"), 1);
        ret.mobCount = DataUtil.toInt(source.resolve("mobCount"), 1);
        ret.cooldown = DataUtil.toInt(source.resolve("cooltime"), 0);
        ret.morphId = DataUtil.toInt(source.resolve("morph"), 0);
        ret.itemConsume = DataUtil.toInt(source.resolve("itemConsume"), 0);
        ret.remark = remarrk;
        ret.sourceid = sourceid;
        ret.skill = skill;

        if (!ret.skill && ret.duration > -1) {
            ret.overTime = true;
        } else {
            ret.duration *= 1000; // items have their times stored in ms, of course
            ret.overTime = overTime;
        }
        ArrayList<Pair<MapleBuffStat, Integer>> statups = new ArrayList<>();

        ret.watk = (short) DataUtil.toInt(source.resolve("pad"), 0);
        ret.wdef = (short) DataUtil.toInt(source.resolve("pdd"), 0);
        ret.matk = (short) DataUtil.toInt(source.resolve("mad"), 0);
        ret.mdef = (short) DataUtil.toInt(source.resolve("mdd"), 0);
        ret.acc = (short) DataUtil.toInt(source.resolve("acc"), 0);
        ret.avoid = (short) DataUtil.toInt(source.resolve("eva"), 0);
        ret.speed = (short) DataUtil.toInt(source.resolve("speed"), 0);
        ret.jump = (short) DataUtil.toInt(source.resolve("jump"), 0);
        ret.thaw = (short) DataUtil.toInt(source.resolve("thaw"), 0);
        if (ret.overTime && ret.getSummonMovementType() == null && !ret.isBattleShip()) {
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.WATK, (int) ret.watk);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.WDEF, (int) ret.wdef);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.MATK, (int) ret.matk);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.MDEF, (int) ret.mdef);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.ACC, (int) ret.acc);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.AVOID, (int) ret.avoid);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.SPEED, (int) ret.speed);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.JUMP, (int) ret.jump);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.MORPH, ret.morphId);
            addBuffStatPairToListIfNotZero(statups, MapleBuffStat.THAW, (int) ret.thaw);
        }

        MapleData ltd = source.getChild("lt");
        if (ltd != null) {
            ret.lt = (Point) ltd.getData();
            ret.rb = (Point) source.getChild("rb").getData();
        }

        int x = DataUtil.toInt(source.resolve("x"), 0);
        ret.x = x;
        ret.y = DataUtil.toInt(source.resolve("y"), 0);
        ret.z = DataUtil.toInt(source.resolve("z"), 0);
        ret.damage = DataUtil.toInt(source.resolve("damage"), 100);
        ret.bulletCount = DataUtil.toInt(source.resolve("bulletCount"), 0);
        ret.bulletConsume = DataUtil.toInt(source.resolve("bulletConsume"), 0);
        ret.moneyCon = DataUtil.toInt(source.resolve("moneyCon"), 0);

        ret.itemCon = DataUtil.toInt(source.resolve("itemCon"), 0);
        ret.itemConNo = DataUtil.toInt(source.resolve("itemConNo"), 0);
        ret.fixDamage = DataUtil.toInt(source.resolve("fixdamage"), 0);

        int def = -1;
        ret.moveTo = DataUtil.toInt(source.resolve("moveTo"), def);

        ret.mastery = DataUtil.toInt(source.resolve("mastery"), 0);
        ret.range = DataUtil.toInt(source.resolve("range"), 0);
        List<MapleDisease> localCureDebuffs = new ArrayList<>();
        if (DataUtil.toInt(source.resolve("poison"), 0) > 0) {
            localCureDebuffs.add(MapleDisease.POISON);
        }
        if (DataUtil.toInt(source.resolve("seal"), 0) > 0) {
            localCureDebuffs.add(MapleDisease.SEAL);
        }
        if (DataUtil.toInt(source.resolve("darkness"), 0) > 0) {
            localCureDebuffs.add(MapleDisease.DARKNESS);
        }
        if (DataUtil.toInt(source.resolve("weakness"), 0) > 0) {
            localCureDebuffs.add(MapleDisease.WEAKEN);
        }
        if (DataUtil.toInt(source.resolve("curse"), 0) > 0) {
            localCureDebuffs.add(MapleDisease.CURSE);
        }
        ret.cureDebuffs = localCureDebuffs;

        Map<MonsterStatus, Integer> monsterStatus = new EnumMap<>(MonsterStatus.class);

        if (skill) { // hack because we can't get from the datafile...
            switch (sourceid) {
                case 2001002: // magic guard
                case 12001001:
                    statups.add(new Pair<>(MapleBuffStat.MAGIC_GUARD, x));
                    break;
                case 2301003: // invincible
                    statups.add(new Pair<>(MapleBuffStat.INVINCIBLE, x));
                    break;
                case 4001003: // darksight
                case 13101006:
                case 14001003:
                    statups.add(new Pair<>(MapleBuffStat.DARKSIGHT, x));
                    break;
                case 4211003: // pickpocket
                    statups.add(new Pair<>(MapleBuffStat.PICKPOCKET, x));
                    break;
                case 4211005: // mesoguard
                    statups.add(new Pair<>(MapleBuffStat.MESOGUARD, x));
                    break;
                case 4111001: // mesoup
                    statups.add(new Pair<>(MapleBuffStat.MESOUP, x));
                    break;
                case 4111002: // shadowpartner
                case 14111000:
                    statups.add(new Pair<>(MapleBuffStat.SHADOWPARTNER, x));
                    break;
                case 3101004: // soul arrow
                case 3201004:
                case 13101003:
                case 2311002: // mystic door - hacked buff icon
                    statups.add(new Pair<>(MapleBuffStat.SOULARROW, x));
                    break;
                case 1211003:
                case 1211004:
                case 1211005:
                case 1211006: // wk charges
                case 1211007:
                case 1211008:
                case 1221003:
                case 1221004:
                case 11111007:
                case 15101006:
                case 21111005:
                    statups.add(new Pair<>(MapleBuffStat.WK_CHARGE, x));
                    break;
                case 1101004:
                case 1101005: // booster
                case 1201004:
                case 1201005:
                case 1301004:
                case 1301005:
                case 2111005: // spell booster, do these work the same?
                case 2211005:
                case 3101002:
                case 3201002:
                case 4101003:
                case 4201002:
                case 5101006:
                case 5201003:
                case 11101001:
                case 12101004:
                case 13101001:
                case 14101002:
                case 15101002:
                case 21001003:
                    statups.add(new Pair<>(MapleBuffStat.BOOSTER, x));
                    break;
                case 5121009: // speed infusion
                case 15111005:
                    statups.add(new Pair<>(MapleBuffStat.SPEED_INFUSION, x));
                    break;
                case 1101006: // rage
                    statups.add(new Pair<>(MapleBuffStat.WDEF, (int) ret.wdef));
                case 1121010: // enrage
                    statups.add(new Pair<>(MapleBuffStat.WATK, (int) ret.watk));
                    break;
                case 1301006: // iron will
                    statups.add(new Pair<>(MapleBuffStat.MDEF, (int) ret.mdef));
                case 1001003: // iron body
                    statups.add(new Pair<>(MapleBuffStat.WDEF, (int) ret.wdef));
                    break;
                case 2001003: // magic armor
                    statups.add(new Pair<>(MapleBuffStat.WDEF, (int) ret.wdef));
                    break;
                case 2101001: // meditation
                case 2201001: // meditation
                    statups.add(new Pair<>(MapleBuffStat.MATK, (int) ret.matk));
                    break;
                case 4101004: // haste
                case 4201003: // haste
                case 9101001: // gm haste
                    statups.add(new Pair<>(MapleBuffStat.SPEED, (int) ret.speed));
                    statups.add(new Pair<>(MapleBuffStat.JUMP, (int) ret.jump));
                    break;
                case 2301004: // bless
                    statups.add(new Pair<>(MapleBuffStat.WDEF, (int) ret.wdef));
                    statups.add(new Pair<>(MapleBuffStat.MDEF, (int) ret.mdef));
                case 3001003: // focus
                    statups.add(new Pair<>(MapleBuffStat.ACC, (int) ret.acc));
                    statups.add(new Pair<>(MapleBuffStat.AVOID, (int) ret.avoid));
                    break;
                case 9101003: // gm bless
                    statups.add(new Pair<>(MapleBuffStat.MATK, (int) ret.matk));
                case 3121008: // concentrate
                    statups.add(new Pair<>(MapleBuffStat.WATK, (int) ret.watk));
                    break;
                case 5001005: // Dash
                case 15001003: // Cygnus Dash
                    statups.add(new Pair<>(MapleBuffStat.DASH_SPEED, x));
                    statups.add(new Pair<>(MapleBuffStat.DASH_JUMP, ret.y));
                    break;
                case 1101007: // pguard
                case 1201007:
                    statups.add(new Pair<>(MapleBuffStat.POWERGUARD, x));
                    break;
                case 1301007:
                case 9101008:
                    statups.add(new Pair<>(MapleBuffStat.HYPERBODYHP, x));
                    statups.add(new Pair<>(MapleBuffStat.HYPERBODYMP, ret.y));
                    break;
                case 12101005:
                    statups.add(new Pair<>(MapleBuffStat.ELEMENTAL_RESET, x));
                    break;
                case 1001: // recovery
                    statups.add(new Pair<>(MapleBuffStat.RECOVERY, x));
                    break;
                case 1111002: // combo
                case 11111001:
                    statups.add(new Pair<>(MapleBuffStat.COMBO, 1));
                    break;
                case 1004: // monster riding
                case 5221006: // 4th Job - Pirate riding
                case 10001004:
                case 20001004:
                    statups.add(new Pair<>(MapleBuffStat.MONSTER_RIDING, 1));
                    break;
                case 1311006: //dragon roar
                    ret.hpR = -x / 100.0;
                    statups.add(new Pair<>(MapleBuffStat.DRAGON_ROAR, ret.y));
                    break;
                case 1311008: // dragon blood
                    statups.add(new Pair<>(MapleBuffStat.DRAGONBLOOD, ret.x));
                    break;
                case 1121000: // maple warrior, all classes
                case 1221000:
                case 1321000:
                case 2121000:
                case 2221000:
                case 2321000:
                case 3121000:
                case 3221000:
                case 4121000:
                case 4221000:
                case 5121000:
                case 5221000:
                case 21121000:
                    statups.add(new Pair<>(MapleBuffStat.MAPLE_WARRIOR, ret.x));
                    break;
                case 3121002: // sharp eyes bowmaster
                case 3221002: // sharp eyes marksmen
                    statups.add(new Pair<>(MapleBuffStat.SHARP_EYES, ret.x << 8 | ret.y));
                    break;
                case 21000000: // Aran Combo
                    statups.add(new Pair<>(MapleBuffStat.ARAN_COMBO, 100));
                    break;
                case 21101003: // Body Pressure
                    statups.add(new Pair<>(MapleBuffStat.BODY_PRESSURE, ret.x));
                    break;
                case 21100005: // Combo Drain
                    statups.add(new Pair<>(MapleBuffStat.COMBO_DRAIN, ret.x));
                    break;
                case 21111001: // Smart Knockback
                    statups.add(new Pair<>(MapleBuffStat.SMART_KNOCKBACK, ret.x));
                    break;
                case 21120007: // Combo Barrier
                    statups.add(new Pair<>(MapleBuffStat.COMBO_BARRIER, ret.x));
                    break;
                case 1321007: // Beholder
                case 2221005: // ifrit
                case 2311006: // summon dragon
                case 2321003: // bahamut
                case 3121006: // phoenix
                case 5211001: // Pirate octopus summon
                case 5211002: // Pirate bird summon
                case 5220002: // wrath of the octopi
                case 11001004: // Soul Master "Soul"
                case 12001004: // Flame Wizard "Flame"
                case 13001004: // Windbreaker "Storm"
                case 14001005: // Nightwalker "Darkness"
                case 15001004: // Thunder guy "Lightning"
                case 12111004: // fw ifrit
                    statups.add(new Pair<>(MapleBuffStat.SUMMON, 1));
                    break;
                case 2311003: // holy symbol
                case 9101002: // GM holy symbol
                    statups.add(new Pair<>(MapleBuffStat.HOLY_SYMBOL, x));
                    break;
                case 4121006: // spirit claw
                    statups.add(new Pair<>(MapleBuffStat.SHADOW_CLAW, 0));
                    break;
                case 2121004:
                case 2221004:
                case 2321004: // Infinity
                    statups.add(new Pair<>(MapleBuffStat.INFINITY, x));
                    break;
                case 1121002:
                case 1221002:
                case 1321002: // Stance
                case 21121003: // Aran - Freezing Posture
                    statups.add(new Pair<>(MapleBuffStat.STANCE, iprop));
                    break;
                case 1005: // Echo of Hero
                case 10001005:
                case 20001005:
                    statups.add(new Pair<>(MapleBuffStat.ECHO_OF_HERO, ret.x));
                    break;
                case 2121002: // mana reflection
                case 2221002:
                case 2321002:
                    statups.add(new Pair<>(MapleBuffStat.MANA_REFLECTION, 1));
                    break;
                case 2321005: // holy shield
                    statups.add(new Pair<>(MapleBuffStat.HOLY_SHIELD, x));
                    break;
                case 3111002: // puppet ranger
                case 3211002: // puppet sniper
                case 13111004: // puppeeet
                    statups.add(new Pair<>(MapleBuffStat.PUPPET, 1));
                    break;
                case 1010:
                case 10001010:
                case 20001010:
                    statups.add(new Pair<>(MapleBuffStat.DOJO_INVINCIBILITY, ret.x));
                    break;
                case 1011:
                case 10001011:
                case 20001011:
                    statups.add(new Pair<>(MapleBuffStat.POWER_EXPLOSION, ret.x));
                    break;

                // ----------------------------- MONSTER STATUS PUT! ----------------------------- //

                case 4001002: // disorder
                case 14001002: // cyg disorder
                case 1201006: // threaten
                    monsterStatus.put(MonsterStatus.WATK, ret.x);
                    monsterStatus.put(MonsterStatus.WDEF, ret.y);
                    break;
                case 1211002: // charged blow
                case 1111008: // shout
                case 4211002: // assaulter
                case 3101005: // arrow bomb
                case 1111005: // coma: sword
                case 1111006: // coma: axe
                case 4221007: // boomerang step
                case 5101002: // Backspin Blow
                case 5101003: // Double Uppercut
                case 5121004: // Demolition
                case 5121005: // Snatch
                case 5121007: // Barrage
                case 5201004: // pirate blank shot
                    monsterStatus.put(MonsterStatus.STUN, 1);
                    break;
                case 4121003:
                case 4221003:
                    monsterStatus.put(MonsterStatus.TAUNT, ret.x);
                    monsterStatus.put(MonsterStatus.MDEF, ret.x);
                    monsterStatus.put(MonsterStatus.WDEF, ret.x);
                    break;
                case 4121004: // Ninja ambush
                case 4221004:
                    monsterStatus.put(MonsterStatus.NINJA_AMBUSH, 1);
                    break;
                case 2201004: // cold beam
                case 2211002: // ice strike
                case 3211003: // blizzard
                case 2211006: // il elemental compo
                case 2221007: // Blizzard
                case 5211005: // Ice Splitter
                case 2121006: // Paralyze
                case 21120006:// Combo Tempest
                    monsterStatus.put(MonsterStatus.FREEZE, 1);
                    ret.duration *= 2; // freezing skills are a little strange
                    break;
                case 2221003:
                    monsterStatus.put(MonsterStatus.FREEZE, 1);
                case 2121003: // fire demon
                    monsterStatus.put(MonsterStatus.POISON, 1);
                    break;
                case 2101003: // fp slow
                case 2201003: // il slow
                case 12101001: // FW Slow
                    monsterStatus.put(MonsterStatus.SPEED, ret.x);
                    break;
                case 2101005: // poison breath
                case 2111006: // fp elemental compo
                    monsterStatus.put(MonsterStatus.POISON, 1);
                    break;
                case 2311005:
                    monsterStatus.put(MonsterStatus.DOOM, 1);
                    break;
                case 3111005: // golden hawk
                case 3211005: // golden eagle
                    statups.add(new Pair<>(MapleBuffStat.SUMMON, 1));
                    monsterStatus.put(MonsterStatus.STUN, 1);
                    break;
                case 2121005: // elquines
                case 3221005: // frostprey
                    statups.add(new Pair<>(MapleBuffStat.SUMMON, 1));
                    monsterStatus.put(MonsterStatus.FREEZE, 1);
                    break;
                case 2111004: // fp seal
                case 2211004: // il seal
                case 12111002: // FW Seal
                    monsterStatus.put(MonsterStatus.SEAL, 1);
                    break;
                case 4111003: // shadow web
                case 14111001:
                    monsterStatus.put(MonsterStatus.SHADOW_WEB, 1);
                    break;
                case 3121007: // Hamstring
                    statups.add(new Pair<>(MapleBuffStat.HAMSTRING, x));
                    monsterStatus.put(MonsterStatus.SPEED, x);
                    break;
                case 3221006: // Blind
                    statups.add(new Pair<>(MapleBuffStat.BLIND, x));
                    monsterStatus.put(MonsterStatus.ACC, x);
                    break;
                case 5221009:
                    monsterStatus.put(MonsterStatus.HYPNOTIZED, 1);
                    break;
                case 5211006:
                case 5220011:
                    statups.add(new Pair<>(MapleBuffStat.HOMING_BEACON, -1));
                    break;
                case 11101002:
                case 13101002:
                    statups.add(new Pair<>(MapleBuffStat.CYGFINALATTACK, 1));
                    break;
            }
        }

        if (ret.isMorph() && !ret.isPirateMorph()) {
            statups.add(new Pair<>(MapleBuffStat.MORPH, ret.morphId));
        }

        ret.monsterStatus = monsterStatus;

        statups.trimToSize();
        ret.statups = statups;

        return ret;
    }

    public String getRemark() {
        return remark;
    }

    /**
     * @param applyto
     * @param obj
     * @param attack  damage done by the skill
     */
    public void applyPassive(MapleCharacter applyto, MapleMapObject obj, int attack) {
        if (makeChanceResult()) {
            // MP eater
            // x is absorb percentage
            switch (sourceid) {
                case 2100000, 2200000, 2300000 -> {
                    if (!(obj instanceof MapleMonster)) {
                        return;
                    }
                    MapleMonster mob = (MapleMonster) obj;
                    if (!mob.isBoss()) {
                        int absorbMp = Math.min((int) (mob.getMaxMp() * x / 100.0), mob.getMp());
                        if (absorbMp > 0) {
                            mob.setMp(mob.getMp() - absorbMp);
                            applyto.addMP(absorbMp);
                            applyto.getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(sourceid, 1, (byte) applyto.getLevel()));
                            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showBuffeffect(applyto.getId(), sourceid, 1, (byte) applyto.getLevel(), (byte) 3), false);
                        }
                    }
                }
            }
        }
    }

    public final void applyComboBuff(MapleCharacter applyto, short combo) {
        final List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.ARAN_COMBO, (int) combo));
        applyto.getClient().sendPacket(MaplePacketCreator.giveBuff(applyto, sourceid, 99999, stat)); // Hackish timing, todo find out
        applyto.registerEffect(this, System.currentTimeMillis(), null);
    }

    public boolean applyTo(MapleCharacter chr) {
        return applyTo(chr, chr, true, null, -1);
    }

    public boolean applyTo(MapleCharacter chr, Point pos) {
        return applyTo(chr, chr, true, pos, -1);
    }

    public boolean applyTo(MapleCharacter chr, int beacon) {
        return applyTo(chr, chr, true, null, beacon);
    }

    private boolean applyTo(MapleCharacter applyfrom, MapleCharacter applyto, boolean primary, Point pos, int oid) {
        int hpchange = calcHPChange(applyfrom, primary);
        int mpchange = calcMPChange(applyfrom, primary);

        if (primary && !isMagicDoor()) {
            if (itemConNo != 0) {
                MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemCon);
                MapleInventoryManipulator.removeById(applyto.getClient(), type, itemCon, itemConNo, false, true);
            }
        }
        if (!cureDebuffs.isEmpty()) {
            for (MapleDisease debuff : cureDebuffs) {
                applyfrom.dispelDebuff(debuff);
            }
        }
        List<Pair<MapleStat, Integer>> hpmpupdate = new ArrayList<>(2);
        if (!primary && isResurrection()) {
            hpchange = applyto.getMaxHp();
            applyto.setStance((byte) 0); //TODO fix death bug, player doesnt spawn on other screen
        }
        if (isComboDrain()) {
            applyto.setComboCounter((short) 0);
        } else if (isDispel() && makeChanceResult()) {
            applyto.dispelDebuffs();
        } else if (isHeroWill()) {
            applyto.cancelAllDebuffs();
        }
        if (isDispel() && primary) {
            priestDispelMonster(applyfrom);
        }
        if (hpchange != 0) {
            if (hpchange < 0 && -hpchange >= applyto.getHp()) {
                return false;
            }
            int newHp = applyto.getHp() + hpchange;
            if (newHp < 1) {
                newHp = 1;
            }
            applyto.setHp(newHp);
            hpmpupdate.add(new Pair<>(MapleStat.HP, applyto.getHp()));
        }
        if (mpchange != 0) {
            if (mpchange < 0 && -mpchange > applyto.getMp()) {
                return false;
            }
            applyto.setMp(applyto.getMp() + mpchange);
            hpmpupdate.add(new Pair<>(MapleStat.MP, applyto.getMp()));
        }
        applyto.getClient().sendPacket(MaplePacketCreator.updatePlayerStats(hpmpupdate, true));
        if (moveTo != -1) {
            MapleMap target = null;
            boolean nearest = false;
            if (moveTo == 999999999) {
                nearest = true;
                if (applyto.getMap().getReturnMapId() != 999999999) {
                    target = applyto.getMap().getReturnMap();
                }
            } else {
                target = applyto.getClient().getChannelServer().getMapFactory().getMap(moveTo);
                int targetMapId = target.getId() / 10000000;
                int charMapId = applyto.getMapId() / 10000000;
                if (targetMapId != 60 && charMapId != 61) {
                    if (targetMapId != 21 && charMapId != 20) {
                        if (targetMapId != 12 && charMapId != 10) {
                            if (targetMapId != 10 && charMapId != 12) {
                                if (targetMapId != charMapId) {
                                    log.info("Player {} is trying to use a return scroll to an illegal location ({}->{})", applyto.getName(), applyto.getMapId(), target.getId());
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
            if (target == applyto.getMap() || nearest && applyto.getMap().isTown()) {
                return false;
            }
        }
        if (isShadowClaw()) {
            MapleInventory use = applyto.getInventory(MapleInventoryType.USE);
            MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
            int projectile = 0;
            for (int i = 0; i < 255; i++) { // impose order...
                IItem item = use.getItem((short) i);
                if (item != null) {
                    boolean isStar = mii.isThrowingStar(item.getItemId());
                    if (isStar && item.getQuantity() >= 200) {
                        projectile = item.getItemId();
                        break;
                    }
                }
            }
            if (projectile == 0) {
                return false;
            } else {
                MapleInventoryManipulator.removeById(applyto.getClient(), MapleInventoryType.USE, projectile, 200, false, true);
            }
        }
        if (overTime) {
            applyBuffEffect(applyfrom, applyto, primary, oid);
        }
        if (primary) {
            if (overTime || isHeal() || isTimeLeap()) {
                applyBuff(applyfrom);
            }
            if (isMonsterBuff()) {
                applyMonsterBuff(applyfrom);
            }
        }
        SummonMovementType summonMovementType = getSummonMovementType();
        if (summonMovementType != null && pos != null) {
            final MapleSummon tosummon = new MapleSummon(applyfrom, sourceid, pos, summonMovementType);
            if (!tosummon.isPuppet()) {
                applyfrom.getCheatTracker().resetSummonAttack();
            }
            applyfrom.getMap().spawnSummon(tosummon);
            applyfrom.getSummons().put(sourceid, tosummon);
            tosummon.addHP(x);
            if (isBeholder()) {
                tosummon.addHP(1);
            }
        }

        // Magic Door
        if (isMagicDoor()) {
            //applyto.cancelMagicDoor();
            Point doorPosition = new Point(applyto.getPosition());
            //doorPosition.y -= 280;
            MapleDoor door = new MapleDoor(applyto, doorPosition);
            if (door.getTownPortal() != null) {
                applyto.getMap().spawnDoor(door);
                applyto.setDoor(0, door);
                door = new MapleDoor(door);
                applyto.setDoor(1, door);
                door.getTown().spawnDoor(door);
                if (applyto.getParty() != null) {
                    // update town doors
                    applyto.silentPartyUpdate();
                }
                applyto.disableDoor();
                MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemCon);
                MapleInventoryManipulator.removeById(applyto.getClient(), type, itemCon, itemConNo, false, true);
            } else {
                return false;
            }
        } else if (isMist()) {
            Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
            MapleMist mist = new MapleMist(bounds, applyfrom, this);
            applyfrom.getMap().spawnMist(mist, duration, false);
        } else if (isTimeLeap()) {
            for (SkillCooldown i : applyto.getAllCooldowns()) {
                if (i.getSkillId() != 5121010) {
                    applyto.removeCooldown(i.getSkillId());
                }
            }
        } else if (isHide()) {
            if (applyto.isHidden()) {
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.removePlayerFromMap(applyto.getId()), false);
                applyto.getClient().sendPacket(MaplePacketCreator.giveGMHide(true));
            } else {
                applyto.getClient().sendPacket(MaplePacketCreator.giveGMHide(false));
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.spawnPlayerMapobject(applyto, false), false);
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.playerGuildName(applyto), false);
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.playerGuildInfo(applyto), false);
                if (applyto.hasGMLevel(5)) {
                    for (MapleCharacter c : applyto.getMap().getCharacters()) {
                        c.finishAchievement(11);
                    }
                }
            }
        } else if (isPowerCrash()) {
            final Rectangle rect = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
            List<MapleMapObject> mobs = applyto.getMap().getMapObjectsInRect(rect, Collections.singletonList(MapleMapObjectType.MONSTER));
            int count = 0;
            for (MapleMapObject mobz : mobs) {
                final MapleMonster mob = (MapleMonster) mobz;
                if (makeChanceResult()) {
                    mob.dispelBuffs();
                }
                count++;
                if (count >= mobCount) {
                    break;
                }
            }
        }
        return true;
    }

    public void useTownScroll(MapleCharacter player) {
        MapleMap target;
        if (moveTo == 999999999) {
            target = player.getMap().getReturnMap();
        } else {
            target = player.getClient().getChannelServer().getMapFactory().getMap(moveTo);
        }
        if (target != null && target.canEnter() && player.getMap().canExit() || player.isGM()) {
            player.changeMap(target, target.getRandomSpawnPoint());
        } else {
            player.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "Either the map you are being returned to cannot be entered or the map you are in cannot be exited. As such, you will not be warped."));
        }
    }

    private void applyBuff(MapleCharacter applyfrom) {
        if (isPartyBuff() && (applyfrom.getParty() != null || isGMBuff())) {
            Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
            List<MapleMapObject> affecteds = applyfrom.getMap().getMapObjectsInRect(bounds, Collections.singletonList(MapleMapObjectType.PLAYER));
            List<MapleCharacter> affectedp = new ArrayList<>(affecteds.size());
            for (MapleMapObject affectedmo : affecteds) {
                MapleCharacter affected = (MapleCharacter) affectedmo;
                //this is new and weird...
                if (affected != null && isHeal() && affected != applyfrom && affected.getParty() == applyfrom.getParty() && affected.isAlive()) {
                    int expadd = (int) (calcHPChange(applyfrom, false) / 25.25 * applyfrom.getClient().getChannelServer().getExpRate());
                    if (affected.getHp() < affected.getCurrentMaxHp() - affected.getCurrentMaxHp() / 20) {
                        applyfrom.gainExp(expadd, true, false, true, false);
                        applyfrom.resetMobKillTimer();
                    }
                }
                if (affected != applyfrom && (isGMBuff() || applyfrom.getParty().equals(affected.getParty()))) {
                    boolean isRessurection = isResurrection();
                    if (isRessurection && !affected.isAlive() || !isRessurection && affected.isAlive()) {
                        affectedp.add(affected);
                    }
                }
            }
            for (MapleCharacter affected : affectedp) {
                // TODO actually heal (and others) shouldn't recalculate everything
                // for heal this is an actual bug since heal hp is decreased with the number
                // of affected players
                applyTo(applyfrom, affected, false, null, -1);
                affected.getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(sourceid, 2, (byte) applyfrom.getLevel()));
                affected.getMap().broadcastMessage(affected, MaplePacketCreator.showBuffeffect(affected.getId(), sourceid, 2, (byte) applyfrom.getLevel(), (byte) 3), false);
            }
        }
    }

    private void applyMonsterBuff(MapleCharacter applyfrom) {
        Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
        List<MapleMapObject> affected = applyfrom.getMap().getMapObjectsInRect(bounds, Collections.singletonList(MapleMapObjectType.MONSTER));
        ISkill skill_ = SkillFactory.getSkill(sourceid);
        int i = 0;
        for (MapleMapObject mo : affected) {
            MapleMonster monster = (MapleMonster) mo;
            if (makeChanceResult()) {
                monster.applyStatus(applyfrom, new MonsterStatusEffect(monsterStatus, skill_, false), isPoison(), duration);
            }
            i++;
            if (i >= mobCount) {
                break;
            }
        }
    }

    private void priestDispelMonster(MapleCharacter applyfrom) {
        Rectangle bounds = calculateBoundingBox(applyfrom.getPosition(), applyfrom.isFacingLeft());
        List<MapleMapObject> affected = applyfrom.getMap().getMapObjectsInRect(bounds, Collections.singletonList(MapleMapObjectType.MONSTER));
        int i = 0;
        for (MapleMapObject mo : affected) {
            MapleMonster monster = (MapleMonster) mo;
            if (monster.isBoss()) {
                continue; // no dispel boss :(((((( or i dispel zak's magic damage lock :D
            }
            if (makeChanceResult()) {
                monster.dispelBuffs();
            }
            i++;
            if (i >= mobCount) {
                break;
            }
        }
    }

    private Rectangle calculateBoundingBox(Point posFrom, boolean facingLeft) {
        Point mylt;
        Point myrb;
        if (facingLeft) {
            mylt = new Point(lt.x + posFrom.x, lt.y + posFrom.y);
            myrb = new Point(rb.x + posFrom.x, rb.y + posFrom.y);
        } else {
            myrb = new Point(lt.x * -1 + posFrom.x, rb.y + posFrom.y);
            mylt = new Point(rb.x * -1 + posFrom.x, lt.y + posFrom.y);
        }
        return new Rectangle(mylt.x, mylt.y, myrb.x - mylt.x, myrb.y - mylt.y);
    }

    public void silentApplyBuff(MapleCharacter chr, long starttime) {
        int localDuration = duration;
        localDuration = alchemistModifyVal(chr, localDuration, false);
        CancelEffectAction cancelAction = new CancelEffectAction(chr, this, starttime);
        ScheduledFuture<?> schedule = TimerManager.getInstance().schedule(cancelAction, (starttime + localDuration - System.currentTimeMillis()));
        chr.registerEffect(this, starttime, schedule);

        SummonMovementType summonMovementType = getSummonMovementType();
        if (summonMovementType != null) {
            final MapleSummon tosummon = new MapleSummon(chr, sourceid, chr.getPosition(), summonMovementType);
            if (!tosummon.isPuppet()) {
                chr.getCheatTracker().resetSummonAttack();
                chr.getSummons().put(sourceid, tosummon);
                tosummon.addHP(x);
            }
        }
    }

    private void applyBuffEffect(MapleCharacter applyfrom, MapleCharacter applyto, boolean primary, int oid) {
        if (!isMonsterRiding()) {
            if (isHomingBeacon()) {
                applyto.offBeacon(true);
            } else {
                applyto.cancelEffect(this, true, -1);
            }
        }
        List<Pair<MapleBuffStat, Integer>> localstatups = statups;
        int localDuration = duration;
        int localsourceid = sourceid;
        if (isMonsterRiding()) {
            int ridingLevel = 0;
            IItem mount = applyfrom.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -18);
            if (mount != null) {
                ridingLevel = mount.getItemId();
            }
            localDuration = sourceid;
            localsourceid = ridingLevel;
            localstatups = Collections.singletonList(new Pair<>(MapleBuffStat.MONSTER_RIDING, 0));
        } else if (isBattleShip()) {
            int ridingLevel = 1932000;
            localDuration = sourceid;
            localsourceid = ridingLevel;
            localstatups = Collections.singletonList(new Pair<>(MapleBuffStat.MONSTER_RIDING, 0));
        }
        if (primary) {
            localDuration = alchemistModifyVal(applyfrom, localDuration, false);
        }
        if (!localstatups.isEmpty()) {
            if (isDash()) {
                applyto.getClient().sendPacket(MaplePacketCreator.giveDash(localstatups, sourceid, localDuration / 1000));
            } else if (isInfusion()) {
                applyto.getClient().sendPacket(MaplePacketCreator.giveSpeedInfusion(sourceid, localDuration / 1000, localstatups));
            } else if (isHomingBeacon()) {
                applyto.setBeacon(oid);
                applyto.getClient().sendPacket(MaplePacketCreator.giveHomingBeacon(localsourceid, oid));
            } else {
                if (!skill || !SkillFactory.getSkill(sourceid).isSummon()) {
                    applyto.getClient().sendPacket(MaplePacketCreator.giveBuff(applyto, (skill ? localsourceid : -localsourceid), localDuration, localstatups));
                }
            }
        } else { //apply empty buff icon
            applyto.getClient().sendPacket(MaplePacketCreator.giveBuffTest((skill ? localsourceid : -localsourceid), localDuration, 0));
        }
        if (isMonsterRiding()) {
            int ridingLevel = 0;
            IItem mount = applyfrom.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -18);
            if (mount != null) {
                ridingLevel = mount.getItemId();
            }
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.MONSTER_RIDING, 1));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showMonsterRiding(applyto.getId(), stat, ridingLevel, sourceid), false);
            localDuration = duration;
        } else if (isBattleShip()) {
            int ridingLevel = 1932000;
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.MONSTER_RIDING, 1));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showMonsterRiding(applyto.getId(), stat, ridingLevel, sourceid), false);
            localDuration = duration;
        } else if (isDs()) {
            List<Pair<MapleBuffStat, Integer>> dsstat = Collections.singletonList(new Pair<>(MapleBuffStat.DARKSIGHT, 0));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, dsstat, this), false);
        } else if (isCombo()) {
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, 1));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, stat, this), false);
        } else if (isShadowPartner()) {
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.SHADOWPARTNER, 0));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, stat, this), false);
        } else if (isSoulArrow()) {
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.SOULARROW, 0));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, stat, this), false);
        } else if (isEnrage()) {
            applyto.handleOrbconsume();
        } else if (isMorph()) {
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.MORPH, morphId));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, stat, this), false);
        } else if (isPirateMorph()) {
            List<Pair<MapleBuffStat, Integer>> stat = new ArrayList<>();
            stat.add(new Pair<>(MapleBuffStat.SPEED, (int) speed));
            stat.add(new Pair<>(MapleBuffStat.MORPH, morphId));
            applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignBuff(applyto, stat, this), false);
        }
        if (!localstatups.isEmpty()) {
            long starttime = System.currentTimeMillis();
            CancelEffectAction cancelAction = new CancelEffectAction(applyto, this, starttime);
            ScheduledFuture<?> schedule = isHomingBeacon() ? null : TimerManager.getInstance().schedule(cancelAction, localDuration);
            applyto.registerEffect(this, starttime, schedule);
        }
        if (primary && !isHide()) {
            if (isDash()) {
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showDashEffecttoOthers(applyto.getId(), localstatups, sourceid, localDuration / 1000), false);
            } else if (isInfusion()) {
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.giveForeignInfusion(applyto.getId(), localstatups, localDuration, sourceid), false);
            } else if (!isHomingBeacon()) {
                applyto.getMap().broadcastMessage(applyto, MaplePacketCreator.showBuffeffect(applyto.getId(), sourceid, 1, (byte) applyto.getLevel(), (byte) 3), false);
            }
        }
    }

    private int calcHPChange(MapleCharacter applyfrom, boolean primary) {
        int hpchange = 0;
        if (hp != 0) {
            if (!skill) {
                if (primary) {
                    hpchange += alchemistModifyVal(applyfrom, hp, true);
                } else {
                    hpchange += hp;
                }
            } else { // assumption: this is heal
                hpchange += makeHealHP(hp / 100.0, applyfrom.getTotalMagic(), 3, 5);
            }
        }
        if (hpR != 0) {
            hpchange += (int) (applyfrom.getCurrentMaxHp() * hpR);
            applyfrom.checkBerserk();
        }
        if (primary) {
            if (hpCon != 0) {
                hpchange -= hpCon;
            }
        }
        if (isChakra()) {
            hpchange += makeHealHP(y / 100.0, applyfrom.getTotalLuk(), 2.3, 3.5);
        }
        if (isPirateMpRecovery()) {
            hpchange -= x / 100.0 * applyfrom.getCurrentMaxHp();
        }
        return hpchange;
    }

    private int makeHealHP(double rate, double stat, double lowerfactor, double upperfactor) {
        int maxHeal = (int) (stat * upperfactor * rate);
        int minHeal = (int) (stat * lowerfactor * rate);
        return (int) (Math.random() * (maxHeal - minHeal + 1) + minHeal);
    }

    public int calcMagicAttackMPUsage(MapleCharacter magician) {
        return calcMPChange(magician, true);
    }

    private int calcMPChange(MapleCharacter applyfrom, boolean primary) {
        int mpchange = 0;
        if (mp != 0) {
            if (primary) {
                mpchange += alchemistModifyVal(applyfrom, mp, true);
            } else {
                mpchange += mp;
            }
        }
        if (mpR != 0) {
            mpchange += (int) (applyfrom.getCurrentMaxMp() * mpR);
        }
        if (primary) {
            if (mpCon != 0) {
                double mod = 1.0;
                boolean isAFpMage = applyfrom.getJob().isA(MapleJob.FP_MAGE);
                boolean isAIlMage = applyfrom.getJob().isA(MapleJob.IL_MAGE);
                boolean isAFw = applyfrom.getJob().isA(MapleJob.BLAZEWIZARD3);
                ISkill skill_ = SkillFactory.getSkill(sourceid);
                if ((isAFpMage || isAIlMage || isAFw) && skill_.isAttack() && !skill_.isSummon()) {
                    ISkill amp;
                    if (isAFpMage) {
                        amp = SkillFactory.getSkill(2110001);
                    } else if (isAIlMage) {
                        amp = SkillFactory.getSkill(2210001);
                    } else {
                        amp = SkillFactory.getSkill(12110001);
                    }
                    int ampLevel = applyfrom.getSkillLevel(amp);
                    if (ampLevel > 0) {
                        mod = amp.getEffect(ampLevel).x / 100.0;
                    }
                }
                mpchange -= mpCon * mod;
                if (applyfrom.getBuffedValue(MapleBuffStat.INFINITY) != null) {
                    mpchange = 0;
                }
            }
        }
        if (isPirateMpRecovery()) {
            mpchange += y * x / 10000.0 * applyfrom.getCurrentMaxHp();
        }
        return mpchange;
    }

    private int alchemistModifyVal(MapleCharacter chr, int val, boolean withX) {
        if (!skill && (chr.getJob().isA(MapleJob.HERMIT) || chr.getJob().isA(MapleJob.NIGHTLORD))) {
            MapleStatEffect alchemistEffect = getAlchemistEffect(chr);
            if (alchemistEffect != null) {
                return (int) (val * (withX ? alchemistEffect.x : alchemistEffect.y) / 100.0);
            }
        }
        return val;
    }

    private MapleStatEffect getAlchemistEffect(MapleCharacter chr) {
        ISkill alchemist = SkillFactory.getSkill(4110000);
        int alchemistLevel = chr.getSkillLevel(alchemist);
        ISkill alchemist2 = SkillFactory.getSkill(14110003);
        int alchemistLevel2 = chr.getSkillLevel(alchemist2);

        if (alchemistLevel == 0 && alchemistLevel2 == 0) {
            return null;
        }
        boolean cyg = chr.getJob().isA(MapleJob.NIGHTWALKER3);
        if (cyg) {
            return alchemist2.getEffect(alchemistLevel2);
        } else {
            return alchemist.getEffect(alchemistLevel);
        }
    }

    public void setSourceId(int newid) {
        sourceid = newid;
    }

    private boolean isGMBuff() {
        switch (sourceid) {
            case 1005: // Echo of Hero
            case 10001005:
            case 20001005:
            case 9101000:
            case 9101001:
            case 9101002:
            case 9101003:
            case 9101005:
            case 9101008:
                return true;
            default:
                return false;
        }
    }

    private boolean isMonsterBuff() {
        if (!skill) {
            return false;
        }
        switch (sourceid) {
            case 1201006: // threaten
            case 2101003: // fp slow
            case 2201003: // il slow
            case 12101001:
            case 2211004: // il seal
            case 2111004: // fp seal
            case 12111002:
            case 2311005: // doom
            case 4111003: // shadow web
            case 14111001:
            case 4121004: // Ninja ambush
            case 4421004: // Ninja ambush
                return true;
        }
        return false;
    }

    private boolean isPartyBuff() {
        if (!skill) {
            return false;
        }
        if (lt == null || rb == null) {
            return false;
        }
        return !(sourceid >= 1211003 && sourceid <= 1211008 || sourceid == 1221003 || sourceid == 1221004);
    }

    public boolean isHeal() {
        return skill && (sourceid == 2301002 || sourceid == 9101000);
    }

    public boolean isResurrection() {
        return skill && (sourceid == 9101005 || sourceid == 2321006);
    }

    public boolean isTimeLeap() {
        return skill && sourceid == 5121010;
    }

    public boolean isInfusion() {
        return skill && (sourceid == 5121009 || sourceid == 15111005);
    }

    public short getHp() {
        return hp;
    }

    public short getMp() {
        return mp;
    }

    public int getMpCon() {
        return mpCon;
    }

    public short getWatk() {
        return watk;
    }

    public short getMatk() {
        return matk;
    }

    public short getWdef() {
        return wdef;
    }

    public short getMdef() {
        return mdef;
    }

    public short getAcc() {
        return acc;
    }

    public short getAvoid() {
        return avoid;
    }

    public short getHands() {
        return hands;
    }

    public short getSpeed() {
        return speed;
    }

    public short getJump() {
        return jump;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isOverTime() {
        return overTime;
    }

    public List<Pair<MapleBuffStat, Integer>> getStatups() {
        return statups;
    }

    public boolean sameSource(MapleStatEffect effect) {
        return sourceid == effect.sourceid && skill == effect.skill;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getDamage() {
        return damage;
    }

    public int getAttackCount() {
        return attackCount;
    }

    public int getMobCount() {
        return mobCount;
    }

    public int getBulletCount() {
        return bulletCount;
    }

    public int getBulletConsume() {
        return bulletConsume;
    }

    public int getMoneyCon() {
        return moneyCon;
    }

    public int getCooldown() {
        return cooldown;
    }

    public Map<MonsterStatus, Integer> getMonsterStati() {
        return monsterStatus;
    }

    public boolean isHide() {
        return skill && sourceid == 9101004;
    }

    public boolean isDragonBlood() {
        return skill && sourceid == 1311008;
    }

    public boolean isBerserk() {
        return skill && sourceid == 1320006;
    }

    private boolean isDs() {
        return skill && (sourceid == 4001003 || sourceid == 14001003);
    }

    private boolean isCombo() {
        return skill && (sourceid == 1111002 || sourceid == 11111001);
    }

    private boolean isEnrage() {
        return skill && sourceid == 1121010;
    }

    public boolean isBeholder() {
        return skill && sourceid == 1321007;
    }

    private boolean isShadowPartner() {
        return skill && (sourceid == 4111002 || sourceid == 14111000);
    }

    private boolean isChakra() {
        return skill && sourceid == 4211001;
    }

    private boolean isPirateMpRecovery() {
        return skill && sourceid == 5101005;
    }

    private boolean isMonsterRiding() {
        return skill && (sourceid == 1004 || sourceid == 10001004 || sourceid == 20001004);
    }

    private boolean isBattleShip() {
        return skill && sourceid == 5221006;
    }

    public boolean isMagicDoor() {
        return skill && sourceid == 2311002;
    }

    public boolean isMesoGuard() {
        return skill && sourceid == 4211005;
    }

    public boolean isCharge() {
        return skill && sourceid >= 1211003 && sourceid <= 1211008;
    }

    public boolean isPoison() {
        return skill && (sourceid == 2111003 || sourceid == 2101005 || sourceid == 2111006 || sourceid == 12111005 || sourceid == 14111006);
    }

    private boolean isMist() {
        return skill && (sourceid == 2111003 || sourceid == 4221006 || sourceid == 12111005 || sourceid == 14111006); // poison mist and smokescreen
    }

    private boolean isSoulArrow() {
        return skill && (sourceid == 3101004 || sourceid == 3201004 || sourceid == 13101003); // bow and crossbow
    }

    private boolean isShadowClaw() {
        return skill && sourceid == 4121006;
    }

    private boolean isDispel() {
        return skill && (sourceid == 2311001 || sourceid == 9101000);
    }

    private boolean isHeroWill() {
        return skill && (sourceid == 1121011 || sourceid == 1221012 || sourceid == 1321010 || sourceid == 2121008 || sourceid == 2221008 || sourceid == 2321009 || sourceid == 3121009 || sourceid == 3221008 || sourceid == 4121009 || sourceid == 4221008 || sourceid == 5121008 || sourceid == 5221010);
    }

    private boolean isDash() {
        return skill && (sourceid == 5001005 || sourceid == 15001003);
    }

    private boolean isPowerCrash() {
        return skill && sourceid == 1311007;
    }

    public final boolean isAranCombo() {
        return sourceid == 21000000;
    }

    public boolean isPirateMorph() {
        return skill && (sourceid == 5111005 || sourceid == 5121003 || sourceid == 13111005 || sourceid == 15111002);
    }

    public boolean isMorph() {
        return morphId > 0;
    }

    public int getMorphId() {
        return morphId;
    }

    public int getItemConsume() {
        return itemConsume;
    }

    public boolean isElementalReset() {
        return skill && sourceid == 12101005;
    }

    private boolean isComboDrain() {
        return sourceid == 21100005;
    }

    public SummonMovementType getSummonMovementType() {
        if (!skill) {
            return null;
        }
        switch (sourceid) {
            case 3211002: // puppet sniper
            case 3111002: // puppet ranger
            case 5211001: // octopus - pirate
            case 5220002: // advanced octopus - pirate
            case 13111004:
                return SummonMovementType.STATIONARY;
            case 3211005: // golden eagle
            case 3111005: // golden hawk
            case 2311006: // summon dragon
            case 3221005: // frostprey
            case 3121006: // phoenix
            case 5211002: // bird - pirate
                return SummonMovementType.CIRCLE_FOLLOW;
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
                return SummonMovementType.FOLLOW;
        }
        return null;
    }

    public boolean isSkill() {
        return skill;
    }

    public int getSourceId() {
        return sourceid;
    }

    public double getIProp() {
        return prop * 100;
    }

    public int getMastery() {
        return mastery;
    }

    public int getRange() {
        return range;
    }

    public int getFixedDamage() {
        return fixDamage;
    }

    public short getThaw() {
        return thaw;
    }

    public String getBuffString() {

        return "WATK: " + watk + ", " + "WDEF: " + wdef + ", " + "MATK: " + matk + ", " + "MDEF: " + mdef + ", " + "ACC: " + acc + ", " + "AVOID: " + avoid + ", " + "SPEED: " + speed + ", " + "JUMP: " + jump + ".";
    }

    /**
     * @return true if the effect should happen based on it's probablity, false otherwise
     */
    public boolean makeChanceResult() {
        return prop == 1.0 || Math.random() < prop;
    }

    public boolean isHomingBeacon() {
        return skill && (sourceid == 5211006 || sourceid == 5220011);
    }

    public static class CancelEffectAction implements Runnable {

        private final MapleStatEffect effect;
        private final WeakReference<MapleCharacter> target;
        private final long startTime;

        public CancelEffectAction(MapleCharacter target, MapleStatEffect effect, long startTime) {
            this.effect = effect;
            this.target = new WeakReference<>(target);
            this.startTime = startTime;
        }

        @Override
        public void run() {
            MapleCharacter realTarget = target.get();
            if (realTarget != null) {
                if (realTarget.inCS() || realTarget.inMTS()) {
                    realTarget.addToCancelBuffPackets(effect, startTime);
                    return;
                }
                realTarget.cancelEffect(effect, false, startTime);
            }
        }
    }
}