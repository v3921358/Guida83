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

package guida.scripting;

import guida.client.MapleClient;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileReader;

/**
 * @author Matze
 */
public abstract class AbstractScriptManager {

    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AbstractScriptManager.class);
    private final ScriptEngineManager sem;
    protected ScriptEngine engine;

    protected AbstractScriptManager() {
        sem = new ScriptEngineManager();
    }

    protected Invocable getInvocable(String path, MapleClient c) {
        try {
            path = "scripts/" + path;
            engine = null;
            if (c != null) {
                engine = c.getScriptEngine(path);
            }
            if (engine == null) {
                final File scriptFile = new File(path);
                if (!scriptFile.exists()) {
                    return null;
                }
                engine = sem.getEngineByName("graal.js");
                if (c != null) {
                    c.setScriptEngine(path, engine);
                }
                final FileReader fr = new FileReader(scriptFile);
                if (engine == null) {
                    fr.close();
                    return null;
                }
                Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
                bindings.put("polyglot.js.allowAllAccess", true);
                engine.eval(fr);
                fr.close();
            }
            return (Invocable) engine;
        } catch (Exception e) {
            log.error("Error executing script. Script file: " + path + ".", e);
            return null;
        }
    }

    protected void resetContext(String path, MapleClient c) {
        path = "scripts/" + path;
        c.removeScriptEngine(path);
    }
}