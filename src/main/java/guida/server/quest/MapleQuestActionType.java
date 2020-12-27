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

package guida.server.quest;

/**
 * @author Matze
 */
public enum MapleQuestActionType {

    UNDEFINED(-1),
    EXP(0),
    ITEM(1),
    NEXTQUEST(2),
    MESO(3),
    QUEST(4),
    SKILL(5),
    FAME(6),
    BUFF(7),
    INFO(8),
    NPC_ACT(9);

    final byte type;

    MapleQuestActionType(int type) {
        this.type = (byte) type;
    }

    public static MapleQuestActionType getByType(byte type) {
        for (MapleQuestActionType l : MapleQuestActionType.values()) {
            if (l.type == type) {
                return l;
            }
        }
        return null;
    }

    public static MapleQuestActionType getByWZName(String name) {
        return switch (name) {
            case "info" -> INFO;
            case "exp" -> EXP;
            case "money" -> MESO;
            case "item" -> ITEM;
            case "skill" -> SKILL;
            case "nextQuest" -> NEXTQUEST;
            case "pop" -> FAME;
            case "buffItemID" -> BUFF;
            case "npcAct" -> NPC_ACT;
            default -> UNDEFINED;
        };
    }

    public byte getType() {
        return type;
    }
}
