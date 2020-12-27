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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleQuestStatus.Status;
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.quest.QuestScriptManager;
import guida.server.quest.MapleQuest;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Matze
 */
public class QuestActionHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final byte action = slea.readByte();
        final short quest = slea.readShort();
        final MapleCharacter player = c.getPlayer();
        if (action == 0) {
            slea.skip(4);
            final int itemId = slea.readInt();
            if (player.getQuest(quest).getStatus().equals(Status.STARTED)) {
                MapleQuest.getInstance(quest).recoverItem(player, itemId);
            }
        } else if (action == 1) { // start quest
            final int npc = slea.readInt();
            MapleQuest.getInstance(quest).start(player, npc);
        } else if (action == 2) { // complete quest
            if (player.getQuest(quest).getStatus().equals(Status.STARTED)) {
                final int npc = slea.readInt();
                //slea.readInt(); // dont know *o*
                if (slea.available() >= 4) {
                    final int selection = slea.readInt();
                    MapleQuest.getInstance(quest).complete(player, npc, selection, false, true);
                } else {
                    MapleQuest.getInstance(quest).complete(player, npc);
                }
            }
        } else if (action == 3) { // forfeit quest
            MapleQuest.getInstance(quest).forfeit(player);
        } else if (action == 4) { // scripted start quest
            final int npc = slea.readInt();
            //slea.readInt();
            if (player.getQuest(quest).getStatus().equals(Status.NOT_STARTED)) {
                QuestScriptManager.getInstance().start(c, npc, quest);
            }
        } else if (action == 5) { // scripted end quests
            final int npc = slea.readInt();
            //slea.readInt();
            if (player.getQuest(quest).getStatus().equals(Status.STARTED)) {
                QuestScriptManager.getInstance().end(c, npc, quest);
            }
        }
    }
}