
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

package guida.net.login;

import guida.client.Equip;
import guida.client.IItem;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Xterminator
 */
public class CharCreationInformationProvider {

    private static CharCreationInformationProvider instance = null;
    private final Map<Integer, Map<String, Integer>> itemStatsCache = new HashMap<>();
    private final Map<Integer, Equip> equipCache = new HashMap<>();
    private final Map<Integer, List<Integer>> validMaleBeginnerStats = new HashMap<>();
    private final Map<Integer, List<Integer>> validFemaleBeginnerStats = new HashMap<>();
    private final Map<Integer, List<Integer>> validMaleCygnusStats = new HashMap<>();
    private final Map<Integer, List<Integer>> validFemaleCygnusStats = new HashMap<>();
    private final Map<Integer, List<Integer>> validMaleAranStats = new HashMap<>();
    private final Map<Integer, List<Integer>> validFemaleAranStats = new HashMap<>();
    private MapleDataProvider itemData;
    private MapleDataProvider equipData;
    //private final Map<Integer, List<Integer>> validMaleEvanStats = new HashMap<Integer, List<Integer>>();
    //private final Map<Integer, List<Integer>> validFemaleEvanStats = new HashMap<Integer, List<Integer>>();

    protected CharCreationInformationProvider() {
        itemData = MapleDataProviderFactory.getDataProvider("Item");
        equipData = MapleDataProviderFactory.getDataProvider("Character");
    }

    public static CharCreationInformationProvider getInstance() {
        if (instance == null) {
            instance = new CharCreationInformationProvider();
        }
        return instance;
    }

    private MapleData getItemData(int itemId) {
        MapleData ret = null;
        final String idStr = "0" + itemId;
        MapleData root = itemData.getRoot();
        for (MapleData topDir : root) {
            for (MapleData iFile : topDir) {
                if (iFile.getName().equals(idStr.substring(0, 4) + ".img")) {
                    ret = itemData.getData(topDir.getName() + "/" + iFile.getName());
                    if (ret == null) {
                        return null;
                    }
                    ret = ret.resolve(idStr);
                    return ret;
                } else if (iFile.getName().equals(idStr.substring(1) + ".img")) {
                    return itemData.getData(topDir.getName() + "/" + iFile.getName());
                }
            }
        }
        root = equipData.getRoot();
        for (MapleData topDir : root) {
            for (MapleData iFile : topDir) {
                if (iFile.getName().equals(idStr + ".img")) {
                    return equipData.getData(topDir.getName() + "/" + iFile.getName());
                }
            }
        }
        return null;
    }

