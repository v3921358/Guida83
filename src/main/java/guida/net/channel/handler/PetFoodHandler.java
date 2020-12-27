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

import guida.client.ExpTable;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.List;

public class PetFoodHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.setLastAction(action);
        slea.readShort();
        int itemId = slea.readInt();
        if (c.getPlayer().getPets().isEmpty() || !c.getPlayer().haveItem(itemId, 1, false, false)) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        int previousFullness = 100;
        MaplePet targetPet = c.getPlayer().getPet(0);
        final List<MaplePet> pets = c.getPlayer().getPets();
        for (MaplePet pet : pets) {
            if (pet.getFullness() < previousFullness) {
                previousFullness = pet.getFullness();
                targetPet = pet;
            }
        }
        boolean gainCloseness = false;
        int random = Randomizer.nextInt(101);
        if (random <= 50) {
            gainCloseness = true;
        }
        if (targetPet.getFullness() < 100) {
            int newFullness = targetPet.getFullness() + 30;
            if (newFullness > 100) {
                newFullness = 100;
            }
            targetPet.setFullness(newFullness);
            if (gainCloseness && targetPet.getCloseness() < 30000) {
                int newCloseness = targetPet.getCloseness() + c.getChannelServer().getPetExpRate();
                if (newCloseness > 30000) {
                    newCloseness = 30000;
                }
                targetPet.setCloseness(newCloseness);
                if (newCloseness >= ExpTable.getClosenessNeededForLevel(targetPet.getLevel() + 1)) {
                    targetPet.setLevel(targetPet.getLevel() + 1);
                    c.sendPacket(MaplePacketCreator.showOwnPetLevelUp((byte) 0, c.getPlayer().getPetIndex(targetPet)));
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.showPetLevelUp(c.getPlayer(), c.getPlayer().getPetIndex(targetPet)));
                }
            }
            c.getPlayer().updatePet(targetPet);
            c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), (byte) 1, c.getPlayer().getPetIndex(targetPet), true, true, targetPet.hasQuoteRing()), true);
        } else {
            if (gainCloseness) {
                int newCloseness = targetPet.getCloseness() - c.getChannelServer().getPetExpRate();
                if (newCloseness < 0) {
                    newCloseness = 0;
                }
                targetPet.setCloseness(newCloseness);
                if (newCloseness < ExpTable.getClosenessNeededForLevel(targetPet.getLevel())) {
                    targetPet.setLevel(targetPet.getLevel() - 1);
                }
            }
            c.getPlayer().updatePet(targetPet);
            c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.commandResponse(c.getPlayer().getId(), (byte) 1, c.getPlayer().getPetIndex(targetPet), false, true, targetPet.hasQuoteRing()), true);
        }
        MapleInventoryManipulator.removeById(c, MapleInventoryType.USE, itemId, 1, true, false);
    }
}