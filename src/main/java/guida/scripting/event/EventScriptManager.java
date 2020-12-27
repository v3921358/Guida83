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

package guida.scripting.event;

import guida.net.channel.ChannelServer;
import guida.scripting.AbstractScriptManager;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Matze
 */
public class EventScriptManager extends AbstractScriptManager {

    private final Map<String, EventEntry> events = new LinkedHashMap<>();

    public EventScriptManager(ChannelServer cserv, String[] scripts) {
        super();
        for (String script : scripts) {
            if (script.length() != 0) {
                final Invocable iv = getInvocable("event/" + script + ".js", null);
                events.put(script, new EventEntry(script, iv, new EventManager(cserv, iv, script)));
            }
        }
    }

    public EventManager getEventManager(String event) {
        final EventEntry entry = events.get(event);
        if (entry == null) {
            return null;
        }
        return entry.em;
    }

    public void init() {
        for (EventEntry entry : events.values()) {
            try {
                ((ScriptEngine) entry.iv).put("em", entry.em);
                entry.iv.invokeFunction("init", (Object) null);
            } catch (ScriptException ex) {
                Logger.getLogger(EventScriptManager.class.getName()).log(Level.SEVERE, "Error initializing script: " + entry.script, ex);
            } catch (NoSuchMethodException ex) {
                Logger.getLogger(EventScriptManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void cancel() {
        for (EventEntry entry : events.values()) {
            entry.em.cancel();
        }
    }

    private static class EventEntry {

        public final String script;
        public final Invocable iv;
        public final EventManager em;
        public EventEntry(String script, Invocable iv, EventManager em) {
            this.script = script;
            this.iv = iv;
            this.em = em;
        }
    }
}