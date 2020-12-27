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
import guida.net.AbstractMaplePacketHandler;
import guida.scripting.npc.Marriage;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * Ring actions o.O
 *
 * @author Jvlaple
 */
//header  mode
//[7C 00] [00] 08 00 53 68 69 74 46 75 63 6B 01 2E 22 00 => Send
//[7C 00] [01] Cancel send?
//[7C 00] [03] 84 83 3D 00 => Dropping engagement ring
public class RingActionHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RingActionHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final byte mode = slea.readByte();
        final MapleCharacter player = c.getPlayer();
        //c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, "TEST"));
        switch (mode) {
            case 0x00: //Send
                final String partnerName = slea.readMapleAsciiString();
                final MapleCharacter partner = c.getChannelServer().getPlayerStorage().getCharacterByName(partnerName);
                if (partnerName.equalsIgnoreCase(player.getName())) {
                    c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, "You cannot put your own name in it."));
                    return;
                } else if (partner == null) {
                    c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, partnerName + " was not found on this channel. If you are both logged in, please make sure you are in the same channel."));
                    return;
                } else if (partner.getGender() == player.getGender()) {
                    c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, "Your partner is the same gender as you."));
                    return;
                    //} Fuck marriage for now
                    // else if (player.isMarried() == 0 && partner.isMarried() == 0) {
                    //	NPCScriptManager.getInstance().start(partner.getClient(), 9201002, "marriagequestion");
                }
                break;
            case 0x01: //Cancel send
                c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, "You've cancelled the request."));
                break;
            case 0x03: //Drop Ring
                if (player.getPartner() != null) {
                    Marriage.divorceEngagement(player, player.getPartner());
                    c.sendPacket(guida.tools.MaplePacketCreator.serverNotice(1, "Your engagement has been broken up."));
                    break;
                } else {
                    log.info("Failed canceling engagement..");
                    break;
                }
            default:
                log.info("Unhandled Ring Packet : " + slea.toString());
                break;
        }
    }
}