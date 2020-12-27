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

package guida.client.messages.commands;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.net.channel.ChannelServer;

import java.util.Collection;

public class SaveAllCommand implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted[0].equals("!saveall")) {
            Collection<ChannelServer> ccs = ChannelServer.getAllInstances();
            for (ChannelServer chan : ccs) {
                if (chan != null) {
                    mc.dropMessage("Saving characters on channel " + chan.getChannel());
                    Collection<MapleCharacter> allchars = chan.getPlayerStorage().getAllCharacters();
                    MapleCharacter[] chrs = allchars.toArray(new MapleCharacter[0]);
                    for (MapleCharacter chr : chrs) {
                        try {
                            if (chr != null) {
                                chr.saveToDB(true);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            mc.dropMessage("All characters have been saved.");
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("saveall", "", "Saves all characters", 4)
        };
    }
}