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

package guida.net.channel.handler;

import guida.client.MapleClient;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.sql.SQLException;

/**
 * @author Penguins (Acrylic)
 */
public class CouponCodeHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        slea.skip(2);
        String code = slea.readMapleAsciiString();
        boolean validcode = false;
        int type = -1;
        int item = -1;

        try {
            validcode = c.getPlayer().getNXCodeValid(code.toUpperCase());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (validcode) {
            try {
                type = c.getPlayer().getNXCodeType(code);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                item = c.getPlayer().getNXCodeItem(code);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (type != 5) {
                try {
                    c.getPlayer().setNXCodeUsed(code);
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            /*
             * Explanation of type!
             * Basically, this makes coupon codes do
             * different things!
             *
             * Type 1: PayPal/Pay by Cash
             * Type 2: Maple Points
             * Type 3: Item
             * Type 4: Nexon Game Card Cash
             * Type 5: NX Coupon that can be used over and over
             *
             * When using Types 1, 2 or 4 the item is the amount
             * of Paypal Cash, Nexon Game Card Cash or Maple Points you get.
             * When using Type 3 the item is the ID of the item you get.
             * Enjoy!
             */
            switch (type) {
                case 1, 2, 4 -> c.getPlayer().modifyCSPoints(type, item);
                case 3 -> {
                    MapleInventoryManipulator.addById(c, item, (short) 1, "An item was obtain from a coupon.", null, null);
                    c.sendPacket(MaplePacketCreator.showCouponRedeemedItem(item));
                }
                case 5 -> c.getPlayer().modifyCSPoints(1, item);
            }
            c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
        } else {
            c.sendPacket(MaplePacketCreator.wrongCouponCode());
        }
    }
}