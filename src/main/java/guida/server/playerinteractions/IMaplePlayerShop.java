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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.net.MaplePacket;
import guida.tools.Pair;

import java.sql.SQLException;
import java.util.List;

/**
 * @author XoticStory
 */
public interface IMaplePlayerShop {

    byte HIRED_MERCHANT = 1;
    byte PLAYER_SHOP = 2;

    void broadcastToVisitors(MaplePacket packet);

    void addVisitor(MapleCharacter visitor);

    void removeVisitor(MapleCharacter visitor);

    int getVisitorSlot(MapleCharacter visitor);

    void removeAllVisitors(int error, int type);

    void buy(MapleClient c, int item, short quantity);

    void closeShop();

    String getOwnerName();

    int getOwnerId();

    String getDescription();

    void setDescription(String desc);

    List<Pair<Byte, MapleCharacter>> getVisitors();

    List<MaplePlayerShopItem> getItems();

    void addItem(MaplePlayerShopItem item) throws SQLException;

    boolean removeItem(int item) throws SQLException;

    void updateItem(MaplePlayerShopItem item) throws SQLException;

    void removeFromSlot(int slot);

    int getFreeSlot();

    int getItemId();

    boolean isOwner(MapleCharacter chr);

    byte getShopType();

    void spawned();

    boolean isSpawned();

    boolean isSoldOut();

    void makeAvailableAtFred();
}