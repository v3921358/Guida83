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
import guida.net.MaplePacket;
import guida.server.life.MapleMonster;
import guida.server.life.MobSkill;
import guida.server.life.MobSkillFactory;
import guida.server.maps.MapleMapObject;
import guida.server.movement.LifeMovementFragment;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Point;
import java.util.List;

public class MoveLifeHandler extends AbstractMovementPacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MoveLifeHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        // 9A 00
        // 05 58 1E 00
        // 24 00
        // 01
        // 2A 79 01 84 03 00 01 00 00 00 3E 03 9F 06 03 02 00 00 00 00 02 00 00 00 3E 03 9F 06 00 00 00 00 26 00 02 84 03 00 4C 03 9F 06 6A 00 00 00 26 00 02 B4 00 00 3E 03 9F 06 4C 03 9F 06

        // 9A 00
        // CC 58 1E 00
        // 0D 00
        // 00
        // FF 00 00 00 00 00 01 00 00 00 D6 03 9F 06 01 00 D6 03 9F 06 00 00 00 00 22 00 04 38 04 00 D6 03 9F 06 D6 03 9F 06

        final int objectid = slea.readInt();
        final short moveid = slea.readShort();
        // or is the moveid an int?
        // when someone trys to move an item/npc he gets thrown out with a class cast exception mwaha

        final MapleMapObject mmo = c.getPlayer().getMap().getMapObject(objectid);
        if (!(mmo instanceof MapleMonster)) {
            return;
        }
        final MapleMonster monster = (MapleMonster) mmo;
        if (monster.isMoveLocked()) {
            return;
        }
        List<LifeMovementFragment> res = null;
        final int skillByte = slea.readByte();
        final int skill = slea.readByte();
        final int skill_1 = slea.readByte() & 0xFF;
        final int skill_2 = slea.readByte();
        final int skill_3 = slea.readByte();
        slea.readByte(); // skill_4

        MobSkill toUse = null;

        if (skillByte == 1 && monster.getNoSkills() > 0) {
            final int random = (int) (Math.random() * monster.getNoSkills());
            final Pair<Integer, Integer> skillToUse = monster.getSkills().get(random);
            toUse = MobSkillFactory.getMobSkill(skillToUse.getLeft(), skillToUse.getRight());
            if (!monster.canUseSkill(toUse)) {
                toUse = null;
            }
        }

        if (skill_1 >= 100 && skill_1 <= 200 && monster.hasSkill(skill_1, skill_2)) {
            final MobSkill skillData = MobSkillFactory.getMobSkill(skill_1, skill_2);
            if (skillData != null && monster.canUseSkill(skillData)) {
                skillData.applyEffect(c.getPlayer(), monster, true);
            }
        }

        slea.readByte();
        slea.readInt();
        slea.readInt();
        slea.readInt();
        final int start_x = slea.readShort(); // hmm.. startpos?
        final int start_y = slea.readShort(); // hmm...
        final Point startPos = new Point(start_x, start_y);

        res = parseMovement(slea);

        if (monster.getController() != c.getPlayer()) {
            if (monster.isAttackedBy(c.getPlayer())) { // aggro and controller change
                monster.switchController(c.getPlayer(), true);
            } else {
                return;
            }
        } else {
            if (skill == -1 && monster.isControllerKnowsAboutAggro() && !monster.isMobile() && !monster.isFirstAttack()) {
                monster.setControllerHasAggro(false);
                monster.setControllerKnowsAboutAggro(false);
            }
        }
        final boolean aggro = monster.isControllerHasAggro();

        if (toUse != null) {
            c.sendPacket(MaplePacketCreator.moveMonsterResponse(objectid, moveid, monster.getMp(), aggro, toUse.getSkillId(), toUse.getSkillLevel()));
        } else {
            c.sendPacket(MaplePacketCreator.moveMonsterResponse(objectid, moveid, monster.getMp(), aggro));
        }

        if (aggro) {
            monster.setControllerKnowsAboutAggro(true);
        }

        if (res != null) {
            if (slea.available() != 9) {
                //c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.MOVE_MONSTERS);
                //c.disconnect();
                log.warn(c.getPlayer().getName() + " : slea.available != 9 (movement parsing error)");
                return;
            }
            final MaplePacket packet = MaplePacketCreator.moveMonster(skillByte, skill, skill_1, skill_2, skill_3, objectid, startPos, res);
            c.getPlayer().getMap().broadcastMessage(c.getPlayer(), packet, monster.getPosition());
            updatePosition(res, monster, -1);
            c.getPlayer().getMap().moveMonster(monster, monster.getPosition());
            c.getPlayer().getCheatTracker().checkMoveMonster(monster.getPosition());
        }
    }
}