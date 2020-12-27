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

package guida.net.login.handler;

import guida.client.IItem;
import guida.client.Item;
import guida.client.MapleCharacter;
import guida.client.MapleCharacterUtil;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleQuestStatus;
import guida.client.MapleSkinColor;
import guida.net.AbstractMaplePacketHandler;
import guida.net.login.CharCreationInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class CreateCharHandler extends AbstractMaplePacketHandler {

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CreateCharHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final String name = slea.readMapleAsciiString();
        final int job = slea.readInt();
        final int face = slea.readInt();
        final int hair = slea.readInt();
        final int hairColor = slea.readInt();
        final int skinColor = slea.readInt();
        final int top = slea.readInt();
        final int bottom = slea.readInt();
        final int shoes = slea.readInt();
        final int weapon = slea.readInt();
        final int gender = slea.readByte();
        final int[] stats = {face, hair, hairColor, skinColor, top, bottom, shoes, weapon};

        if (job == 2 && !c.isGM()) {
            c.sendPacket(MaplePacketCreator.addNewCharEntryStatus((byte) 30));
            return;
        }
        CharCreationInformationProvider ii = CharCreationInformationProvider.getInstance();
        boolean charOk = ii.validateStats(job, gender, stats);
        if (charOk && MapleCharacterUtil.canCreateChar(name, c.getWorld())) {
            final MapleCharacter newchar = MapleCharacter.getDefault(c, job);
            newchar.setWorld(c.getWorld());
            newchar.setFace(face);
            newchar.setHair(hair + hairColor);
            newchar.setGender(gender);
            newchar.setStr(12);
            newchar.setDex(5);
            newchar.setInt(4);
            newchar.setLuk(4);
            newchar.setName(name);
            newchar.setSkinColor(MapleSkinColor.getById(skinColor));

            final MapleInventory equip = newchar.getInventory(MapleInventoryType.EQUIPPED);
            final IItem eq_top = ii.getEquipById(top);
            eq_top.setPosition((byte) -5);
            equip.addFromDB(eq_top);
            final IItem eq_bottom = ii.getEquipById(bottom);
            eq_bottom.setPosition((byte) -6);
            equip.addFromDB(eq_bottom);
            final IItem eq_shoes = ii.getEquipById(shoes);
            eq_shoes.setPosition((byte) -7);
            equip.addFromDB(eq_shoes);
            final IItem eq_weapon = ii.getEquipById(weapon);
            ii = null;
            eq_weapon.setPosition((byte) -11);
            equip.addFromDB(eq_weapon);

            final MapleInventory etc = newchar.getInventory(MapleInventoryType.ETC);
            if (job == 0) {
                etc.addItem(new Item(4161047, (byte) 0, (short) 1));
                newchar.updateQuest(new MapleQuestStatus(20022, MapleQuestStatus.Status.STARTED, "1"), false, false, true);
            } else if (job == 1) {
                etc.addItem(new Item(4161001, (byte) 0, (short) 1));
            } else {
                etc.addItem(new Item(4161048, (byte) 0, (short) 1));
            }
            newchar.gainMeso(25000, false, false, false, false, false);
            newchar.saveToDB(false);
            c.sendPacket(MaplePacketCreator.addNewCharEntry(newchar, true));
        } else {
            log.warn(MapleClient.getLogMessage(c, "Trying to create a character with a name: {}", name));
        }
    }
}