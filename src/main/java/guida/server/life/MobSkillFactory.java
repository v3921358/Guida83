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
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Danny (Leifde)
 */
public class MobSkillFactory {

    private static final Map<Pair<Integer, Integer>, MobSkill> mobSkills = new HashMap<>();
    private static final MapleData skillRoot = MapleDataProviderFactory.getDataProvider("Skill").getData("MobSkill.img");

    public static MobSkill getMobSkill(int skillId, int level) {
        MobSkill ret = mobSkills.get(new Pair<>(skillId, level));
        if (ret != null) {
            return ret;
        }
        synchronized (mobSkills) {
            // see if someone else that's also synchronized has loaded the skill by now
            ret = mobSkills.get(new Pair<>(skillId, level));
            if (ret == null) {
                MapleData skillData = skillRoot.resolve(skillId + "/level/" + level);
                if (skillData != null) {
                    List<Integer> toSummon = new ArrayList<>();
                    for (int i = 0; i > -1; i++) {
                        if (skillData.getChild(String.valueOf(i)) == null) {
                            break;
                        }
                        toSummon.add(DataUtil.toInt(skillData.getChild(String.valueOf(i)), 0));
                    }
                    MapleData ltd = skillData.getChild("lt");
                    MapleData rtd = skillData.getChild("rb");
                    Point lt = null;
                    Point rb = null;
                    if (ltd != null && rtd != null) {
                        lt = (Point) ltd.getData();
                        rb = (Point) rtd.getData();
                    }
                    ret = new MobSkill(skillId, level);
                    ret.addSummons(toSummon);
                    ret.setCoolTime(DataUtil.toInt(skillData.resolve("interval"), 0) * 1000);
                    ret.setDuration(DataUtil.toInt(skillData.resolve("time"), 0) * 1000);
                    ret.setHp(DataUtil.toInt(skillData.resolve("hp"), 100));
                    ret.setMpCon(DataUtil.toInt(skillData.resolve("mpCon"), 0));
                    ret.setSpawnEffect(DataUtil.toInt(skillData.resolve("summonEffect"), 0));
                    ret.setX(DataUtil.toInt(skillData.resolve("x"), 1));
                    ret.setY(DataUtil.toInt(skillData.resolve("y"), 1));
                    ret.setProp(DataUtil.toInt(skillData.resolve("prop"), 100) / (double) 100);
                    ret.setLimit(DataUtil.toInt(skillData.resolve("limit"), 0));
                    ret.setLtRb(lt, rb);
                    ret.setCount(DataUtil.toInt(skillData.resolve("count"), 1));
                }
                mobSkills.put(new Pair<>(skillId, level), ret);
            }
            return ret;
        }
    }
}