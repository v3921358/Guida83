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
import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.server.maps.AbstractMapleMapObject;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;

import java.lang.ref.WeakReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

/**
 * @author XoticStory
 */
public abstract class AbstractPlayerStore extends AbstractMapleMapObject implements IMaplePlayerShop {

    protected final List<MaplePlayerShopItem> items = new LinkedList<>();
    private final String ownerName;
    private final int ownerId;
    private final int itemId;
    protected WeakReference<MapleCharacter> chr1 = new WeakReference<>(null);
    protected WeakReference<MapleCharacter> chr2 = new WeakReference<>(null);
    protected WeakReference<MapleCharacter> chr3 = new WeakReference<>(null);
    private String description = "";
    private boolean spawned;

    public AbstractPlayerStore(MapleCharacter owner, int itemId, String desc) {
        setPosition(owner.getPosition());
        ownerName = owner.getName();
        ownerId = owner.getId();
        this.itemId = itemId;
        description = desc;
    }

    @Override
    public boolean isSpawned() {
        return spawned;
    }

    @Override
    public void spawned() {
        spawned = true;
    }

    @Override
    public boolean isSoldOut() {
        boolean soldOut = true;
        for (MaplePlayerShopItem item : items) {
            if (item.getBundles() > 0) {
                soldOut = false;
                break;
            }
        }
        return soldOut;
    }

    public void broadcastToVisitors(MaplePacket packet, boolean owner) {
        MapleCharacter chr = chr1.get();
        if (chr != null) {
            chr.getClient().sendPacket(packet);
        }
        chr = chr2.get();
        if (chr != null) {
            chr.getClient().sendPacket(packet);
        }
        chr = chr3.get();
        if (chr != null) {
            chr.getClient().sendPacket(packet);
        }
        if (getShopType() == 2 && owner) {
            ((MaplePlayerShop) this).getMCOwner().getClient().sendPacket(packet);
        }
    }

    @Override
    public void broadcastToVisitors(MaplePacket packet) {
        broadcastToVisitors(packet, true);
    }

    @Override
    public void removeVisitor(MapleCharacter visitor) {
        int slot = getVisitorSlot(visitor);
        boolean shouldUpdate = getFreeSlot() == -1;
        if (slot > -1) {
            //broadcastToVisitors(MaplePacketCreator.shopVisitorLeave(slot));
            switch (slot) {
                case 1 -> chr1 = new WeakReference<>(null);
                case 2 -> chr2 = new WeakReference<>(null);
                case 3 -> chr3 = new WeakReference<>(null);
            }
            if (shouldUpdate) {
                if (getShopType() == 1) {
                    ((HiredMerchant) this).getMap().broadcastMessage(MaplePacketCreator.updateHiredMerchant((HiredMerchant) this));
                } else {
                    ((MaplePlayerShop) this).getMCOwner().getMap().broadcastMessage(MaplePacketCreator.sendPlayerShopBox(((MaplePlayerShop) this).getMCOwner())); // Contains Cast Error (MaplePlayerShop can not be applied to 'this')
                }
            }
        }
    }

    public void removeVisitors() {
        MapleCharacter chr = chr1.get();
        if (chr != null) {
            removeVisitor(chr);
        }
        chr = chr2.get();
        if (chr != null) {
            removeVisitor(chr);
        }
        chr = chr3.get();
        if (chr != null) {
            removeVisitor(chr);
        }
    }

    @Override
    public void addVisitor(MapleCharacter visitor) {
        int i = getFreeSlot();
        if (i != -1) {
            //broadcastToVisitors(MaplePacketCreator.shopVisitorAdd(visitor, i));
            switch (i) {
                case 1 -> chr1 = new WeakReference<>(visitor);
                case 2 -> chr2 = new WeakReference<>(visitor);
                case 3 -> chr3 = new WeakReference<>(visitor);
            }
            if (i == 3) {
                if (getShopType() == 1) {
                    ((HiredMerchant) this).getMap().broadcastMessage(MaplePacketCreator.updateHiredMerchant((HiredMerchant) this));
                } else {
                    ((MaplePlayerShop) this).getMCOwner().getMap().broadcastMessage(MaplePacketCreator.sendPlayerShopBox(((MaplePlayerShop) this).getMCOwner()));
                }
            }
        }
    }

    @Override
    public int getVisitorSlot(MapleCharacter visitor) {
        MapleCharacter chr = chr1.get();
        if (chr == visitor) {
            return 1;
        }
        chr = chr2.get();
        if (chr == visitor) {
            return 2;
        }
        chr = chr3.get();
        if (chr == visitor) {
            return 3;
        }
        return -1;
    }

    public MapleCharacter getVisitor(int num) {
        return switch (num) {
            case 1 -> chr1.get();
            case 2 -> chr2.get();
            case 3 -> chr3.get();
            default -> null;
        };
    }

