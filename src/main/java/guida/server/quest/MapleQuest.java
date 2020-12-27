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

import guida.client.MapleCharacter;
import guida.client.MapleQuestStatus;
import guida.client.MapleQuestStatus.Status;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.MaplePacketCreator;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleQuest {

    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleQuest.class);
    private final static Map<Integer, MapleQuest> quests = new HashMap<>();
    private static final MapleDataProvider questData = MapleDataProviderFactory.getDataProvider("Quest");
    private static final MapleData actions = questData.getData("Act.img");
    private static final MapleData requirements = questData.getData("Check.img");
    private static final MapleData info = questData.getData("QuestInfo.img");
    private final Map<Integer, Integer> questMobs = new LinkedHashMap<>();
    private final Map<Integer, Integer> questItems = new HashMap<>();
    protected int id;
    protected List<MapleQuestRequirement> startReqs;
    protected List<MapleQuestRequirement> completeReqs;
    protected List<MapleQuestAction> startActs;
    protected List<MapleQuestAction> completeActs;
    private boolean autoStart;
    private boolean autoAccept;
    private boolean autoPreComplete;
    private boolean autoComplete;
    private boolean repeatable = false;
    private boolean dayByDay = false;
    private int medalItem;

    protected MapleQuest() {
    }

    private MapleQuest(int id) {
        this.id = id;
        MapleData startReqData = requirements.resolve(String.valueOf(id)).getChild("0");
        startReqs = new LinkedList<>();
        if (startReqData != null) {
            for (MapleData startReq : startReqData) {
                MapleQuestRequirementType type = MapleQuestRequirementType.getByWZName(startReq.getName());
                if (type.equals(MapleQuestRequirementType.INTERVAL)) {
                    repeatable = true;
                }
                if (type.equals(MapleQuestRequirementType.DAY_BY_DAY)) {
                    dayByDay = true;
                }
                MapleQuestRequirement req = new MapleQuestRequirement(this, type, startReq);
                startReqs.add(req);
            }
        }
        MapleData completeReqData = requirements.resolve(String.valueOf(id)).getChild("1");
        completeReqs = new LinkedList<>();
        if (completeReqData != null) {
            for (MapleData completeReq : completeReqData) {
                MapleQuestRequirement req = new MapleQuestRequirement(this, MapleQuestRequirementType.getByWZName(completeReq.getName()), completeReq);
                if (req.getType().equals(MapleQuestRequirementType.MOB)) {
                    for (MapleData mob : completeReq) {
                        questMobs.put(DataUtil.toInt(mob.getChild("id")), DataUtil.toInt(mob.getChild("count")));
                    }
                }
                if (req.getType().equals(MapleQuestRequirementType.ITEM)) {
                    for (MapleData item : completeReq) {
                        questItems.put(DataUtil.toInt(item.getChild("id")), DataUtil.toInt(item.getChild("count"), 0));
                    }
                }
                completeReqs.add(req);
            }
        }
        // read acts
        MapleData startActData = actions.resolve(String.valueOf(id)).getChild("0");
        startActs = new LinkedList<>();
        if (startActData != null) {
            for (MapleData startAct : startActData) {
                MapleQuestActionType questActionType = MapleQuestActionType.getByWZName(startAct.getName());
                startActs.add(new MapleQuestAction(questActionType, startAct, this, true));
            }
        }
        MapleData completeActData = actions.resolve(String.valueOf(id)).getChild("1");
        completeActs = new LinkedList<>();
        if (completeActData != null) {
            for (MapleData completeAct : completeActData) {
                completeActs.add(new MapleQuestAction(MapleQuestActionType.getByWZName(completeAct.getName()), completeAct, this, false));
            }
        }
        MapleData questInfo = info.resolve(String.valueOf(id));
        autoStart = DataUtil.toInt(questInfo.resolve("autoStart"), 0) == 1;
        autoAccept = DataUtil.toInt(questInfo.resolve("autoAccept"), 0) == 1;
        autoComplete = DataUtil.toInt(questInfo.resolve("autoComplete"), 0) == 1;
        autoPreComplete = DataUtil.toInt(questInfo.resolve("autoPreComplete"), 0) == 1;
        medalItem = DataUtil.toInt(questInfo.resolve("viewMedalItem"), 0);
    }

    public static MapleQuest getInstance(int id) {
        MapleQuest ret = quests.get(id);
        if (ret == null) {
            try {
                ret = new MapleQuest(id);
            } catch (Exception e) {
                ret = new MapleCustomQuest(id);
            }
            quests.put(id, ret);
        }
        return ret;
    }

    public int getId() {
        return id;
    }

    public int getMedalId() {
        return medalItem;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public boolean isAutoComplete() {
        return autoPreComplete || autoComplete || autoAccept;
    }

    private boolean canStart(MapleCharacter c, Integer npcid) {
        if (!dayByDay && c.getQuest(id).getStatus() != Status.NOT_STARTED && !(c.getQuest(id).getStatus() == Status.COMPLETED && repeatable)) {
            return false;
        }
        for (MapleQuestRequirement r : startReqs) {
            if (!r.check(c, npcid)) {
                return false;
            }
        }
        return true;
    }

    public boolean canComplete(MapleCharacter c, Integer npcid) {
        if (!c.getQuest(id).getStatus().equals(Status.STARTED)) {
            return false;
        }
        if (completeReqs != null) {
            for (MapleQuestRequirement r : completeReqs) {
                if (!r.check(c, npcid)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void start(MapleCharacter c, int npc) {
        start(c, npc, false);
    }

    public void start(MapleCharacter c, int npc, boolean force) {
        if (id == 7103) {
            c.dropMessage("This quest is disabled");
            return;
        }
        if (force && checkNPCOnMap(c, npc) || (autoStart || checkNPCOnMap(c, npc)) && canStart(c, npc)) {
            for (MapleQuestAction a : startActs) {
                final byte status = a.check(c, null);
                if (status != 0) {
                    c.getClient().sendPacket(MaplePacketCreator.updateQuestInfo((short) id, true, status));
                    return;
                }
            }
            for (MapleQuestAction a : startActs) {
                a.run(c, null, npc);
            }
            MapleQuestStatus oldStatus = c.getQuest(id);
            MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.STARTED, c.getQuest(id).getQuestRecord(), npc);
            newStatus.setCompletionTime(oldStatus.getCompletionTime());
            c.updateQuest(newStatus, false, false, false);
        }
        if (autoPreComplete) {
            complete(c, npc, null, force, true);
        }
    }

    public void setQuestInfo(MapleCharacter c, String info, boolean update, boolean silent) {
        c.updateQuest(new MapleQuestStatus(id, MapleQuestStatus.Status.STARTED, info, c.getQuest(id).getNpc()), update, false, silent);
    }

    public void setQuestRecordExInfo(MapleCharacter c, String info) {
        c.updateQuest(new MapleQuestStatus(id, MapleQuestStatus.Status.STARTED, info, c.getQuest(id).getNpc()), false, true, false, false);
    }

    public void recoverItem(MapleCharacter c, int itemId) {
        for (MapleQuestAction a : startActs) {
            final byte status = a.check(c, null);
            if (status != 0) {
                c.getClient().sendPacket(MaplePacketCreator.updateQuestInfo((short) id, true, status));
                return;
            }
        }
        for (MapleQuestAction a : startActs) {
            a.recoverItem(c, itemId);
        }
    }

    public void complete(MapleCharacter c, int npc) {
        complete(c, npc, null, false, true);
    }

    public void complete(MapleCharacter c, int npc, boolean force, boolean showAnimation) {
        complete(c, npc, null, force, showAnimation);
    }

    public void complete(MapleCharacter c, int npc, Integer selection, boolean force, boolean showAnimation) {
        if (id == 7103) {
            c.dropMessage("This quest is disabled");
            return;
        }
        if (force || (autoPreComplete || autoComplete || checkNPCOnMap(c, npc)) && canComplete(c, npc)) {
            for (MapleQuestAction a : completeActs) {
                final byte status = a.check(c, selection);
                if (status != 0) {
                    c.getClient().sendPacket(MaplePacketCreator.updateQuestInfo((short) id, true, status));
                    return;
                }
            }
            for (MapleQuestAction a : completeActs) {
                a.run(c, selection, npc);
            }
            MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.COMPLETED, c.getQuest(id).getQuestRecord(), npc);
            c.updateQuest(newStatus, false, showAnimation, false);
            if (id == 6303) {
                MapleQuest.getInstance(6304).start(c, npc, true);
            }
        }
    }

    public void silentCompleteQuest(MapleCharacter c, int npc, boolean force) {
        if (force || (autoPreComplete || autoAccept || checkNPCOnMap(c, npc)) && canComplete(c, npc)) {
            MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.COMPLETED, c.getQuest(id).getQuestRecord(), npc);
            c.completeQuestOnly(newStatus);
        }
    }

    public void completeCustomQuest(MapleCharacter c, int npc) {
        MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.COMPLETED, c.getQuest(id).getQuestRecord(), npc);
        c.updateQuest(newStatus, false, false, false);
    }

    public void completeCustomQuestEx(MapleCharacter c, int npc) {
        MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.COMPLETED, c.getQuest(id).getQuestRecord(), npc);
        c.updateQuest(newStatus, false, true, false, false);
    }

    public void forfeit(MapleCharacter c) {
        if (!c.getQuest(id).getStatus().equals(Status.STARTED)) {
            return;
        }
        MapleQuestStatus oldStatus = c.getQuest(id);
        MapleQuestStatus newStatus = new MapleQuestStatus(id, MapleQuestStatus.Status.NOT_STARTED, "");
        newStatus.setCompletionTime(oldStatus.getCompletionTime());
        c.updateQuest(newStatus, false, false, false);
    }

    public Map<Integer, Integer> getQuestMobs() {
        return Collections.unmodifiableMap(questMobs);
    }

    public Map<Integer, Integer> getRequiredItems() {
        return Collections.unmodifiableMap(questItems);
    }

    private boolean checkNPCOnMap(MapleCharacter player, int npcid) {
        return player.getMap().containsNPC(npcid);
    }
}