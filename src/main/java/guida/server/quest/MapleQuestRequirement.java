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

import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MapleQuestStatus;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.server.MapleItemInformationProvider;

import java.util.Calendar;

/**
 * @author Matze
 */
public class MapleQuestRequirement {

    private final MapleQuestRequirementType type;
    private final MapleData data;
    private final MapleQuest quest;

    /**
     * Creates a new instance of MapleQuestRequirement
     */
    public MapleQuestRequirement(MapleQuest quest, MapleQuestRequirementType type, MapleData data) {
        this.type = type;
        this.data = data;
        this.quest = quest;
    }

    boolean check(MapleCharacter c, Integer npcid) {
        switch (type) {
            case INFO:
                for (MapleData infoEntry : data) {
                    if (c.getQuest(quest.getId()).getQuestRecord().matches(infoEntry.getData().toString())) {
                        return true;
                    }
                }
                return false;
            case JOB:
                for (MapleData jobEntry : data) {
                    if (c.getJob().equals(MapleJob.getById(DataUtil.toInt(jobEntry))) || c.isGM()) {
                        return true;
                    }
                }
                return false;
            case QUEST:
                for (MapleData questEntry : data) {
                    MapleQuestStatus q = c.getQuest(DataUtil.toInt(questEntry.getChild("id")));
                    if (q == null && MapleQuestStatus.Status.getById(DataUtil.toInt(questEntry.getChild("state"), 0)).equals(MapleQuestStatus.Status.NOT_STARTED)) {
                        continue;
                    }
                    if (q == null || !q.getStatus().equals(MapleQuestStatus.Status.getById(DataUtil.toInt(questEntry.getChild("state"), 0)))) {
                        return false;
                    }
                }
                return true;
            case ITEM:
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                for (MapleData itemEntry : data) {
                    int itemId = DataUtil.toInt(itemEntry.getChild("id"));
                    short quantity = 0;
                    MapleInventoryType iType = ii.getInventoryType(itemId);
                    for (IItem item : c.getInventory(iType).listById(itemId)) {
                        quantity += item.getQuantity();
                    }
                    if (quantity < DataUtil.toInt(itemEntry.getChild("count"), 0) || DataUtil.toInt(itemEntry.getChild("count"), 0) <= 0 && quantity > 0) {
                        return false;
                    }
                }
                return true;
            case MIN_LEVEL:
                return c.getLevel() >= DataUtil.toInt(data);
            case MAX_LEVEL:
                return c.getLevel() <= DataUtil.toInt(data);
            case END_DATE:
                String timeStr = DataUtil.toString(data);
                Calendar cal = Calendar.getInstance();
                cal.set(Integer.parseInt(timeStr.substring(0, 4)), Integer.parseInt(timeStr.substring(4, 6)), Integer.parseInt(timeStr.substring(6, 8)), Integer.parseInt(timeStr.substring(8, 10)), 0);
                return cal.getTimeInMillis() > System.currentTimeMillis();
            case MOB:
                for (MapleData mobEntry : data) {
                    int mobId = DataUtil.toInt(mobEntry.getChild("id"));
                    int killReq = DataUtil.toInt(mobEntry.getChild("count"));
                    if (c.getQuest(quest.getId()).getMobKills(mobId) < killReq) {
                        return false;
                    }
                }
                return true;
            case NPC:
                return npcid == null || npcid == DataUtil.toInt(data);
            case FIELD_ENTER:
                MapleData zeroField = data.getChild("0");
                if (zeroField != null) {
                    return DataUtil.toInt(zeroField) == c.getMapId();
                }
                return false;
            case INTERVAL:
                return !c.getQuest(quest.getId()).getStatus().equals(MapleQuestStatus.Status.COMPLETED) || c.getQuest(quest.getId()).getCompletionTime() <= System.currentTimeMillis() - DataUtil.toInt(data) * 60 * 1000L;
            //case PET:
            //case MIN_PET_TAMENESS:
            case MONSTER_BOOK:
                return c.getMonsterBook().getTotalCards() >= DataUtil.toInt(data);
            default:
                return true;
        }
    }

    public MapleQuestRequirementType getType() {
        return type;
    }

    public MapleData getData() {
        return data;
    }

    @Override
    public String toString() {
        return type.toString() + " " + DataUtil.toString(data) + " " + quest.toString();
    }
}