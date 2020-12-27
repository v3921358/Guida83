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

package guida.server.playerinteractions;

import guida.client.Equip;
import guida.client.IEquip;
import guida.client.IItem;
import guida.client.Item;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.database.DatabaseConnection;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleStorage {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleStorage.class);
    private final int id;
    private final List<IItem> items;
    private final byte slots;
    //private Set<MapleInventoryType> updatedTypes = new HashSet<MapleInventoryType>();
    private final Map<MapleInventoryType, List<IItem>> typeItems = new EnumMap<>(MapleInventoryType.class);
    private int meso;

    private MapleStorage(int id, byte slots, int meso) {
        this.id = id;
        this.slots = slots;
        items = new LinkedList<>();
        this.meso = meso;
    }

    public static MapleStorage create(int id) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO storages (accountid, slots, meso) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            ps.setInt(2, 16);
            ps.setInt(3, 0);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            log.error("Error creating storage", ex);
        }
        return loadOrCreateFromDB(id);
    }

    public static MapleStorage loadOrCreateFromDB(int id) {
        MapleStorage ret = null;
        int storageID;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM storages WHERE accountid = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                ps.close();
                return create(id);
            } else {
                storageID = rs.getInt("storageid");
                ret = new MapleStorage(storageID, (byte) rs.getInt("slots"), rs.getInt("meso"));
                rs.close();
                ps.close();
                ps = con.prepareStatement("SELECT * FROM storage_eqp WHERE StorageID = ? ORDER BY StoragePosition");
                ps.setInt(1, storageID);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Equip equip = new Equip(rs.getInt("ItemID"), (byte) rs.getInt("Position"), rs.getInt("RingID"));
                    equip.setStoragePosition(rs.getByte("StoragePosition"));
                    equip.setStr(rs.getShort("STR"));
                    equip.setDex(rs.getShort("DEX"));
                    equip.setInt(rs.getShort("INT"));
                    equip.setLuk(rs.getShort("LUK"));
                    equip.setHp(rs.getShort("MaxHP"));
                    equip.setMp(rs.getShort("MaxMP"));
                    equip.setWatk(rs.getShort("PAD"));
                    equip.setMatk(rs.getShort("MAD"));
                    equip.setWdef(rs.getShort("PDD"));
                    equip.setMdef(rs.getShort("MDD"));
                    equip.setAcc(rs.getShort("ACC"));
                    equip.setAvoid(rs.getShort("EVA"));
                    equip.setHands(rs.getShort("Hands"));
                    equip.setSpeed(rs.getShort("Speed"));
                    equip.setJump(rs.getShort("Jump"));
                    equip.setViciousHammers(rs.getByte("ViciousHammers"));
                    equip.setLevel(rs.getByte("Level"));
                    equip.setUpgradeSlots(rs.getByte("RemainingSlots"));
                    equip.setExpiration(rs.getTimestamp("ExpireDate"));
                    equip.setOwner(rs.getString("Owner"));
                    equip.setFlag(rs.getByte("Flag"));
                    if (rs.getByte("IsGM") == 1) {
                        equip.setGMFlag();
                    }
                    ret.items.add(equip);
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("SELECT * FROM storage_use WHERE StorageID = ? ORDER BY StoragePosition");
                ps.setInt(1, storageID);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                    item.setStoragePosition(rs.getByte("StoragePosition"));
                    item.setOwner(rs.getString("Owner"));
                    item.setExpiration(rs.getTimestamp("ExpireDate"));
                    if (rs.getByte("IsGM") == 1) {
                        item.setGMFlag();
                    }
                    ret.items.add(item);
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("SELECT * FROM storage_setup WHERE StorageID = ? ORDER BY StoragePosition");
                ps.setInt(1, storageID);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                    item.setStoragePosition(rs.getByte("StoragePosition"));
                    item.setOwner(rs.getString("Owner"));
                    item.setExpiration(rs.getTimestamp("ExpireDate"));
                    if (rs.getByte("IsGM") == 1) {
                        item.setGMFlag();
                    }
                    ret.items.add(item);
                }
                rs.close();
                ps.close();
                ps = con.prepareStatement("SELECT * FROM storage_etc WHERE StorageID = ? ORDER BY StoragePosition");
                ps.setInt(1, storageID);
                rs = ps.executeQuery();
                while (rs.next()) {
                    Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                    item.setStoragePosition(rs.getByte("StoragePosition"));
                    item.setOwner(rs.getString("Owner"));
                    item.setExpiration(rs.getTimestamp("ExpireDate"));
                    if (rs.getByte("IsGM") == 1) {
                        item.setGMFlag();
                    }
                    ret.items.add(item);
                }
                rs.close();
                ps.close();
                /*ps = con.prepareStatement("SELECT * FROM inventoryitems LEFT JOIN inventoryequipment USING (inventoryitemid) WHERE storageid = ?");
				ps.setInt(1, storeId);
				rs = ps.executeQuery();
				while (rs.next()) {
					MapleInventoryType type = MapleInventoryType.getByType((byte) rs.getInt("inventorytype"));
					if (type.equals(MapleInventoryType.EQUIP) || type.equals(MapleInventoryType.EQUIPPED)) {
						int itemid = rs.getInt("itemid");
						Equip equip = new Equip(itemid, (byte) rs.getInt("position"), rs.getInt("ringid"));
						equip.setOwner(rs.getString("owner"));
						equip.setFlag((short) rs.getInt("flag"));
						equip.setExpiration(rs.getTimestamp("ExpireDate"));
						equip.setQuantity((short) rs.getInt("quantity"));
						equip.setAcc((short) rs.getInt("acc"));
						equip.setAvoid((short) rs.getInt("avoid"));
						equip.setDex((short) rs.getInt("dex"));
						equip.setHands((short) rs.getInt("hands"));
						equip.setHp((short) rs.getInt("hp"));
						equip.setInt((short) rs.getInt("int"));
						equip.setJump((short) rs.getInt("jump"));
						equip.setLuk((short) rs.getInt("luk"));
						equip.setMatk((short) rs.getInt("matk"));
						equip.setMdef((short) rs.getInt("mdef"));
						equip.setMp((short) rs.getInt("mp"));
						equip.setSpeed((short) rs.getInt("speed"));
						equip.setStr((short) rs.getInt("str"));
						equip.setWatk((short) rs.getInt("watk"));
						equip.setWdef((short) rs.getInt("wdef"));
						equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
						equip.setLevel((byte) rs.getInt("level"));
						equip.setViciousHammers(rs.getInt("hammer"));
						ret.items.add(equip);
					} else {
						Item item = new Item(rs.getInt("itemid"), (byte) rs.getInt("position"), (short) rs.getInt("quantity"), (byte) rs.getInt("flag"), rs.getInt("petid"));
						item.setOwner(rs.getString("owner"));
						item.setExpiration(rs.getTimestamp("ExpireDate"));
						ret.items.add(item);
					}
				}
				rs.close();
				ps.close();*/
            }
        } catch (SQLException ex) {
            log.error("Error loading storage", ex);
        }
        return ret;
    }

    public void saveToDB() {
        byte position = 0;
        try {
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE storages SET slots = ?, meso = ? WHERE storageid = ?");
            ps.setInt(1, slots);
            ps.setInt(2, meso);
            ps.setInt(3, id);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("DELETE FROM storage_eqp WHERE StorageID = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO storage_eqp (StorageID, StoragePosition, ItemID, Position, STR, DEX, `INT`, LUK, MaxHP, MaxMP, PAD, MAD, PDD, MDD, ACC, EVA, Hands, Speed, Jump, ViciousHammers, Level, RemainingSlots, ExpireDate, Owner, Flag, RingID, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : items) {
                if (ii.getInventoryType(item.getItemId()).equals(MapleInventoryType.EQUIP)) {
                    IEquip equip = (IEquip) item;
                    ps.setByte(2, position);
                    ps.setInt(3, item.getItemId());
                    ps.setShort(4, item.getPosition());
                    ps.setShort(5, equip.getStr());
                    ps.setShort(6, equip.getDex());
                    ps.setShort(7, equip.getInt());
                    ps.setShort(8, equip.getLuk());
                    ps.setShort(9, equip.getHp());
                    ps.setShort(10, equip.getMp());
                    ps.setShort(11, equip.getWatk());
                    ps.setShort(12, equip.getMatk());
                    ps.setShort(13, equip.getWdef());
                    ps.setShort(14, equip.getMdef());
                    ps.setShort(15, equip.getAcc());
                    ps.setShort(16, equip.getAvoid());
                    ps.setShort(17, equip.getHands());
                    ps.setShort(18, equip.getSpeed());
                    ps.setShort(19, equip.getJump());
                    ps.setByte(20, (byte) equip.getViciousHammers());
                    ps.setByte(21, equip.getLevel());
                    ps.setByte(22, equip.getUpgradeSlots());
                    ps.setTimestamp(23, item.getExpiration());
                    ps.setString(24, item.getOwner());
                    ps.setByte(25, (byte) item.getFlag());
                    ps.setInt(26, equip.getRingId());
                    ps.setByte(27, (byte) (equip.isByGM() ? 1 : 0));
                    ps.addBatch();
                    position++;
                }
            }
            ps.executeBatch();
            ps.close();

            ps = con.prepareStatement("DELETE FROM storage_use WHERE StorageID = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO storage_use (StorageID, StoragePosition, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : items) {
                if (ii.getInventoryType(item.getItemId()).equals(MapleInventoryType.USE)) {
                    ps.setByte(2, position);
                    ps.setInt(3, item.getItemId());
                    ps.setShort(4, item.getPosition());
                    ps.setShort(5, item.getQuantity());
                    ps.setTimestamp(6, item.getExpiration());
                    ps.setString(7, item.getOwner());
                    ps.setByte(8, (byte) item.getFlag());
                    ps.setByte(9, (byte) (item.isByGM() ? 1 : 0));
                    ps.addBatch();
                    position++;
                }
            }
            ps.executeBatch();
            ps.close();

            ps = con.prepareStatement("DELETE FROM storage_setup WHERE StorageID = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO storage_setup (StorageID, StoragePosition, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : items) {
                if (ii.getInventoryType(item.getItemId()).equals(MapleInventoryType.SETUP)) {
                    ps.setByte(2, position);
                    ps.setInt(3, item.getItemId());
                    ps.setShort(4, item.getPosition());
                    ps.setShort(5, item.getQuantity());
                    ps.setTimestamp(6, item.getExpiration());
                    ps.setString(7, item.getOwner());
                    ps.setByte(8, (byte) item.getFlag());
                    ps.setByte(9, (byte) (item.isByGM() ? 1 : 0));
                    ps.addBatch();
                    position++;
                }
            }
            ps.executeBatch();
            ps.close();

            ps = con.prepareStatement("DELETE FROM storage_etc WHERE StorageID = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO storage_etc (StorageID, StoragePosition, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : items) {
                if (ii.getInventoryType(item.getItemId()).equals(MapleInventoryType.ETC)) {
                    ps.setByte(2, position);
                    ps.setInt(3, item.getItemId());
                    ps.setShort(4, item.getPosition());
                    ps.setShort(5, item.getQuantity());
                    ps.setTimestamp(6, item.getExpiration());
                    ps.setString(7, item.getOwner());
                    ps.setByte(8, (byte) item.getFlag());
                    ps.setByte(9, (byte) (item.isByGM() ? 1 : 0));
                    ps.addBatch();
                    position++;
                }
            }
            ps.executeBatch();
            ps.close();
        } catch (SQLException ex) {
            log.error("Error saving storage", ex);
        }
    }

    public IItem takeOut(byte slot) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem ret = items.remove(slot);
        MapleInventoryType type = ii.getInventoryType(ret.getItemId());
        typeItems.put(type, new ArrayList<>(filterItems(type)));
        return ret;
    }

    public IItem itemAt(byte slot) {
        return items.get(slot);
    }

    public void store(IItem item) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        items.add(item);
        MapleInventoryType type = ii.getInventoryType(item.getItemId());
        typeItems.put(type, new ArrayList<>(filterItems(type)));
    }

    public List<IItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    private List<IItem> filterItems(MapleInventoryType type) {
        List<IItem> ret = new LinkedList<>();
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (IItem item : items) {
            if (ii.getInventoryType(item.getItemId()) == type) {
                ret.add(item);
            }
        }
        return ret;
    }

    public byte getSlot(MapleInventoryType type, byte slot) {
        // MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        byte ret = 0;
        for (IItem item : items) {
            if (item == typeItems.get(type).get(slot)) {
                return ret;
            }
            ret++;
        }
        return -1;
    }

    public void sendStorage(MapleClient c, int npcId) {
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        // sort by inventorytype to avoid confusion
        items.sort((o1, o2) -> {
            if (ii.getInventoryType(o1.getItemId()).getType() < ii.getInventoryType(o2.getItemId()).getType()) {
                return -1;
            } else if (ii.getInventoryType(o1.getItemId()) == ii.getInventoryType(o2.getItemId())) {
                return 0;
            } else {
                return 1;
            }
        });
        for (MapleInventoryType type : MapleInventoryType.values()) {
            typeItems.put(type, new ArrayList<>(items));
        }
        c.sendPacket(MaplePacketCreator.getStorage(npcId, slots, items, meso));
    }

    public void sendStored(MapleClient c, MapleInventoryType type) {
        c.sendPacket(MaplePacketCreator.storeStorage(slots, type, typeItems.get(type)));
    }

    public void sendTakenOut(MapleClient c, MapleInventoryType type) {
        c.sendPacket(MaplePacketCreator.takeOutStorage(slots, type, typeItems.get(type)));
    }

    public int getMeso() {
        return meso;
    }

    public void setMeso(int meso) {
        if (meso < 0) {
            throw new RuntimeException();
        }
        this.meso = meso;
    }

    public void sendMeso(MapleClient c) {
        c.sendPacket(MaplePacketCreator.mesoStorage(slots, meso));
    }

    public boolean isFull() {
        return items.size() >= slots;
    }

    public void close() {
        typeItems.clear();
    }
}