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

import guida.database.DatabaseConnection;
import guida.tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jay Estrella
 */
public class MakerItemFactory {

    private static final Map<Integer, MakerItemCreateEntry> createCache = new HashMap<>();

    public static MakerItemCreateEntry getItemCreateEntry(int toCreate) {
        if (createCache.get(toCreate) != null) {
            return createCache.get(toCreate);
        } else {
            Connection con = DatabaseConnection.getConnection();
            try {
                PreparedStatement ps = con.prepareStatement("SELECT * FROM makercreatedata WHERE itemid = ?");
                ps.setInt(1, toCreate);
                ResultSet rs = ps.executeQuery();

                int reqLevel = 0;
                int reqMakerLevel = 0;
                int cost = 0;
                short toGive = 0;
                int stimulator = 0;
                if (rs.next()) {
                    reqLevel = rs.getInt("req_level");
                    reqMakerLevel = rs.getInt("req_maker_level");
                    cost = rs.getInt("req_meso");
                    stimulator = rs.getInt("catalyst");
                    toGive = rs.getShort("quantity");
                }
                ps.close();
                rs.close();

                MakerItemCreateEntry ret = new MakerItemCreateEntry(cost, reqLevel, reqMakerLevel, toGive, stimulator);

                ps = con.prepareStatement("SELECT * FROM makerrecipedata WHERE itemid = ?");
                ps.setInt(1, toCreate);
                rs = ps.executeQuery();
                while (rs.next()) {
                    ret.addReqItem(rs.getInt("req_item"), rs.getInt("count"));
                }
                ps.close();
                rs.close();

                createCache.put(toCreate, ret);
            } catch (SQLException sqle) {
                sqle.printStackTrace();
            }
        }
        return createCache.get(toCreate);
    }

    public static class MakerItemCreateEntry {

        private final int reqLevel, reqMakerLevel;
        private final int cost;
        private final List<Pair<Integer, Integer>> reqItems = new ArrayList<>(); // itemId / amount
        private final List<Integer> reqEquips = new ArrayList<>();
        private final short toGive;
        private final int stimulator;

        public MakerItemCreateEntry(int cost, int reqLevel, int reqMakerLevel, short toGive, int stimulator) {
            this.cost = cost;
            this.reqLevel = reqLevel;
            this.reqMakerLevel = reqMakerLevel;
            this.toGive = toGive;
            this.stimulator = stimulator;
        }

        public short getRewardAmount() {
            return toGive;
        }

        public List<Pair<Integer, Integer>> getReqItems() {
            return reqItems;
        }

        public List<Integer> getReqEquips() {
            return reqEquips;
        }

        public int getReqLevel() {
            return reqLevel;
        }

        public int getReqSkillLevel() {
            return reqMakerLevel;
        }

        public int getCost() {
            return cost;
        }

        public int getStimulator() {
            return stimulator;
        }

        protected void addReqItem(int itemId, int amount) {
            reqItems.add(new Pair<>(itemId, amount));
        }
    }
}