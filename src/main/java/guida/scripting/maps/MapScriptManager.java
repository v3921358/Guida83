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

package guida.scripting.maps;

import guida.client.MapleClient;

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

public class MapScriptManager {

    private static final MapScriptManager instance = new MapScriptManager();
    private final Map<String, MapScript> scripts = new HashMap<>();
    private final ScriptEngineFactory sef;

    private MapScriptManager() {
        final ScriptEngineManager sem = new ScriptEngineManager();
        sef = sem.getEngineByName("javascript").getFactory();
    }

    public static MapScriptManager getInstance() {
        return instance;
    }

    public void getMapScript(MapleClient c, String scriptName, boolean firstUser) {
        if (scripts.containsKey(scriptName)) {
            scripts.get(scriptName).start(new MapScriptMethods(c));
            return;
        }

        String type;
        if (firstUser) {
            type = "onFirstUserEnter";
        } else {
            type = "onUserEnter";
        }

        final File scriptFile = new File("scripts/" + type + "/" + scriptName + ".js");
        if (!scriptFile.exists()) {
            return;
        }

        FileReader fr = null;
        final ScriptEngine portal = sef.getScriptEngine();
        try {
            fr = new FileReader(scriptFile);
            final CompiledScript compiled = ((Compilable) portal).compile(fr);
            compiled.eval();
        } catch (ScriptException | IOException e) {
            System.err.println("THROW" + e);
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (IOException e) {
                    System.err.println("ERROR CLOSING" + e);
                }
            }
        }

        final MapScript script = ((Invocable) portal).getInterface(MapScript.class);
        scripts.put(scriptName, script);
        script.start(new MapScriptMethods(c));
    }

    public void clearScripts() {
        scripts.clear();
    }
}