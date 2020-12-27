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

import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.TimerManager;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapObjectType;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * @author XoticStory
 */
public class HiredMerchant extends AbstractPlayerStore {

    private static final HashMap<Integer, HiredMerchant> allMerchants = new HashMap<>(); //Used for Minerva and Remote Shop Controller
    private final MapleMap map;
    private final MapleCharacter owner;
    private final int channel;
    public ScheduledFuture<?> schedule = null;
    private boolean open;

    public HiredMerchant(MapleCharacter owner, int itemId, String desc) {
        super(owner, itemId, desc);
        map = owner.getMap();
        this.owner = owner;
        channel = owner.getClient().getChannel();
        allMerchants.put(owner.getId(), this);
        schedule = TimerManager.getInstance().schedule(() -> {
            HiredMerchant.this.closeShop();
            HiredMerchant.this.makeAvailableAtFred();
        }, 1000 * 60 * 60 * 24);
    }

    public static HiredMerchant getMerchantByOwner(MapleCharacter chr) {
        if (allMerchants.containsKey(chr.getId())) {
            return allMerchants.get(chr.getId());
        }
        return null;
    }

    public static HiredMerchant getMerchByOwner(int id) {
        if (allMerchants.containsKey(id)) {
            return allMerchants.get(id);
        }
        return null;
    }

    public static HashMap<Integer, HiredMerchant> getAllMerchants() {
        return allMerchants;
    }

    public static List<Pair<HiredMerchant, MaplePlayerShopItem>> getMerchsByIId(int itemid, boolean onSale) {
        final List<Pair<HiredMerchant, MaplePlayerShopItem>> ret = new ArrayList<>();
        for (HiredMerchant merch : allMerchants.values()) {
            for (MaplePlayerShopItem item : merch.items) {
                if (item.getItem().getItemId() == itemid && (!onSale || item.getBundles() > 0)) {
                    ret.add(new Pair<>(merch, item));
                }
            }
        }
        return ret;
    }

    public static void clearMerchants() {
        allMerchants.clear();
    }

    public int getChannel() {
        return channel;
    }

    public List<MaplePlayerShopItem> getItemsById(int itemid) {
        ArrayList<MaplePlayerShopItem> ret = new ArrayList<>();

        for (MaplePlayerShopItem mpsi : items) {
            if (mpsi.getItem().getItemId() == itemid) {
                ret.add(mpsi);
            }

        }
        return ret;
    }

    public byte getShopType() {
        return IMaplePlayerShop.HIRED_MERCHANT;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
        MaplePlayerShopItem pItem = items.get(item);
        if (pItem.getBundles() > 0) {
            synchronized (items) {
                IItem newItem = pItem.getItem().copy();
                int perBundle = newItem.getQuantity();
                short final2 = (short) (quantity * perBundle);
                if (final2 < 0) {
                    c.getPlayer().dropMessage(1, "You cannot buy so many. Please buy less.");
                    return;
                }
                if (MapleItemInformationProvider.getInstance().canHaveOnlyOne(newItem.getItemId()) && c.getPlayer().haveItem(newItem.getItemId(), 1, true, false)) {
                    c.getPlayer().dropMessage(1, "You already have this item and it's ONE of A KIND!");
                    return;
                }
                newItem.setQuantity(final2);
                if (c.getPlayer().getMeso() >= pItem.getPrice() * quantity) {
                    if (MapleInventoryManipulator.checkSpace(c, newItem.getItemId(), quantity, "")) {
                        MapleInventoryManipulator.addByItem(c, newItem, "", true);
                        Connection con = DatabaseConnection.getConnection();
                        try {
                            PreparedStatement ps = con.prepareStatement("UPDATE characters set MerchantMesos = MerchantMesos + " + pItem.getPrice() * quantity + " where id = ?");
                            ps.setInt(1, getOwnerId());
                            ps.executeUpdate();
                            ps.close();
                        } catch (SQLException se) {
                            se.printStackTrace();
                        }
                        c.getPlayer().gainMeso(-pItem.getPrice() * quantity, false);
                        pItem.setBundles((short) (pItem.getBundles() - quantity));
                        try {
                            updateItem(pItem);
                        } catch (SQLException se) {
                            se.printStackTrace();
                        }
                    } else {
                        c.getPlayer().dropMessage(1, "Your inventory is full.");
                    }
                } else {
                    c.getPlayer().dropMessage(1, "You do not have enough mesos.");
                }
            }
            if (isSoldOut()) {
                closeShop();
            }
        }
    }

    public void closeShop() {
        map.removeMapObject(this);
        map.broadcastMessage(MaplePacketCreator.destroyHiredMerchant(getOwnerId()));
        allMerchants.remove(getOwnerId());
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET HasMerchant = 0 WHERE id = ?");
            ps.setInt(1, getOwnerId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }
        if (owner != null) {
            owner.setHasMerchant(false);
        }
    }

    public void cancelShop() {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET HasMerchant = 0 WHERE id = ?");
            ps.setInt(1, getOwnerId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
        if (owner != null) {
            owner.setHasMerchant(false);
        }
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean set) {
        if (set && owner != null) {
            owner.setHasMerchant(true);
        }
        open = set;
    }

    public MapleMap getMap() {
        return map;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.HIRED_MERCHANT;
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.spawnHiredMerchant(this));
    }
}