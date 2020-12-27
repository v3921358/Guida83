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

import guida.client.MapleCharacter;
import guida.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Provides information about the Player's PlayerNPC
 *
 * @author Anujan
 * @version 1.0
 * @since Revision 1871
 */

public class MaplePlayerNPC {

    private static final int[] curNPCIds = {1200, 1100, 1000, 1300, 1400};
    private final int charId;
    private final int hair;
    private final int skin;
    private final int eyes;
    private final int job;
    private final int gender;
    private final String name;
    private final int[][] equips = new int[12][2];
    private final int npcid;

    public MaplePlayerNPC(int charId, String name, int hair, int eyes, int skin, int gender, int job) {
        this.charId = charId;
        this.name = name;
        this.hair = hair;
        this.eyes = eyes;
        this.skin = skin;
        this.gender = gender;
        this.job = job;
        job = job > 499 ? 500 : job;
        npcid = 9900000 + curNPCIds[job / 100 - 1];
        curNPCIds[job / 100 - 1]++;
        loadEquips();
    }

    public MaplePlayerNPC(MapleCharacter player) {
        charId = player.getId();
        name = player.getName();
        hair = player.getHair();
        eyes = player.getFace();
        skin = player.getSkinColor().getId();
        gender = player.getGender();
        job = player.getJob().getId();
        npcid = 9900000 + curNPCIds[job / 100 - 1];
        curNPCIds[job / 100 - 1]++;
        loadEquips();
    }

    public int getCharId() {
        return charId;
    }

    public int getHair() {
        return hair;
    }

    public int getSkin() {
        return skin;
    }

    public int getJob() {
        return job;
    }

    public int getEyes() {
        return eyes;
    }

    public int getGender() {
        return gender;
    }

    public int getNpcId() {
        return npcid;
    }

    @Override
    public String toString() {
        return "Hello, I am #b" + name + "#k, and I am #rLEVEL 200!#k";
    }

    public String getPlayerNPCName() {
        return name;
    }

    private void loadEquips() {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = con.prepareStatement("SELECT itemid, position FROM inventory_eqp WHERE characterid = ? AND position < 0 ORDER BY position");
            ps.setInt(1, charId);
            rs = ps.executeQuery();
            while (rs.next()) {
                int pos = Math.abs(rs.getInt("position"));
                boolean masked = pos >= 100;
                if (pos > equips.length || masked && pos - 100 > equips.length) {
                    continue;
                }
                equips[masked ? pos - 100 : pos][masked ? 1 : 0] = rs.getInt("itemid");
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.out.println("Error loading PlayerNPC: " + name);
            e.printStackTrace();
        }
    }

    public int[][] getEquips() {
        return equips;
    }
}