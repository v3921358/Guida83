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

import guida.client.ISkill;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.SkillFactory;
import guida.client.status.MonsterStatus;
import guida.client.status.MonsterStatusEffect;
import guida.net.AbstractMaplePacketHandler;
import guida.server.life.MapleMonster;
import guida.server.life.MobAttackInfo;
import guida.server.life.MobAttackInfoFactory;
import guida.server.life.MobSkill;
import guida.server.life.MobSkillFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.Collections;

public class TakeDamageHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TakeDamageHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // damage from map object
        // 26 00 EB F2 2B 01 FE 25 00 00 00 00 00
        // damage from monster
        // 26 00 0F 60 4C 00 FF 48 01 00 00 B5 89 5D 00 CC CC CC CC 00 00 00 00

        /* Damagefrom:  -2 = map attack
         *		-1 = walk over monster
         *		0 = spell
         *		1 = seems to be a spell too...
         *
         * Damage: -1 = none taken?
         *	   > 0 = normal damage
         */

        final MapleCharacter player = c.getPlayer();

        slea.readInt();
        final int damagefrom = slea.readByte();
        slea.readByte();
        int damage = slea.readInt();
        int oid = 0;
        int monsteridfrom = 0;
        final int pgmr = 0;
        int direction = 0;
        final int pos_x = 0;
        final int pos_y = 0;
        int fake = 0;
        final boolean is_pgmr = false;
        final boolean is_pg = true;
        int mpattack = 0;
        MapleMonster attacker = null;

        if (damagefrom != -3 && damagefrom != -4) {
            monsteridfrom = slea.readInt();
            oid = slea.readInt();
            if (c.getPlayer().getMap().getMapObject(oid) == null || !(c.getPlayer().getMap().getMapObject(oid) instanceof MapleMonster)) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            attacker = (MapleMonster) player.getMap().getMapObject(oid);
            if (monsteridfrom != attacker.getId()) {
                monsteridfrom = attacker.getId();
            }
            direction = slea.readByte();
        }
        try {
            if (damagefrom != -1 && damagefrom != -3 && damagefrom != -4 && attacker != null) {
                final MobAttackInfo attackInfo = MobAttackInfoFactory.getMobAttackInfo(attacker, damagefrom);
                if (damage != -1 && damage != 0) {
                    if (attackInfo.isDeadlyAttack()) {
                        mpattack = player.getMp() - 1;
                    } else {
                        mpattack += attackInfo.getMpBurn();
                    }
                }

                final MobSkill skill = MobSkillFactory.getMobSkill(attackInfo.getDiseaseSkill(), attackInfo.getDiseaseLevel());
                if (skill != null && damage > 0) {
                    skill.applyEffect(player, attacker, false);
                }
                attacker.setMp(attacker.getMp() - attackInfo.getMpCon());
            }
        } catch (NullPointerException npe) {
            // Something is null here for sure, but don't know what it is...
        }

        if (damage == -1) {
            final int job = player.getJob().getId() / 10 - 40;
            fake = 4020002 + job * 100000;
            if (damagefrom == -1 && player.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -10) != null) {
                final int[] guardianSkillId = {1120005, 1220006};
                for (int guardian : guardianSkillId) {
                    final ISkill guardianSkill = SkillFactory.getSkill(guardian);
                    if (player.getSkillLevel(guardianSkill) > 0 && attacker != null) {
                        final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.STUN, 1), guardianSkill, false);
                        attacker.applyStatus(player, monsterStatusEffect, false, 2 * 1000);
                    }
                }
            }
        }

        if ((damage < -1 || damage > 60000) && !player.isGM()) {
            log.warn(player.getName() + " taking abnormal amounts of damage from " + monsteridfrom + ": " + damage);
            c.disconnect();
            return;
        }

        player.getCheatTracker().checkTakeDamage();

        if (damage > 0) {
            player.getCheatTracker().setAttacksWithoutHit(0);
            player.getCheatTracker().resetHPRegen();
            player.getCheatTracker().resetMPRegen();
            player.resetAfkTimer();
        }

        if (!player.isHidden() && player.isAlive() && !player.hasGodmode()) {
            if (player.getBuffedValue(MapleBuffStat.MORPH) != null && damage > 0) {
                player.cancelMorphs();
            }
            if (player.hasBattleShip()) {
                player.handleBattleShipHpLoss(damage);
                player.getMap().broadcastMessage(player, MaplePacketCreator.damagePlayer(damagefrom, monsteridfrom, player.getId(), damage, fake, direction, is_pgmr, pgmr, is_pg, oid, pos_x, pos_y), false);
                player.checkBerserk();
            }
            if (damagefrom == -1) {
                final Integer pguard = player.getBuffedValue(MapleBuffStat.POWERGUARD);
                if (pguard != null) {
                    attacker = (MapleMonster) player.getMap().getMapObject(oid);
                    if (attacker != null) {
                        int bouncedamage = (int) (damage * pguard.doubleValue() / 100);
                        bouncedamage = Math.min(bouncedamage, attacker.getMaxHp() / 10);
                        player.getMap().damageMonster(player, attacker, bouncedamage);
                        damage -= bouncedamage;
                        player.getMap().broadcastMessage(player, MaplePacketCreator.damageMonster(oid, bouncedamage), false, true);
                        player.checkMonsterAggro(attacker);
                    }
                }
            }
            if (damagefrom == 0 && attacker != null) {
                final Integer manaReflection = player.getBuffedValue(MapleBuffStat.MANA_REFLECTION);
                if (manaReflection != null) {
                    final int skillId = player.getBuffSource(MapleBuffStat.MANA_REFLECTION);
                    final ISkill manaReflectSkill = SkillFactory.getSkill(skillId);
                    if (manaReflectSkill.getEffect(player.getSkillLevel(manaReflectSkill)).makeChanceResult()) {
                        int bouncedamage = (int) (damage * manaReflection.doubleValue() / 100.0);
                        if (bouncedamage > attacker.getMaxHp() * .2) {
                            bouncedamage = (int) (attacker.getMaxHp() * .2);
                        }
                        player.getMap().damageMonster(player, attacker, bouncedamage);
                        player.getMap().broadcastMessage(player, MaplePacketCreator.damageMonster(oid, bouncedamage), false, true);
                        player.getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(skillId, 5, (byte) player.getLevel()));
                        player.getMap().broadcastMessage(player, MaplePacketCreator.showBuffeffect(player.getId(), skillId, 5, (byte) player.getLevel(), (byte) 3), false);
                    }
                }
            }
            if (damagefrom == -1) {
                try {
                    final int[] achillesSkillId = {1120004, 1220005, 1320005};
                    for (int achilles : achillesSkillId) {
                        final ISkill achillesSkill = SkillFactory.getSkill(achilles);
                        if (player.getSkillLevel(achillesSkill) > 0) {
                            final double multiplier = achillesSkill.getEffect(player.getSkillLevel(achillesSkill)).getX() / 1000.0;
                            damage = (int) (multiplier * damage);
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to handle achilles..", e);
                }
            }
            if (player.getBuffedValue(MapleBuffStat.MAGIC_GUARD) != null && mpattack == 0) {
                int mploss = (int) (damage * player.getBuffedValue(MapleBuffStat.MAGIC_GUARD).doubleValue() / 100.0);
                int hploss = damage - mploss;
                if (mploss > player.getMp()) {
                    hploss += mploss - player.getMp();
                    mploss = player.getMp();
                }
                player.addMPHP(-hploss, -mploss, attacker);
            } else if (player.getBuffedValue(MapleBuffStat.MESOGUARD) != null) {
                damage = damage % 2 == 0 ? damage / 2 : damage / 2 + 1;
                final int mesoloss = (int) (damage * player.getBuffedValue(MapleBuffStat.MESOGUARD).doubleValue() / 100.0);
                if (player.getMeso() < mesoloss) {
                    player.gainMeso(-player.getMeso(), false);
                    player.cancelBuffStats(MapleBuffStat.MESOGUARD);
                } else {
                    player.gainMeso(-mesoloss, false);
                }
                player.addMPHP(-damage, -mpattack, attacker);
            } else {
                player.addMPHP(-damage, -mpattack, attacker);
            }
            if (damagefrom == -3) {
                player.getMap().broadcastMessage(player, MaplePacketCreator.damagePlayer(-1, 9400711, player.getId(), damage, 0, 0, false, 0, false, 0, 0, 0), false);
            } else {
                player.getMap().broadcastMessage(player, MaplePacketCreator.damagePlayer(damagefrom, monsteridfrom, player.getId(), damage, fake, direction, is_pgmr, pgmr, is_pg, oid, pos_x, pos_y), false);
            }
            player.checkBerserk();
        }
    }
}