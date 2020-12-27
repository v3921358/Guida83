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

import guida.client.IItem;

import java.sql.Timestamp;

/**
 * @author Traitor
 */
public class MTSItemInfo {

    private final int price;
    private final IItem item;
    private final String seller;
    private final int id;
    private final int cid;
    private final Timestamp endDate;

    public MTSItemInfo(IItem item, int price, int id, int cid, String seller, Timestamp endDate) {
        this.item = item;
        this.price = price;
        this.seller = seller;
        this.id = id;
        this.cid = cid;
        this.endDate = endDate;
    }

    public IItem getItem() {
        return item;
    }

    public int getPrice() {
        return price;
    }

    public int getRealPrice() {
        return price + getTaxes();
    }

    public int getTaxes() {
        return 100 + (int) (price * 0.1);
    }

    public int getID() {
        return id;
    }

    public int getCID() {
        return cid;
    }

    public Timestamp getEndingDate() {
        return endDate;
    }

    public String getSeller() {
        return seller;
    }
}