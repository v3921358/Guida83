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
import guida.net.world.MapleParty;
import guida.server.MapleSquad;
import guida.server.TimerManager;
import guida.server.maps.MapleMap;

import javax.script.Invocable;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Matze
 */
public class EventManager {

    private final Invocable iv;
    private final ChannelServer cserv;
    private final Map<String, EventInstanceManager> instances = new HashMap<>();
    private final Properties props = new Properties();
    private final String name;

    public EventManager(ChannelServer cserv, Invocable iv, String name) {
        this.iv = iv;
        this.cserv = cserv;
        this.name = name;
    }

    public void cancel() {
        try {
            iv.invokeFunction("cancelSchedule", (Object) null);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
    }

    public ScheduledFuture<?> schedule(final String methodName, long delay) {
        return TimerManager.getInstance().schedule(() -> {
            try {
                iv.invokeFunction(methodName, (Object) null);
            } catch (ScriptException | NoSuchMethodException ex) {
                Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
            }
        }, delay);
    }

    public ScheduledFuture<?> scheduleAtTimestamp(final String methodName, long timestamp) {
        return TimerManager.getInstance().scheduleAtTimestamp(() -> {
            try {
                iv.invokeFunction(methodName, (Object) null);
            } catch (ScriptException | NoSuchMethodException ex) {
                Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
            }
        }, timestamp);
    }

    public ChannelServer getChannelServer() {
        return cserv;
    }

    public EventInstanceManager getInstance(String name) {
        return instances.get(name);
    }

    public ArrayList<EventInstanceManager> getInstances() {
        return new ArrayList<>(instances.values());
    }

    public EventInstanceManager newInstance(String name) {
        final EventInstanceManager ret = new EventInstanceManager(this, name);
        instances.put(name, ret);
        return ret;
    }

    public void disposeInstance(String name) {
        instances.remove(name);
    }

    public Invocable getIv() {
        return iv;
    }

    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    public String getName() {
        return name;
    }

    //PQ method: starts a PQ
    public void startInstance(MapleParty party, MapleMap map) {
        try {
            final EventInstanceManager eim = (EventInstanceManager) iv.invokeFunction("setup", (Object) null);
            eim.registerParty(party, map);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
    }

    public void startInstance(MapleParty party, MapleMap map, boolean partyid) {
        try {
            EventInstanceManager eim;
            if (partyid) {
                eim = (EventInstanceManager) iv.invokeFunction("setup", party.getId());
            } else {
                eim = (EventInstanceManager) iv.invokeFunction("setup", (Object) null);
            }
            eim.registerParty(party, map);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
    }

    public void startInstance(MapleSquad squad, MapleMap map) {
        try {
            final EventInstanceManager eim = (EventInstanceManager) iv.invokeFunction("setup", squad.getLeader().getId());
            eim.registerSquad(squad, map);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
    }

    //non-PQ method for starting instance
    public void startInstance(EventInstanceManager eim, String leader) {
        try {
            iv.invokeFunction("setup", eim);
            eim.setProperty("leader", leader);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
    }

    //returns EventInstanceManager
    public EventInstanceManager startEventInstance(MapleParty party, MapleMap map, boolean partyid) {
        try {
            EventInstanceManager eim;
            if (partyid) {
                eim = (EventInstanceManager) iv.invokeFunction("setup", party.getId());
            } else {
                eim = (EventInstanceManager) iv.invokeFunction("setup", (Object) null);
            }
            eim.registerParty(party, map);
            return eim;
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, "Error in event script " + name, ex);
        }
        return null;
    }
}