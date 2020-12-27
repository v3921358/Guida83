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

import guida.client.MapleCharacter;
import guida.database.DatabaseConnection;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.provider.MapleDataProviderFactory;
import guida.server.MapleSquad;
import guida.server.TimerManager;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapFactory;

import javax.script.ScriptException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Matze
 */
public class EventInstanceManager {

    private final List<MapleCharacter> chars = new LinkedList<>();
    private final List<MapleMonster> mobs = new LinkedList<>();
    private final Map<MapleCharacter, Integer> killCount = new HashMap<>();
    private final String name;
    private final Properties props = new Properties();
    private EventManager em;
    private MapleMapFactory mapFactory;
    private long timeStarted = 0;
    private long eventTime = 0;

    public EventInstanceManager(EventManager em, String name) {
        this.em = em;
        this.name = name;
        mapFactory = new MapleMapFactory(MapleDataProviderFactory.getDataProvider("Map"), MapleDataProviderFactory.getDataProvider("String"));
        mapFactory.setChannel(em.getChannelServer().getChannel());
    }

    public void registerPlayer(MapleCharacter chr) {
        if (chr != null && chr.getEventInstance() == null) {
            try {
                chars.add(chr);
                chr.setEventInstance(this);
                em.getIv().invokeFunction("playerEntry", this, chr);
            } catch (ScriptException | NoSuchMethodException ex) {
                Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
            }
        }
    }

    public void startEventTimer(long time) {
        timeStarted = System.currentTimeMillis();
        eventTime = time;
    }

    public boolean isTimerStarted() {
        return eventTime > 0 && timeStarted > 0;
    }

    public long getTimeLeft() {
        return eventTime - (System.currentTimeMillis() - timeStarted);
    }

    public void registerParty(MapleParty party, MapleMap map) {
        List<MaplePartyCharacter> pMembers = new ArrayList<>(party.getMembers());
        for (MaplePartyCharacter pc : pMembers) {
            if (pc.isOnline()) {
                MapleCharacter c = map.getCharacterById(pc.getId());
                registerPlayer(c);
            }
        }
    }

    public void registerSquad(MapleSquad squad, MapleMap map) {
        List<MapleCharacter> sMembers = new ArrayList<>(squad.getMembers());
        for (MapleCharacter player : sMembers) {
            if (map.getCharacterById(player.getId()) != null) {
                registerPlayer(player);
            }
        }
        sMembers.clear();
    }

    public void unregisterPlayer(MapleCharacter chr) {
        chars.remove(chr);
        chr.setEventInstance(null);
    }

    public int getPlayerCount() {
        return chars.size();
    }

    public List<MapleCharacter> getPlayers() {
        return new ArrayList<>(chars);
    }

    public boolean allPlayersInMap(int mapid) {
        int inMap = 0;
        for (MapleCharacter c : getPlayers()) {
            if (c.getMapId() == mapid) {
                inMap++;
            }
        }
        return inMap >= getPlayerCount(); // Even though it should never be more than... lol
    }

    public void registerMonster(MapleMonster mob) {
        mobs.add(mob);
        mob.setEventInstance(this);
    }

    public void unregisterMonster(MapleMonster mob) {
        if (mob != null) {
            mobs.remove(mob);
            mob.setEventInstance(null);
        }
        if (mobs.isEmpty()) {
            try {
                em.getIv().invokeFunction("allMonstersDead", this);
            } catch (ScriptException | NoSuchMethodException ex) {
                Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
            }
        }
    }

    public void playerKilled(MapleCharacter chr) {
        try {
            em.getIv().invokeFunction("playerDead", this, chr);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public boolean revivePlayer(MapleCharacter chr) {
        try {
            Object b = em.getIv().invokeFunction("playerRevive", this, chr);
            if (b instanceof Boolean) {
                return (Boolean) b;
            }
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
        return true;
    }

    public void playerDisconnected(MapleCharacter chr) {
        try {
            em.getIv().invokeFunction("playerDisconnected", this, chr);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public void playerMapExit(MapleCharacter chr) {
        try {
            em.getIv().invokeFunction("playerMapExit", this, chr);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    /**
     * @param chr
     * @param mobId
     */
    public void monsterKilled(MapleCharacter chr, int mobId) {
        try {
            Integer kc = killCount.get(chr);
            int inc = ((Double) em.getIv().invokeFunction("monsterValue", this, mobId)).intValue();
            if (kc == null) {
                kc = inc;
            } else {
                kc += inc;
            }
            killCount.put(chr, kc);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public int getKillCount(MapleCharacter chr) {
        Integer kc = killCount.get(chr);
        if (kc == null) {
            return 0;
        } else {
            return kc;
        }
    }

    public void dispose() {
        chars.clear();
        mobs.clear();
        killCount.clear();
        mapFactory = null;
        em.disposeInstance(name);
        em = null;
    }

    public MapleMapFactory getMapFactory() {
        return mapFactory;
    }

    public ScheduledFuture<?> schedule(final String methodName, long delay) {
        return TimerManager.getInstance().schedule(() -> {
            try {
                em.getIv().invokeFunction(methodName, EventInstanceManager.this);
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            } catch (ScriptException | NoSuchMethodException ex) {
                Logger.getLogger(EventManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }, delay);
    }

    public String getName() {
        return name;
    }

    public void saveWinner(MapleCharacter chr) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO eventstats (event, instance, characterid, channel) VALUES (?, ?, ?, ?)");
            ps.setString(1, em.getName());
            ps.setString(2, name);
            ps.setInt(3, chr.getId());
            ps.setInt(4, chr.getClient().getChannel());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public MapleMap getMapInstance(int mapId) {
        boolean wasLoaded = mapFactory.isMapLoaded(mapId);
        MapleMap map = mapFactory.getMap(mapId, true);

        // in case reactors need shuffling and we are actually loading the map
        if (!wasLoaded) {
            if (em != null && em.getProperty("shuffleReactors") != null && em.getProperty("shuffleReactors").equals("true")) {
                map.shuffleReactors();
            }
        }

        return map;
    }

    public void setProperty(String key, String value) {
        props.setProperty(key, value);
    }

    public Object setProperty(String key, String value, boolean prev) {
        return props.setProperty(key, value);
    }

    public String getProperty(String key) {
        return props.getProperty(key);
    }

    public void leftParty(MapleCharacter chr) {
        try {
            em.getIv().invokeFunction("leftParty", this, chr);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public void disbandParty() {
        try {
            em.getIv().invokeFunction("disbandParty", this);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    //Separate function to warp players to a "finish" map, if applicable
    public void finishPQ() {
        try {
            em.getIv().invokeFunction("clearPQ", this);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public void removePlayer(MapleCharacter chr) {
        try {
            em.getIv().invokeFunction("playerExit", this, chr);
        } catch (ScriptException | NoSuchMethodException ex) {
            Logger.getLogger(EventInstanceManager.class.getName()).log(Level.SEVERE, "Error found in event script " + em.getName(), ex);
        }
    }

    public boolean isPartyLeader(MapleCharacter chr) {
        return chr.getParty().getLeader().getId() == chr.getId();
    }

    public void saveAllBossQuestPoints(int bossPoints) {
        for (MapleCharacter character : chars) {
            int points = character.getBossPoints();
            character.setBossPoints(points + bossPoints);
        }
    }

    public void saveBossQuestPoints(int bossPoints, MapleCharacter character) {
        int points = character.getBossPoints();
        character.setBossPoints(points + bossPoints);
    }
}