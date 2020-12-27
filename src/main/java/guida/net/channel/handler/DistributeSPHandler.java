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
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleQuestStatus;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.Map.Entry;

public class DistributeSPHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.setLastAction(action);
        final int skillid = slea.readInt();
        boolean isBegginnerSkill = false;

        final MapleCharacter player = c.getPlayer();
        int remainingSp = player.getRemainingSp();
        if (skillid == 1000 || skillid == 1001 || skillid == 1002) { // Beginner Skills
            final int snailsLevel = player.getSkillLevel(SkillFactory.getSkill(1000));
            final int recoveryLevel = player.getSkillLevel(SkillFactory.getSkill(1001));
            final int nimbleFeetLevel = player.getSkillLevel(SkillFactory.getSkill(1002));
            remainingSp = Math.min((player.getLevel() - 1), 6) - snailsLevel - recoveryLevel - nimbleFeetLevel;
            isBegginnerSkill = true;
        } else if (skillid == 10001000 || skillid == 10001001 || skillid == 10001002) { // Noblesse Skills
            final int snailsLevel = player.getSkillLevel(SkillFactory.getSkill(10001000));
            final int recoveryLevel = player.getSkillLevel(SkillFactory.getSkill(10001001));
            final int nimbleFeetLevel = player.getSkillLevel(SkillFactory.getSkill(10001002));
            remainingSp = Math.min((player.getLevel() - 1), 6) - snailsLevel - recoveryLevel - nimbleFeetLevel;
            isBegginnerSkill = true;
        } else if (skillid == 20001000 || skillid == 20001001 || skillid == 20001002) { // Aran Skills
            final int snailsLevel = player.getSkillLevel(SkillFactory.getSkill(20001000));
            final int recoveryLevel = player.getSkillLevel(SkillFactory.getSkill(20001001));
            final int nimbleFeetLevel = player.getSkillLevel(SkillFactory.getSkill(20001002));
            remainingSp = Math.min((player.getLevel() - 1), 6) - snailsLevel - recoveryLevel - nimbleFeetLevel;
            isBegginnerSkill = true;
        }
        if (!isBegginnerSkill && (skillid / 10000 == 0 || skillid / 10000 == 1000 || skillid / 10000 == 2000)) { //skillid == 1005 || skillid == 1006 || skillid == 1003 || skillid == 1004) {
            return;
        }
        if ((skillid == 1121011 || skillid == 1221012 || skillid == 1321010 || skillid == 2121008 || skillid == 2221008 || skillid == 2321009 || skillid == 3121009 || skillid == 3221008 || skillid == 4121009 || skillid == 4221008) && !player.getQuest(6304).getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
            return;
        }
        final ISkill skill = SkillFactory.getSkill(skillid);
        final int maxlevel = skill.isFourthJob() ? player.getMasterLevel(skill) : skill.getMaxLevel();
        final int curLevel = player.getSkillLevel(skill);
        if (skill.hasRequirements()) {
            for (Entry<Integer, Integer> reqLevel : skill.getRequirements().entrySet()) {
                if (player.getSkillLevel(SkillFactory.getSkill(reqLevel.getKey())) < reqLevel.getValue()) {
                    c.disconnect();
                    return;
                }
            }
        }
        if (remainingSp > 0 && curLevel + 1 <= maxlevel && skill.canBeLearnedBy(player.getJob())) {
            if (!isBegginnerSkill) {
                player.setRemainingSp(player.getRemainingSp() - 1);
            }
            player.updateSingleStat(MapleStat.AVAILABLESP, player.getRemainingSp());
            player.changeSkillLevel(skill, curLevel + 1, player.getMasterLevel(skill));
        } /* else if (!skill.canBeLearnedBy(player.getJob())) {
        } else if (!(remainingSp > 0 && curLevel + 1 <= maxlevel) && !player.isGM()) {
        } */
    }
}