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

import guida.client.IItem;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleItemInformationProvider.SummonEntry;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.List;

/**
 * @author AngelSL
 */
public class UseSummonBagHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        if (action <= c.getLastAction() || !c.getPlayer().isAlive()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.setLastAction(action);
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        final short slot = slea.readShort();
        final int itemId = slea.readInt();
        final IItem toUse = c.getPlayer().getInventory(MapleInventoryType.USE).getItem(slot);
        c.getPlayer().resetAfkTimer();
        if (toUse != null && toUse.getQuantity() > 0) {
            if (toUse.getItemId() != itemId) {
                return;
            }
            MapleInventoryManipulator.removeFromSlot(c, MapleInventoryType.USE, slot, (short) 1, false);
            final List<SummonEntry> toSpawn = ii.getSummonMobs(itemId);
            for (final SummonEntry se : toSpawn) {
                if ((int) Math.ceil(Math.random() * 100) <= se.getChance()) {
                    final MapleMonster mob = MapleLifeFactory.getMonster(se.getMobId());
                    mob.setSummonedBy(c.getPlayer());
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob, c.getPlayer().getPosition());
                    switch (se.getMobId()) {
                        case 8810024, 8810025 -> {
                            c.getPlayer().getMap().killMonster(mob, c.getPlayer(), false);
                            c.getPlayer().getMap().mapMessage(6, "[Notice] Horntail is summoned by the summoning bag.");
                        }
                    }
                }
            }
        }
        c.sendPacket(MaplePacketCreator.enableActions());
    }
}