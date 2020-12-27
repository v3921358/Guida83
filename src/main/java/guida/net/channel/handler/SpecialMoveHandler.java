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
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.SkillCooldown.CancelCooldownAction;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.client.messages.ServernoticeMapleClientMessageCallback;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MapleMonster;
import guida.server.life.MobSkillFactory;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Point;
import java.util.concurrent.ScheduledFuture;

public class SpecialMoveHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SpecialMoveHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // [53 00] [12 62] [AA 01] [6B 6A 23 00] [1E] [BA 00] [97 00] 00
        c.getPlayer().addBuffCount();
        slea.readShort();
        slea.readShort();
        final int skillid = slea.readInt();
        if (!c.getPlayer().getMap().canUseSkills() && !c.getPlayer().isGM()) {
            if (!c.getPlayer().getDiseases().contains(MapleDisease.GM_DISABLE_SKILL)) {
                c.getPlayer().giveDebuff(MapleDisease.GM_DISABLE_SKILL, MobSkillFactory.getMobSkill(120, 1), true);
            }
            return;
        }
        if ((skillid == 4001003 || skillid == 4221006 || skillid == 5101007) && !c.getPlayer().isGM() && c.getPlayer().getMap().cannotInvincible()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            c.getPlayer().dropMessage(5, "Dark Sight, Oak Barrel and Smokescreen are disabled on this map.");
            return;
        }
        // seems to be skilllevel for movement skills and -32748 for buffs
        Point pos = null;
        final int __skillLevel = slea.readByte();
        final ISkill skill = SkillFactory.getSkill(skillid);
        int skillLevel = c.getPlayer().getSkillLevel(skill);
        if (skillid == 1010 || skillid == 1011 || skillid == 10001010 || skillid == 10001011) {
            if (c.getPlayer().getDojo().getEnergy() < 10000) { // PE hacking or maybe just lagging
                return;
            }
            skillLevel = 1;
            c.getPlayer().getDojo().setEnergy(0);
            c.sendPacket(MaplePacketCreator.setDojoEnergy(c.getPlayer().getDojo().getEnergy()));
            c.sendPacket(MaplePacketCreator.serverNotice(5, "As you used the secret skill, your energy bar has been reset."));
        }
        c.getPlayer().resetAfkTimer();
        final MapleStatEffect effect = skill.getEffect(skillLevel);
        if (effect != null) {
            if (c.getPlayer().getMp() < effect.getMpCon()) {
                return;
            }
            if (effect.getCooldown() > 0) {
                if (c.getPlayer().skillisCooling(skillid)) {
                    c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.COOLDOWN_HACK);
                    return;
                } else {
                    if (skillid != 5221006) {
                        c.sendPacket(MaplePacketCreator.skillCooldown(skillid, effect.getCooldown()));
                        final ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(c.getPlayer(), skillid), effect.getCooldown() * 1000);
                        c.getPlayer().addCooldown(skillid, System.currentTimeMillis(), effect.getCooldown() * 1000, timer);
                    }
                }
            }
        }
        //monster magnet
        try {
            switch (skillid) {
                case 1121001, 1221001, 1321001 -> {
                    final int num = slea.readInt();
                    int mobId;
                    byte success;
                    for (int i = 0; i < num; i++) {
                        mobId = slea.readInt();
                        success = slea.readByte();
                        c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showMagnet(mobId, success), false);
                        final MapleMonster monster = c.getPlayer().getMap().getMonsterByOid(mobId);
                        if (monster != null) {
                            monster.switchController(c.getPlayer(), monster.isControllerHasAggro());
                        }
                    }
                    final byte direction = slea.readByte();
                    c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showBuffeffect(c.getPlayer().getId(), skillid, 1, (byte) c.getPlayer().getLevel(), direction), false);
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to handle monster magnet..", e);
        }
        if (slea.available() == 5) {
            pos = new Point(slea.readShort(), slea.readShort());
        }
        if (skillLevel == 0 || skillLevel != __skillLevel) {
            log.warn(c.getPlayer().getName() + " is using a move skill he doesn't have.. ID: " + skill.getId());
            c.disconnect();
        } else {
            if (c.getPlayer().isAlive()) {
                if (skillid == 9101004 && c.getPlayer().isGM()) {
                    c.getPlayer().setHidden(!c.getPlayer().isHidden());
                }
                if (skill.getId() != 2311002 || c.getPlayer().canDoor()) {
                    skill.getEffect(skillLevel).applyTo(c.getPlayer(), pos);
                } else {
                    new ServernoticeMapleClientMessageCallback(5, c).dropMessage("Please wait 5 seconds before casting Mystic Door again");
                    c.sendPacket(MaplePacketCreator.enableActions());
                }
            } else {
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
    }
}