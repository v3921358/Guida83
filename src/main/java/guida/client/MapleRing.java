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

import guida.database.DatabaseConnection;
import guida.server.MapleInventoryManipulator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * @author Danny
 */
public class MapleRing implements Comparable<MapleRing> {

    private final int ringId;
    private final int ringId2;
    private final int partnerId;
    private final int itemId;
    private final String partnerName;

    private MapleRing(int id, int id2, int partnerId, int itemid, String partnername) {
        ringId = id;
        ringId2 = id2;
        this.partnerId = partnerId;
        itemId = itemid;
        partnerName = partnername;
    }

    public static MapleRing loadFromDb(int ringId) {
        try {
            Connection con = DatabaseConnection.getConnection(); // Get a connection to the database

            PreparedStatement ps = con.prepareStatement("SELECT * FROM rings WHERE id = ?"); // Get ring details..

            ps.setInt(1, ringId);
            ResultSet rs = ps.executeQuery();

            rs.next();
            MapleRing ret = new MapleRing(ringId, rs.getInt("partnerRingId"), rs.getInt("partnerChrId"), rs.getInt("itemid"), rs.getString("partnerName"));

            rs.close();
            ps.close();

            return ret;
        } catch (SQLException ex) {
            return null;
        }
    }

    public static int createRing(int itemid, final MapleCharacter partner1, final MapleCharacter partner2, String message) {
        try {
            if (partner1 == null) {
                return -2; // Partner Number 1 is not on the same channel.
            } else if (partner2 == null) {
                return -1; // Partner Number 2 is not on the same channel.
            } /*else if (checkRingDB(partner1) || checkRingDB(partner2)) {
                return 0; // Error or Already have ring.
			}*/

            int[] ringID = new int[2];
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO rings (itemid, partnerChrId, partnername) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, itemid);
            ps.setInt(2, partner2.getId());
            ps.setString(3, partner2.getName());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            ringID[0] = rs.getInt(1);
            rs.close();
            ps.close();

            ps = con.prepareStatement("INSERT INTO rings (itemid, partnerRingId, partnerChrId, partnername) VALUES (?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, itemid);
            ps.setInt(2, ringID[0]);
            ps.setInt(3, partner1.getId());
            ps.setString(4, partner1.getName());
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            rs.next();
            ringID[1] = rs.getInt(1);
            rs.close();
            ps.close();

            ps = con.prepareStatement("UPDATE rings SET partnerRingId = ? WHERE id = ?");
            ps.setInt(1, ringID[1]);
            ps.setInt(2, ringID[0]);
            ps.executeUpdate();
            ps.close();

            MapleInventoryManipulator.addRing(partner1, itemid, ringID[0]);
            MapleInventoryManipulator.addRing(partner2, itemid, ringID[1]);

			/*TimerManager.getInstance().schedule(new Runnable() {

				public void run() {
					partner1.getClient().sendPacket(MaplePacketCreator.getCharInfo(partner1));
					partner1.getMap().removePlayer(partner1);
					partner1.getMap().addPlayer(partner1);
					partner2.getClient().sendPacket(MaplePacketCreator.getCharInfo(partner2));
					partner2.getMap().removePlayer(partner2);
					partner2.getMap().addPlayer(partner2);
				}
			}, 1000);


			partner1.dropMessage(5, "Congratulations, you and " + partner2.getName() + " have successfully purchased a ring together!");
			partner1.dropMessage(5, "Please log off and log back in if the rings do not work.");*/

            partner2.dropMessage(5, "Congratulations, " + partner1.getName() + " has bought you a ring!");
            partner2.dropMessage(5, partner1.getName() + "'s message to you: " + message);
            partner2.setCCRequired(true);
            partner2.dropMessage(5, "Please change channels for the rings to take effect.");
            return 1;
        } catch (SQLException ex) {
            return 0;
        }
    }

    public static boolean checkRingDB(MapleCharacter player) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT id FROM rings WHERE partnerChrId = ?");
            ps.setInt(1, player.getId());
            ResultSet rs = ps.executeQuery();
            boolean ret = rs.next();
            rs.close();
            ps.close();
            return ret;
        } catch (SQLException ex) {
            return true;
        }
    }

    public static void removeRingFromDb(MapleCharacter player) {
        try {
            Connection con = DatabaseConnection.getConnection();
            int otherId;
            PreparedStatement ps = con.prepareStatement("SELECT partnerRingId FROM rings WHERE partnerChrId = ?");
            ps.setInt(1, player.getId());
            ResultSet rs = ps.executeQuery();
            rs.next();
            otherId = rs.getInt("partnerRingId");
            rs.close();
            ps.close();
            ps = con.prepareStatement("DELETE FROM rings WHERE partnerChrId = ?");
            ps.setInt(1, player.getId());
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("DELETE FROM rings WHERE partnerChrId = ?");
            ps.setInt(1, otherId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public static void removeWeddingRing(MapleCharacter player) {
        int itemid = player.getMarriageRings().get(0).itemId;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("DELETE from rings where partnercharid = ? and itemid = ?");
            ps.setInt(1, player.getId());
            ps.setInt(1, itemid);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("DELETE FROM rings where partnercharid = ? and itemid = ?");
            ps.setInt(1, player.getPartnerId());
            ps.setInt(2, itemid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public int getRingId() {
        return ringId;
    }

    public int getPartnerRingId() {
        return ringId2;
    }

    public int getPartnerChrId() {
        return partnerId;
    }

    public int getItemId() {
        return itemId;
    }

    public String getPartnerName() {
        return partnerName;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MapleRing) {
            return ((MapleRing) o).ringId == ringId;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + ringId;
        return hash;
    }

    @Override
    public int compareTo(MapleRing other) {
        return Integer.compare(ringId, other.ringId);
    }
}