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
import guida.server.MapleItemInformationProvider;
import guida.server.movement.AbsoluteLifeMovement;
import guida.server.movement.LifeMovement;
import guida.server.movement.LifeMovementFragment;
import guida.tools.FileTimeUtil;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Matze
 */
public class MaplePet {

    private final int itemId;
    private String name;
    private int petId;
    private int closeness = 0;
    private int level = 1;
    private int fullness = 100;
    private Timestamp deadDate = FileTimeUtil.getDefaultPetTimestamp();
    private byte index = 0;
    private int fh, stance;
    private Point pos;
    private boolean labelRing = false, quoteRing = false;

    private MaplePet(int id, int uniqueid) {
        itemId = id;
        petId = uniqueid;
        pos = new Point(0, 0);
        stance = 0;
        fh = 0;
    }

    public static MaplePet loadFromDb(int itemid, int petid) {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            MaplePet ret = new MaplePet(itemid, petid);

            ps = con.prepareStatement("SELECT * FROM inventory_pets WHERE PetID = ?");
            ps.setInt(1, petid);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret.index = rs.getByte("PetPosition");
                ret.name = rs.getString("Name");
                ret.level = rs.getInt("Level");
                ret.closeness = rs.getInt("Closeness");
                ret.fullness = rs.getInt("Fullness");
                ret.deadDate = rs.getTimestamp("DeadDate");
            } else {
                ret = null;
            }
            rs.close();
            ps.close();

            return ret;
        } catch (Exception e) {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException ex) {
                Logger.getLogger(MaplePet.class.getName()).log(Level.SEVERE, null, ex);
            }
            return null;
        }
    }

    public static MaplePet createPet(int cid, int itemid) {
        int ret = -1;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO inventory_pets (CharacterID, Name, Level, Closeness, Fullness) VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, cid);
            ps.setString(2, MapleItemInformationProvider.getInstance().getName(itemid));
            ps.setInt(3, 1);
            ps.setInt(4, 0);
            ps.setInt(5, 100);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            ret = rs.getInt(1);
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            Logger.getLogger(MaplePet.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        final MaplePet pet = new MaplePet(itemid, ret);
        pet.name = MapleItemInformationProvider.getInstance().getName(itemid);
        pet.level = 1;
        pet.closeness = 0;
        pet.fullness = 100;
        return pet;
    }

    public static MaplePet updateExisting(int cid, MaplePet pet) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE inventory_pets SET CharacterID = ? WHERE PetId = ?");
            ps.setInt(1, cid);
            ps.setInt(2, pet.petId);
            ps.close();
        } catch (SQLException ex) {
            return null;
        }
        return pet;
    }

    public void saveToDb() {
        try {
            Connection con = DatabaseConnection.getConnection(); // Get a connection to the database
            PreparedStatement ps = con.prepareStatement("UPDATE inventory_pets SET PetPosition = ?, Name = ?, Level = ?, Closeness = ?, Fullness = ?, DeadDate = ? WHERE PetID = ?");
            ps.setByte(1, index);
            ps.setString(2, name);
            ps.setInt(3, level);
            ps.setInt(4, closeness);
            ps.setInt(5, fullness);
            ps.setTimestamp(6, deadDate);
            ps.setInt(7, petId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            Logger.getLogger(MaplePet.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public byte getIndex() {
        return index;
    }

    public void setIndex(byte index) {
        this.index = index;
    }

    public int getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getUniqueId() {
        return petId;
    }

    public void setUniqueId(int id) {
        petId = id;
    }

    public int getCloseness() {
        return closeness;
    }

    public void setCloseness(int closeness) {
        this.closeness = closeness;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getFullness() {
        return fullness;
    }

    public void setFullness(int fullness) {
        this.fullness = fullness;
    }

    public Timestamp getDeadDate() {
        return deadDate;
    }

    public void setDeadDate(Timestamp date) {
        deadDate = date;
    }

    public int getFh() {
        return fh;
    }

    public void setFh(int Fh) {
        fh = Fh;
    }

    public Point getPos() {
        return pos;
    }

    public void setPos(Point pos) {
        this.pos = pos;
    }

    public int getStance() {
        return stance;
    }

    public void setStance(int stance) {
        this.stance = stance;
    }

    public boolean hasLabelRing() {
        return labelRing;
    }

    public void setLabelRing(boolean equipped) {
        labelRing = equipped;
    }

    public boolean hasQuoteRing() {
        return quoteRing;
    }

    public void setQuoteRing(boolean equipped) {
        quoteRing = equipped;
    }

    public boolean canConsume(int itemId) {
        MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
        for (int petItemId : mii.petsCanConsume(itemId)) {
            if (petItemId == this.itemId) {
                return true;
            }
        }
        return false;
    }

    public void updatePosition(List<LifeMovementFragment> movement) {
        for (LifeMovementFragment move : movement) {
            if (move instanceof LifeMovement) {
                if (move instanceof AbsoluteLifeMovement) {
                    pos = move.getPosition();
                }
                stance = ((LifeMovement) move).getStance();
            }
        }
    }
}