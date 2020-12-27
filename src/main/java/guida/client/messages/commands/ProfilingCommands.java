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

import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.tools.performance.CPUSampler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ProfilingCommands implements Command {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProfilingCommands.class);

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) {
        if (splitted[0].equalsIgnoreCase("!startProfiling")) {
            CPUSampler sampler = CPUSampler.getInstance();
            sampler.addIncluded("guida");
            sampler.start();
        } else if (splitted[0].equalsIgnoreCase("!stopProfiling")) {
            CPUSampler sampler = CPUSampler.getInstance();
            try {
                String filename = "odinprofile.txt";
                if (splitted.length > 1) {
                    filename = splitted[1];
                }
                File file = new File(filename);
                int index = 0;
                while (file.exists()) {
                    index++;
                    file = new File("odinprofile_" + index + ".txt");
                }
                sampler.stop();
                FileWriter fw = new FileWriter(file);
                sampler.save(fw, 1, 10);
                fw.close();
            } catch (IOException e) {
                log.error("THROW", e);
            }
            sampler.reset();
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("startProfiling", "", "Starts the CPU Sampling based profiler", 5),
                new CommandDefinition("stopProfiling", "<File Name>", "Stops the Profiler and saves the results to the given fileName", 5)
        };
    }
}
