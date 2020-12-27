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
import guida.client.messages.CommandProcessor;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;

public class MultiChatHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        c.getPlayer().resetAfkTimer();
        final int type = slea.readByte(); // 0 for buddys, 1 for partys
        final int numRecipients = slea.readByte();
        final int[] recipients = new int[numRecipients];
        for (int i = 0; i < numRecipients; i++) {
            recipients[i] = slea.readInt();
        }
        final String chattext = slea.readMapleAsciiString();
        if (chattext.length() > 70 && !c.getPlayer().isGM()) {
            return;
        }
        if (!CommandProcessor.getInstance().processCommand(c, chattext)) {
            final MapleCharacter player = c.getPlayer();
            if (player.isMuted()) {
                player.dropMessage(5, "You are muted, therefore you are unable to talk.");
                return;
            }
            if (c.getPlayer().getChatSpam(type + 1) + 500 > System.currentTimeMillis()) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            c.getPlayer().setChatSpam(type + 1);
            try {
                if (type == 0) {
                    c.getChannelServer().getWorldInterface().buddyChat(recipients, player.getId(), player.getName(), chattext);
                } else if (type == 1 && player.getParty() != null) {
                    c.getChannelServer().getWorldInterface().partyChat(player.getParty().getId(), chattext, player.getName());
                } else if (type == 2 && player.getGuildId() > 0) {
                    c.getChannelServer().getWorldInterface().guildChat(c.getPlayer().getGuildId(), c.getPlayer().getName(), c.getPlayer().getId(), chattext);
                } else if (type == 3 && player.getGuild() != null) {
                    final int allianceId = player.getGuild().getAllianceId();
                    if (allianceId > 0) {
                        c.getChannelServer().getWorldInterface().allianceMessage(allianceId, MaplePacketCreator.multiChat(player.getName(), chattext, 3), player.getId(), -1);
                    }
                }
            } catch (RemoteException e) {
                c.getChannelServer().reconnectWorld();
            }
        }
    }
}