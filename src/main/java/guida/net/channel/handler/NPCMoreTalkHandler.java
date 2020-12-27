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
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.npc.NPCScriptManager;
import guida.scripting.quest.QuestScriptManager;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Matze
 */
public class NPCMoreTalkHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {

        final byte lastMsg = slea.readByte(); // 00 (last msg type I think)
        final byte action = slea.readByte(); // 00 = end chat, 01 == follow

        if (lastMsg == 2) {
            if (action == 1) {
                final String returnText = slea.readMapleAsciiString();
                final int rtlen = returnText.length();
                if (c.getQM() != null) {
                    if ((!c.getQM().hasGetLimits() || !(rtlen >= c.getQM().getMinimumGetLimit() && rtlen <= c.getQM().getMaximumGetLimit())) && !(c.getQM().getMinimumGetLimit() == 0 && c.getQM().getMaximumGetLimit() == 0)) {
                        c.disconnect(); // packet edit
                        return;
                    }
                    c.getQM().unsetGetLimits();
                    c.getQM().setGetText(returnText);
                    if (c.getQM().isStart()) {
                        QuestScriptManager.getInstance().start(c, action, lastMsg, -1);
                    } else {
                        QuestScriptManager.getInstance().end(c, action, lastMsg, -1);
                    }
                } else {
                    if ((!c.getCM().hasGetLimits() || !(rtlen >= c.getCM().getMinimumGetLimit() && rtlen <= c.getCM().getMaximumGetLimit())) && !(c.getCM().getMinimumGetLimit() == 0 && c.getCM().getMaximumGetLimit() == 0)) {
                        c.disconnect(); // packet edit
                        return;
                    }
                    c.getCM().unsetGetLimits();
                    c.getCM().setGetText(returnText);
                    NPCScriptManager.getInstance().action(c, action, lastMsg, -1);
                }
            } else {
                if (c.getQM() != null) {
                    c.getQM().dispose();
                } else {
                    c.getCM().dispose();
                }
            }
        } else if (lastMsg == 3) {
            int selection = -1;
            if (slea.available() >= 4) {
                selection = slea.readInt();
                if (selection < 0) {
                    if (c.getQM() != null) {
                        c.getQM().dispose();
                    } else {
                        c.getCM().dispose();
                    }
                    return;
                }
            } else if (slea.available() > 0) {
                selection = slea.readByte();
                if (selection < 0) {
                    selection = 1;
                }
            }

            if (c.getQM() != null) {
                if ((!c.getQM().hasGetLimits() || !(selection >= c.getQM().getMinimumGetLimit() && selection <= c.getQM().getMaximumGetLimit())) && !(c.getQM().getMinimumGetLimit() == 0 && c.getQM().getMaximumGetLimit() == 0)) {
                    c.disconnect(); // packet edit
                    return;
                }
                c.getQM().unsetGetLimits();
                if (c.getQM().isStart()) {
                    QuestScriptManager.getInstance().start(c, action, lastMsg, selection);
                } else {
                    QuestScriptManager.getInstance().end(c, action, lastMsg, selection);
                }
            } else if (c.getCM() != null) {
                if (action != 0 && (!c.getCM().hasGetLimits() || !(selection >= c.getCM().getMinimumGetLimit() && selection <= c.getCM().getMaximumGetLimit())) && !(c.getCM().getMinimumGetLimit() == 0 && c.getCM().getMaximumGetLimit() == 0)) {
                    c.disconnect(); // packet edit
                    return;
                }
                c.getCM().unsetGetLimits();
                NPCScriptManager.getInstance().action(c, action, lastMsg, selection);
            }
        } else {
            int selection = -1;
            if (slea.available() >= 4) {
                selection = slea.readInt();
                if (selection < 0) {
                    if (c.getQM() != null) {
                        c.getQM().dispose();
                    } else {
                        c.getCM().dispose();
                    }
                    return;
                }
            } else if (slea.available() > 0) {
                selection = slea.readByte();
                if (selection < 0) {
                    selection = 1;
                }
            }
            if (c.getQM() != null) {
                if (c.getQM().isStart()) {
                    QuestScriptManager.getInstance().start(c, action, lastMsg, selection);
                } else {
                    QuestScriptManager.getInstance().end(c, action, lastMsg, selection);
                }
            } else if (c.getCM() != null) {
                NPCScriptManager.getInstance().action(c, action, lastMsg, selection);
            }
        }
    }
}