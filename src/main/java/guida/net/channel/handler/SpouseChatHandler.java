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
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class SpouseChatHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        /*
         * DISABLED UNTIL IT WORKS PROPERLY.
         */
		/*final String recipient = slea.readMapleAsciiString();
		final String text = slea.readMapleAsciiString();
		if (!CommandProcessor.getInstance().processCommand(c, text)) {
			final MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(recipient);
			if (player != null) {
				player.getClient().sendPacket(MaplePacketCreator.spouseChat(c.getPlayer().getName(), text, 5));
				c.sendPacket(MaplePacketCreator.spouseChat(c.getPlayer().getName(), text, 4));
			} else {
				try {
					if (ChannelServer.getInstance(c.getChannel()).getWorldInterface().isConnected(recipient, false)) {
						ChannelServer.getInstance(c.getChannel()).getWorldInterface().spouseChat(c.getPlayer().getName(), recipient, text);
						c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
					} else {
						c.sendPacket(MaplePacketCreator.spouseChatOffline());
					}
				} catch (RemoteException e) {
					c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
					c.getChannelServer().reconnectWorld();
				}
			}
		}*/
    }
}