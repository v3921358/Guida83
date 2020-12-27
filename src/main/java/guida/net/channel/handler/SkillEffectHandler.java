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
import guida.client.SkillFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class SkillEffectHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int skillId = slea.readInt();
        final int level = slea.readByte();
        final short stance = slea.readShort();
        final int speed = slea.readByte();
        final ISkill skill = SkillFactory.getSkill(skillId);
        final int skillLevel = c.getPlayer().getSkillLevel(SkillFactory.getSkill(skillId));

        if (skill == null || skillLevel == 0 || skillLevel != level) { // using a skill when it's level 0
            c.disconnect();
            return;
        }
        c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.skillEffect(c.getPlayer(), skillId, level, stance, speed), false);
    }
}