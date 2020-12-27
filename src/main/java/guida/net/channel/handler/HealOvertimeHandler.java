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

import guida.client.MapleClient;
import guida.client.MapleJob;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class HealOvertimeHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        slea.skip(8);
        int healHP = slea.readShort();
        int healMP = slea.readShort();
        if (!c.getPlayer().isAlive()) {
            return;
        }
        if (healHP != 0) {
            if (healHP > 140) {
                c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.REGEN_HIGH_HP, String.valueOf(healHP));
                return;
            }
            c.getPlayer().getCheatTracker().checkHPRegen();
            if (c.getPlayer().getCurrentMaxHp() == c.getPlayer().getHp()) {
                c.getPlayer().getCheatTracker().resetHPRegen();
            }
            c.getPlayer().addHP(healHP);
            c.getPlayer().checkBerserk();
        }
        if (healMP != 0) {
            if (healMP > 1000) // Definitely impossible
            {
                return;
            }
            float mpRec = 0;
            if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN)) {
                mpRec = (float) c.getPlayer().getSkillLevel(SkillFactory.getSkill(2000000)) / 10 * c.getPlayer().getLevel();
            }
            float theoreticalRecovery = 0;
            theoreticalRecovery += Math.floor(mpRec + 3);
            if (healMP > theoreticalRecovery) {
                if (healMP > 300) { // seems almost impossible
                    c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.REGEN_HIGH_MP, String.valueOf(healMP));
                }
            }
            c.getPlayer().getCheatTracker().checkMPRegen();
            c.getPlayer().addMP(healMP);
            if (c.getPlayer().getCurrentMaxMp() == c.getPlayer().getMp()) {
                c.getPlayer().getCheatTracker().resetMPRegen();
            }
        }
    }
}