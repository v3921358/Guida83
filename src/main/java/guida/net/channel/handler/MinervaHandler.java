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
import guida.server.playerinteractions.HiredMerchant;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Jay Estrella
 */
public class MinervaHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        //3F 00 - header
        //02 E3 94 00 - ?? un needed anyways...
        //96 7F 3D 36 - merch id
        if (!c.getPlayer().isMinerving()) {
            c.getPlayer().dropMessage(1, "Shoo! Packet Editor!");
            return;
        }
        c.getPlayer().minerving(false);
        int ownId = slea.readInt();
        //slea.readInt(); <-- Map ID of store
        HiredMerchant res = HiredMerchant.getMerchByOwner(ownId);
        if (res == null) {
            c.getPlayer().dropMessage(1, "There was an error.");
            MapleInventoryManipulator.addById(c, 5230000, (short) 1, "");

            return;
        }
        visit(c, res);
    }

    private void visit(MapleClient c, HiredMerchant merchant) {
        if (c.getPlayer().getMapId() < 910000000 || c.getPlayer().getMapId() > 910000030) {
            c.getPlayer().dropMessage(1, "You cannot use the Owl of Minerva outside of the Free Market.");
            return;
        }
        if (merchant.getChannel() != c.getChannel()) {
            c.getPlayer().dropMessage(1, "The Hired Merchant is on channel " + merchant.getChannel() + ". Please go to that channel.");

        } else if (!merchant.isOpen()) {
            c.getPlayer().dropMessage(1, "The Hired Merchant is not open.");
        } else {
            c.getPlayer().changeMap(merchant.getMap(), merchant.getMap().getPortal(0));
            if (!merchant.isOwner(c.getPlayer())) {
                if (merchant.getFreeSlot() == -1) {
                    c.getPlayer().dropMessage(5, "The shop has reached it's maximum capacity, please come by later.");
                    c.getPlayer().dropMessage(5, "Shop Owner: " + merchant.getOwnerName());
                    c.getPlayer().dropMessage(5, "Shop Description: " + merchant.getDescription());
                    c.getPlayer().dropMessage(5, "Shop Location: FM" + (merchant.getMap().getId() - 910000000));
                    return;
                }
                c.getPlayer().setPlayerShop(merchant);
                merchant.addVisitor(c.getPlayer());
                c.sendPacket(MaplePacketCreator.getMaplePlayerStore(c.getPlayer(), false));
            } else {
                c.getPlayer().dropMessage(5, "Since the store is your own store, you have been warped to the map.");
            }
        }
    }
}