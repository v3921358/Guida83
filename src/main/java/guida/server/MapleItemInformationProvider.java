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

package guida.server;

import guida.client.Equip;
import guida.client.IEquip;
import guida.client.IEquip.ScrollResult;
import guida.client.IItem;
import guida.client.ItemFlag;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MapleWeaponType;
import guida.client.SkillFactory;
import guida.net.channel.handler.FishingHandler.MapleFish;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Matze
 * <p/>
 * TODO: make faster
 */
public class MapleItemInformationProvider {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleItemInformationProvider.class);
    private static MapleItemInformationProvider instance = null;
    protected final MapleDataProvider itemData;
    protected final MapleDataProvider equipData;
    protected final MapleDataProvider stringData;
    protected final MapleDataProvider effectData;
    protected final MapleData cashStringData;
    protected final MapleData consumeStringData;
    protected final MapleData eqpStringData;
    protected final MapleData etcStringData;
    protected final MapleData insStringData;
    protected final MapleData petStringData;
    protected final Map<Integer, Short> slotMaxCache = new HashMap<>();
    protected final Map<Integer, MapleStatEffect> itemEffects = new HashMap<>();
    protected final Map<Integer, Map<String, Integer>> itemStatsCache = new HashMap<>();
    protected final Map<Integer, Map<String, Integer>> scrollStatsCache = new HashMap<>();
    protected final Map<Integer, Map<Integer, ItemLevelInfo>> itemLevelCache = new HashMap<>();
    protected final Map<Integer, Map<String, Integer>> crystalProperties = new HashMap<>();
    protected final Map<Integer, Equip> equipCache = new HashMap<>();
    protected final Map<Integer, Double> priceCache = new HashMap<>();
    protected final Map<Integer, Integer> wholePriceCache = new HashMap<>();
    protected final Map<Integer, Integer> projectileWatkCache = new HashMap<>();
    protected final Map<Integer, String> nameCache = new HashMap<>();
    protected final Map<Integer, String> descCache = new HashMap<>();
    protected final Map<Integer, String> msgCache = new HashMap<>();
    protected final Map<Integer, Boolean> dropRestrictionCache = new HashMap<>();
    protected final Map<Integer, Boolean> pickupRestrictionCache = new HashMap<>();
    protected final Map<Integer, Boolean> consumeOnPickup = new HashMap<>();
    protected final Map<Integer, Boolean> isQuestItemCache = new HashMap<>();
    protected final Map<Integer, List<SummonEntry>> summonEntryCache = new HashMap<>();
    protected final List<Pair<Integer, String>> itemNameCache = new LinkedList<>();
    protected final Map<Integer, Integer> getMesoCache = new HashMap<>();
    protected final Map<Integer, Integer> getExpCache = new HashMap<>();
    protected final Map<Integer, String> itemTypeCache = new HashMap<>();
    protected final List<Pair<Integer, Integer>> monsterBook = new ArrayList<>();
    protected final Map<Integer, List<MapleFish>> fishingCache = new HashMap<>();
    protected final Map<Integer, Integer> scriptedItemCache = new HashMap<>();
    protected final Map<String, Integer> mapAutoChangeCache = new HashMap<>();

    /**
     * Creates a new instance of MapleItemInformationProvider
     */
    protected MapleItemInformationProvider() {
        itemData = MapleDataProviderFactory.getDataProvider("Item");
        equipData = MapleDataProviderFactory.getDataProvider("Character");
        stringData = MapleDataProviderFactory.getDataProvider("String");
        effectData = MapleDataProviderFactory.getDataProvider("Effect");
        cashStringData = stringData.getData("Cash.img");
        consumeStringData = stringData.getData("Consume.img");
        eqpStringData = stringData.getData("Eqp.img");
        etcStringData = stringData.getData("Etc.img");
        insStringData = stringData.getData("Ins.img");
        petStringData = stringData.getData("Pet.img");
        loadCardIdData();
    }

    public static MapleItemInformationProvider getInstance() {
        if (instance == null) {
            instance = new MapleItemInformationProvider();
        }
        return instance;
    }

    /* returns the inventory type for the specified item id */
    public MapleInventoryType getInventoryType(int itemId) {
        MapleInventoryType ret = MapleInventoryType.getByType((byte) (itemId / 1000000));
        return ret != null ? ret : MapleInventoryType.UNDEFINED;
    }

    public List<Pair<Integer, String>> getAllItems() {
        if (!itemNameCache.isEmpty()) {
            return itemNameCache;
        }
        List<Pair<Integer, String>> itemPairs = new ArrayList<>();
        MapleData itemsData;

        itemsData = stringData.getData("Cash.img");
        for (MapleData itemFolder : itemsData) {
            int itemId = Integer.parseInt(itemFolder.getName());
            String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
            itemPairs.add(new Pair<>(itemId, itemName));
        }

        itemsData = stringData.getData("Consume.img");
        for (MapleData itemFolder : itemsData) {
            int itemId = Integer.parseInt(itemFolder.getName());
            String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
            itemPairs.add(new Pair<>(itemId, itemName));
        }

        itemsData = stringData.getData("Eqp.img").getChild("Eqp");
        for (MapleData eqpType : itemsData) {
            for (MapleData itemFolder : eqpType) {
                int itemId = Integer.parseInt(itemFolder.getName());
                String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
                itemPairs.add(new Pair<>(itemId, itemName));
            }
        }

        itemsData = stringData.getData("Etc.img").getChild("Etc");
        for (MapleData itemFolder : itemsData) {
            int itemId = Integer.parseInt(itemFolder.getName());
            String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
            itemPairs.add(new Pair<>(itemId, itemName));
        }

        itemsData = stringData.getData("Ins.img");
        for (MapleData itemFolder : itemsData) {
            int itemId = Integer.parseInt(itemFolder.getName());
            String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
            itemPairs.add(new Pair<>(itemId, itemName));
        }

        itemsData = stringData.getData("Pet.img");
        for (MapleData itemFolder : itemsData) {
            int itemId = Integer.parseInt(itemFolder.getName());
            String itemName = DataUtil.toString(itemFolder.resolve("name"), "NO-NAME");
            itemPairs.add(new Pair<>(itemId, itemName));
        }
        itemNameCache.addAll(itemPairs);
        return itemPairs;
    }

    protected MapleData getStringData(int itemId) {
        String cat = "null";
        MapleData theData;
        if (itemId >= 5010000) {
            theData = cashStringData;
        } else if (itemId >= 2000000 && itemId < 3000000) {
            theData = consumeStringData;
        } else if (itemId >= 1010000 && itemId < 1040000 || itemId >= 1122000 && itemId < 1143000) {
            theData = eqpStringData;
            cat = "Accessory";
        } else if (itemId >= 1000000 && itemId < 1010000) {
            theData = eqpStringData;
            cat = "Cap";
        } else if (itemId >= 1102000 && itemId < 1103000) {
            theData = eqpStringData;
            cat = "Cape";
        } else if (itemId >= 1040000 && itemId < 1050000) {
            theData = eqpStringData;
            cat = "Coat";
        } else if (itemId >= 20000 && itemId < 22000) {
            theData = eqpStringData;
            cat = "Face";
        } else if (itemId >= 1080000 && itemId < 1090000) {
            theData = eqpStringData;
            cat = "Glove";
        } else if (itemId >= 30000 && itemId < 32000) {
            theData = eqpStringData;
            cat = "Hair";
        } else if (itemId >= 1050000 && itemId < 1060000) {
            theData = eqpStringData;
            cat = "Longcoat";
        } else if (itemId >= 1060000 && itemId < 1070000) {
            theData = eqpStringData;
            cat = "Pants";
        } else if (itemId >= 1802000 && itemId < 1803000 || itemId >= 1812000 && itemId < 1813000 || itemId == 1822000 || itemId == 1832000) {
            theData = eqpStringData;
            cat = "PetEquip";
        } else if (itemId >= 1112000 && itemId < 1120000) {
            theData = eqpStringData;
            cat = "Ring";
        } else if (itemId >= 1092000 && itemId < 1100000) {
            theData = eqpStringData;
            cat = "Shield";
        } else if (itemId >= 1070000 && itemId < 1080000) {
            theData = eqpStringData;
            cat = "Shoes";
        } else if (itemId >= 1900000 && itemId < 1920000) {
            theData = eqpStringData;
            cat = "Taming";
        } else if (itemId >= 1300000 && itemId < 1800000) {
            theData = eqpStringData;
            cat = "Weapon";
        } else if (itemId >= 1940000 && itemId < 1980000) {
            theData = eqpStringData;
            cat = "Dragon";
        } else if (itemId >= 4000000 && itemId < 5000000) {
            theData = etcStringData;
        } else if (itemId >= 3000000 && itemId < 4000000) {
            theData = insStringData;
        } else if (itemId >= 5000000 && itemId < 5010000) {
            theData = petStringData;
        } else {
            return null;
        }
        if (cat.equals("null")) {
            if (theData == etcStringData) {
                return theData.resolve("Etc/" + itemId);
            } else {
                return theData.getChild(String.valueOf(itemId));
            }
        } else {
            if (theData == eqpStringData) {
                return theData.resolve("Eqp/" + cat + "/" + itemId);
            } else {
                return theData.resolve(cat + "/" + itemId);
            }
        }
    }

    protected MapleData getItemData(int itemId) {
        MapleData ret = null;
        String idStr = "0" + itemId;
        MapleData root = itemData.getRoot();
        for (MapleData topDir : root) {
            // we should have .img files here beginning with the first 4 IID
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

    /**
     * returns the maximum of items in one slot
     */
    public short getSlotMax(MapleClient c, int itemId) {
        if (slotMaxCache.containsKey(itemId)) {
            short slotMax = slotMaxCache.get(itemId);
            if (c != null) {
                if (isThrowingStar(itemId)) {
                    slotMax += c.getPlayer().getSkillLevel(SkillFactory.getSkill(4100000)) * 10;
                    slotMax += c.getPlayer().getSkillLevel(SkillFactory.getSkill(14100000)) * 10;
                } else if (isShootingBullet(itemId)) {
                    slotMax += c.getPlayer().getSkillLevel(SkillFactory.getSkill(5200000)) * 10;
                }
            }
            return slotMax;
        }
        short ret = 0;
        MapleData item = getItemData(itemId);
        if (item != null) {
            MapleData smEntry = item.resolve("info/slotMax");
            if (smEntry == null) {
                if (getInventoryType(itemId).equals(MapleInventoryType.EQUIP) || isPet(itemId)) {
                    ret = 1;
                } else {
                    ret = 100;
                }
            } else {
                ret = (short) Math.max(1, DataUtil.toInt(smEntry));
            }
        }
        slotMaxCache.put(itemId, ret);

        return ret;
    }

    public int getMeso(int itemId) {
        if (getMesoCache.containsKey(itemId)) {
            return getMesoCache.get(itemId);
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return -1;
        }
        int pEntry = 0;
        MapleData pData = item.resolve("info/meso");
        if (pData == null) {
            return -1;
        }
        pEntry = DataUtil.toInt(pData);

        getMesoCache.put(itemId, pEntry);
        return pEntry;
    }

    public int getWholePrice(int itemId) {
        if (wholePriceCache.containsKey(itemId)) {
            return wholePriceCache.get(itemId);
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return -1;
        }

        int pEntry = 0;
        MapleData pData = item.resolve("info/price");
        if (pData == null) {
            return -1;
        }
        pEntry = DataUtil.toInt(pData);

        wholePriceCache.put(itemId, pEntry);
        return pEntry;
    }

    public String getType(int itemId) {
        if (itemTypeCache.containsKey(itemId)) {
            return itemTypeCache.get(itemId);
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return "";
        }

        String pEntry;
        MapleData pData = item.resolve("info/islot");
        if (pData == null) {
            return "";
        }
        pEntry = DataUtil.toString(pData);

        itemTypeCache.put(itemId, pEntry);
        return pEntry;
    }

    public double getPrice(int itemId) {
        if (priceCache.containsKey(itemId)) {
            return priceCache.get(itemId);
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return -1;
        }

        double pEntry = 0.0;
        MapleData pData = item.resolve("info/unitPrice");
        if (pData != null) {
            try {
                pEntry = DataUtil.toDouble(pData);
            } catch (Exception e) {
                pEntry = DataUtil.toInt(pData);
            }
        } else {
            pData = item.resolve("info/price");
            if (pData == null) {
                return -1;
            }
            pEntry = DataUtil.toInt(pData);
        }

        priceCache.put(itemId, pEntry);
        return pEntry;
    }

    protected Map<String, Integer> getEquipStats(int itemId) {
        if (itemStatsCache.containsKey(itemId)) {
            return itemStatsCache.get(itemId);
        }
        Map<String, Integer> ret = new LinkedHashMap<>();
        MapleData item = getItemData(itemId);
        if (item == null) {
            return ret;
        }
        MapleData info = item.getChild("info");
        if (info == null) {
            return ret;
        }
        for (MapleData data : info) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), DataUtil.toInt(data));
            }
        }
        ret.put("tuc", DataUtil.toInt(info.resolve("tuc"), 0));
        ret.put("reqLevel", DataUtil.toInt(info.resolve("reqLevel"), 0));
        ret.put("reqJob", DataUtil.toInt(info.resolve("reqJob"), 0));
        ret.put("reqSTR", DataUtil.toInt(info.resolve("reqSTR"), 0));
        ret.put("reqDEX", DataUtil.toInt(info.resolve("reqDEX"), 0));
        ret.put("reqINT", DataUtil.toInt(info.resolve("reqINT"), 0));
        ret.put("reqLUK", DataUtil.toInt(info.resolve("reqLUK"), 0));
        ret.put("cash", DataUtil.toInt(info.resolve("cash"), 0));
        ret.put("expireOnLogout", DataUtil.toInt(info.resolve("expireOnLogout"), 0));
        ret.put("equipTradeBlock", DataUtil.toInt(info.resolve("equipTradeBlock"), 0));
        ret.put("tradeAvailable", DataUtil.toInt(info.resolve("tradeAvailable"), 0));
        ret.put("fs", DataUtil.toInt(info.resolve("fs"), 0));
        ret.put("protectTime", DataUtil.toInt(info.resolve("protectTime"), 0));
        /*MapleData itemLevels = info.getChildByName("level");
		Map<Integer, ItemLevelInfo> levels = new LinkedHashMap<Integer, ItemLevelInfo>();
		MapleData levelInfo = itemLevels.getChildByName("info");
		if (levelInfo != null) {
			int curLevel = 1;
			for (MapleData dataz : levelInfo) {
				Map<String, Integer> upgrades = new LinkedHashMap<String, Integer>();
				int exp = 0;
				for (MapleData data : dataz) {
					if (data.getName().startsWith("inc")) {
						upgrades.put(data.getName().substring(3), MapleDataTool.getIntConvert(data));
					} else if (data.getName().equals("exp")) {
						exp = MapleDataTool.getIntConvert(data);
					}
				}
				int skillId = -1;
				int skillLevel = -1;
				int prob = -1;
				if (curLevel == 6) {
					MapleData skill = itemLevels.getChildByName("case");
					prob = MapleDataTool.getInt(skill.getChildByPath("0/prob"), -1);
					MapleData skillInfo = skill.getChildByPath("1/6/Skill/0");
					if (skillInfo != null) {
						skillId = MapleDataTool.getInt(skillInfo.getChildByName("id"), -1);
						skillLevel = MapleDataTool.getInt(skillInfo.getChildByName("level"), -1);
					}
				}
				levels.put(curLevel, new ItemLevelInfo(exp, skillId, skillLevel, prob, upgrades));
				curLevel++;
			}
			itemLevelCache.put(itemId, levels);
		}*/
        itemStatsCache.put(itemId, ret);
        return ret;
    }

    protected Map<String, Integer> getScrollStats(int itemId) {
        if (scrollStatsCache.containsKey(itemId)) {
            return scrollStatsCache.get(itemId);
        }
        Map<String, Integer> ret = new LinkedHashMap<>();
        MapleData item = getItemData(itemId);
        MapleData info = item.getChild("info");
        if (item == null || info == null) {
            return ret;
        }
        for (MapleData data : info) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), DataUtil.toInt(data));
            }
        }
        ret.put("cursed", DataUtil.toInt(info.resolve("cursed"), 0));
        ret.put("recover", DataUtil.toInt(info.resolve("recover"), 0));
        ret.put("randstat", DataUtil.toInt(info.resolve("randstat"), 0));
        ret.put("success", DataUtil.toInt(info.resolve("success"), 0));
        ret.put("preventslip", DataUtil.toInt(info.resolve("preventslip"), 0));
        ret.put("warmsupport", DataUtil.toInt(info.resolve("warmsupport"), 0));
        ret.put("protectTime", DataUtil.toInt(info.resolve("protectTime"), 0));
        scrollStatsCache.put(itemId, ret);
        return ret;
    }

    protected Map<String, Integer> getCrystalProperties(int itemId) {
        if (crystalProperties.containsKey(itemId)) {
            return crystalProperties.get(itemId);
        }
        final Map<String, Integer> ret = new LinkedHashMap<>();
        MapleData item = getItemData(itemId);
        if (item == null || item.getChild("info") == null) {
            return ret;
        }
        MapleData info = item.getChild("info");
        for (MapleData data : info) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), DataUtil.toInt(data));
            } else if (data.getName().startsWith("rand")) {
                ret.put(data.getName(), DataUtil.toInt(data));
            }
        }
        crystalProperties.put(itemId, ret);
        return ret;
    }

    public int getGemStatbyName(int itemId, String name) {
        Integer stat = getCrystalProperties(itemId).get(name);
        return stat == null ? 0 : stat;
    }

    public int getItemExpNeededToLevel(int itemid, int curLevel) {
        if (!itemLevelCache.containsKey(itemid) && itemStatsCache.containsKey(itemid)) {
            return -1;
        } else if (!itemStatsCache.containsKey(itemid)) {
            getEquipStats(itemid);
            if (!itemLevelCache.containsKey(itemid)) {
                return -1;
            }
        }
        return itemLevelCache.get(itemid).get(curLevel + 1).getExp();
    }

	/*public IItem levelUpItem(IItem item, int newLevel) {
		Equip equip = (Equip) item;
		equip.setItemExp(0);
		equip.setItemLevel(equip.getItemLevel() + 1);
		return item;
	}*/

    public int getTotalUpgrades(int itemId) {
        Integer req = getEquipStats(itemId).get("tuc");
        return req == null ? 0 : req;
    }

    public int getReqLevel(int itemId) {
        Integer req = getEquipStats(itemId).get("reqLevel");
        return req == null ? 0 : req;
    }

    public int getReqJob(int itemId) {
        Integer req = getEquipStats(itemId).get("reqJob");
        return req == null ? 0 : req;
    }

    public int getReqStr(int itemId) {
        Integer req = getEquipStats(itemId).get("reqSTR");
        return req == null ? 0 : req;
    }

    public int getReqDex(int itemId) {
        Integer req = getEquipStats(itemId).get("reqDEX");
        return req == null ? 0 : req;
    }

    public int getReqInt(int itemId) {
        Integer req = getEquipStats(itemId).get("reqINT");
        return req == null ? 0 : req;
    }

    public int getReqLuk(int itemId) {
        Integer req = getEquipStats(itemId).get("reqLUK");
        return req == null ? 0 : req;
    }

    public boolean isCashItem(int itemId) {
        Integer cashVal = getEquipStats(itemId).get("cash");
        return cashVal != null && cashVal > 0;
    }

    public boolean expiresOnLogOut(int itemId) {
        Integer expireOnLogOut = getEquipStats(itemId).get("expireOnLogout");
        return expireOnLogOut != null && expireOnLogOut > 0;
    }

    public boolean isEquipTradeBlocked(int itemId) {
        Integer equipTradeBlock = getEquipStats(itemId).get("equipTradeBlock");
        return equipTradeBlock != null && equipTradeBlock > 0;
    }

    public boolean canTradeOnce(int itemId) {
        Integer tradeAvailable = getEquipStats(itemId).get("tradeAvailable");
        return tradeAvailable != null && tradeAvailable > 0;
    }

    public boolean isSnowshoe(int itemId) {
        Integer fs = getEquipStats(itemId).get("fs");
        return fs != null && fs > 0;
    }

    public boolean isCleanSlate(int scrollId) {
        Integer stat = getScrollStats(scrollId).get("recover");
        return stat != null && stat == 1;
    }

    public boolean isSpikeScroll(int scrollId) {
        Integer spike = getScrollStats(scrollId).get("preventslip");
        return spike != null && spike == 1;
    }

    public boolean isColdProtectionScroll(int scrollId) {
        Integer coldProtection = getScrollStats(scrollId).get("warmsupport");
        return coldProtection != null && coldProtection == 1;
    }

    public int getProtectionTime(int itemId) {
        Integer protectTime = getEquipStats(itemId).get("protectTime");
        return protectTime == null ? 0 : protectTime;
    }

    public List<Integer> getScrollReqs(int itemId) {
        List<Integer> ret = new ArrayList<>();
        MapleData data = getItemData(itemId);
        data = data.getChild("req");
        if (data == null) {
            return ret;
        }
        for (MapleData req : data) {
            ret.add(DataUtil.toInt(req));
        }
        return ret;
    }

    public List<SummonEntry> getSummonMobs(int itemId) {
        if (summonEntryCache.containsKey(itemId)) {
            return summonEntryCache.get(itemId);
        }
        List<SummonEntry> ret = new LinkedList<>();
        final MapleData data = getItemData(itemId);
        for (MapleData children : data.getChild("mob")) {
            int mobId = DataUtil.toInt(children.getChild("id"), -1);
            int prob = DataUtil.toInt(children.getChild("prob"), -1);
            if (mobId != -1 && prob != -1) {
                ret.add(new SummonEntry(mobId, prob));
            }
        }
        if (ret.isEmpty()) {
            log.warn("Empty summon bag, itemID: {}", itemId);
        }
        summonEntryCache.put(itemId, ret);
        return ret;
    }

    public boolean isWeapon(int itemId) {
        return itemId >= 1302000 && itemId < 1492024;
    }

    public MapleWeaponType getWeaponType(int itemId) {
        int cat = itemId / 10000;
        cat = cat % 100;
        return switch (cat) {
            case 30 -> MapleWeaponType.SWORD1H;
            case 31 -> MapleWeaponType.AXE1H;
            case 32 -> MapleWeaponType.BLUNT1H;
            case 33 -> MapleWeaponType.DAGGER;
            case 37 -> MapleWeaponType.WAND;
            case 38 -> MapleWeaponType.STAFF;
            case 40 -> MapleWeaponType.SWORD2H;
            case 41 -> MapleWeaponType.AXE2H;
            case 42 -> MapleWeaponType.BLUNT2H;
            case 43 -> MapleWeaponType.SPEAR;
            case 44 -> MapleWeaponType.POLE_ARM;
            case 45 -> MapleWeaponType.BOW;
            case 46 -> MapleWeaponType.CROSSBOW;
            case 47 -> MapleWeaponType.CLAW;
// Barefists
            case 39, 48 -> MapleWeaponType.KNUCKLE;
            case 49 -> MapleWeaponType.GUN;
            default -> MapleWeaponType.NOT_A_WEAPON;
        };
    }

    public boolean isShield(int itemId) {
        int cat = itemId / 10000;
        cat = cat % 100;
        return cat == 9;
    }

    public boolean isEquip(int itemId) {
        return itemId / 1000000 == 1;
    }

    public Pair<ScrollResult, IItem> scrollEquipWithId(IItem equip, int scrollId, boolean usingWhiteScroll) {
        ScrollResult result = IEquip.ScrollResult.FAIL;
        if (equip instanceof Equip) {
            Equip nEquip = (Equip) equip;
            Map<String, Integer> stats = getScrollStats(scrollId);
            Map<String, Integer> eqstats = getEquipStats(equip.getItemId());
            if ((nEquip.getUpgradeSlots() > 0 || isCleanSlate(scrollId) || isSpikeScroll(scrollId) || isColdProtectionScroll(scrollId)) && Math.ceil(Math.random() * 100.0) <= stats.get("success")) {
                if (stats.get("preventslip") == 1) {
                    nEquip.setFlag((short) (nEquip.getFlag() | ItemFlag.SPIKES.getValue()));
                } else if (stats.get("warmsupport") == 1) {
                    nEquip.setFlag((short) (nEquip.getFlag() | ItemFlag.COLD_PROTECTION.getValue()));
                } else if (isCleanSlate(scrollId)) {
                    if (nEquip.getLevel() + nEquip.getUpgradeSlots() < eqstats.get("tuc") + nEquip.getViciousHammers()) {
                        byte newSlots = (byte) (nEquip.getUpgradeSlots() + 1);
                        nEquip.setUpgradeSlots(newSlots);
                    }
                } else if (stats.get("randstat") == 1) {
                    if (nEquip.getStr() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getStr() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setStr(newStat);
                    }
                    if (nEquip.getDex() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getDex() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setDex(newStat);
                    }
                    if (nEquip.getInt() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getInt() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setInt(newStat);
                    }
                    if (nEquip.getLuk() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getLuk() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setLuk(newStat);
                    }
                    if (nEquip.getWatk() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getWatk() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setWatk(newStat);
                    }
                    if (nEquip.getWdef() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getWdef() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setWdef(newStat);
                    }
                    if (nEquip.getMatk() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getMatk() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setMatk(newStat);
                    }
                    if (nEquip.getMdef() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getMdef() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setMdef(newStat);
                    }
                    if (nEquip.getAcc() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getAcc() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setAcc(newStat);
                    }
                    if (nEquip.getAvoid() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getAvoid() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setAvoid(newStat);
                    }
                    if (nEquip.getSpeed() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getSpeed() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setSpeed(newStat);
                    }
                    if (nEquip.getJump() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getJump() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setJump(newStat);
                    }
                    if (nEquip.getHp() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getHp() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setHp(newStat);
                    }
                    if (nEquip.getMp() > 0) {
                        int increase = 1;
                        if (Math.ceil(Math.random() * 100.0) <= 50) {
                            increase = increase * -1;
                        }
                        short newStat = (short) (nEquip.getMp() + Math.ceil(Math.random() * 5.0) * increase);
                        nEquip.setMp(newStat);
                    }
                } else {
                    for (Entry<String, Integer> stat : stats.entrySet()) {
                        switch (stat.getKey()) {
                            case "STR" -> nEquip.setStr((short) (nEquip.getStr() + stat.getValue()));
                            case "DEX" -> nEquip.setDex((short) (nEquip.getDex() + stat.getValue()));
                            case "INT" -> nEquip.setInt((short) (nEquip.getInt() + stat.getValue()));
                            case "LUK" -> nEquip.setLuk((short) (nEquip.getLuk() + stat.getValue()));
                            case "PAD" -> nEquip.setWatk((short) (nEquip.getWatk() + stat.getValue()));
                            case "PDD" -> nEquip.setWdef((short) (nEquip.getWdef() + stat.getValue()));
                            case "MAD" -> nEquip.setMatk((short) (nEquip.getMatk() + stat.getValue()));
                            case "MDD" -> nEquip.setMdef((short) (nEquip.getMdef() + stat.getValue()));
                            case "ACC" -> nEquip.setAcc((short) (nEquip.getAcc() + stat.getValue()));
                            case "EVA" -> nEquip.setAvoid((short) (nEquip.getAvoid() + stat.getValue()));
                            case "Speed" -> nEquip.setSpeed((short) (nEquip.getSpeed() + stat.getValue()));
                            case "Jump" -> nEquip.setJump((short) (nEquip.getJump() + stat.getValue()));
                            case "MHP" -> nEquip.setHp((short) (nEquip.getHp() + stat.getValue()));
                            case "MMP" -> nEquip.setMp((short) (nEquip.getMp() + stat.getValue()));
                        }
                    }
                }
                if (!isCleanSlate(scrollId) && !isSpikeScroll(scrollId) && !isColdProtectionScroll(scrollId)) {
                    nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() - 1));
                    nEquip.setLevel((byte) (nEquip.getLevel() + 1));
                }
                result = IEquip.ScrollResult.SUCCESS;
            } else {
                if (!usingWhiteScroll && !isCleanSlate(scrollId) && !isSpikeScroll(scrollId) && !isColdProtectionScroll(scrollId)) {
                    nEquip.setUpgradeSlots((byte) (nEquip.getUpgradeSlots() - 1));
                }
                if (Math.ceil(1.0 + Math.random() * 100.0) < stats.get("cursed")) {
                    result = IEquip.ScrollResult.CURSE;
                }
            }
        }
        return new Pair<>(result, equip);
    }

    public IItem getEquipById(int equipId) {
        return getEquipById(equipId, -1);
    }

    public IItem getEquipById(int equipId, int ringId) {
        if (equipCache.containsKey(equipId) && ringId == -1) {
            return equipCache.get(equipId).copy();
        }
        Equip nEquip;
        nEquip = new Equip(equipId, (byte) 0, ringId);
        nEquip.setQuantity((short) 1);
        Map<String, Integer> stats = getEquipStats(equipId);
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

    private short getRandStat(short defaultValue, int maxRange) {
        if (defaultValue == 0) {
            return 0;
        }
        // vary no more than ceil of 10% of stat
        int lMaxRange = (int) Math.min(Math.ceil(defaultValue * 0.1), maxRange);
        return (short) (defaultValue - lMaxRange + Math.floor(Math.random() * (lMaxRange * 2 + 1)));
    }

    private short getRandStatPositiveOnly(short defaultValue, int maxRange) {
        if (defaultValue == 0) {
            return 0;
        }
        // vary no more than ceil of 10% of stat
        int lMaxRange = (int) Math.min(Math.ceil(defaultValue * 0.1), maxRange);
        return (short) (Math.random() * (defaultValue + lMaxRange - defaultValue + 1) + defaultValue);
    }

    public Equip randomizeStats(Equip equip) {
        return randomizeStats(equip, false);
    }

    public Equip randomizeStats(Equip equip, boolean positiveOnly) {
        if (!positiveOnly) {
            equip.setStr(getRandStat(equip.getStr(), 5));
            equip.setDex(getRandStat(equip.getDex(), 5));
            equip.setInt(getRandStat(equip.getInt(), 5));
            equip.setLuk(getRandStat(equip.getLuk(), 5));
            equip.setMatk(getRandStat(equip.getMatk(), 5));
            equip.setWatk(getRandStat(equip.getWatk(), 5));
            equip.setAcc(getRandStat(equip.getAcc(), 5));
            equip.setAvoid(getRandStat(equip.getAvoid(), 5));
            equip.setJump(getRandStat(equip.getJump(), 5));
            equip.setSpeed(getRandStat(equip.getSpeed(), 5));
            equip.setWdef(getRandStat(equip.getWdef(), 10));
            equip.setMdef(getRandStat(equip.getMdef(), 10));
            equip.setHp(getRandStat(equip.getHp(), 10));
            equip.setMp(getRandStat(equip.getMp(), 10));
        } else {
            equip.setStr(getRandStatPositiveOnly(equip.getStr(), 5));
            equip.setDex(getRandStatPositiveOnly(equip.getDex(), 5));
            equip.setInt(getRandStatPositiveOnly(equip.getInt(), 5));
            equip.setLuk(getRandStatPositiveOnly(equip.getLuk(), 5));
            equip.setMatk(getRandStatPositiveOnly(equip.getMatk(), 5));
            equip.setWatk(getRandStatPositiveOnly(equip.getWatk(), 5));
            equip.setAcc(getRandStatPositiveOnly(equip.getAcc(), 5));
            equip.setAvoid(getRandStatPositiveOnly(equip.getAvoid(), 5));
            equip.setJump(getRandStatPositiveOnly(equip.getJump(), 5));
            equip.setSpeed(getRandStatPositiveOnly(equip.getSpeed(), 5));
            equip.setWdef(getRandStatPositiveOnly(equip.getWdef(), 10));
            equip.setMdef(getRandStatPositiveOnly(equip.getMdef(), 10));
            equip.setHp(getRandStatPositiveOnly(equip.getHp(), 10));
            equip.setMp(getRandStatPositiveOnly(equip.getMp(), 10));
        }
        return equip;
    }

    public MapleStatEffect getItemEffect(int itemId) {
        MapleStatEffect ret = itemEffects.get(itemId);
        if (ret == null) {
            MapleData item = getItemData(itemId);
            if (item == null) {
                return null;
            }
            MapleData spec = item.getChild("spec");
            ret = MapleStatEffect.loadItemEffectFromData(spec, itemId);
            itemEffects.put(itemId, ret);
        }
        return ret;
    }

    public boolean isThrowingStar(int itemId) {
        return itemId / 10000 == 207;
    }

    public boolean isShootingBullet(int itemId) {
        return itemId / 10000 == 233;
    }

    public boolean isRechargable(int itemId) {
        int id = itemId / 10000;
        return id == 233 || id == 207;
    }

    public boolean isProjectile(int itemId) {
        int id = itemId / 10000;
        return id == 206 || id == 207 || id == 233;
    }

    public boolean isOverall(int itemId) {
        return itemId >= 1050000 && itemId < 1060000;
    }

    public boolean isPet(int itemId) {
        return itemId >= 5000000 && itemId <= 5000100;
    }

    public boolean isArrowForCrossBow(int itemId) {
        return itemId >= 2061000 && itemId < 2062000;
    }

    public boolean isArrowForBow(int itemId) {
        return itemId >= 2060000 && itemId < 2061000;
    }

    public boolean isTwoHanded(int itemId) {
        return switch (getWeaponType(itemId)) {
            case AXE2H, BLUNT2H, BOW, CLAW, CROSSBOW, POLE_ARM, SPEAR, SWORD2H, GUN, KNUCKLE -> true;
            default -> false;
        };
    }

    public boolean isTownScroll(int itemId) {
        return itemId >= 2030000 && itemId < 2030020;
    }

    public boolean isGun(int itemId) {
        return itemId >= 1492000 && itemId <= 1492024;
    }

    public boolean isWritOfSolomon(int itemId) {
        return itemId >= 2370000 && itemId <= 2370012;
    }

    public boolean isWeddingRing(int itemId) {
        return itemId >= 1112803 && itemId <= 1112807 || itemId == 1112809;
    }

    public boolean isCrushRing(int itemid) {
        return itemid >= 1112001 && itemid <= 1112007 || itemid == 1112012;
    }

    public boolean isFriendshipRing(int itemid) {
        return itemid >= 1112800 && itemid <= 1112802 || itemid >= 1112810 && itemid <= 1112812;
    }

    public List<MapleFish> getFishReward(int itemId) {
        if (fishingCache.containsKey(itemId)) {
            return fishingCache.get(itemId);
        } else {
            List<MapleFish> rewards = new ArrayList<>();
            for (MapleData child : getItemData(itemId).getChild("reward")) {
                rewards.add(new MapleFish(DataUtil.toInt(child.resolve("item"), 0), DataUtil.toInt(child.resolve("prob"), 0), DataUtil.toInt(child.resolve("count"), 0), DataUtil.toString(child.resolve("Effect"), "")));
            }
            fishingCache.put(itemId, rewards);
            return rewards;
        }
    }

    public int getExpCache(int itemId) {
        if (getExpCache.containsKey(itemId)) {
            return getExpCache.get(itemId);
        }
        MapleData item = getItemData(itemId);
        if (item == null) {
            return 0;
        }
        int pEntry = 0;
        MapleData pData = item.resolve("spec/exp");
        if (pData == null) {
            return 0;
        }
        pEntry = DataUtil.toInt(pData);

        getExpCache.put(itemId, pEntry);
        return pEntry;
    }

    public int getWatkForProjectile(int itemId) {
        Integer atk = projectileWatkCache.get(itemId);
        if (atk != null) {
            return atk;
        }
        MapleData data = getItemData(itemId);
        atk = DataUtil.toInt(data.resolve("info/incPAD"), 0);
        projectileWatkCache.put(itemId, atk);
        return atk;
    }

    public boolean canScroll(int scrollid, int itemid) {
        int scrollCategoryQualifier = scrollid / 100 % 100;
        int itemCategoryQualifier = itemid / 10000 % 100;
        return scrollCategoryQualifier == itemCategoryQualifier;
    }

    public String getName(int itemId) {
        if (nameCache.containsKey(itemId)) {
            return nameCache.get(itemId);
        }
        MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        String ret = DataUtil.toString(strings.getChild("name"), null);
        nameCache.put(itemId, ret);
        return ret;
    }

    public String getDesc(int itemId) {
        if (descCache.containsKey(itemId)) {
            return descCache.get(itemId);
        }
        MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        String ret = DataUtil.toString(strings.resolve("desc"), null);
        descCache.put(itemId, ret);
        return ret;
    }

    public String getMsg(int itemId) {
        if (msgCache.containsKey(itemId)) {
            return msgCache.get(itemId);
        }
        MapleData strings = getStringData(itemId);
        if (strings == null) {
            return null;
        }
        String ret = DataUtil.toString(strings.resolve("msg"), null);
        msgCache.put(itemId, ret);
        return ret;
    }

    public boolean isDropRestricted(int itemId) {
        if (itemId >= 3010046 && itemId <= 3010047 || itemId == 3010057 || itemId == 3010058 || itemId == 1102174 || itemId == 1082245 || itemId == 1002800 || itemId == 1052166 || itemId == 1072368 || itemId == 1032058 || itemId >= 1002515 && itemId <= 1002518) {
            return true;
        }
        if (dropRestrictionCache.containsKey(itemId)) {
            return dropRestrictionCache.get(itemId);
        }
        MapleData data = getItemData(itemId);
        boolean bRestricted = DataUtil.toInt(data.resolve("info/tradeBlock"), 0) == 1;
        if (!bRestricted) {
            bRestricted = DataUtil.toInt(data.resolve("info/quest"), 0) == 1;
        }
        dropRestrictionCache.put(itemId, bRestricted);

        return bRestricted;
    }

    public boolean canHaveOnlyOne(int itemId) {
        if (pickupRestrictionCache.containsKey(itemId)) {
            return pickupRestrictionCache.get(itemId);
        }

        MapleData data = getItemData(itemId);
        boolean bRestricted = DataUtil.toInt(data.resolve("info/only"), 0) == 1;

        pickupRestrictionCache.put(itemId, bRestricted);
        return bRestricted;
    }

    public boolean isConsumeOnPickup(int itemId) {
        if (consumeOnPickup.containsKey(itemId)) {
            return consumeOnPickup.get(itemId);
        }
        boolean consumeonPickup = false;
        MapleData data = getItemData(itemId);
        if (data != null) {
            consumeonPickup = DataUtil.toInt(data.resolve("spec/consumeOnPickup"), 0) == 1;
            if (!consumeonPickup && data.getChild("specEx") != null) {
                consumeonPickup = DataUtil.toInt(data.resolve("specEx/consumeOnPickup"), 0) == 1;
            }
        }

        consumeOnPickup.put(itemId, consumeonPickup);
        return consumeonPickup;
    }

    public int getScriptedItemNpc(int itemId) {
        if (scriptedItemCache.containsKey(itemId)) {
            return scriptedItemCache.get(itemId);
        }
        int npcId = DataUtil.toInt(getItemData(itemId).resolve("spec/npc"), 0);
        scriptedItemCache.put(itemId, npcId);
        return scriptedItemCache.get(itemId);
    }

    public Map<String, Integer> getSkillStats(int itemId, int playerJob) {
        final Map<String, Integer> ret = new LinkedHashMap<>();
        MapleData item = getItemData(itemId);
        if (item == null) {
            return ret;
        }
        MapleData info = item.getChild("info");
        if (info == null) {
            return ret;
        }
        for (MapleData data : info) {
            if (data.getName().startsWith("inc")) {
                ret.put(data.getName().substring(3), DataUtil.toInt(data));
            }
        }
        ret.put("masterLevel", DataUtil.toInt(info.resolve("masterLevel"), 0));
        ret.put("reqSkillLevel", DataUtil.toInt(info.resolve("reqSkillLevel"), 0));
        ret.put("success", DataUtil.toInt(info.resolve("success"), 0));
        MapleData skillIds = info.getChild("skill");
        for (MapleData skillId : skillIds) {
            int cSkillId = DataUtil.toInt(skillId);
            if (cSkillId / 10000 == playerJob) {
                ret.put("skillid", cSkillId);
                break;
            }
        }
        ret.putIfAbsent("skillid", 0);
        return ret;
    }

    private void loadCardIdData() {
        for (MapleData item : itemData.getData("Consume/0238.img")) {
            monsterBook.add(new Pair<>(Integer.parseInt(item.getName()), DataUtil.toInt(item.resolve("info/mob"))));
        }
    }

    public boolean isMonsterCard(int id) {
        return id / 10000 == 238;
    }

    public boolean isSpecialCard(int id) {
        return id / 1000 >= 2388;
    }

    public int getCardMobId(int id) {
        if (id == 0) {
            return 0;
        }
        for (Pair<Integer, Integer> card : monsterBook) {
            if (card.getLeft() == id) {
                return card.getRight();
            }
        }
        return 0;
    }

    public int getMobCardId(int id) {
        for (Pair<Integer, Integer> card : monsterBook) { // Cache this if you wish since it may be called a lot.
            if (card.getRight() == id) {
                return card.getLeft();
            }
        }
        return -1;
    }

    public int getCardShortId(int id) {
        return id % 10000;
    }

    public final List<Integer> petsCanConsume(int itemId) {
        final List<Integer> ret = new ArrayList<>();
        MapleData item = getItemData(itemId);
        MapleData info = item.getChild("spec");
        if (item == null || info == null) {
            return ret;
        }
        for (MapleData petIds : info) {
            if (!item.getName().equals("inc")) {
                ret.add(DataUtil.toInt(petIds));
            }
        }
        return ret;
    }

    public boolean isQuestItem(int itemId) {
        if (isQuestItemCache.containsKey(itemId)) {
            return isQuestItemCache.get(itemId);
        }
        MapleData data = getItemData(itemId);
        boolean questItem = DataUtil.toInt(data.resolve("info/quest"), 0) == 1;
        isQuestItemCache.put(itemId, questItem);
        return questItem;
    }

    public int getAutoChangeMapId(String path) {
        if (mapAutoChangeCache.containsKey(path)) {
            return mapAutoChangeCache.get(path);
        }
        String[] parts = path.split("/");
        MapleData root = effectData.getData(parts[1]).resolve(parts[2] + "/" + parts[3]);
        int mapId = DataUtil.toInt(root.resolve(root.getChildCount() - 1 + "/field"), -1);
        mapAutoChangeCache.put(path, mapId);
        return mapId;
    }

    public boolean isMiniDungeonMap(int mapId) {
        return switch (mapId) {
            case 100020000, 105040304, 105050100, 221023400 -> true;
            default -> false;
        };
    }

    public static class SummonEntry {

        private final int chance, mobId;

        public SummonEntry(int a, int b) {
            mobId = a;
            chance = b;
        }

        public int getChance() {
            return chance;
        }

        public int getMobId() {
            return mobId;
        }
    }

    public static class ItemLevelInfo {
        private final int exp, skill, skillLevel, prob;
        private final Map<String, Integer> upgrades;

        public ItemLevelInfo(int exp, int skill, int skillLevel, int prob, Map<String, Integer> upgrades) {
            this.exp = exp;
            this.skill = skill;
            this.prob = prob;
            this.skillLevel = skillLevel;
            this.upgrades = upgrades;
        }

        public int getExp() {
            return exp;
        }

        public int getSkill() {
            return skill;
        }

        public int getSkillLevel() {
            return skillLevel;
        }

        public int getProbability() {
            return prob;
        }

        public Map<String, Integer> getUpgrades() {
            return upgrades;
        }
    }
}