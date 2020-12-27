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

package guida.client;

import guida.server.life.MapleLifeFactory;
import guida.server.quest.MapleQuest;
import guida.tools.StringUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleQuestStatus {

    public enum Status {

        UNDEFINED(-1),
        NOT_STARTED(0),
        STARTED(1),
        COMPLETED(2);

        final int status;

        Status(int id) {
            status = id;
        }

        public static Status getById(int id) {
            for (Status l : Status.values()) {
                if (l.status == id) {
                    return l;
                }
            }
            return null;
        }

        public int getId() {
            return status;
        }
    }

    private final int questId;
    private final Status status;
    private final Map<Integer, Integer> killedMobs = new HashMap<>();
    private String questRecord;
    private Map<Integer, Integer> questItems;
    private Map<Integer, Integer> mobsToKill;
    private int npc;
    private long completionTime;

    /**
     * Creates a new instance of MapleQuestStatus
     */
    public MapleQuestStatus(int questId, Status status, String questRecord) {
        this.questId = questId;
        this.status = status;
        this.questRecord = questRecord;
        completionTime = System.currentTimeMillis();
        if (status == Status.STARTED) {
            registerRequirements();
            if (!killedMobs.isEmpty() && questRecord.length() != 0 && questRecord.length() % 3 == 0) {
                final String[] kills = questRecord.split("(?<=\\G...)");
                int index = 0;
                for (int mobId : mobsToKill.keySet()) {
                    killedMobs.put(mobId, Integer.parseInt(kills[index++]));
                }
            }
        }
    }

    public MapleQuestStatus(int questId, Status status, String questRecord, int npc) {
        this.questId = questId;
        this.status = status;
        this.questRecord = questRecord;
        this.npc = npc;
        completionTime = System.currentTimeMillis();
        if (status == Status.STARTED) {
            registerRequirements();
        }
    }

    public int getQuestId() {
        return questId;
    }

    public Status getStatus() {
        return status;
    }

    public String getQuestRecord() {
        return questRecord;
    }

    public int getNpc() {
        return npc;
    }

    public long getCompletionTime() {
        return completionTime;
    }

    public void setCompletionTime(long completionTime) {
        this.completionTime = completionTime;
    }

    private void registerRequirements() {
        MapleQuest quest = MapleQuest.getInstance(questId);
        questItems = new HashMap<>(quest.getRequiredItems());
        mobsToKill = new LinkedHashMap<>(quest.getQuestMobs());
        for (int i : mobsToKill.keySet()) {
            killedMobs.put(i, 0);
        }
    }

    public int getRequiredItemAmount(int itemId) {
        return questItems.get(itemId) != null ? questItems.get(itemId) : 0;
    }

    public int getMobKills(int id) {
        if (killedMobs.get(id) == null) {
            return 0;
        }
        return killedMobs.get(id);
    }

    public int getMobSize() {
        return killedMobs.size();
    }

    public boolean mobKilled(int id) {
        if (killedMobs.get(id) != null) {
            return updateMobKills(id);
        }
        for (int mobId : mobsToKill.keySet()) {
            if (mobId / 1000 == 9101 && killedMobs.get(mobId) != null) {
                final List<Integer> group = MapleLifeFactory.getQuestCountGroup(mobId);
                for (int gMobId : group) {
                    if (gMobId == id) {
                        return updateMobKills(mobId);
                    }
                }
            }
        }
        return false;
    }

    private boolean updateMobKills(int id) {
        if (mobsToKill.get(id) > killedMobs.get(id)) {
            killedMobs.put(id, killedMobs.get(id) + 1);
            final StringBuilder newQR = new StringBuilder();
            for (int kills : killedMobs.values()) {
                newQR.append(StringUtil.getLeftPaddedStr(String.valueOf(kills), '0', 3));
            }
            questRecord = newQR.toString();
            return true;
        }
        return false;
    }
}