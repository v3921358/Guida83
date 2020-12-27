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
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.maps.MapleMapObjectType;
import guida.tools.MaplePacketCreator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matze FIXED by XoticStory.
 */
public class MaplePlayerShop extends AbstractPlayerStore {

    private final MapleCharacter owner;
    private final List<String> bannedList = new ArrayList<>();
    private int boughtnumber = 0;

    public MaplePlayerShop(MapleCharacter owner, int itemId, String desc) {
        super(owner, itemId, desc);
        this.owner = owner;
    }

    @Override
    public void buy(MapleClient c, int item, short quantity) {
        MaplePlayerShopItem pItem = items.get(item);
        if (pItem.getPrice() + owner.getMeso() < 0) {
            c.getPlayer().dropMessage("The owner has too many mesos already.");
            return;
        }
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
                    if (!MapleInventoryManipulator.addByItem(c, newItem, "", true).isEmpty()) {
                        c.getPlayer().gainMeso(-pItem.getPrice() * quantity, false);
                        owner.gainMeso(pItem.getPrice() * quantity, false);
                        pItem.setBundles((short) (pItem.getBundles() - quantity));
                        try {
                            updateItem(pItem);
                        } catch (SQLException se) {
                            se.printStackTrace();
                        }
                        if (pItem.getBundles() == 0) {
                            boughtnumber++;
                            if (boughtnumber >= items.size()) {
                                removeAllVisitors(10, 1);
                                owner.getClient().sendPacket(MaplePacketCreator.shopErrorMessage(10, 1));
                                closeShop();
                            }
                        }
                    } else {
                        c.getPlayer().dropMessage(1, "Your inventory is full.");
                    }
                } else {
                    c.getPlayer().dropMessage(1, "You do not have enough mesos.");
                }
            }
            owner.getClient().sendPacket(MaplePacketCreator.shopItemUpdate(this));
            if (isSoldOut()) {
                closeShop();
            }
        }
    }

    @Override
    public byte getShopType() {
        return IMaplePlayerShop.PLAYER_SHOP;
    }

    public void closeShop() {
        owner.getMap().broadcastMessage(MaplePacketCreator.removeCharBox(owner));
        owner.getMap().removeMapObject(this);
        owner.setPlayerShop(null);
    }

    public void banPlayer(String name) {
        if (!bannedList.contains(name)) {
            bannedList.add(name);
        }
        for (int i = 1; i < 4; i++) {
            MapleCharacter chr = getVisitor(i);
            if (chr != null && chr.getName().equals(name)) {
                chr.getClient().sendPacket(MaplePacketCreator.shopErrorMessage(5, 1));
                chr.setPlayerShop(null);
                removeVisitor(chr);
            }
        }
    }

    public boolean isBanned(String name) {
        return bannedList.contains(name);
    }

    public MapleCharacter getMCOwner() {
        return owner;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.SHOP;
    }
}