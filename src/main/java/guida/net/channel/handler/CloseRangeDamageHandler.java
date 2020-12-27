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
import guida.client.MapleDisease;
import guida.client.MapleStat;
import guida.client.SkillCooldown.CancelCooldownAction;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.net.MaplePacket;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MobSkillFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.concurrent.ScheduledFuture;

public class CloseRangeDamageHandler extends AbstractDealDamageHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (!c.getPlayer().getMap().canUseSkills() && !c.getPlayer().isGM()) {
            if (!c.getPlayer().getDiseases().contains(MapleDisease.GM_DISABLE_SKILL)) {
                c.getPlayer().giveDebuff(MapleDisease.GM_DISABLE_SKILL, MobSkillFactory.getMobSkill(120, 1), true);
            }
            return;
        }
        final AttackInfo attack = parseDamage(c.getPlayer(), slea, false);
        if (c.getPlayer().getDojo().getEnergy() < 10000 && (attack.skill == 1009 || attack.skill == 10001009 || attack.skill == 20001009)) // PE hacking or maybe just lagging
        {
            return;
        }
        MapleCharacter player = c.getPlayer();
        if (player.getMap().isDojoMap() && attack.numAttacked > 0) {
            player.getDojo().setEnergy(player.getDojo().getEnergy() + 100);
            c.sendPacket(MaplePacketCreator.setDojoEnergy(player.getDojo().getEnergy()));
        }
        ISkill skill = null;
        int skillLevel = 0;
        if (attack.skill == 1009 || attack.skill == 10001009 || attack.skill == 20001009) {
            skill = SkillFactory.getSkill(attack.skill);
            skillLevel = 1;
        } else if (attack.skill != 0) {
            skill = SkillFactory.getSkill(attack.skill);
            skillLevel = player.getSkillLevel(skill);
        }
        MapleStatEffect skillEffect = null;
        if (attack.skill != 0 && attack.skill != 1009 && attack.skill != 10001009) {
            skillEffect = attack.getAttackEffect(c.getPlayer());
            if (player.getMp() < skillEffect.getMpCon() || attack.numAttacked > skillEffect.getMobCount() || attack.numDamage > skillEffect.getAttackCount()) {
                return;
            }
        }
        int maxdamage = c.getPlayer().getCurrentMaxBaseDamage();
        Integer sharpEyes = player.getBuffedValue(MapleBuffStat.SHARP_EYES);
        int mult = skillEffect != null ? skillEffect.getDamage() : 100;
        int crit = sharpEyes == null ? 0 : sharpEyes;
        if (attack.skill != 4211006 && crit > 0) {
            handleCritical(attack, maxdamage, mult, mult + crit, player.getGender() == 0);
        }
        MaplePacket packet = MaplePacketCreator.closeRangeAttack(player.getId(), (byte) player.getLevel(), attack.skill, skillLevel, attack.stance, attack.numAttackedAndDamage, attack.allDamage, attack.speed, attack.unk);
        player.getMap().broadcastMessage(player, packet, false, true);
        // handle combo orb consume
        int numFinisherOrbs = 0;
        Integer comboBuff = player.getBuffedValue(MapleBuffStat.COMBO);
        ISkill energycharge = SkillFactory.getSkill(5110001);
        int energyChargeSkillLevel = player.getSkillLevel(energycharge);

        if (skill != null && skill.isFinisher()) {
            if (comboBuff != null) {
                numFinisherOrbs = comboBuff - 1;
            }
            player.handleOrbconsume();
        } else if (attack.numAttacked > 0) {
            // handle combo orbgain
            if (attack.skill != 1111008 && comboBuff != null) { // shout should not give orbs
                player.handleOrbgain();
            }
            if (energyChargeSkillLevel > 0) {
                int ecEffect = energycharge.getEffect(energyChargeSkillLevel).getX();
                for (int x = 0; x < attack.numAttacked; x++) {
                    player.handleEnergyChargeGain(ecEffect, false);
                }
            }
        }
        // handle sacrifice hp loss
        if (attack.numAttacked > 0 && attack.skill == 1311005) {
            double remainingHP = c.getPlayer().getHp() - attack.allDamage.get(0).getRight().get(0).getLeft() * attack.getAttackEffect(c.getPlayer()).getX() / 100;
            if (remainingHP > 1) {
                c.getPlayer().setHp((int) remainingHP);
            } else {
                c.getPlayer().setHp(1);
            }
            c.getPlayer().updateSingleStat(MapleStat.HP, c.getPlayer().getHp());
            c.getPlayer().checkBerserk();
        }
        // handle charged blow
        if (attack.numAttacked > 0 && attack.skill == 1211002) {
            boolean advcharge_prob = false;
            int advcharge_level = player.getSkillLevel(SkillFactory.getSkill(1220010));
            if (advcharge_level > 0) {
                MapleStatEffect advcharge_effect = SkillFactory.getSkill(1220010).getEffect(advcharge_level);
                advcharge_prob = advcharge_effect.makeChanceResult();
            } else {
                advcharge_prob = false;
            }
            if (!advcharge_prob) {
                player.cancelEffectFromBuffStat(MapleBuffStat.WK_CHARGE);
            }
        }
        int attackCount = 1;
        if (attack.skill != 0 && attack.skill != 1009 && attack.skill != 10001009) {
            if (attack.skill != 4221001 || attack.skill == 4221001 && attack.numDamage != 1) {
                attackCount = skillEffect.getAttackCount();
            }
            maxdamage *= (mult + crit) / 100.0;
            maxdamage *= attackCount;
        }
        maxdamage = Math.min(maxdamage, 199999);
        if (attack.skill == 4211006) {
            maxdamage = 700000;
        } else if (numFinisherOrbs > 0) {
            maxdamage *= numFinisherOrbs;
        } else if (comboBuff != null) {
            ISkill combo = SkillFactory.getSkill(1111002);
            int comboLevel = player.getSkillLevel(combo);
            if (comboLevel > 0) {
                MapleStatEffect comboEffect = combo.getEffect(comboLevel);
                double comboMod = 1.0 + (comboEffect.getDamage() / 100.0 - 1.0) * (comboBuff - 1);
                maxdamage *= comboMod;
            }
        }
        if (attack.skill == 1009 || attack.skill == 10001009) {
            maxdamage = Integer.MAX_VALUE;
        }
        if (numFinisherOrbs == 0 && skill != null && skill.isFinisher()) {
            return;
        }
        if (skill != null && skill.isFinisher()) {
            maxdamage = 199999; // FIXME reenable damage calculation for finishers
        }
        if (attack.skill == 1009 || attack.skill == 10001009 || attack.skill == 20001009) { // bamboo
            player.getDojo().setEnergy(0);
            c.sendPacket(MaplePacketCreator.setDojoEnergy(player.getDojo().getEnergy()));
            c.sendPacket(MaplePacketCreator.serverNotice(5, "As you used the secret skill, your energy bar has been reset."));
        } else if (attack.skill > 0) {
            MapleStatEffect effect_ = skill.getEffect(skillLevel);
            if (effect_.getCooldown() > 0) {
                if (player.skillisCooling(attack.skill)) {
                    player.getCheatTracker().registerOffense(CheatingOffense.COOLDOWN_HACK);
                    return;
                } else {
                    c.sendPacket(MaplePacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
                    ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), attack.skill), effect_.getCooldown() * 1000);
                    player.addCooldown(attack.skill, System.currentTimeMillis(), effect_.getCooldown() * 1000, timer);
                }
            }
        }
        applyAttack(attack, player, maxdamage, attackCount);
    }
}