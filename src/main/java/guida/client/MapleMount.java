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
import guida.provider.MapleData;
import guida.provider.MapleDataProviderFactory;
import guida.server.TimerManager;
import guida.tools.MaplePacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ScheduledFuture;

/**
 * @author iamSTEVE
 */
public class MapleMount {

    private final MapleCharacter owner;
    private final int inventoryitemId;
    private int mountid;
    private int itemid, level, exp, tiredness;
    private ScheduledFuture<?> tirednessSchedule;

    public MapleMount(MapleCharacter owner, int itemid, int inventoryitemId) {
        this.owner = owner;
        this.itemid = itemid;
        this.inventoryitemId = inventoryitemId;
        level = 1;
        tiredness = 0;
        exp = 0;
    }

    public int getId() {
        MapleData data = MapleDataProviderFactory.getDataProvider("Character").getData("TamingMob/0" + itemid + ".img");
        MapleData tamingMob = data.resolve("info/tamingMob");

        if (tamingMob != null) {
            return Integer.parseInt(tamingMob.getData().toString());
        } else {
            return 0;
        }
    }

    public int getTiredness() {
        return tiredness;
    }

    public int getExp() {
        return exp;
    }

    public int getLevel() {
        return level;
    }

    public void setTiredness(int newtiredness) {
        tiredness = newtiredness;
        if (tiredness < 0) {
            tiredness = 0;
        }
    }

    public void increaseTiredness() {
        tiredness++;
        owner.getMap().broadcastMessage(MaplePacketCreator.updateMount(owner, this, false));
        if (tiredness > 100) {
            owner.dispelSkill(1004);
            owner.dispelSkill(10001004);
            owner.dispelSkill(20001004);
        }
    }

    public void setExp(int newexp) {
        exp = newexp;
    }

    public void setLevel(int newlevel) {
        level = newlevel;
    }

    public void setItemId(int newitemid) {
        itemid = newitemid;
    }

    public int getItemId() {
        return itemid;
    }

    public void startTirednessSchedule() {
        tirednessSchedule = TimerManager.getInstance().register(() -> increaseTiredness(), 60000, 60000);
    }

    public void cancelTirednessSchedule() {
        tirednessSchedule.cancel(false);
        tirednessSchedule = null;
    }

    public void saveToDb() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE mounts SET ownerid = ?, level = ?, tiredness = ?, exp = ?, inventoryItemId = ? WHERE id = ?");
            ps.setInt(1, owner.getId());
            ps.setInt(2, level);
            ps.setInt(3, tiredness);
            ps.setInt(4, exp);
            ps.setInt(5, inventoryitemId);
            ps.setInt(6, mountid);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            System.out.println("There was an error with saving the mount to the database.");
        }
    }
}