    @Override
    public void removeAllVisitors(int error, int type) {
        for (int i = 1; i < 4; i++) {
            MapleCharacter visitor = getVisitor(i);
            if (visitor != null) {
                if (type != -1) {
                    visitor.getClient().sendPacket(MaplePacketCreator.shopErrorMessage(error, type));
                }
                visitor.setPlayerShop(null);

                switch (i) {
                    case 1 -> chr1 = new WeakReference<>(null);
                    case 2 -> chr2 = new WeakReference<>(null);
                    case 3 -> chr3 = new WeakReference<>(null);
                }
            }
        }
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public int getOwnerId() {
        return ownerId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String d) {
        description = d;
        if (getShopType() == 1) {
            ((HiredMerchant) this).getMap().broadcastMessage(MaplePacketCreator.updateHiredMerchant((HiredMerchant) this));
        } else {
            ((MaplePlayerShop) this).getMCOwner().getMap().broadcastMessage(MaplePacketCreator.sendPlayerShopBox(((MaplePlayerShop) this).getMCOwner())); // Contains Cast Error (MaplePlayerShop can not be applied to 'this')
        }
    }

    @Override
    public List<Pair<Byte, MapleCharacter>> getVisitors() {
        List<Pair<Byte, MapleCharacter>> chrs = new LinkedList<>();
        MapleCharacter chr = chr1.get();
        if (chr != null) {
            chrs.add(new Pair<>((byte) 1, chr));
        }
        chr = chr2.get();
        if (chr != null) {
            chrs.add(new Pair<>((byte) 2, chr));
        }
        chr = chr3.get();
        if (chr != null) {
            chrs.add(new Pair<>((byte) 3, chr));
        }
        return chrs;
    }

    @Override
    public List<MaplePlayerShopItem> getItems() {
        return items;
    }

    @Override
    public void addItem(MaplePlayerShopItem item) throws SQLException {
        items.add(item);
        int setId = saveItem(item);
        item.setId(setId);
    }

    @Override
    public boolean removeItem(int item) throws SQLException {
        synchronized (items) {
            if (items.get(item) != null) {
                deleteItem(items.get(item));
                items.remove(item);
                return true;
            }
            return false;
        }
    }

    @Override
    public void removeFromSlot(int slot) {
        items.remove(slot);
    }

    @Override
    public int getFreeSlot() {
        MapleCharacter chr = chr1.get();
        if (chr == null) {
            return 1;
        }
        chr = chr2.get();
        if (chr == null) {
            return 2;
        }
        chr = chr3.get();
        if (chr == null) {
            return 3;
        }
        return -1;
    }

    @Override
    public int getItemId() {
        return itemId;
    }

    @Override
    public boolean isOwner(MapleCharacter chr) {
        return chr.getId() == ownerId && chr.getName().equals(ownerName);
    }

    @Override
    public void makeAvailableAtFred() {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE hiredmerchant SET onSale = false WHERE id = ?");
            for (MaplePlayerShopItem pItem : items) {
                if (pItem.getId() != -1) {
                    ps.setInt(1, pItem.getId());
                    ps.executeUpdate();
                }
            }
            ps.close();
            items.clear();
        } catch (Exception e) {
            System.err.println("Error making item available at Frederick!");
            e.printStackTrace();
        }
    }

    public int saveItem(MaplePlayerShopItem pItem) throws SQLException {
        PreparedStatement ps;
        Connection con = DatabaseConnection.getConnection();
        int setKeyId = -1;
        if (pItem.getBundles() > 0) {
            if (pItem.getItem().getType() == 1) {
                ps = con.prepareStatement("INSERT INTO hiredmerchant (ownerid, itemid, quantity, upgradeslots, level, str, dex, `int`, luk, hp, mp, watk, matk, wdef, mdef, acc, avoid, hands, speed, jump, hammer, flag, ExpireDate, owner, type, onSale) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                Equip eq = (Equip) pItem.getItem();
                ps.setInt(2, eq.getItemId());
                ps.setInt(3, 1);
                ps.setInt(4, eq.getUpgradeSlots());
                ps.setInt(5, eq.getLevel());
                ps.setInt(6, eq.getStr());
                ps.setInt(7, eq.getDex());
                ps.setInt(8, eq.getInt());
                ps.setInt(9, eq.getLuk());
                ps.setInt(10, eq.getHp());
                ps.setInt(11, eq.getMp());
                ps.setInt(12, eq.getWatk());
                ps.setInt(13, eq.getMatk());
                ps.setInt(14, eq.getWdef());
                ps.setInt(15, eq.getMdef());
                ps.setInt(16, eq.getAcc());
                ps.setInt(17, eq.getAvoid());
                ps.setInt(18, eq.getHands());
                ps.setInt(19, eq.getSpeed());
                ps.setInt(20, eq.getJump());
                ps.setInt(21, eq.getViciousHammers());
                ps.setInt(22, eq.getFlag());
                ps.setTimestamp(23, eq.getExpiration());
                ps.setString(24, eq.getOwner());
                ps.setInt(25, 1);
                ps.setBoolean(26, true);
            } else {
                ps = con.prepareStatement("INSERT INTO hiredmerchant (ownerid, itemid, quantity, flag, ExpireDate, owner, type, onSale) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                IItem item = pItem.getItem();
                ps.setInt(2, item.getItemId());
                ps.setInt(3, item.getQuantity() * pItem.getBundles());
                ps.setInt(4, item.getFlag());
                ps.setTimestamp(5, item.getExpiration());
                ps.setString(6, item.getOwner());
                ps.setInt(7, 2);
                ps.setBoolean(8, true);
            }
            ps.setInt(1, ownerId);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                setKeyId = rs.getInt(1);
            }
            ps.close();
        }
        return setKeyId;
    }

    @Override
    public void updateItem(MaplePlayerShopItem pItem) throws SQLException {
        PreparedStatement ps;
        Connection con = DatabaseConnection.getConnection();
        if (pItem.getBundles() > 0) {
            ps = con.prepareStatement("UPDATE hiredmerchant SET quantity = ? WHERE id = ?");
            ps.setInt(1, pItem.getItem().getQuantity() * pItem.getBundles());
            ps.setInt(2, pItem.getId());
            ps.executeUpdate();
            ps.close();
        } else {
            deleteItem(pItem);
        }
    }

    public void deleteItem(MaplePlayerShopItem pItem) throws SQLException {
        if (pItem.getId() != -1) {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("DELETE FROM hiredmerchant WHERE id = ?");
            ps.setInt(1, pItem.getId());
            ps.executeUpdate();
            ps.close();
        }
    }
}