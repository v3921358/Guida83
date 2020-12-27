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
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MaplePet;
import guida.client.PetCommand;
import guida.client.PetDataFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class PetCommandHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        int petId = slea.readInt();
        MapleCharacter player = c.getPlayer();
        int petIndex = player.getPetIndex(petId);
        MaplePet pet = null;
        if (petIndex == -1) {
            return;
        } else {
            pet = player.getPet(petIndex);
        }
        slea.readInt();
        slea.readByte();

        byte command = slea.readByte();

        PetCommand petCommand = PetDataFactory.getPetCommand(pet.getItemId(), command);

        boolean success = false;

        int random = Randomizer.nextInt(101);
        if (random <= petCommand.getProbability()) {
            success = true;
            if (pet.getCloseness() < 30000) {
                int newCloseness = pet.getCloseness() + petCommand.getIncrease() * c.getChannelServer().getPetExpRate();
                if (newCloseness > 30000) {
                    newCloseness = 30000;
                }
                pet.setCloseness(newCloseness);
                if (newCloseness >= ExpTable.getClosenessNeededForLevel(pet.getLevel() + 1)) {
                    pet.setLevel(pet.getLevel() + 1);
                    c.sendPacket(MaplePacketCreator.showOwnPetLevelUp((byte) 0, player.getPetIndex(pet)));
                    player.getMap().broadcastMessage(MaplePacketCreator.showPetLevelUp(player, player.getPetIndex(pet)));
                }
                player.updatePet(pet);
            }
        }
        player.getMap().broadcastMessage(player, MaplePacketCreator.commandResponse(player.getId(), command, petIndex, success, false, pet.hasQuoteRing()), true);
    }
}