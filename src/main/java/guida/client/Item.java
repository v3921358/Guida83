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

import guida.tools.FileTimeUtil;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Item implements IItem {

    private final int id;
    protected List<String> log;
    private short prevPosition = -1;
    private short position = Byte.MAX_VALUE;
    private short quantity;
    private int petid = -1;
    private String owner = "";
    private Timestamp expiration = FileTimeUtil.getDefaultTimestamp();
    private int uniqueid;
    private int sn;
    private short flag = 0;
    private boolean isByGM = false;
    private byte storagePosition = 0;
    private MaplePet pet = null;

    public Item(int id, short position, short quantity) {
        super();
        this.id = id;
        this.position = position;
        this.quantity = quantity;
        log = new LinkedList<>();
    }

    public Item(int id, short position, short quantity, short flag) {
        super();
        this.id = id;
        this.position = position;
        this.quantity = quantity;
        this.flag = flag;
        log = new LinkedList<>();
    }

	/*public static void loadInitialDataFromDB() {
        try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT MAX(inventoryitemid) " + "FROM inventoryitems");
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				lastOID = rs.getInt(1);
			} else {
				throw new DatabaseException("Could not retrieve current item OID");
			}
		} catch (SQLException e) {
			log.error(e.toString());
		}
	}*/

    public IItem copy() {
        Item ret = new Item(id, position, quantity, flag);
        ret.isByGM = isByGM;
        ret.owner = owner;
        ret.expiration = expiration;
        ret.prevPosition = prevPosition;
        ret.petid = petid;
        ret.pet = pet;
        ret.log = new LinkedList<>(log);
        return ret;
    }

    public void setPosition(short position) {
        if (this.position != Byte.MAX_VALUE) {
            prevPosition = this.position;
        }
        this.position = position;
    }

    public void setPrevPosition(short position) {
        prevPosition = position;
    }

    public void setQuantity(short quantity) {
        this.quantity = quantity;
    }

    @Override
    public int getItemId() {
        return id;
    }

    @Override
    public short getPosition() {
        return position;
    }

    @Override
    public short getPrevPosition() {
        return prevPosition;
    }

    @Override
    public short getQuantity() {
        return quantity;
    }

    @Override
    public byte getType() {
        return IItem.ITEM;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Override
    public int getPetId() {
        return petid;
    }

    @Override
    public short getFlag() {
        if (isSSOneOfAKind()) {
            return (short) (flag ^ 32);
        }
        return flag;
    }

    @Override
    public void setFlag(short flag) {
        boolean ooak = isSSOneOfAKind();
        this.flag = flag;
        if (ooak && !isSSOneOfAKind()) {
            this.flag |= 32;
        }
    }

    @Override
    public int compareTo(IItem other) {
        return Integer.compare(Math.abs(position), Math.abs(other.getPosition()));
    }

    @Override
    public String toString() {
        return "Item: " + id + " quantity: " + quantity;
    }

    // no op for now as it eats too much ram :( once we have persistent inventoryids we can reenable it in some form.
    @Override
    public void log(String msg, boolean fromDB) {
		/*if (!fromDB) {
			StringBuilder toLog = new StringBuilder("[");
			toLog.append(Calendar.getInstance().getTime().toString());
			toLog.append("] ");
			toLog.append(msg);
			log.add(toLog.toString());
		} else {
			log.add(msg);
		}*/
    }

    @Override
    public List<String> getLog() {
        return Collections.unmodifiableList(log);
    }

    @Override
    public Timestamp getExpiration() {
        return expiration;
    }

    @Override
    public void setExpiration(Timestamp expire) {
        expiration = expire;
    }

    @Override
    public int getSN() {
        return sn;
    }

    @Override
    public void setSN(int sn) {
        this.sn = sn;
    }

    @Override
    public int getUniqueId() {
        return uniqueid;
    }

    @Override
    public void setGMFlag() {
        isByGM = true;
    }

    @Override
    public void setUniqueId(int id) {
        uniqueid = id;
    }

    @Override
    public boolean isByGM() {
        return isByGM;
    }

    @Override
    public void setSSOneOfAKind(boolean sets) {
        if (isSSOneOfAKind() && !sets) {
            flag ^= 32;
        }
        if (!isSSOneOfAKind() && sets) {
            flag |= 32;
        }
    }

    @Override
    public boolean isSSOneOfAKind() {
        return (flag & 32) == 32;
    }

    @Override
    public void setStoragePosition(byte position) {
        storagePosition = position;
    }

    @Override
    public byte getStoragePosition() {
        return storagePosition;
    }

    @Override
    public void setPet(MaplePet pet) {
        this.pet = pet;
        petid = pet.getUniqueId();
    }

    @Override
    public MaplePet getPet() {
        return pet;
    }
}