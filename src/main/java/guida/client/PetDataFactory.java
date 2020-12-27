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

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Danny (Leifde)
 */
public class PetDataFactory {

    private final static MapleDataProvider dataRoot = MapleDataProviderFactory.getDataProvider("Item");
    private final static Map<Pair<Integer, Integer>, PetCommand> petCommands = new HashMap<>();
    private final static Map<Integer, Map<String, Integer>> petInfoCache = new HashMap<>();

    public static PetCommand getPetCommand(int petId, int skillId) {
        PetCommand ret = petCommands.get(new Pair<>(petId, skillId));
        if (ret != null) {
            return ret;
        }
        synchronized (petCommands) {
            // see if someone else that's also synchronized has loaded the skill by now
            ret = petCommands.get(new Pair<>(petId, skillId));
            if (ret == null) {
                final MapleData skillData = dataRoot.getData("Pet/" + petId + ".img");
                int prob = 0;
                int inc = 0;
                if (skillData != null) {
                    prob = DataUtil.toInt(skillData.resolve("interact/" + skillId + "/prob"), 0);
                    inc = DataUtil.toInt(skillData.resolve("interact/" + skillId + "/inc"), 0);
                }
                ret = new PetCommand(petId, skillId, prob, inc);
                petCommands.put(new Pair<>(petId, skillId), ret);
            }
            return ret;
        }
    }

    private static Map<String, Integer> getPetInfo(int petId) {
        if (petInfoCache.containsKey(petId)) {
            return petInfoCache.get(petId);
        }
        Map<String, Integer> ret = new LinkedHashMap<>(3);
        final MapleData petData = dataRoot.getData("Pet/" + petId + ".img");
        if (petData == null || petData.getChild("info") == null) {
            return ret;
        }
        MapleData info = petData.getChild("info");
        ret.put("hungry", DataUtil.toInt(info.resolve("hungry"), 1));
        int def1 = -1;
        ret.put("chatBalloon", DataUtil.toInt(info.resolve("chatBalloon"), def1));
        int def = -1;
        ret.put("nameTag", DataUtil.toInt(info.resolve("nameTag"), def));
        petInfoCache.put(petId, ret);
        return ret;
    }

    public static int getHunger(int petId) {
        int ret = 1;
        if (!getPetInfo(petId).isEmpty()) {
            ret = getPetInfo(petId).get("hungry");
        }
        return ret;
    }

    public static int getChatBalloon(int petId) {
        int ret = -1;
        if (!getPetInfo(petId).isEmpty()) {
            ret = getPetInfo(petId).get("chatBalloon");
        }
        return ret;
    }

    public static int getNameTag(int petId) {
        int ret = -1;
        if (!getPetInfo(petId).isEmpty()) {
            ret = getPetInfo(petId).get("nameTag");
        }
        return ret;
    }
}