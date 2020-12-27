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

import guida.client.IItem;
import guida.client.ISkill;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MapleWeaponType;
import guida.client.SkillCooldown.CancelCooldownAction;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.net.MaplePacket;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MobSkillFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.concurrent.ScheduledFuture;

public class RangedAttackHandler extends AbstractDealDamageHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        //System.out.println(slea);
        if (!c.getPlayer().getMap().canUseSkills() && !c.getPlayer().isGM()) {
            if (!c.getPlayer().getDiseases().contains(MapleDisease.GM_DISABLE_SKILL)) {
                c.getPlayer().giveDebuff(MapleDisease.GM_DISABLE_SKILL, MobSkillFactory.getMobSkill(120, 1), true);
            }
            return;
        }
        final AttackInfo attack = parseDamage(c.getPlayer(), slea, true);
        final MapleCharacter player = c.getPlayer();
        if (player.getMap().isDojoMap() && attack.numAttacked > 0) {
            player.getDojo().setEnergy(player.getDojo().getEnergy() + 100);
            c.sendPacket(MaplePacketCreator.setDojoEnergy(player.getDojo().getEnergy()));
        }
        final MapleInventory equip = player.getInventory(MapleInventoryType.EQUIPPED);
        final IItem weapon = equip.getItem((short) -11);
        final MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
        final MapleWeaponType type = mii.getWeaponType(weapon.getItemId());
        if (type == MapleWeaponType.NOT_A_WEAPON) {
            throw new RuntimeException("[h4x] Player " + player.getName() + " is attacking with something that's not a weapon");
        }
        final MapleInventory use = player.getInventory(MapleInventoryType.USE);
        int skillLevel = 0, projectile = 0, bulletCount = 1;
        MapleStatEffect effect = null;
        if (attack.skill != 0) {
            skillLevel = player.getSkillLevel(SkillFactory.getSkill(attack.skill));
            effect = attack.getAttackEffect(c.getPlayer());
            if (player.getMp() < effect.getMpCon()) {
                return;
            }
            bulletCount = effect.getBulletCount();
        }
        final boolean hasShadowPartner = player.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null;
        short itemPos = attack.useItemPos;
        final boolean soulArrow = player.getBuffedValue(MapleBuffStat.SOULARROW) != null || attack.skill == 5121002; //weird pirate skillz
        final boolean shadowClaw = player.getBuffedValue(MapleBuffStat.SHADOW_CLAW) != null;
        if (itemPos > 0) {
            final IItem item = use.getItem(itemPos);
            if (item != null) {
                final boolean clawCondition = type == MapleWeaponType.CLAW && mii.isThrowingStar(item.getItemId()) && weapon.getItemId() != 1472063;
                final boolean bowCondition = type == MapleWeaponType.BOW && mii.isArrowForBow(item.getItemId());
                final boolean crossbowCondition = type == MapleWeaponType.CROSSBOW && mii.isArrowForCrossBow(item.getItemId());
                final boolean gunCondition = type == MapleWeaponType.GUN && mii.isShootingBullet(item.getItemId());
                final boolean mittenCondition = weapon.getItemId() == 1472063 && (mii.isArrowForBow(item.getItemId()) || mii.isArrowForCrossBow(item.getItemId()));
                if (clawCondition || bowCondition || crossbowCondition || mittenCondition || gunCondition) {
                    projectile = item.getItemId();
                } else {
                    return;
                }
            } else {
                System.out.println("WARNING - itempos > 0 but item is null - Item Pos: " + itemPos + " Skill ID: " + attack.skill);
                return;
            }
        } else {
            if (!soulArrow && !shadowClaw && (bulletCount > 0 || attack.skill == 0)) {
                return;
            }
        }
        if (itemPos > 0) {
            if (bulletCount < 1) {
                bulletCount = (hasShadowPartner ? 2 : 1);
            }
            if (!soulArrow && !shadowClaw) {
                int bulletConsume = bulletCount;
                if (effect != null && effect.getBulletConsume() != 0) {
                    bulletConsume = effect.getBulletConsume() * (hasShadowPartner ? 2 : 1);
                }
                if (use.getItem(itemPos).getQuantity() >= bulletConsume) {
                    MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, itemPos, bulletConsume, false, true);
                } else {
                    return;
                }
            }
        }
        int visProjectile = projectile; // visible projectile sent to players
        if (mii.isThrowingStar(projectile)) {
            final MapleInventory cash = player.getInventory(MapleInventoryType.CASH);
            final IItem item = cash.getItem(attack.cashStarPos);
            if (item != null && item.getItemId() / 1000 == 5021) {
                visProjectile = item.getItemId();
            }
        } else { // bow, crossbow
            if (soulArrow || attack.skill == 3111004 || attack.skill == 3211004) {
                visProjectile = 0; //arrow rain/eruption show no arrows
            }
        }

        int basedamage;
        int projectileWatk = 0;
        if (projectile != 0) {
            projectileWatk = mii.getWatkForProjectile(projectile);
        }
        if (attack.skill != 4001344 && attack.skill != 14001004) { // not lucky 7
            if (projectileWatk != 0) {
                basedamage = c.getPlayer().calculateMaxBaseDamage(c.getPlayer().getTotalWatk() + projectileWatk);
            } else {
                basedamage = c.getPlayer().getCurrentMaxBaseDamage();
            }
        } else { // L7 has a different formula :>
            basedamage = 2 * (int) (c.getPlayer().getTotalLuk() * 5.0 * (c.getPlayer().getTotalWatk() + projectileWatk) / 100.0);
        }
        if (attack.skill == 3101005) { // arrowbomb is hardcore like that O.o
            basedamage *= effect.getX() / 100.0;
        }
        int maxdamage = basedamage;
        int mult = effect == null ? 100 : effect.getDamage();
        int crit = 0;
        if (player.getJob().isA(MapleJob.ASSASSIN)) {
            final ISkill criticalthrow = SkillFactory.getSkill(4100001);
            final int critlevel = player.getSkillLevel(criticalthrow);
            if (critlevel > 0) {
                crit = criticalthrow.getEffect(critlevel).getDamage();
            }
        } else if (player.getJob().isA(MapleJob.BOWMAN)) {
            final ISkill criticalshot = SkillFactory.getSkill(3000001);
            final int critlevel = player.getSkillLevel(criticalshot);
            if (critlevel > 0) {
                crit = criticalshot.getEffect(critlevel).getDamage();
            }
        } else if (player.getJob().isA(MapleJob.WINDARCHER1)) {
            final ISkill criticalshot = SkillFactory.getSkill(13000000);
            final int critlevel = player.getSkillLevel(criticalshot);
            if (critlevel > 0) {
                crit = criticalshot.getEffect(critlevel).getDamage();
            }
        } else if (player.getJob().isA(MapleJob.NIGHTWALKER1)) {
            final ISkill criticalthrow = SkillFactory.getSkill(14100001);
            final int critlevel = player.getSkillLevel(criticalthrow);
            if (critlevel > 0) {
                crit = criticalthrow.getEffect(critlevel).getDamage();
            }
        } else if (player.getJob().equals(MapleJob.THUNDERBREAKER3)) {
            final ISkill criticalpunch = SkillFactory.getSkill(15110000);
            final int critlevel = player.getSkillLevel(criticalpunch);
            if (critlevel > 0) {
                crit = criticalpunch.getEffect(critlevel).getDamage();
            }
        }
        Integer sharpEyes = player.getBuffedValue(MapleBuffStat.SHARP_EYES);
        if (sharpEyes != null) {
            crit += sharpEyes;
        }
        handleCritical(attack, basedamage, mult, mult + crit, player.getGender() == 0 && sharpEyes != null);
        maxdamage *= ((mult + crit) / 100.0);

        final MaplePacket packet;
        switch (attack.skill) {
            case 3121004: // Hurricane
            case 3221001: // Pierce
            case 5221004: // Rapid Fire
                packet = MaplePacketCreator.rangedAttack(player.getId(), (byte) player.getLevel(), attack.skill, skillLevel, attack.direction, attack.numAttackedAndDamage, visProjectile, attack.allDamage, attack.speed, attack.projectilePos);
                break;
            default:
                packet = MaplePacketCreator.rangedAttack(player.getId(), (byte) player.getLevel(), attack.skill, skillLevel, attack.stance, attack.numAttackedAndDamage, visProjectile, attack.allDamage, attack.speed, attack.projectilePos);
                break;
        }
        player.getMap().broadcastMessage(player, packet, false, true);

        if (effect != null) {
            maxdamage *= effect.getAttackCount();
        }
        if (hasShadowPartner) {
            final ISkill shadowPartner = SkillFactory.getSkill(player.getBuffSource(MapleBuffStat.SHADOWPARTNER));
            final MapleStatEffect shadowPartnerEffect = shadowPartner.getEffect(player.getSkillLevel(shadowPartner));
            if (attack.skill != 0) {
                maxdamage *= 1.0 + shadowPartnerEffect.getY() / 100.0;
            } else {
                maxdamage *= 1.0 + shadowPartnerEffect.getX() / 100.0;
            }
        }
        if (attack.skill == 4111004) {
            maxdamage = 35000;
        }
        if (effect != null) {
            int money = effect.getMoneyCon();
            if (money != 0) {
                final double moneyMod = money * 0.5;
                money = (int) (money + Math.random() * moneyMod);
                if (money > player.getMeso()) {
                    money = player.getMeso();
                }
                player.gainMeso(-money, false);
            }
            if (effect.getCooldown() > 0) {
                if (player.skillisCooling(attack.skill)) {
                    player.getCheatTracker().registerOffense(CheatingOffense.COOLDOWN_HACK);
                    return;
                } else {
                    c.sendPacket(MaplePacketCreator.skillCooldown(attack.skill, effect.getCooldown()));
                    final ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), attack.skill), effect.getCooldown() * 1000);
                    player.addCooldown(attack.skill, System.currentTimeMillis(), effect.getCooldown() * 1000, timer);
                }
            }
            if (effect.isHomingBeacon() && !attack.allDamage.isEmpty()) {
                effect.applyTo(player, attack.allDamage.get(0).getLeft().getLeft());
            }
        }
        applyAttack(attack, player, maxdamage, bulletCount);
    }
}