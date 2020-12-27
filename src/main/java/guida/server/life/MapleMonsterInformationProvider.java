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

import guida.database.DatabaseConnection;
import guida.tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleMonsterInformationProvider {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleMonsterInformationProvider.class);
    private static MapleMonsterInformationProvider instance = null;
    private final Map<Integer, List<DropEntry>> drops = new HashMap<>();
    private MapleMonsterInformationProvider() {
    }

    public static MapleMonsterInformationProvider getInstance() {
        if (instance == null) {
            instance = new MapleMonsterInformationProvider();
        }
        return instance;
    }

    public List<DropEntry> retrieveDropChances(int monsterid) {
        if (drops.containsKey(monsterid)) {
            List<DropEntry> retrdChances = drops.get(monsterid);
            Collections.shuffle(retrdChances);
            return retrdChances;
        }
        List<DropEntry> ret = new LinkedList<>();
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT itemid, chance, questid FROM monsterdrops WHERE monsterid = ? OR monsterid = -1");
            ps.setInt(1, monsterid);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int chance = rs.getInt("chance");
                int questid = rs.getInt("questid");
                ret.add(new DropEntry(rs.getInt("itemid"), chance, questid));
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            log.error("Error retrieving drop", e);
        }
        Collections.shuffle(ret);
        drops.put(monsterid, ret);
        return ret;
    }

    public List<Pair<Integer, Integer>> whoDrops(int itemId) {
        List<Pair<Integer, Integer>> monsterIds = new ArrayList<>();
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT monsterid, chance FROM monsterdrops WHERE itemid = ?");
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                monsterIds.add(new Pair<>(rs.getInt("monsterid"), rs.getInt("chance")));
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            log.error("Error retrieving drop", e);
        }
        return monsterIds;
    }

    public void clearDrops() {
        drops.clear();
    }

    public static class DropEntry {

        public final int itemid;
        public final int chance;
        public final int questid;
        public int assignedRangeStart;
        public int assignedRangeLength;

        public DropEntry(int itemid, int chance, int questid) {
            this.itemid = itemid;
            this.chance = chance;
            this.questid = questid;
        }

        public DropEntry(int itemid, int chance) {
            this.itemid = itemid;
            this.chance = chance;
            questid = 0;
        }

        @Override
        public String toString() {
            return itemid + " chance: " + chance;
        }
    }
}