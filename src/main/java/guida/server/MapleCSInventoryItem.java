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

import guida.client.Equip;
import guida.client.IItem;
import guida.client.Item;
import guida.client.MapleInventoryType;

import java.sql.Timestamp;

/**
 * @author Rob
 */
public class MapleCSInventoryItem {

    private final int uniqueid;
    private final int itemid;
    private final int sn;
    private final short quantity;
    private final boolean gift;
    private Timestamp expire = null;
    private String sender = "";
    private String message = "";

    public MapleCSInventoryItem(int uniqueid, int itemid, int sn, short quantity, boolean gift) {
        this.uniqueid = uniqueid;
        this.itemid = itemid;
        this.sn = sn;
        this.quantity = quantity;
        this.gift = gift;
    }

    public boolean isGift() {
        return gift;
    }

    public int getItemId() {
        return itemid;
    }

    public int getSn() {
        return sn;
    }

    public short getQuantity() {
        return quantity;
    }

    public Timestamp getExpire() {
        return expire;
    }

    public void setExpire(Timestamp expire) {
        this.expire = expire;
    }

    public int getUniqueId() {
        return uniqueid;
    }

    public void setSender(String sendername) {
        sender = sendername;
    }

    public void setMessage(String msg) {
        message = msg;
    }

    public String getSender() {
        return sender;
    }

    public String getMessage() {
        return message;
    }

    public IItem toItem() {
        IItem newitem;
        MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
        if (type.equals(MapleInventoryType.EQUIP)) {
            newitem = new Equip(itemid, (byte) -1);
        } else {
            newitem = new Item(itemid, (byte) -1, quantity);
        }
        newitem.setExpiration(expire);
        newitem.setUniqueId(uniqueid);
        return newitem;
    }
}