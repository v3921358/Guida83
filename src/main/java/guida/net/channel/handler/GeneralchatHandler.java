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
import guida.client.messages.CommandProcessor;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class GeneralchatHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final String text = slea.readMapleAsciiString();
        final int show = slea.readByte();

        if (show != 1 && c.getPlayer().getCheatTracker().textSpam(text) && !c.getPlayer().isGM()) {
            c.sendPacket(MaplePacketCreator.serverNotice(5, "Too much chatting"));
            return;
        }
        if (text.length() > 70 && !c.getPlayer().isGM()) {
            return;
        }
        if (!CommandProcessor.getInstance().processCommand(c, text)) {
            if (c.getPlayer().isMuted() || c.getPlayer().getMap().getMuted() && !c.getPlayer().isGM()) {
                c.getPlayer().dropMessage(5, c.getPlayer().isMuted() ? "You are " : "The map is " + "muted, therefore you are unable to talk.");
                return;
            }
            c.getPlayer().resetAfkTimer();
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getChatText(c.getPlayer().getId(), text, c.getPlayer().isGM() && c.getChannelServer().allowGmWhiteText() && c.getPlayer().getWhiteText(), show));
            if (text.equalsIgnoreCase("cc plz")) {
                c.getPlayer().finishAchievement(14);
            }
        }
    }
}