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

package guida.scripting.portal;

import guida.client.MapleClient;
import guida.server.MaplePortal;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PortalScriptManager {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PortalScriptManager.class);
    private static final PortalScriptManager instance = new PortalScriptManager();
    private final Map<String, PortalScript> scripts = new HashMap<>();
    private final ScriptEngineFactory sef;

    private PortalScriptManager() {
        final ScriptEngineManager sem = new ScriptEngineManager();
        sef = sem.getEngineByName("javascript").getFactory();
    }

    public static PortalScriptManager getInstance() {
        return instance;
    }

    private PortalScript getPortalScript(String scriptName) {
        if (scripts.containsKey(scriptName)) {
            return scripts.get(scriptName);
        }

        final File scriptFile = new File("scripts/portal/" + scriptName + ".js");
        if (!scriptFile.exists()) {
            scripts.put(scriptName, null);
            return null;
        }

        FileReader fr = null;
        final ScriptEngine portal = sef.getScriptEngine();
        try {
            fr = new FileReader(scriptFile);
            final CompiledScript compiled = ((Compilable) portal).compile(fr);
            compiled.eval();
        } catch (ScriptException e) {
            log.error("Error executing portal script: " + scriptFile.getName(), e);
        } catch (IOException e) {
            log.error("THROW", e);
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    log.error("ERROR CLOSING", e);
                }
            }
        }

        final PortalScript script = ((Invocable) portal).getInterface(PortalScript.class);
        scripts.put(scriptName, script);
        return script;
    }

    // rhino is thread safe so this should be fine without synchronisation
    public boolean executePortalScript(MaplePortal portal, MapleClient c) {
        final PortalScript script = getPortalScript(portal.getScriptName());

        if (script != null) {
            try {
                return script.enter(new PortalPlayerInteraction(c, portal));
            } catch (Exception e) {
                log.info("Error executing portal script: " + portal.getScriptName());
                e.printStackTrace();
                return false;
            }
        } else {
            //log.info("Unscripted portal script: " + portal.getScriptName());
            return false;
        }
    }

    public void clearScripts() {
        scripts.clear();
    }
}