    private Map<String, Integer> getItemStats(int itemId) {
        if (itemStatsCache.containsKey(itemId)) {
            return itemStatsCache.get(itemId);
        }
        final Map<String, Integer> ret = new LinkedHashMap<>();
        final MapleData item = getItemData(itemId);
        if (item == null) {
            return ret;
        }
        final MapleData info = item.getChild("info");
        if (info == null) {
            return ret;
        }
        for (MapleData data : info) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), DataUtil.toInt(data));
            }
        }
        ret.put("tuc", DataUtil.toInt(info.resolve("tuc"), 0));
        itemStatsCache.put(itemId, ret);
        return ret;
    }

    public IItem getEquipById(int equipId) {
        if (equipCache.containsKey(equipId)) {
            return equipCache.get(equipId).copy();
        }
        Equip nEquip = new Equip(equipId, (short) 0, -1);
        nEquip.setQuantity((short) 1);
        final Map<String, Integer> stats = getItemStats(equipId);
        if (stats != null) {
            for (Entry<String, Integer> stat : stats.entrySet()) {
                switch (stat.getKey()) {
                    case "STR" -> nEquip.setStr(stat.getValue().shortValue());
                    case "DEX" -> nEquip.setDex(stat.getValue().shortValue());
                    case "INT" -> nEquip.setInt(stat.getValue().shortValue());
                    case "LUK" -> nEquip.setLuk(stat.getValue().shortValue());
                    case "PAD" -> nEquip.setWatk(stat.getValue().shortValue());
                    case "PDD" -> nEquip.setWdef(stat.getValue().shortValue());
                    case "MAD" -> nEquip.setMatk(stat.getValue().shortValue());
                    case "MDD" -> nEquip.setMdef(stat.getValue().shortValue());
                    case "ACC" -> nEquip.setAcc(stat.getValue().shortValue());
                    case "EVA" -> nEquip.setAvoid(stat.getValue().shortValue());
                    case "Speed" -> nEquip.setSpeed(stat.getValue().shortValue());
                    case "Jump" -> nEquip.setJump(stat.getValue().shortValue());
                    case "MHP" -> nEquip.setHp(stat.getValue().shortValue());
                    case "MMP" -> nEquip.setMp(stat.getValue().shortValue());
                    case "tuc" -> nEquip.setUpgradeSlots(stat.getValue().byteValue());
                }
            }
        }
        equipCache.put(equipId, nEquip);
        return nEquip.copy();
    }

    public void loadValidValues() {
        MapleData charCreationInfo = MapleDataProviderFactory.getDataProvider("Etc").getData("MakeCharInfo.img");
        ArrayList<Integer> values = new ArrayList<>();
        for (MapleData child : charCreationInfo.resolve("Info/CharMale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validMaleBeginnerStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        for (MapleData child : charCreationInfo.resolve("Info/CharFemale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validFemaleBeginnerStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        for (MapleData child : charCreationInfo.getChild("PremiumCharMale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validMaleCygnusStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        for (MapleData child : charCreationInfo.getChild("PremiumCharFemale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validFemaleCygnusStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        for (MapleData child : charCreationInfo.getChild("OrientCharMale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validMaleAranStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        for (MapleData child : charCreationInfo.getChild("OrientCharFemale")) {
            int i = Integer.parseInt(child.getName());
            for (MapleData childs : child) {
                int value = DataUtil.toInt(childs);
                values.add(value);
                if (i > 3) {
                    getInstance().getEquipById(value);
                }
            }
            validFemaleAranStats.put(i, new ArrayList<>(values));
            values.clear();
        }
        /*for (MapleData child : charCreationInfo.getChildByName("EvanCharMale")) {
			int i = Integer.valueOf(child.getName());
			for (MapleData childs : child) {
				int value = MapleDataTool.getInt(childs);
				values.add(value);
				if (i > 3)
					getInstance().getEquipById(value);
			}
			validMaleEvanStats.put(i, new ArrayList<Integer>(values));
			values.clear();
		}
		for (MapleData child : charCreationInfo.getChildByName("EvanCharFemale")) {
			int i = Integer.valueOf(child.getName());
			for (MapleData childs : child) {
				int value = MapleDataTool.getInt(childs);
				values.add(value);
				if (i > 3)
					getInstance().getEquipById(value);
			}
			validFemaleEvanStats.put(i, new ArrayList<Integer>(values));
			values.clear();
		}*/
        charCreationInfo = null;
        dispose();
    }

    public boolean validateStats(int job, int gender, int[] stats) {
        Map<Integer, List<Integer>> map = null;
        switch (job) {
            case 0:
                map = gender == 0 ? validMaleCygnusStats : validFemaleCygnusStats;
                break;
            case 1:
                map = gender == 0 ? validMaleBeginnerStats : validFemaleBeginnerStats;
                break;
            case 2:
                map = gender == 0 ? validMaleAranStats : validFemaleAranStats;
                break;
            //case 3:
            //	map = gender == 0 ? validMaleEvanStats : validFemaleEvanStats;
            //	break;
            default:
                return false;
        }
        for (int i = 0; i < stats.length; i++) {
            if (!map.get(i).contains(stats[i])) {
                return false;
            }
        }
        return true;
    }

    private void dispose() {
        itemData = null;
        equipData = null;
        itemStatsCache.clear();
    }
}