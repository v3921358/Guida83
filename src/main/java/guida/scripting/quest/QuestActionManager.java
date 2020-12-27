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

package guida.scripting.quest;

import guida.client.MapleClient;
import guida.scripting.npc.NPCConversationManager;
import guida.server.MapleItemInformationProvider;
import guida.server.quest.MapleQuest;

/**
 * @author RMZero213
 */
public class QuestActionManager extends NPCConversationManager {

    private final boolean start;
    private final int quest;

    public QuestActionManager(MapleClient c, int npc, int quest, boolean start) {
        super(c, npc);
        this.quest = quest;
        this.start = start;
    }

    public int getQuest() {
        return quest;
    }

    public boolean isStart() {
        return start;
    }

    @Override
    public void dispose() {
        QuestScriptManager.getInstance().dispose(this, getClient());
    }

    public void quickStart() {
        MapleQuest.getInstance(quest).setQuestInfo(getPlayer(), "", true, false);
    }

    public void quickStart(int id) {
        MapleQuest.getInstance(id).setQuestInfo(getPlayer(), "", true, false);
    }

    public void startQuest() {
        MapleQuest.getInstance(quest).start(getPlayer(), getNpc(), true);
    }

    public void completeQuest() {
        MapleQuest.getInstance(quest).complete(getPlayer(), getNpc(), true, true);
    }

    public void completeQuest(boolean animation) {
        MapleQuest.getInstance(quest).complete(getPlayer(), getNpc(), true, animation);
    }

    public void silentCompleteQuest() {
        MapleQuest.getInstance(quest).silentCompleteQuest(getPlayer(), getNpc(), true);
    }

    public int getMedalId() {
        return MapleQuest.getInstance(quest).getMedalId();
    }

    public String getMedalName() {
        return MapleItemInformationProvider.getInstance().getName(MapleQuest.getInstance(quest).getMedalId());
    }
}