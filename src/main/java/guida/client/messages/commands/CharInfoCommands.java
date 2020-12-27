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
import guida.tools.ReadableMillisecondFormat;

public class CharInfoCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted[0].equalsIgnoreCase("!charinfo")) {
            if (splitted.length < 2) {
                mc.dropMessage("!charinfo <Character Name>");
            } else {
                MapleCharacter other = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
                if (other == null) {
                    mc.dropMessage("The character does not exist or is not online.");
                    return;
                }
                mc.dropMessage(MapleClient.getLogMessage(other, "") + " Pos: " + other.getPosition().x + "," + other.getPosition().y + " Map ID: " + other.getMapId() + " Map Name: " + other.getMap().getMapName() + " HP: " + other.getHp() + "/" + other.getCurrentMaxHp() + " MP: " + other.getMp() + "/" + other.getCurrentMaxMp() + " EXP: " + other.getExp() + " Job: " + other.getJob().name() + " Guild Id: " + other.getGuildId() + " Event Status: " + (other.getEventInstance() != null) + " Party Status: " + (other.getParty() != null) + " Trade Status: " + (other.getTrade() != null) + " IP: " + other.getClient().getIP() + " Online Time: " + new ReadableMillisecondFormat(other.getLoggedInTimer()).toString());
            }
        } else if (splitted[0].equalsIgnoreCase("!selfinfo")) {
            MapleCharacter self = c.getPlayer();
            mc.dropMessage(MapleClient.getLogMessage(self, "") + " Pos: " + self.getPosition().x + "," + self.getPosition().y + " Map ID: " + self.getMapId() + " Map Name: " + self.getMap().getMapName() + " HP: " + self.getHp() + "/" + self.getCurrentMaxHp() + " MP: " + self.getMp() + "/" + self.getCurrentMaxMp() + " EXP: " + self.getExp() + " Job: " + self.getJob().name() + " Guild Id: " + self.getGuildId() + " Event Status: " + (self.getEventInstance() != null) + " Party Status: " + (self.getParty() != null) + " Trade Status: " + (self.getTrade() != null) + " IP: " + c.getIP() + " Online Time: " + new ReadableMillisecondFormat(self.getLoggedInTimer()).toString());
        } else if (splitted[0].equalsIgnoreCase("!position")) {
            mc.dropMessage("Your current co-ordinates are: " + c.getPlayer().getPosition().x + " x and " + c.getPlayer().getPosition().y + " y.");
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("charinfo", "charname", "Shows info about the charcter with the given name", 4),
                new CommandDefinition("selfinfo", "charname", "Shows info about your own character", 4),
                new CommandDefinition("position", "", "Shows your character's coordinates", 4)
        };
    }
}