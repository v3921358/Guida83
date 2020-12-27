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
import guida.server.maps.MapleMapObjectType;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.Arrays;

public class HiredMerchantRequestHandler extends AbstractMaplePacketHandler {

    private static final String confirmGreeting = "How's it going? This is the Hired Merchant Disclaimer Test. You will need to pass this test each time you want to open a Hired Merchant.\r\n\r\n#e#rThe purpose of this test is to stop people holding the staff responsible for item loss due to use of Hired Merchants.#k#n";
    private static final String confirmTos1 = "#eTERMS OF SERVICE, PART 1#n\r\n\r\n1. The Staff are not responsible for the loss of items when using a Hired Merchant.\r\n2. The use of a Hired Merchant is not required.\r\n3. Loss of items due to use of a Hired Merchant will not be dealt by the staff.\r\n4. Hired Merchants may cause you to lose your items.\r\n\r\n#eEND#n\r\n\r\nDo you agree? If so, solve:";
    private static final String confirmTos2 = "#eTERMS OF SERVICE, PART 2#n\r\n\r\n#e#r1. You are not allowed to exploit glitches in the system (if any) to duplicate items.#k#n\r\n\r\n#eEND#n\r\n\r\nDo you agree? If so, solve:";

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, final MapleClient c) {
        if (c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 23000, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP)).isEmpty() && c.getPlayer().getMap().allowShops()) {
            if (!c.getPlayer().hasMerchant()) {
                int mapid = c.getPlayer().getMapId();
                if (mapid >= 910000001 && mapid <= 910000022 && c.getPlayer().hasHiredMerchantTicket()) {
                    //NPCScriptManager.getInstance().start(c, 9030000, "hm_disclaimer");
                    //c.sendPacket(MaplePacketCreator.hiredMerchantBox(c.getPlayer()));
                    c.getPlayer().setPassMerchTest(true);
                    c.sendPacket(guida.tools.MaplePacketCreator.hiredMerchantBox(c.getPlayer()));
                } else {
                    c.disconnect();
                }
            } else {
                c.getPlayer().dropMessage(1, "You already have a store open, please go close that store first.");
            }
        } else {
            c.getPlayer().dropMessage(1, "You may not establish a store here.");
        }
    }
}