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

package guida.server.life;

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;
import guida.tools.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MapleLifeFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleLifeFactory.class);
    private static final MapleDataProvider data = MapleDataProviderFactory.getDataProvider("Mob");
    private static final MapleDataProvider stringDataWZ = MapleDataProviderFactory.getDataProvider("String");
    private static final MapleData mobStringData = stringDataWZ.getData("Mob.img");
    private static final MapleData npcStringData = stringDataWZ.getData("Npc.img");
    private static final Map<Integer, MapleMonsterStats> monsterStats = new HashMap<>();
    private static final Map<Integer, Map<String, String>> npcVariables = new HashMap<>();
    private static final Map<Integer, List<Integer>> questCountGroups = new HashMap<>();

    public static AbstractLoadedMapleLife getLife(int id, String type) {
        if (type.equalsIgnoreCase("n")) {
            return getNPC(id);
        } else if (type.equalsIgnoreCase("m")) {
            return getMonster(id);
        } else {
            log.warn("Unknown Life type: {}", type);
            return null;
        }
    }

    public static List<Integer> getQuestCountGroup(int id) {
        if (questCountGroups.get(id) != null) {
            return questCountGroups.get(id);
        }
        List<Integer> mobIds = new ArrayList<>();
        MapleData monsterData = data.getData("QuestCountGroup/" + StringUtil.getLeftPaddedStr(id + ".img", '0', 11));
        for (MapleData child : monsterData.getChild("info")) {
            mobIds.add(DataUtil.toInt(child));
        }
        questCountGroups.put(id, mobIds);
        return mobIds;
    }

    public static MapleMonster getMonster(int mid) {
        MapleMonsterStats stats = monsterStats.get(mid);
        if (stats == null) {
            MapleData monsterData = data.getData(StringUtil.getLeftPaddedStr(mid + ".img", '0', 11));
            if (monsterData == null) {
                return null;
            }
            MapleData monsterInfoData = monsterData.getChild("info");
            stats = new MapleMonsterStats();
            stats.setHp(DataUtil.toInt(monsterInfoData.resolve("maxHP")));
            stats.setMp(DataUtil.toInt(monsterInfoData.resolve("maxMP"), 0));
            stats.setExp(DataUtil.toInt(monsterInfoData.resolve("exp"), 0));
            stats.setLevel(DataUtil.toInt(monsterInfoData.resolve("level")));
            stats.setRemoveAfter(DataUtil.toInt(monsterInfoData.resolve("removeAfter"), 0));
            stats.setBoss(DataUtil.toInt(monsterInfoData.resolve("boss"), 0) > 0);
            stats.setFfaLoot(mid == 9400608 || DataUtil.toInt(monsterInfoData.resolve("publicReward"), 0) > 0);
            stats.setUndead(DataUtil.toInt(monsterInfoData.resolve("undead"), 0) > 0);
            stats.setName(DataUtil.toString(mobStringData.resolve(mid + "/name"), "MISSINGNO"));
            int def1 = -1;
            stats.setBuffToGive(DataUtil.toInt(monsterInfoData.resolve("buff"), def1));
            stats.setExplosive(DataUtil.toInt(monsterInfoData.resolve("explosiveReward"), 0) > 0);
            MapleData firstAttackData = monsterInfoData.getChild("firstAttack");
            int firstAttack = 0;
            if (firstAttackData != null) {
                // TODO: Decouple this from NX/etc
                if (firstAttackData.getData() instanceof Double) {
                    firstAttack = Math.round(((Double) firstAttackData.getData()).floatValue());
                } else {
                    firstAttack = DataUtil.toInt(firstAttackData);
                }
            }
            stats.setFirstAttack(firstAttack > 0);
            stats.setNoRegen(DataUtil.toInt(monsterInfoData.resolve("noregen"), 0) > 0);
            MapleData banData = monsterInfoData.getChild("ban");
            if (banData != null) {
                String message = DataUtil.toString(banData.resolve("banMsg"));
                int def = -1;
                int mapid = DataUtil.toInt(banData.resolve("banMap/0/field"), def);
                String portal = DataUtil.toString(banData.resolve("banMap/0/portal"), "sp");
                stats.setBanInfo(new MapleMonsterBanInfo(message, mapid, portal));
            }
            if (stats.isBoss() || mid == 8810018) {
                MapleData hpTagColor = monsterInfoData.getChild("hpTagColor");
                MapleData hpTagBgColor = monsterInfoData.getChild("hpTagBgcolor");
                if (hpTagBgColor == null || hpTagColor == null) {
                    log.trace("Monster " + stats.getName() + " (" + mid + ") flagged as boss without boss HP bars.");
                    stats.setTagColor(0);
                    stats.setTagBgColor(0);
                } else {
                    stats.setTagColor(DataUtil.toInt(monsterInfoData.resolve("hpTagColor")));
                    stats.setTagBgColor(DataUtil.toInt(monsterInfoData.resolve("hpTagBgcolor")));
                }
            }

            for (MapleData idata : monsterData) {
                if (!idata.getName().equals("info")) {
                    int delay = 0;
                    for (MapleData pic : idata) {
                        delay += DataUtil.toInt(pic.resolve("delay"), 0);
                    }
                    stats.setAnimationTime(idata.getName(), delay);
                }
                if (idata.getName().equals("fly")) {
                    stats.setFly(true);
                    stats.setMobile(true);
                } else if (idata.getName().equals("move")) {
                    stats.setMobile(true);
                }
            }

            MapleData reviveInfo = monsterInfoData.getChild("revive");
            if (reviveInfo != null) {
                List<Integer> revives = new LinkedList<>();
                for (MapleData data_ : reviveInfo) {
                    revives.add(DataUtil.toInt(data_));
                }
                stats.setRevives(revives);
            }
            decodeElementalString(stats, DataUtil.toString(monsterInfoData.resolve("elemAttr"), ""));

            MapleData monsterSkillData = monsterInfoData.getChild("skill");
            if (monsterSkillData != null) {
                int i = 0;
                List<Pair<Integer, Integer>> skills = new ArrayList<>();
                while (monsterSkillData.getChild(Integer.toString(i)) != null) {
                    skills.add(new Pair<>(DataUtil.toInt(monsterSkillData.resolve(i + "/skill"), 0), DataUtil.toInt(monsterSkillData.resolve(i + "/level"), 0)));
                    i++;
                }
                stats.setSkills(skills);
            }

            monsterStats.put(mid, stats);
        }
        return new MapleMonster(mid, stats);
    }

    public static void decodeElementalString(MapleMonsterStats stats, String elemAttr) {
        for (int i = 0; i < elemAttr.length(); i += 2) {
            Element e = Element.getFromChar(elemAttr.charAt(i));
            ElementalEffectiveness ee = ElementalEffectiveness.getByNumber(Integer.parseInt(String.valueOf(elemAttr.charAt(i + 1))));
            stats.setEffectiveness(e, ee);
        }
    }

    public static MapleNPC getNPC(int nid) {
        return new MapleNPC(nid, new MapleNPCStats(DataUtil.toString(npcStringData.resolve(nid + "/name"), "MISSINGNO")));
    }

    public static void addNPCVar(int id, String name, String var) {
        Map<String, String> ret = npcVariables.get(id);
        if (ret == null) {
            ret = new HashMap<>();
            ret.put(name, var);
        }
        npcVariables.put(id, ret);
    }

    public static String getNPCVar(int id, String name) {
        Map<String, String> ret = npcVariables.get(id);
        if (ret != null && ret.containsKey(name)) {
            return ret.get(name);
        }
        return "";
    }
}