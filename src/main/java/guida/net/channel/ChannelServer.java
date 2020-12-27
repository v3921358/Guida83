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

package guida.net.channel;

import guida.client.MapleCharacter;
import guida.client.SkillFactory;
import guida.client.messages.CommandProcessor;
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.PacketProcessor;
import guida.net.Server;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.world.MapleParty;
import guida.net.world.guild.MapleGuild;
import guida.net.world.guild.MapleGuildCharacter;
import guida.net.world.guild.MapleGuildSummary;
import guida.net.world.remote.WorldChannelInterface;
import guida.net.world.remote.WorldRegistry;
import guida.provider.MapleDataProviderFactory;
import guida.scripting.event.EventScriptManager;
import guida.server.AutobanManager;
import guida.server.CashItemFactory;
import guida.server.MapleSquad;
import guida.server.MapleSquadType;
import guida.server.ShutdownServer;
import guida.server.TimerManager;
import guida.server.maps.MapMonitor;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapFactory;
import guida.server.maps.MapleMapTimer;
import guida.tools.MaplePacketCreator;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.rmi.ssl.SslRMIClientSocketFactory;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ChannelServer implements Runnable, ChannelServerMBean {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChannelServer.class);
    private static final Map<Integer, ChannelServer> instances = new HashMap<>();
    private static final Map<String, ChannelServer> pendingInstances = new HashMap<>();
    private static int uniqueID = 1;
    private static Properties initialProp;
    private static WorldRegistry worldRegistry;
    public final int[] level = {1, 200};
    private final PlayerStorage players = new PlayerStorage();
    private final String key;
    private final MapleMapFactory mapFactory;
    private final Map<Integer, MapleGuildSummary> gsStore = new HashMap<>();
    private final Map<MapleSquadType, MapleSquad> mapleSquads = new EnumMap<>(MapleSquadType.class);
    private final Map<Integer, MapMonitor> mapMonitors = new HashMap<>();
    private final Map<Integer, Integer> flagMaps = new HashMap<>();
    public int eventmap;
    private String serverMessage;
    private String GMList;
    private int expRate;
    private int mesoRate;
    private int dropRate;
    private int bossdropRate;
    private int petExpRate;
    private boolean gmWhiteText;
    private boolean cashshop;
    private boolean mts;
    private boolean dropUndroppables;
    private boolean moreThanOne;
    private int channel;
    private int instanceId = 0;
    private Properties props = new Properties();
    private ChannelWorldInterface cwi;
    private WorldChannelInterface wci = null;
    private Server server;
    private String ip;
    private boolean shutdown = false;
    private boolean finishedShutdown = false;
    private long lastEvent = 0;
    private EventScriptManager eventSM;
    private Boolean worldReady = true;
    private MapleMapTimer shutdownTimer = null;

    private ChannelServer(String key) {
        mapFactory = new MapleMapFactory(MapleDataProviderFactory.getDataProvider("Map"), MapleDataProviderFactory.getDataProvider("String"));
        this.key = key;
    }

    public static WorldRegistry getWorldRegistry() {
        return worldRegistry;
    }

    public static ChannelServer newInstance(String key) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
        ChannelServer instance = new ChannelServer(key);
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        mBeanServer.registerMBean(instance, new ObjectName("guida.net.channel:type=ChannelServer,name=ChannelServer" + uniqueID++));
        pendingInstances.put(key, instance);
        return instance;
    }

    public static ChannelServer getInstance(int channel) {
        ChannelServer ret = null;
        try {
            ret = instances.get(channel);
        } catch (IndexOutOfBoundsException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public static Collection<ChannelServer> getAllInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    public static void main(String[] args) throws IOException, NotBoundException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException {
        FileReader fileReader = null;
        try {
            Properties dbProp = new Properties();
            fileReader = new FileReader("db.properties");
            dbProp.load(fileReader);
            fileReader.close();
            DatabaseConnection.setProps(dbProp);
            DatabaseConnection.getConnection();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                fileReader.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        CashItemFactory.loadCommodities();
        initialProp = new Properties();
        initialProp.load(new FileReader(System.getProperty("guida.channel.config")));
        Registry registry = LocateRegistry.getRegistry(initialProp.getProperty("guida.world.host"), Registry.REGISTRY_PORT, new SslRMIClientSocketFactory());
        worldRegistry = (WorldRegistry) registry.lookup("WorldRegistry");
        for (int i = 0; i < Integer.parseInt(initialProp.getProperty("guida.channel.count", "0")); i++) {
            newInstance(initialProp.getProperty("guida.channel." + i + ".key")).run();
        }
        SkillFactory.loadSkills();
        CommandProcessor.registerMBean();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down.");
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                cs.shutdown();
            }
            log.info("Shutdown complete; exiting.");
        }));
    }

    public void reconnectWorld() {
        try { // check if the connection is really gone
            wci.isAvailable();
        } catch (RemoteException ex) {
            synchronized (worldReady) {
                worldReady = false;
            }
            synchronized (cwi) {
                synchronized (worldReady) {
                    if (worldReady) {
                        return;
                    }
                }
                log.warn("Reconnecting to world server");
                synchronized (wci) {
                    try { // completely re-establish the rmi connection
                        initialProp = new Properties();
                        FileReader fr = new FileReader(System.getProperty("guida.channel.config"));
                        initialProp.load(fr);
                        fr.close();
                        Registry registry = LocateRegistry.getRegistry(initialProp.getProperty("guida.world.host"), Registry.REGISTRY_PORT, new SslRMIClientSocketFactory());
                        worldRegistry = (WorldRegistry) registry.lookup("WorldRegistry");
                        cwi = new ChannelWorldInterfaceImpl(this);
                        wci = worldRegistry.registerChannelServer(key, cwi);
                        props = wci.getGameProperties();
                        expRate = Integer.parseInt(props.getProperty("guida.world.exp"));
                        mesoRate = Integer.parseInt(props.getProperty("guida.world.meso"));
                        dropRate = Integer.parseInt(props.getProperty("guida.world.drop"));
                        bossdropRate = Integer.parseInt(props.getProperty("guida.world.bossdrop"));
                        petExpRate = Integer.parseInt(props.getProperty("guida.world.petExp"));
                        serverMessage = props.getProperty("guida.world.serverMessage");
                        dropUndroppables = Boolean.parseBoolean(props.getProperty("guida.world.alldrop", "false"));
                        moreThanOne = Boolean.parseBoolean(props.getProperty("guida.world.morethanone", "false"));
                        gmWhiteText = Boolean.parseBoolean(props.getProperty("guida.world.gmWhiteText", "false"));
                        cashshop = Boolean.parseBoolean(props.getProperty("guida.world.cashshop", "false"));
                        mts = Boolean.parseBoolean(props.getProperty("guida.world.mts", "false"));
                        GMList = props.getProperty("guida.world.GMList");
                        Properties dbProp = new Properties();
                        fr = new FileReader("db.properties");
                        dbProp.load(fr);
                        fr.close();
                        DatabaseConnection.setProps(dbProp);
                        DatabaseConnection.getConnection();
                        wci.serverReady();
                    } catch (Exception e) {
                        log.error("Reconnecting failed", e);
                    }
                    worldReady = true;
                }
            }
            synchronized (worldReady) {
                worldReady.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        try {
            cwi = new ChannelWorldInterfaceImpl(this);
            wci = worldRegistry.registerChannelServer(key, cwi);
            props = wci.getGameProperties();
            expRate = Integer.parseInt(props.getProperty("guida.world.exp"));
            mesoRate = Integer.parseInt(props.getProperty("guida.world.meso"));
            dropRate = Integer.parseInt(props.getProperty("guida.world.drop"));
            bossdropRate = Integer.parseInt(props.getProperty("guida.world.bossdrop"));
            petExpRate = Integer.parseInt(props.getProperty("guida.world.petExp"));
            serverMessage = props.getProperty("guida.world.serverMessage");
            dropUndroppables = Boolean.parseBoolean(props.getProperty("guida.world.alldrop", "false"));
            moreThanOne = Boolean.parseBoolean(props.getProperty("guida.world.morethanone", "false"));
            eventSM = new EventScriptManager(this, props.getProperty("guida.channel.events").split(","));
            gmWhiteText = Boolean.parseBoolean(props.getProperty("guida.world.gmWhiteText", "false"));
            cashshop = Boolean.parseBoolean(props.getProperty("guida.world.cashshop", "false"));
            mts = Boolean.parseBoolean(props.getProperty("guida.world.mts", "false"));
            GMList = props.getProperty("guida.world.GMList");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        int port = Integer.parseInt(props.getProperty("guida.channel.net.port").trim());
        ip = props.getProperty("guida.channel.net.interface") + ":" + port;
        PacketProcessor.initialise(PacketProcessor.Mode.CHANNELSERVER);

        TimerManager tMan = TimerManager.getInstance();
        tMan.start();
        tMan.register(AutobanManager.getInstance(), 60000);
        try {
            server = new Server(new InetSocketAddress(port), channel);
            server.run();
            log.info("Channel {}: Listening on port {}", channel, port);
            wci.serverReady();
            eventSM.init();
        } catch (IOException e) {
            log.error("Binding to port " + port + " failed (ch: " + channel + ")", e);
        } catch (Exception e) {
            log.error("An error occured while loading ChannelServer", e);
        }
    }

    public void shutdown() { // dc all clients by hand so we get sessionClosed...
        eventSM.cancel();
        server.stop();
        shutdown = true;
        List<CountDownLatch> futures = new LinkedList<>();
        Collection<MapleCharacter> allchars = Collections.synchronizedCollection(players.getAllCharacters());

        MapleCharacter[] chrs = allchars.toArray(new MapleCharacter[0]);
        for (MapleCharacter chr : chrs) {
            if (chr != null && chr.getClient() != null) {
                futures.add(chr.getClient().getDisconnectLatch());
                chr.getClient().disconnect();
            }
        }

        for (CountDownLatch future : futures) {
            try {
                future.await();
            } catch (InterruptedException ignored) {
            }
        }

        finishedShutdown = true;

        wci = null;
        cwi = null;
    }

    public void unbind() {
        server.stop();
    }

    public boolean hasFinishedShutdown() {
        return finishedShutdown;
    }

    public MapleMapFactory getMapFactory() {
        return mapFactory;
    }

    public void addPlayer(MapleCharacter chr) {
        players.registerPlayer(chr);
    }

    public IPlayerStorage getPlayerStorage() {
        return players;
    }

    public void removePlayer(MapleCharacter chr) {
        players.deregisterPlayer(chr);
    }

    public int getConnectedClients() {
        return players.getAllCharacters().size();
    }

    @Override
    public String getServerMessage() {
        return serverMessage;
    }

    @Override
    public void setServerMessage(String newMessage) {
        serverMessage = newMessage;
        broadcastPacket(MaplePacketCreator.serverMessage(serverMessage));
    }

    public String getGMList() {
        if (GMList == null || GMList.length() < 2) {
            reloadGMList();
        }
        return GMList;
    }

    public void reloadGMList() {
        StringBuilder sb = new StringBuilder();
        for (MapleCharacter character : players.getAllCharacters()) {
            if (character.isGM()) {
                sb.append(character.getName()).append(", ");
            }
        }
        sb.setLength(sb.length() - 2);
        GMList = sb.toString();
    }

    public void broadcastPacket(MaplePacket data, boolean smega) {
        ArrayList<MapleCharacter> allChars = new ArrayList<>(players.getAllCharacters());
        for (MapleCharacter chr : allChars) {
            if (smega && !chr.getSmegaEnabled()) {
                continue;
            }
            chr.getClient().sendPacket(data);
        }
        allChars.clear();
    }

    public void broadcastPacket(MaplePacket data) {
        broadcastPacket(data, false);
    }

    public void broadcastGMPacket(MaplePacket data) {
        for (MapleCharacter chr : players.getAllCharacters()) {
            if (chr.isGM()) {
                chr.getClient().sendPacket(data);
            }
        }
    }

    @Override
    public int getExpRate() {
        return expRate;
    }

    @Override
    public void setExpRate(int expRate) {
        this.expRate = expRate;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        pendingInstances.remove(key);
        instances.remove(channel);
        instances.put(channel, this);
        this.channel = channel;
        mapFactory.setChannel(channel);
    }

    public String getIP() {
        return ip;
    }

    public String getIP(int channel) {
        try {
            return getWorldInterface().getIP(channel);
        } catch (RemoteException e) {
            log.error("Lost connection to world server", e);
            throw new RuntimeException("Lost connection to world server");
        }
    }

    public WorldChannelInterface getWorldInterface() {
        synchronized (worldReady) {
            reconnectWorld();
            while (!worldReady) {
                try {
                    worldReady.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return wci;
    }

    public String getProperty(String name) {
        return props.getProperty(name);
    }

    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public void broadcastWorldMessage(String message) {
        try {
            getWorldInterface().broadcastWorldMessage(message);
        } catch (RemoteException e) {
            reconnectWorld();
        }
    }

    @Override
    public void shutdown(int time) {
        broadcastPacket(MaplePacketCreator.serverNotice(0, "The server will shut down in " + time / 60000 + " minute(s). Please log off safely."));
        TimerManager.getInstance().schedule(new ShutdownServer(channel), time);
        for (MapleMap map : mapFactory.getMaps()) {
            map.clearShownMapTimer();
            map.addMapTimer(time / 1000, time / 1000, new String[0], false, true, null);
            if (shutdownTimer != null) {
                shutdownTimer = map.getShownMapTimer();
            }
        }
    }

    public MapleMapTimer getShutdownTimer() {
        return shutdownTimer;
    }

    public void setShutdownTimer(MapleMapTimer mmt) {
        shutdownTimer = mmt;
    }

    @Override
    public void shutdownWorld(int time) {
        time *= 60000;
        try {
            getWorldInterface().shutdown(time);
        } catch (RemoteException e) {
            reconnectWorld();
        }
    }

    public int getLoadedMaps() {
        return mapFactory.getLoadedMaps();
    }

    public EventScriptManager getEventSM() {
        return eventSM;
    }

    public void reloadEvents() {
        eventSM.cancel();
        eventSM = new EventScriptManager(this, props.getProperty("guida.channel.events").split(","));
        eventSM.init();
    }

    @Override
    public int getMesoRate() {
        return mesoRate;
    }

    @Override
    public void setMesoRate(int mesoRate) {
        this.mesoRate = mesoRate;
    }

    @Override
    public int getDropRate() {
        return dropRate;
    }

    @Override
    public void setDropRate(int dropRate) {
        this.dropRate = dropRate;
    }

    @Override
    public int getBossDropRate() {
        return bossdropRate;
    }

    @Override
    public void setBossDropRate(int bossdropRate) {
        this.bossdropRate = bossdropRate;
    }

    @Override
    public int getPetExpRate() {
        return petExpRate;
    }

    @Override
    public void setPetExpRate(int petExpRate) {
        this.petExpRate = petExpRate;
    }

    public boolean allowUndroppablesDrop() {
        return dropUndroppables;
    }

    public boolean allowMoreThanOne() {
        return moreThanOne;
    }

    public boolean allowGmWhiteText() {
        return gmWhiteText;
    }

    public boolean allowCashshop() {
        return cashshop;
    }

    public boolean allowMTS() {
        return mts;
    }

    public boolean characterNameExists(String name) {
        int size = 0;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT id FROM characters WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                size++;
            }
            ps.close();
            rs.close();
        } catch (SQLException e) {
            log.error("Error in charname check: \r\n" + e.toString());
        }
        return size >= 1;
    }

    public MapleGuild getGuild(MapleGuildCharacter mgc) {
        int gid = mgc.getGuildId();
        MapleGuild g = null;
        try {
            g = getWorldInterface().getGuild(gid);
        } catch (RemoteException re) {
            log.error("RemoteException while fetching MapleGuild.", re);
            return null;
        }

        if (gsStore.get(gid) == null) {
            gsStore.put(gid, new MapleGuildSummary(g));
        }

        return g;
    }

    public MapleGuildSummary getGuildSummary(int gid) {
        if (gid == 0) {
            return null;
        }
        if (gsStore.containsKey(gid)) {
            return gsStore.get(gid);
        } else { //this shouldn't happen much, if ever, but if we're caught, without the summary, we'll have to do a worldop
            try {
                MapleGuild g = getWorldInterface().getGuild(gid);
                if (g != null) {
                    gsStore.put(gid, new MapleGuildSummary(g));
                }
                return gsStore.get(gid); //if g is null, we will end up returning null
            } catch (RemoteException re) {
                log.error("RemoteException while fetching GuildSummary.", re);
                return null;
            }
        }
    }

    public void updateGuildSummary(int gid, MapleGuildSummary mgs) {
        gsStore.put(gid, mgs);
    }

    public void reloadGuildSummary() {
        try {
            MapleGuild g;
            for (int i : gsStore.keySet()) {
                g = getWorldInterface().getGuild(i);
                if (g != null) {
                    gsStore.put(i, new MapleGuildSummary(g));
                } else {
                    gsStore.remove(i);
                }
            }
        } catch (RemoteException re) {
            log.error("RemoteException while reloading GuildSummary.", re);
        }
    }

    public MapleSquad getMapleSquad(MapleSquadType type) {
        return mapleSquads.getOrDefault(type, null);
    }

    public boolean addMapleSquad(MapleSquad squad, MapleSquadType type) {
        if (mapleSquads.get(type) == null) {
            mapleSquads.remove(type);
            mapleSquads.put(type, squad);
            return true;
        } else {
            return false;
        }
    }

    public boolean removeMapleSquad(MapleSquad squad, MapleSquadType type) {
        if (mapleSquads.containsKey(type)) {
            if (mapleSquads.get(type) == squad) {
                mapleSquads.remove(type);
                return true;
            }
        }
        return false;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(int k) {
        instanceId = k;
    }

    public void addInstanceId() {
        instanceId++;
    }

    public void addMapMonitor(int mapId, MapMonitor monitor) {
        if (mapMonitors.containsKey(mapId)) {
            log.info("ERROR! Trying to add a map monitor to a map that already has it!");
            return;
        }
        mapMonitors.put(mapId, monitor);
    }

    public void removeMapMonitor(int mapId) {
        if (mapMonitors.containsKey(mapId)) {
            mapMonitors.remove(mapId);
        } else {
            log.info("ERROR! Trying to remove a map monitor that doesn't exist!");
        }
    }

    public List<MapleCharacter> getPartyMembers(MapleParty party) {
        List<MapleCharacter> partym = new LinkedList<>();
        for (guida.net.world.MaplePartyCharacter partychar : party.getMembers()) {
            if (partychar.getChannel() == channel) { // Make sure the thing doesn't get duplicate plays due to ccing bug.
                MapleCharacter chr = getPlayerStorage().getCharacterByName(partychar.getName());
                if (chr != null) {
                    partym.add(chr);
                }
            }
        }
        return partym;
    }

	/*public List<MapleCharacter> getAllCharsWithPlayerNPCs() {
        List<MapleCharacter> ret = new ArrayList<>();
		for (MapleCharacter chr : getPlayerStorage().getAllCharacters()) {
			if (chr.hasPlayerNPC()) {
				ret.add(chr);
			}
		}
		return ret;
	}*/

    public void unloadMap(int mapid) {
        mapFactory.destroyMap(mapid);
    }

    public void loadMap(int mapid) {
        mapFactory.getMap(mapid);
    }

    public void startEvent(int minlevel, int maxlevel, int map) {
        level[0] = minlevel;
        level[1] = maxlevel;
        eventmap = map;
    }

    public long getLastEvent() {
        return lastEvent;
    }

    public void setLastEvent(long time) {
        lastEvent = time;
    }

    public int getFlagMap(int charId) {
        return flagMaps.get(charId);
    }

    public void setFlagMap(int charId, int mapId) {
        flagMaps.put(charId, mapId);
    }
}