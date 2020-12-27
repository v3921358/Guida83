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

import guida.client.ISkill;
import guida.client.InventoryException;
import guida.client.MapleCharacter;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MapleQuestStatus;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.client.messages.ServernoticeMapleClientMessageCallback;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.Randomizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleQuestAction {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleQuestAction.class);
    private final MapleQuestActionType type;
    private final MapleData data;
    private final MapleQuest quest;
    private final boolean start;

    /**
     * Creates a new instance of MapleQuestAction
     */
    public MapleQuestAction(MapleQuestActionType type, MapleData data, MapleQuest quest, boolean start) {
        this.type = type;
        this.data = data;
        this.quest = quest;
        this.start = start;
    }

    public byte check(MapleCharacter c, Integer extSelection) {
        switch (type) {
            case MESO -> {
                final int mesos = DataUtil.toInt(data);
                if (mesos > 0) {
                    if (c.getMeso() + mesos < 0) {
                        return 10;
                    }
                } else {
                    if (c.getMeso() < Math.abs(mesos)) {
                        return 11;
                    }
                }
            }
            case ITEM -> {
                final Map<Integer, Integer> props = new HashMap<>();
                for (MapleData iEntry : data) {
                    if (iEntry.getChild("prop") != null && DataUtil.toInt(iEntry.getChild("prop")) != -1 && canGetItem(iEntry, c)) {
                        for (int i = 0; i < DataUtil.toInt(iEntry.getChild("prop")); i++) {
                            props.put(props.size(), DataUtil.toInt(iEntry.getChild("id")));
                        }
                    }
                }
                int selection = 0;
                int extNum = 0;
                if (!props.isEmpty()) {
                    selection = props.get(Randomizer.nextInt(props.size()));
                }
                final int[] items = new int[data.getChildCount() * 2];
                int i = 0;
                for (MapleData iEntry : data) {
                    if (!canGetItem(iEntry, c)) {
                        continue;
                    }
                    if (iEntry.getChild("prop") != null) {
                        if (DataUtil.toInt(iEntry.getChild("prop")) == -1) {
                            if (extSelection != extNum++) {
                                continue;
                            }
                        } else if (DataUtil.toInt(iEntry.getChild("id")) != selection) {
                            continue;
                        }
                    }
                    final int quantity = DataUtil.toInt(iEntry.getChild("count"), 0);
                    if (quantity > 0) {
                        final int itemId = DataUtil.toInt(iEntry.getChild("id"));
                        if (MapleItemInformationProvider.getInstance().canHaveOnlyOne(itemId) && c.haveItem(itemId, 1, true, false)) {
                            return 14;
                        }
                        items[i] = DataUtil.toInt(iEntry.getChild("id"));
                        items[i + 1] = quantity;
                        i += 2;
                    }
                }
                if (!MapleInventoryManipulator.canHold(c.getClient(), items)) {
                    return 10;
                }
            }
        }
        return 0;
    }

    private boolean canGetItem(MapleData item, MapleCharacter c) {
        if (item.getChild("gender") != null) {
            final int gender = DataUtil.toInt(item.getChild("gender"));
            if (gender != 2 && gender != c.getGender()) {
                return false;
            }
        }
        if (item.getChild("job") != null) {
            final int job = DataUtil.toInt(item.getChild("job"));
            final int identifier = MapleJob.getIdentifier(c.getJob());
            return job < 100 && (job & identifier) == identifier || (job & c.getJob().getId()) == 1024 && c.getJob().isA(MapleJob.NOBLESSE) ||
                    (job & c.getJob().getId()) == identifier || (job & MapleJob.getBaseJob(c.getJob()).getId()) == identifier;
        }
        return true;
    }

    public void run(MapleCharacter c, Integer extSelection, int npcId) {
        switch (type) {
            case INFO:
                MapleQuest.getInstance(quest.getId()).setQuestInfo(c, DataUtil.toString(data), false, true);
                break;
            case EXP:
                int exp = DataUtil.toInt(data);
                if (c.getLevel() > 9 || c.getJob().getId() != 0 && c.getJob().getId() != 1000 && c.getJob().getId() != 2000) {
                    exp *= c.getClient().getChannelServer().getExpRate();
                }
                c.gainExp(exp, true, true);
                break;
            case ITEM:
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                // first check for randomness in item selection
                final Map<Integer, Integer> props = new HashMap<>();
                for (MapleData iEntry : data) {
                    if (iEntry.getChild("prop") != null && DataUtil.toInt(iEntry.getChild("prop")) != -1 && canGetItem(iEntry, c)) {
                        for (int i = 0; i < DataUtil.toInt(iEntry.getChild("prop")); i++) {
                            props.put(props.size(), DataUtil.toInt(iEntry.getChild("id")));
                        }
                    }
                }
                int selection = 0;
                int extNum = 0;
                if (!props.isEmpty()) {
                    selection = props.get(Randomizer.nextInt(props.size()));
                }
                final List<Pair<Integer, Integer>> itemAmount = new ArrayList<>();
                for (MapleData iEntry : data) {
                    if (DataUtil.toInt(iEntry.getChild("count"), 0) < 0) { // remove items
                        int itemId = DataUtil.toInt(iEntry.getChild("id"));
                        MapleInventoryType iType = ii.getInventoryType(itemId);
                        short quantity = (short) (DataUtil.toInt(iEntry.getChild("count"), 0) * -1);
                        try {
                            MapleInventoryManipulator.removeById(c.getClient(), iType, itemId, quantity, true, false);
                        } catch (InventoryException ie) {
                            log.warn("[h4x] Completing a quest without meeting the requirements", ie);
                        }
                        itemAmount.add(new Pair<>(itemId, DataUtil.toInt(iEntry.getChild("count"), 0)));
                    } else if (DataUtil.toInt(iEntry.getChild("count"), 0) == 0) { // remove all items
                        int itemId = DataUtil.toInt(iEntry.getChild("id"));
                        MapleInventoryType iType = ii.getInventoryType(itemId);
                        short quantity = (short) c.getItemQuantity(itemId);
                        try {
                            MapleInventoryManipulator.removeById(c.getClient(), iType, itemId, quantity, true, false);
                        } catch (InventoryException ie) {
                            // it's better to catch this here so we'll at least try to remove the other items
                            log.warn("[h4x] Completing a quest without meeting the requirements", ie);
                        }
                        itemAmount.add(new Pair<>(itemId, -quantity));
                    }
                }
                for (MapleData iEntry : data) {
                    if (!canGetItem(iEntry, c)) {
                        continue;
                    }
                    if (iEntry.getChild("prop") != null) {
                        if (DataUtil.toInt(iEntry.getChild("prop")) == -1) {
                            if (extSelection != extNum++) {
                                continue;
                            }
                        } else if (DataUtil.toInt(iEntry.getChild("id")) != selection) {
                            continue;
                        }
                    }
                    if (DataUtil.toInt(iEntry.getChild("count"), 0) > 0) {
                        String owner = null;
                        int itemId = DataUtil.toInt(iEntry.getChild("id"));
                        if (iEntry.getChild("name") != null) {
                            owner = c.getName();
                        }
                        int quantity = DataUtil.toInt(iEntry.getChild("count"), 0) - (start ? c.getItemQuantity(itemId) : 0);
                        if (quantity > 0) {
                            MapleInventoryManipulator.addById(c.getClient(), itemId, (short) quantity, c.getName() + " received " + quantity + " from a quest", owner, null);
                            itemAmount.add(new Pair<>(itemId, quantity));
                        }
                    }
                }
                if (!itemAmount.isEmpty()) {
                    c.getClient().sendPacket(MaplePacketCreator.getShowItemGain(itemAmount));
                }
                break;
            case NEXTQUEST:
                if (DataUtil.toInt(data) != quest.getId()) {
                    c.getClient().sendPacket(MaplePacketCreator.updateQuestInfo((short) quest.getId(), false, npcId, (byte) 8, DataUtil.toInt(data), false));
                }
                break;
            case MESO:
                c.gainMeso(DataUtil.toInt(data), true, false, true);
                break;
            case QUEST:
                for (MapleData qEntry : data) {
                    short questid = (short) DataUtil.toInt(qEntry.getChild("id"));
                    int stat = DataUtil.toInt(qEntry.getChild("state"), 0);
                    c.updateQuest(new MapleQuestStatus(questid, MapleQuestStatus.Status.getById(stat), ""), false, false, false);
                }
                break;
            case SKILL:
                for (MapleData sEntry : data) {
                    int skillid = DataUtil.toInt(sEntry.getChild("id"));
                    int skillLevel = DataUtil.toInt(sEntry.getChild("skillLevel"), 0);
                    int masterLevel = DataUtil.toInt(sEntry.getChild("masterLevel"));
                    ISkill skillObject = SkillFactory.getSkill(skillid);
                    if (skillObject == null) {
                        continue;
                    }
                    boolean shouldLearn = false;
                    MapleData applicableJobs = sEntry.getChild("job");
                    if (applicableJobs != null) {
                        for (MapleData applicableJob : applicableJobs) {
                            MapleJob job = MapleJob.getById(DataUtil.toInt(applicableJob));
                            if (c.getJob() == job) {
                                shouldLearn = true;
                                break;
                            }
                        }
                    }
                    if (skillObject.isBeginnerSkill()) {
                        shouldLearn = true;
                    }
                    skillLevel = Math.max(skillLevel, c.getSkillLevel(skillObject));
                    masterLevel = Math.max(masterLevel, c.getMasterLevel(skillObject));
                    if (shouldLearn) {
                        c.changeSkillLevel(skillObject, skillLevel, masterLevel);
                        new ServernoticeMapleClientMessageCallback(5, c.getClient()).dropMessage("You have learned " + SkillFactory.getSkillName(skillid) + " with level " + skillLevel + " and with max level " + masterLevel);
                    }
                }
                break;
            case FAME:
                c.addFame(DataUtil.toInt(data));
                c.updateSingleStat(MapleStat.FAME, c.getFame());
                int fameGain = DataUtil.toInt(data);
                c.getClient().sendPacket(MaplePacketCreator.getShowFameGain(fameGain));
                if (c.getFame() >= 50) {
                    c.finishAchievement(9);
                }
                break;
            case BUFF:
                MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
                mii.getItemEffect(DataUtil.toInt(data)).applyTo(c);
                break;
            case NPC_ACT:
                int objectId = c.getMap().getNPCbyID(npcId);
                c.getMap().broadcastMessage(MaplePacketCreator.npcAnimation(objectId, DataUtil.toString(data)));
                break;
        }
    }

    public void recoverItem(MapleCharacter c, int itemId) {
        switch (type) {
            case ITEM -> {
                final List<Pair<Integer, Integer>> itemAmount = new ArrayList<>();
                for (MapleData iEntry : data) {
                    if (DataUtil.toInt(iEntry.getChild("count"), 0) > 0) {
                        String owner = null;
                        if (itemId == DataUtil.toInt(iEntry.getChild("id"))) {
                            if (iEntry.getChild("name") != null) {
                                owner = c.getName();
                            }
                            short quantity = (short) (DataUtil.toInt(iEntry.getChild("count")) - c.getItemQuantity(itemId));
                            if (quantity != 0) {
                                MapleInventoryManipulator.addById(c.getClient(), itemId, quantity, c.getName() + " received " + quantity + " from a quest", owner, null);
                                itemAmount.add(new Pair<>(itemId, (int) quantity));
                            }
                        }
                        break;
                    }
                }
                if (!itemAmount.isEmpty()) {
                    c.getClient().sendPacket(MaplePacketCreator.getShowItemGain(itemAmount));
                }
            }
        }
    }

    public MapleQuestActionType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + ": " + data;
    }
}