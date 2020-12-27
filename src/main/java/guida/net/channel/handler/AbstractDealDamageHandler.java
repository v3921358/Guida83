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

import guida.client.Equip;
import guida.client.IItem;
import guida.client.ISkill;
import guida.client.Item;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.client.status.MonsterStatus;
import guida.client.status.MonsterStatusEffect;
import guida.net.AbstractMaplePacketHandler;
import guida.server.AutobanManager;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.Element;
import guida.server.life.ElementalEffectiveness;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapItem;
import guida.server.maps.MapleMapObject;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.LittleEndianAccessor;

import java.awt.Point;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractDealDamageHandler extends AbstractMaplePacketHandler {

    protected synchronized void applyAttack(AttackInfo attack, MapleCharacter player, int maxDamagePerMonster, int attackCount) {
        player.getCheatTracker().resetHPRegen();
        player.resetAfkTimer();
        player.getCheatTracker().checkAttack(attack.skill);
        ISkill theSkill = SkillFactory.getSkill(attack.skill);
        if (!player.isGM() && theSkill != null && !theSkill.isKeyDownAttack()) {
            player.addAttackCount();
            if (player.getAttackCount() >= 170) {
                AutobanManager.getInstance().broadcastMessage(player.getClient(), player.getName() + " was auto banned for unlimited attack");
                player.ban(player.getName() + " was auto banned for unlimited attack (IP: " + player.getClient().getIP() + ")");
                return;
            } else if (player.getAttackCount() >= 140) {
                try {
                    player.getClient().getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, player.getName() + " is suspected of unlimited attack: " + player.getAttackCount() + " attacks without moving").getBytes());
                } catch (RemoteException ex) {
                    player.getClient().getChannelServer().reconnectWorld();
                }
            } else if (player.getAttackCount() > 100) {
                player.getCheatTracker().registerOffense(CheatingOffense.UNLIMITED_ATTACK);
            }
        }
        MapleStatEffect attackEffect = null;
        if (attack.skill != 0 && attack.skill != 1009 && attack.skill != 10001009) {
            theSkill = SkillFactory.getSkill(attack.skill);
            attackEffect = attack.getAttackEffect(player, theSkill);
            if (attackEffect == null) {
                player.getClient().sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (attack.skill != 2301002 && attack.skill != 5211006 && attack.skill != 5220011) {
                // heal is both an attack and a special move (healing)
                // so we'll let the whole applying magic live in the special move part
                if (player.isAlive()) {
                    attackEffect.applyTo(player);
                } else {
                    player.getClient().sendPacket(MaplePacketCreator.enableActions());
                }
            }
        }
        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }
        // meso explosion has a variable bullet count
        /*if (attackCount != attack.numDamage && attack.skill != 4211006) {
			player.getCheatTracker().registerOffense(CheatingOffense.MISMATCHING_BULLETCOUNT, attack.numDamage + "/" + attackCount);
			return;
		}*/
        int totDamage = 0;
        final MapleMap map = player.getMap();

        if (attack.skill == 4211006) { // meso explosion
            int delay = 0;
            for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> oned : attack.allDamage) {
                MapleMapObject mapobject = map.getMapObject(oned.getLeft().getLeft());
                if (mapobject instanceof MapleMapItem) {
                    final MapleMapItem mapitem = (MapleMapItem) mapobject;
                    if (mapitem.getMeso() > 0) {
                        synchronized (mapitem) {
                            if (mapitem.isPickedUp()) {
                                return;
                            }
                            TimerManager.getInstance().schedule(() -> {
                                map.removeMapObject(mapitem);
                                map.broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 4, 0), mapitem.getPosition());
                                mapitem.setPickedUp(true);
                            }, delay);
                            delay += 100;
                        }
                    } else if (mapitem.getMeso() == 0) {
                        player.getCheatTracker().registerOffense(CheatingOffense.ETC_EXPLOSION);
                        return;
                    }
                } else if (mapobject != null && !(mapobject instanceof MapleMonster)) {
                    player.getCheatTracker().registerOffense(CheatingOffense.EXPLODING_NONEXISTANT);
                    return; // etc explosion, exploding nonexistant things, etc.
                }
            }
        }

        for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> oned : attack.allDamage) {
            MapleMonster monster = map.getMonsterByOid(oned.getLeft().getLeft());

            if (monster != null) {
                int totDamageToOneMonster = 0;
                for (Pair<Integer, Boolean> eachd : oned.getRight()) {
                    totDamageToOneMonster += eachd.getLeft();
                }
                totDamage += totDamageToOneMonster;

                player.checkMonsterAggro(monster);
                if (totDamageToOneMonster > attack.numDamage + 1) {
                    int dmgCheck = player.getCheatTracker().checkDamage(totDamageToOneMonster);
                    if (dmgCheck > 5 && totDamageToOneMonster < 199999 && monster.getId() < 9500317 && monster.getId() > 9500319) {
                        player.getCheatTracker().registerOffense(CheatingOffense.SAME_DAMAGE, dmgCheck + " times: " + totDamageToOneMonster);
                    }
                }
                checkHighDamage(player, monster, attack, theSkill, attackEffect, totDamageToOneMonster, maxDamagePerMonster);
                double distance = player.getPosition().distanceSq(monster.getPosition());
                if (distance > 400000.0) { // 600^2, 550 is approximatly the range of ultis
                    player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_FARAWAY_MONSTER, Double.toString(Math.sqrt(distance)));
                }

                if (attack.skill == 2301002 && !monster.getUndead()) {
                    player.getCheatTracker().registerOffense(CheatingOffense.HEAL_ATTACKING_UNDEAD);
                    return;
                }
                if (player.getBuffedValue(MapleBuffStat.PICKPOCKET) != null) {
                    switch (attack.skill) {
                        case 0:
                        case 4001334:
                        case 4201005:
                        case 4211001:
                        case 4211002:
                        case 4211004:
                        case 4221003:
                        case 4221007:
                            handlePickPocket(player, monster, oned);
                            break;
                    }
                }
                switch (attack.skill) {
                    case 1221011: // Sanctuary
                        if (attack.isHH) {
                            // TODO min damage still needs calculated.. using -20% as mindamage in the meantime.. seems to work
                            int HHDmg = player.calculateMaxBaseDamage(player.getTotalWatk()) * (theSkill.getEffect(player.getSkillLevel(theSkill)).getDamage() / 100);
                            HHDmg = (int) Math.floor(Math.random() * (HHDmg - HHDmg * .80) + HHDmg * .80);
                            map.damageMonster(player, monster, HHDmg);
                        }
                        break;
                    case 2221003:
                        monster.setTempEffectiveness(Element.FIRE, ElementalEffectiveness.WEAK, theSkill.getEffect(player.getSkillLevel(theSkill)).getDuration());
                        break;
                    case 2121003:
                        monster.setTempEffectiveness(Element.ICE, ElementalEffectiveness.WEAK, theSkill.getEffect(player.getSkillLevel(theSkill)).getDuration());
                        break;
                    case 4201004: // Steal
                        handleSteal(player, monster);
                        break;
                    case 4101005: // Drain
                    case 5111004: // Energy Drain
                    case 15111001: // Energy Drain
                        int gainhp = (int) ((double) totDamageToOneMonster * (double) theSkill.getEffect(player.getSkillLevel(theSkill)).getX() / 100.0);
                        gainhp = Math.min(monster.getMaxHp(), Math.min(gainhp, player.getMaxHp() / 2));
                        player.addHP(gainhp);
                        break;
                    default:
                        // passives attack bonuses
                        if (totDamageToOneMonster > 0 && monster.isAlive()) {
                            if (player.getBuffedValue(MapleBuffStat.BLIND) != null) {
                                if (SkillFactory.getSkill(3221006).getEffect(player.getSkillLevel(SkillFactory.getSkill(3221006))).makeChanceResult()) {
                                    MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.ACC, SkillFactory.getSkill(3221006).getEffect(player.getSkillLevel(SkillFactory.getSkill(3221006))).getX()), SkillFactory.getSkill(3221006), false);
                                    monster.applyStatus(player, monsterStatusEffect, false, SkillFactory.getSkill(3221006).getEffect(player.getSkillLevel(SkillFactory.getSkill(3221006))).getY() * 1000);
                                }
                            }
                            if (player.getBuffedValue(MapleBuffStat.HAMSTRING) != null) {
                                if (SkillFactory.getSkill(3121007).getEffect(player.getSkillLevel(SkillFactory.getSkill(3121007))).makeChanceResult()) {
                                    MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.SPEED, SkillFactory.getSkill(3121007).getEffect(player.getSkillLevel(SkillFactory.getSkill(3121007))).getX()), SkillFactory.getSkill(3121007), false);
                                    monster.applyStatus(player, monsterStatusEffect, false, SkillFactory.getSkill(3121007).getEffect(player.getSkillLevel(SkillFactory.getSkill(3121007))).getY() * 1000);
                                }
                            }
                            if (player.getJob().isA(MapleJob.WHITEKNIGHT)) {
                                int[] charges = {1211005, 1211006};
                                for (int charge : charges) {
                                    if (player.isBuffFrom(MapleBuffStat.WK_CHARGE, SkillFactory.getSkill(charge))) {
                                        final ElementalEffectiveness iceEffectiveness = monster.getEffectiveness(Element.ICE);
                                        if (iceEffectiveness == ElementalEffectiveness.NORMAL || iceEffectiveness == ElementalEffectiveness.WEAK) {
                                            MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.FREEZE, 1), SkillFactory.getSkill(charge), false);
                                            monster.applyStatus(player, monsterStatusEffect, false, SkillFactory.getSkill(charge).getEffect(player.getSkillLevel(SkillFactory.getSkill(charge))).getY() * 2000);
                                        }
                                        break;
                                    }
                                }
                            }
                            if (player.getBuffedValue(MapleBuffStat.COMBO_DRAIN) != null) {
                                final ISkill skill = SkillFactory.getSkill(21100005);
                                player.addHP(totDamage * skill.getEffect(player.getSkillLevel(skill)).getX() / 100);
                            }
                        }
                        break;
                }
                if (player.getSkillLevel(SkillFactory.getSkill(4120005)) > 0) {
                    MapleStatEffect venomEffect = SkillFactory.getSkill(4120005).getEffect(player.getSkillLevel(SkillFactory.getSkill(4120005)));
                    for (int i = 0; i < attackCount; i++) {
                        if (venomEffect.makeChanceResult()) {
                            if (monster.getVenomMulti() < 3) {
                                monster.setVenomMulti((monster.getVenomMulti() + 1));
                                MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), SkillFactory.getSkill(4120005), false);
                                monster.applyStatus(player, monsterStatusEffect, false, venomEffect.getDuration(), true);
                            }
                        }
                    }
                } else if (player.getSkillLevel(SkillFactory.getSkill(4220005)) > 0) {
                    MapleStatEffect venomEffect = SkillFactory.getSkill(4220005).getEffect(player.getSkillLevel(SkillFactory.getSkill(4220005)));
                    for (int i = 0; i < attackCount; i++) {
                        if (venomEffect.makeChanceResult()) {
                            if (monster.getVenomMulti() < 3) {
                                monster.setVenomMulti((monster.getVenomMulti() + 1));
                                MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), SkillFactory.getSkill(4220005), false);
                                monster.applyStatus(player, monsterStatusEffect, false, venomEffect.getDuration(), true);
                            }
                        }
                    }
                } else if (player.getSkillLevel(SkillFactory.getSkill(14110004)) > 0) {
                    MapleStatEffect venomEffect = SkillFactory.getSkill(14110004).getEffect(player.getSkillLevel(SkillFactory.getSkill(14110004)));
                    for (int i = 0; i < attackCount; i++) {
                        if (venomEffect.makeChanceResult()) {
                            if (monster.getVenomMulti() < 3) {
                                monster.setVenomMulti((monster.getVenomMulti() + 1));
                                MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), SkillFactory.getSkill(14110004), false);
                                monster.applyStatus(player, monsterStatusEffect, false, venomEffect.getDuration(), true);
                            }
                        }
                    }
                }
                if (totDamageToOneMonster > 0 && attackEffect != null && !attackEffect.getMonsterStati().isEmpty()) {
                    if (attackEffect.makeChanceResult()) {
                        MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(attackEffect.getMonsterStati(), theSkill, false);
                        monster.applyStatus(player, monsterStatusEffect, attackEffect.isPoison(), attackEffect.getDuration());
                    }
                }
                if (monster.getId() != 9300013 && monster.getId() != 9300091 && monster.getId() != 9300021 || player.isGM()) {
                    if (!attack.isHH) {
                        map.damageMonster(player, monster, totDamageToOneMonster);
                    } else {
                        // attack is heavens hammar!
                        if (monster.isBoss()) {
                            map.damageMonster(player, monster, 199999);
                        } else {
                            map.damageMonster(player, monster, monster.getHp() - 1);
                        }
                    }
                }
                if (player.getSkillLevel(SkillFactory.getSkill(14100005)) > 0) {
                    if (player.getBuffSource(MapleBuffStat.DARKSIGHT) == 14001003) {
                        player.cancelBuffStats(MapleBuffStat.DARKSIGHT);
                    }
                }
            }
        }
        if (totDamage > 1) {
            player.getCheatTracker().setAttacksWithoutHit(player.getCheatTracker().getAttacksWithoutHit() + 1);
            final int offenseLimit = switch (attack.skill) {
                case 3121004, 5221004 -> 100;
                default -> 500;
            };
            if (player.getCheatTracker().getAttacksWithoutHit() > offenseLimit) {
                player.getCheatTracker().registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT, Integer.toString(player.getCheatTracker().getAttacksWithoutHit()));
            }
        }
    }

    private void handlePickPocket(MapleCharacter player, MapleMonster monster, Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> oned) {
        int delay = 0;
        int maxmeso = player.getBuffedValue(MapleBuffStat.PICKPOCKET);
        int reqdamage = 20000;
        Point monsterPosition = monster.getPosition();

        for (Pair<Integer, Boolean> eachd : oned.getRight()) {
            if (SkillFactory.getSkill(4211003).getEffect(player.getSkillLevel(SkillFactory.getSkill(4211003))).makeChanceResult()) {
                double perc = (double) eachd.getLeft() / (double) reqdamage;

                final int todrop = Math.min((int) Math.max(perc * maxmeso, 1), maxmeso);
                final MapleMap tdmap = player.getMap();
                final Point tdpos = new Point((int) (monsterPosition.getX() + Math.random() * 100 - 50), (int) monsterPosition.getY());
                final MapleMonster tdmob = monster;
                final MapleCharacter tdchar = player;

                TimerManager.getInstance().schedule(() -> tdmap.spawnMesoDrop(todrop, tdpos, tdmob, tdchar, false, (byte) 0, true), delay);

                delay += 100;
            }
        }
    }

    private void checkHighDamage(MapleCharacter player, MapleMonster monster, AttackInfo attack, ISkill theSkill, MapleStatEffect attackEffect, int damageToMonster, int maximumDamageToMonster) {
        if (player.isGM() || player.getMapId() >= 914000200 && player.getMapId() <= 914000220 || attack == null || attack.skill == 1009 || attack.skill == 10001009) {
            return;
        }
        if (attack.skill != 0 && attack.getAttackEffect(player) != null) {
            MapleStatEffect effect = attack.getAttackEffect(player);
            if (effect.getFixedDamage() > 0) {
                maximumDamageToMonster = effect.getFixedDamage() * effect.getAttackCount() * (player.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null ? 2 : 1) + 10;
                if (damageToMonster > maximumDamageToMonster) {
                    String bMessage = player.getName() + " was auto banned for fixed damage hacking: " + damageToMonster + " damage with " + (attack.skill != 0 ? SkillFactory.getSkillName(attack.skill) : "regular attacking") + " at level " + player.getLevel();
                    AutobanManager.getInstance().broadcastMessage(player.getClient(), bMessage);
                    player.ban(bMessage + " - Max Calculated Damage: " + maximumDamageToMonster + " (IP: " + player.getClient().getIP() + ")");

                }
                return;
            }
        }
        if (damageToMonster > 59999 && player.getLevel() < 70) {
            String bMessage = player.getName() + " was auto banned for damage hacking: " + damageToMonster + " damage with " + (attack.skill != 0 ? SkillFactory.getSkillName(attack.skill) : "regular attacking") + " at level " + player.getLevel();
            AutobanManager.getInstance().broadcastMessage(player.getClient(), bMessage);
            player.ban(bMessage + " - Max Calculated Damage: 59999 (IP: " + player.getClient().getIP() + ")");
            return;
        }
        if (damageToMonster > 999 && player.getLevel() <= 10) {
            // Hyper WTF
            // Unless they have some super WA buff, not possible
            String bMessage = player.getName() + " was auto banned for damage hacking: " + damageToMonster + " damage with " + (attack.skill != 0 ? SkillFactory.getSkillName(attack.skill) : "regular attacking") + " at level " + player.getLevel();
            AutobanManager.getInstance().broadcastMessage(player.getClient(), bMessage);
            player.ban(bMessage + " - Max Calculated Damage: 999 (IP: " + player.getClient().getIP() + ")");
            return;
        }
        int elementalMaxDamagePerMonster;
        Element element = Element.NEUTRAL;
        if (theSkill != null) {
            element = theSkill.getElement();
            int skillId = theSkill.getId();
            switch (skillId) {
                case 3221007 -> maximumDamageToMonster = 199999;
                case 4201004, 4211006 -> maximumDamageToMonster = 750000;
                case 4221001 -> maximumDamageToMonster = 400000;
            }
        }
        if (player.getBuffedValue(MapleBuffStat.WK_CHARGE) != null) {
            int chargeSkillId = player.getBuffSource(MapleBuffStat.WK_CHARGE);
            element = switch (chargeSkillId) {
                case 1211003, 1211004 -> Element.FIRE;
                case 1211005, 1211006 -> Element.ICE;
                case 1211007, 1211008, 15101006 -> Element.LIGHTING;
                case 1221003, 1221004, 11111007 -> Element.HOLY;
                default -> throw new RuntimeException("Unknown enum constant");
            };
            ISkill skill = SkillFactory.getSkill(chargeSkillId);
            if (player.getSkillLevel(skill) != 0) {
                maximumDamageToMonster *= skill.getEffect(player.getSkillLevel(skill)).getDamage() / 100.0;
            }
        }
        if (player.getBuffedValue(MapleBuffStat.ELEMENTAL_RESET) != null) {
            element = Element.NEUTRAL;
        }
        if (element != Element.NEUTRAL) {
            double elementalEffect;
            if (attack.skill == 3211003 || attack.skill == 3111003) { // inferno and blizzard
                elementalEffect = attackEffect.getX() / 200.0;
            } else {
                elementalEffect = 0.5;
            }
            elementalMaxDamagePerMonster = switch (monster.getEffectiveness(element)) {
                case IMMUNE -> 1;
                case NORMAL -> maximumDamageToMonster;
                case WEAK -> (int) (maximumDamageToMonster * (1.0 + elementalEffect));
                case STRONG -> (int) (maximumDamageToMonster * (1.0 - elementalEffect));
                default -> throw new RuntimeException("Unknown enum constant");
            };
        } else {
            elementalMaxDamagePerMonster = maximumDamageToMonster;
        }
        if (player.getSkillLevel(SkillFactory.getSkill(1320006)) > 0) {
            int beserkDamage = SkillFactory.getSkill(1320006).getEffect(player.getSkillLevel(SkillFactory.getSkill(1320006))).getDamage();
            elementalMaxDamagePerMonster += elementalMaxDamagePerMonster * (beserkDamage / 100);
        }
        if (player.getSkillLevel(SkillFactory.getSkill(5110000)) > 0) {
            int stunDamage = SkillFactory.getSkill(5110000).getEffect(player.getSkillLevel(SkillFactory.getSkill(5110000))).getDamage();
            elementalMaxDamagePerMonster *= stunDamage / 100;
        }
        if (attack.skill != 0 && attack.getAttackEffect(player) != null && attack.isMagic) {
            elementalMaxDamagePerMonster *= player.getEleAmp();
        }
        if (attack.skill == 1001005 || attack.skill == 11001003) {
            elementalMaxDamagePerMonster *= 3; // as according to slash blast final attack damage formula
        }
        elementalMaxDamagePerMonster *= 1.05;
        if (damageToMonster > elementalMaxDamagePerMonster) {
            player.getCheatTracker().registerOffense(attack.isMagic ? CheatingOffense.HIGH_MAGIC_DAMAGE : CheatingOffense.HIGH_MELEE_DAMAGE);
            if (player.getLevel() < 70 && !attack.forceABWarnOnly) {
                String bMessage = player.getName() + " was auto banned for damage hacking: " + damageToMonster + " damage with " + (attack.skill != 0 ? SkillFactory.getSkillName(attack.skill) : "regular attacking") + " at level " + player.getLevel();
                AutobanManager.getInstance().broadcastMessage(player.getClient(), bMessage);
                player.ban(bMessage + " - Max Calculated Damage: " + elementalMaxDamagePerMonster + " (IP: " + player.getClient().getIP() + ")");
            } else {
                try {
                    if (attack.skill != 1111008) {
                        player.getClient().getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.multiChat("[Server]", player.getName() + " is suspected of damage hacking: " + damageToMonster + " damage with " + (attack.skill != 0 ? SkillFactory.getSkillName(attack.skill) : "regular attacking") + " at level " + player.getLevel() + " (Calculated: " + elementalMaxDamagePerMonster + ")", 0).getBytes());
                    }
                } catch (RemoteException ex) {
                    player.getClient().getChannelServer().reconnectWorld();
                }
            }
        }
    }

    public final AttackInfo parseDamage(MapleCharacter c, LittleEndianAccessor lea, boolean ranged) {
        final AttackInfo ret = new AttackInfo();

        lea.skip(1);
        ret.numAttackedAndDamage = lea.readByte();
        ret.numAttacked = ret.numAttackedAndDamage >>> 4 & 0xF; // guess why there are no skills damaging more than 15 monsters...
        ret.numDamage = ret.numAttackedAndDamage & 0xF; // how often each single monster was attacked
        ret.allDamage = new ArrayList<>();
        ret.skill = lea.readInt();
        ISkill skill = SkillFactory.getSkill(ret.skill);
        boolean charge = skill != null && skill.hasCharge() && !skill.isKeyDownAttack();
        ret.charge = charge ? lea.readInt() : 0;
        lea.skip(9);
        ret.stance = lea.readShort();

        if (ret.skill == 1221011) {
            ret.isHH = true;
        }
        if (ret.skill == 4211006) {
            return parseMesoExplosion(lea, ret);
        }
        if (ranged) {
            lea.skip(1);
            ret.speed = lea.readByte();
            lea.skip(1);
            ret.direction = lea.readByte(); // contains direction on some 4th job skills
            lea.skip(2);
            if (skill != null && skill.isKeyDownAttack()) {
                lea.skip(4);
            }
            ret.useItemPos = lea.readShort();
            ret.cashStarPos = lea.readShort();
            lea.skip(1);
        } else {
            lea.readByte();
            ret.speed = lea.readByte();
            lea.skip(3);
            ret.unk = lea.readByte();
        }
        for (int i = 0; i < ret.numAttacked; i++) {
            int oid = lea.readInt();
            byte uByte = lea.readByte();
            lea.skip(13); // seems to contain some position info
            List<Pair<Integer, Boolean>> allDamageNumbers = new ArrayList<>();
            for (int j = 0; j < ret.numDamage; j++) {
                int damage = lea.readInt();
                MapleStatEffect effect = null;
                if (ret.skill != 0 && ret.skill != 1009 && ret.skill != 10001009) {
                    effect = SkillFactory.getSkill(ret.skill).getEffect(c.getSkillLevel(SkillFactory.getSkill(ret.skill)));
                }
                if (damage != 0 && effect != null && effect.getFixedDamage() != 0) {
                    damage = effect.getFixedDamage();
                }
                if (damage > 199999 && !c.isGM()) {
                    damage = 199999;
                }
                allDamageNumbers.add(new Pair<>(damage, false));
            }
            lea.skip(4);
            ret.allDamage.add(new Pair<>(new Pair<>(oid, uByte), allDamageNumbers));
        }
        if (ranged) {
            lea.skip(4);
        }
        ret.projectilePos = lea.readInt();

        return ret;
    }

    public final void handleCritical(AttackInfo ai, int base, int normalMult, int critMult, boolean maleFix) {
        if (normalMult == critMult) {
            return;
        }

        int maxNormal = (int) (base * (normalMult / 100.0) * 1.1);

        for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> p : ai.allDamage) {
            for (int i = 0; i < p.getRight().size(); ++i) {
                Pair<Integer, Boolean> dmgPair = p.getRight().get(i);
                int dmg = dmgPair.getLeft();
                if (dmg > maxNormal) {
                    if (maleFix) {
                        dmgPair.setLeft((int) (dmg * (critMult / (critMult * 1.0 - 100))));
                    }
                    dmgPair.setRight(true);
                }
            }
        }
    }

    public final AttackInfo parseMesoExplosion(LittleEndianAccessor lea, AttackInfo ret) {
        if (ret.numAttackedAndDamage == 0) {
            lea.skip(10);
            int bullets = lea.readByte();
            for (int j = 0; j < bullets; j++) {
                int mesoid = lea.readInt();
                lea.skip(1);
                ret.allDamage.add(new Pair<>(new Pair<>(mesoid, (byte) 0), null));
            }

            return ret;
        } else {
            lea.skip(6);
        }
        for (int i = 0; i < ret.numAttacked + 1; i++) {
            int oid = lea.readInt();
            if (i < ret.numAttacked) {
                lea.skip(12);
                int bullets = lea.readByte();

                List<Pair<Integer, Boolean>> allDamageNumbers = new ArrayList<>();
                for (int j = 0; j < bullets; j++) {
                    int damage = lea.readInt();
                    allDamageNumbers.add(new Pair<>(damage, false));
                }
                ret.allDamage.add(new Pair<>(new Pair<>(oid, (byte) 0), allDamageNumbers));
                lea.skip(4);
            } else {
                int bullets = lea.readByte();
                for (int j = 0; j < bullets; j++) {
                    int mesoid = lea.readInt();
                    lea.skip(1);
                    ret.allDamage.add(new Pair<>(new Pair<>(mesoid, (byte) 0), null));
                }
            }
        }

        return ret;
    }

    private void handleSteal(MapleCharacter chr, MapleMonster mob) {
        ISkill steal = SkillFactory.getSkill(4201004);
        int level = chr.getSkillLevel(steal);
        if (steal.getEffect(level).makeChanceResult() && !mob.isStolen()) {
            int toSteal = mob.getDrop(chr);
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            IItem stolen;
            MapleInventoryType type = ii.getInventoryType(toSteal);
            if (type.equals(MapleInventoryType.EQUIP)) {
                stolen = ii.randomizeStats((Equip) ii.getEquipById(toSteal));
            } else {
                stolen = new Item(toSteal, (byte) 0, (short) 1);
                if (ii.isArrowForBow(toSteal) || ii.isArrowForCrossBow(toSteal)) { // Randomize quantity for certain items
                    stolen.setQuantity((short) (1 + 100 * Math.random()));
                } else if (ii.isThrowingStar(toSteal) || ii.isShootingBullet(toSteal)) {
                    stolen.setQuantity((short) 1);
                }
            }
            if (toSteal == 4001023 || toSteal == 4001022 || toSteal == 4001007 || toSteal == 4001008 || mob.isBoss()) {
                mob.getMap().disappearingItemDrop(mob, chr, stolen, mob.getPosition());
                chr.dropMessage(5, "The monster took the item back.");

            } else {
                mob.getMap().spawnItemDrop(mob, chr, stolen, mob.getPosition(), false, true);
            }
            mob.stolen();
        }
    }

    protected static class AttackInfo {

        public int numAttacked, numDamage, numAttackedAndDamage;
        public int skill, stance, direction, charge, unk;
        public short useItemPos, cashStarPos;
        public int projectilePos = -1;
        public List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> allDamage;
        public boolean isHH, forceABWarnOnly = true, isMagic = false;
        public int speed = 4;

        private MapleStatEffect getAttackEffect(MapleCharacter chr, ISkill theSkill) {
            ISkill mySkill = theSkill;
            if (mySkill == null) {
                mySkill = SkillFactory.getSkill(skill);
            }
            int skillLevel = chr.getSkillLevel(mySkill);
            if (skillLevel == 0) {
                return null;
            }
            return mySkill.getEffect(skillLevel);
        }

        public MapleStatEffect getAttackEffect(MapleCharacter chr) {
            return getAttackEffect(chr, null);
        }
    }
}