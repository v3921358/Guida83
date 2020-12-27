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
import guida.client.SkillCooldown.CancelCooldownAction;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.net.MaplePacket;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MobSkillFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.List;
import java.util.concurrent.ScheduledFuture;

public class MagicDamageHandler extends AbstractDealDamageHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (!c.getPlayer().getMap().canUseSkills() && !c.getPlayer().isGM()) {
            if (!c.getPlayer().getDiseases().contains(MapleDisease.GM_DISABLE_SKILL)) {
                c.getPlayer().giveDebuff(MapleDisease.GM_DISABLE_SKILL, MobSkillFactory.getMobSkill(120, 1), true);
            }
            return;
        }
        final AttackInfo attack = parseDamage(c.getPlayer(), slea, false);
        final MapleCharacter player = c.getPlayer();
        if (player.getMap().isDojoMap() && attack.numAttacked > 0) {
            player.getDojo().setEnergy(player.getDojo().getEnergy() + 100);
            c.sendPacket(MaplePacketCreator.setDojoEnergy(player.getDojo().getEnergy()));
        }
        ISkill skill = null;
        int skillLevel = 0;
        if (attack.skill != 0) {
            skill = SkillFactory.getSkill(attack.skill);
            skillLevel = player.getSkillLevel(skill);
        }
        final MapleStatEffect effect = attack.getAttackEffect(player);
        int maxdamage = 199999;
        attack.forceABWarnOnly = true;
        attack.isMagic = true;
        if (skill != null) {
            final int matk = player.getTotalMagic();
            maxdamage = skill.getId() == 2301002
                    // heal (INT * rand(0.3, 1.2) + LUK) * Magic / 200 * TargetMulti
                    ? (int) ((player.getTotalInt() * 1.2 + player.getTotalLuk()) * matk / 200.0 * 1.3)
                    // ((Magic * 0.058)^2 + Magic * 3.3 * rand(Mastery * 0.9, 1) + INT * 0.5) * SpellAttack / 100
                    : (int) (Math.pow(matk * 0.058, 2.0) + matk * 3.3 + player.getTotalInt() * 0.5);

            final MapleStatEffect effect_ = skill.getEffect(skillLevel);
            int mult = skill.getId() == 2301002 ? effect_.getHp() : effect_.getMatk();
            Integer sharpEyes = player.getBuffedValue(MapleBuffStat.SHARP_EYES);
            if (sharpEyes != null) {
                int critMult = skill.getId() == 2301002 ? mult + sharpEyes : (int) (mult * ((100 + sharpEyes) / 100.0));
                handleCritical(attack, maxdamage, mult, critMult, player.getGender() == 0);
                mult = critMult;
            }
            maxdamage *= mult / 100.0;
            maxdamage *= effect.getAttackCount();
            if (effect_ != null && effect_.getCooldown() > 0) {
                if (player.skillisCooling(attack.skill)) {
                    player.getCheatTracker().registerOffense(CheatingOffense.COOLDOWN_HACK);
                    return;
                } else {
                    c.sendPacket(MaplePacketCreator.skillCooldown(attack.skill, effect_.getCooldown()));
                    final ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(player, attack.skill), effect_.getCooldown() * 1000);
                    player.addCooldown(attack.skill, System.currentTimeMillis(), effect_.getCooldown() * 1000, timer);
                }
            }
        }
        MaplePacket packet = MaplePacketCreator.magicAttack(player.getId(), (byte) player.getLevel(), attack.skill, skillLevel, attack.stance, attack.numAttackedAndDamage, attack.allDamage, -1, attack.speed);
        if (skill != null && skill.hasCharge()) {
            packet = MaplePacketCreator.magicAttack(player.getId(), (byte) player.getLevel(), attack.skill, skillLevel, attack.stance, attack.numAttackedAndDamage, attack.allDamage, attack.charge, attack.speed);
        }
        player.getMap().broadcastMessage(player, packet, false, true);
        if (player.getMp() < effect.calcMagicAttackMPUsage(c.getPlayer()) * -1) {
            return;
        }

        applyAttack(attack, player, maxdamage, effect.getAttackCount());
        // MP Eater
        for (int i = 1; i <= 3; i++) {
            final ISkill eaterSkill = SkillFactory.getSkill(2000000 + i * 100000);
            final int eaterLevel = player.getSkillLevel(eaterSkill);
            if (eaterLevel > 0) {
                for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> singleDamage : attack.allDamage) {
                    eaterSkill.getEffect(eaterLevel).applyPassive(player, player.getMap().getMapObject(singleDamage.getLeft().getLeft()), 0);
                }
                break;
            }
        }
    }
}