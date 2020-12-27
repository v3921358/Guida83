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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.npc.NPCScriptManager;
import guida.server.life.MapleNPC;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class NPCTalkHandler extends AbstractMaplePacketHandler {

    private static final String confirmFormat = "Your %1$s inventory is has 4 or less slots free. No compensation will be given for items lost due to having insufficient space in your inventory.\r\n\r\nDo you still want to speak to this NPC?";

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, final MapleClient c) {

        final int oid = slea.readInt();
        MapleCharacter chr = c.getPlayer();
        if (chr == null || chr.getMap().getMapObject(oid) == null || !(chr.getMap().getMapObject(oid) instanceof MapleNPC) || chr.getCheatTracker().checkNPCClick()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        final MapleNPC npc = (MapleNPC) chr.getMap().getMapObject(oid);
        if (npc.hasShop()) {
            if (chr.getShop() != null) {
                chr.setShop(null);
                c.sendPacket(MaplePacketCreator.confirmShopTransaction((byte) 20));
            }
            npc.sendShop(c);
            return;
        }
        for (MapleInventory ivnt : c.getPlayer().allInventories()) {
            if (ivnt.getFreeSlots() < 4) {
                Runnable r = () -> handlePacketInt(oid, c);
                c.getPlayer().callConfirmationNpc(r, null, 9010000, 2, String.format(confirmFormat, ivnt.getType().toString()));
                return;
            }

        }
        handlePacketInt(oid, c);
    }

    private void handlePacketInt(final int oid, final MapleClient c) {
        MapleCharacter chr = c.getPlayer();
        final MapleNPC npc = (MapleNPC) chr.getMap().getMapObject(oid);
        if (c.getCM() != null || c.getQM() != null || !chr.getMap().containsNPC(npc.getId())) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if (c.getCM() == null) {
            NPCScriptManager.getInstance().start(c, npc.getId());
        }
        // 0 = next button
        // 1 = yes no
        // 2 = accept decline
        // 5 = select a link
    }
}