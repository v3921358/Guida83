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

import java.util.HashMap;
import java.util.Map;

/**
 * @author Patrick/PurpleMadness
 */
public class MapleAchievements {

    private static MapleAchievements instance = null;
    private final Map<Integer, MapleAchievement> achievements = new HashMap<>();

    protected MapleAchievements() {
        achievements.put(1, new MapleAchievement("finished the training camp for the first time", 250, false));
        achievements.put(3, new MapleAchievement("killed #pp first Anego", 3500));
        achievements.put(4, new MapleAchievement("reached level 70", 5000));
        achievements.put(5, new MapleAchievement("reached level 120", 7500));
        achievements.put(6, new MapleAchievement("killed #pp first boss", 1000));
        achievements.put(7, new MapleAchievement("equipped #pp first dragon item", 3000));
        achievements.put(8, new MapleAchievement("reached the meso cap for the first time", 10000));
        achievements.put(9, new MapleAchievement("reached 50 fame", 2000));
        achievements.put(10, new MapleAchievement("killed #pp first Papulatus", 2500));
        achievements.put(11, new MapleAchievement("saw a GM", 500, false));
        achievements.put(12, new MapleAchievement("succesfully scrolled an item", 1000, false));
        achievements.put(13, new MapleAchievement("earned a Zakum Helm", 2500));
        achievements.put(14, new MapleAchievement("said cc plz", 100, false));
        achievements.put(15, new MapleAchievement("flew to Victoria Island by Shanks", 500, false));
        achievements.put(16, new MapleAchievement("killed the almighty Zakum", 10000));
        achievements.put(17, new MapleAchievement("completed a trade", 250, false));
        achievements.put(18, new MapleAchievement("killed a Snail", 100, false));
        achievements.put(19, new MapleAchievement("killed a Pianus", 2500));
        achievements.put(20, new MapleAchievement("hit more than 10,000 damage to one monster", 3000));
        achievements.put(21, new MapleAchievement("hit 99,999 damage or more to one monster", 6000));
        achievements.put(22, new MapleAchievement("reached level 200", 35000, true, true));
        achievements.put(23, new MapleAchievement("won Field of Judgement", 5000));
        achievements.put(24, new MapleAchievement("created a Guild", 2000));
        achievements.put(25, new MapleAchievement("completed the Guild Quest", 3000));
        achievements.put(26, new MapleAchievement("killed Horntail", 30000));
        achievements.put(27, new MapleAchievement("completed Kerning City PQ for the first time", 3000, false)); //d
        achievements.put(28, new MapleAchievement("completed Ludibrium PQ for the first time", 3000, false));//d
        // PQ Repeatable
        // Complete under time
        achievements.put(29, new MapleAchievement("completed Kerning City PQ under 5 minutes", 400, false, true));//d
        achievements.put(30, new MapleAchievement("completed Ludibrium PQ under 10 minutes", 400, false, true));//d
        // Complete guessing under time
        achievements.put(31, new MapleAchievement("completed Ludibrium PQ Stage 8 under 90 seconds", 250, false, true));
        achievements.put(32, new MapleAchievement("completed Kerning City PQ Stage 4 under 75 seconds", 250, false, true));//d
        // Kill boss under time
        achievements.put(33, new MapleAchievement("completed Kerning City PQ Stage 5 under 1 minute", 300, false, true));//d
        achievements.put(34, new MapleAchievement("completed Ludibrium PQ Stage 9 under 90 seconds", 300, false, true));//d
        // Clear PQ
        achievements.put(35, new MapleAchievement("completed Kerning City PQ", 100, false, true));
        achievements.put(36, new MapleAchievement("completed Ludibrium PQ", 100, false, true));
        // Monster Book
        achievements.put(37, new MapleAchievement("completed a Monster Book for 1 monster", 400, false));
        achievements.put(38, new MapleAchievement("gained 1000 Monster Cards", 6000));
        //new Boss Quest
        achievements.put(39, new MapleAchievement("completed #pp first Boss Quest in Easy mode", 5000, true));
        achievements.put(40, new MapleAchievement("completed #pp first Boss Quest in Medium mode", 10000, true));
        achievements.put(41, new MapleAchievement("completed #pp first Boss Quest in Hard mode", 25000, true));
        achievements.put(42, new MapleAchievement("completed #pp first Boss Quest in Hell mode", 75000, true));

        achievements.put(43, new MapleAchievement("hit 199,999 damage to one monster", 9000, true));
        achievements.put(44, new MapleAchievement("equipped #pp first Reverse item", 4000, true));
        achievements.put(45, new MapleAchievement("equipped #pp first Timeless item", 5000, true));
        achievements.put(46, new MapleAchievement("passed #pp first level 20 mastery book", 1500, true));
        achievements.put(47, new MapleAchievement("passed #pp first level 30 mastery book", 2000, true));
        achievements.put(48, new MapleAchievement("passed Maple Warrior 20", 7000, true));

        achievements.put(49, new MapleAchievement("completed Ludibrium Maze PQ", 100, false, true));
        achievements.put(50, new MapleAchievement("made an Advanced gem while going for a Basic", 750, true));
        achievements.put(51, new MapleAchievement("got killed by a snail", 200, false));
        achievements.put(52, new MapleAchievement("killed someone with a summoning bag", 3000, true));

        achievements.put(53, new MapleAchievement("completed Ludibrium Maze PQ for the first time", 3000, false));
        achievements.put(54, new MapleAchievement("completed Ludibrium Maze PQ under 120 seconds", 400, false, true));
    }

    public static MapleAchievements getInstance() {
        if (instance == null) {
            instance = new MapleAchievements();
        }
        return instance;
    }

    public MapleAchievement getById(int id) {
        return achievements.get(id);
    }

    public Integer getByMapleAchievement(MapleAchievement ma) {
        for (Map.Entry<Integer, MapleAchievement> achievement : achievements.entrySet()) {
            if (achievement.getValue() == ma) {
                return achievement.getKey();
            }
        }
        return null;
    }
}