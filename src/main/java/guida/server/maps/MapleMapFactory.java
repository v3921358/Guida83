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

package guida.server.maps;

import guida.net.channel.ChannelServer;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.server.MaplePortal;
import guida.server.PortalFactory;
import guida.server.life.AbstractLoadedMapleLife;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleNPC;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MapleMapFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleMapFactory.class);
    private final MapleDataProvider source;
    private final MapleData nameData;
    private final ConcurrentMap<Integer, MapleMap> maps = new ConcurrentHashMap<>(1024, 0.75f, 10);
    private int channel;

    public MapleMapFactory(MapleDataProvider source, MapleDataProvider stringSource) {
        this.source = source;
        nameData = stringSource.getData("Map.img");
    }

    public boolean isMapLoaded(int mapId) {
        return maps.containsKey(mapId);
    }

    public int getLoadedMaps() {
        return maps.size();
    }

    public MapleMap getMap(int mapid) {
        return getMap(mapid, true, true, true, true, false);
    }

    public MapleMap getMap(int mapid, boolean isInstance) {
        return getMap(mapid, true, true, true, true, isInstance);
    }

    public MapleMap getMap(int mapid, boolean respawns, boolean npcs, boolean reactors, boolean isInstance) {
        return getMap(mapid, respawns, npcs, reactors, true, isInstance);
    }

    public MapleMap getMap(int mapid, boolean respawns, boolean npcs, boolean reactors, boolean portals, boolean isInstance) {
        MapleMap map = maps.get(mapid);
        if (map == null) {
            MapleData mapData = source.getData(getMapName(mapid));
            if (mapData == null) {
                return null;
            }
            MapleData info = mapData.getChild("info");
            final int lmid = DataUtil.toInt(info.resolve("link"), 0);
            if (lmid != 0) {
                MapleData lmapData = source.getData(getMapName(lmid));
                if (lmapData != null) {
                    mapData = lmapData;
                    info = lmapData.getChild("info");
                }
            }
            float monsterRate = 0;
            if (respawns) {
                MapleData mobRate = mapData.resolve("info/mobRate");
                monsterRate = mobRate != null ? DataUtil.toFloat(mobRate) : 1;
            }
            MapleMap newMap = new MapleMap(mapid, channel, DataUtil.toInt(info.resolve("returnMap")), monsterRate, isInstance);
            newMap.setMapName(DataUtil.toString(nameData.resolve(getMapStringName(mapid)).resolve("mapName"), ""));
            newMap.setStreetName(DataUtil.toString(nameData.resolve(getMapStringName(mapid)).resolve("streetName"), ""));
            newMap.setEverlast(DataUtil.toInt(info.resolve("everlast"), 0) == 1);
            newMap.setTown(DataUtil.toInt(info.resolve("town"), 0) == 1);
            newMap.setAllowShops(DataUtil.toInt(info.resolve("personalShop"), 0) == 1);
            newMap.setForcedReturnMap(DataUtil.toInt(info.resolve("forcedReturn"), 999999999));
            newMap.setFieldLimit(DataUtil.toInt(info.resolve("fieldLimit"), 0));
            newMap.setHPDec((short) DataUtil.toInt(info.resolve("decHP"), 0));
            newMap.setHPDecProtect(DataUtil.toInt(info.resolve("protectItem"), 0));
            int def = -1;
            newMap.setTimeLimit(DataUtil.toInt(info.resolve("timeLimit"), def));
            newMap.setFirstUserEnter(DataUtil.toString(info.resolve("onFirstUserEnter"), ""));
            newMap.setUserEnter(DataUtil.toString(info.resolve("onUserEnter"), ""));
            newMap.setPartyOnly(DataUtil.toInt(info.resolve("partyOnly"), 0) == 1);
            newMap.setLevelLimit(DataUtil.toInt(info.resolve("lvLimit"), 0));
            newMap.setLevelForceMove(DataUtil.toInt(info.resolve("lvForceMove"), 201));
            newMap.setClock(mapData.getChild("clock") != null);
            newMap.setBoat(mapData.getChild("shipObj") != null);
            List<MapleFoothold> allFootholds = new LinkedList<>();
            Point lBound = new Point();
            Point uBound = new Point();
            for (MapleData footRoot : mapData.getChild("foothold")) {
                for (MapleData footCat : footRoot) {
                    for (MapleData footHold : footCat) {
                        int x1 = DataUtil.toInt(footHold.getChild("x1"));
                        int y1 = DataUtil.toInt(footHold.getChild("y1"));
                        int x2 = DataUtil.toInt(footHold.getChild("x2"));
                        int y2 = DataUtil.toInt(footHold.getChild("y2"));
                        MapleFoothold fh = new MapleFoothold(new Point(x1, y1), new Point(x2, y2), Short.parseShort(footHold.getName()));
                        fh.setPrev(DataUtil.toInt(footHold.getChild("prev")));
                        fh.setNext(DataUtil.toInt(footHold.getChild("next")));
                        if (fh.getX1() < lBound.x) {
                            lBound.x = fh.getX1();
                        }
                        if (fh.getX2() > uBound.x) {
                            uBound.x = fh.getX2();
                        }
                        if (fh.getY1() < lBound.y) {
                            lBound.y = fh.getY1();
                        }
                        if (fh.getY2() > uBound.y) {
                            uBound.y = fh.getY2();
                        }
                        allFootholds.add(fh);
                    }
                }
            }
            MapleFootholdTree fTree = new MapleFootholdTree(lBound, uBound);
            for (MapleFoothold fh : allFootholds) {
                fTree.insert(fh);
            }
            newMap.setFootholds(fTree);
            for (MapleData life : mapData.getChild("life")) {
                String id = DataUtil.toString(life.getChild("id"));
                if (id != null) {
                    if (id.equals("2111002") && mapid == 926120200) //new BPQ
                    {
                        id = "9000037";
                    }
                    if (id.equals("2042000") && mapid == 980000000 && isInstance) {
                        continue;
                    }
                    if (isInstance && mapid == 926100401) {
                        continue;
                    }
                    String type = DataUtil.toString(life.getChild("type"));
                    if (npcs || !type.equals("n")) {
                        AbstractLoadedMapleLife myLife = loadLife(life, id, type);
                        if (myLife instanceof MapleMonster) {
                            MapleMonster monster = (MapleMonster) myLife;
                            int mobTime = DataUtil.toInt(life.resolve("mobTime"), 0);
                            if (mobTime == -1 && respawns) { //does not respawn, force spawn once
                                newMap.spawnMonster(monster);
                            } else {
                                newMap.addMonsterSpawn(monster, mobTime);
                            }
                        } else {
                            newMap.addMapObject(myLife);
                        }
                    }
                }
            }
            ArrayList<CustomLifeSpawn> customSpawns = new ArrayList<>();
            switch (mapid) {
                case 100000000 -> {
                    customSpawns.add(new CustomLifeSpawn(9201114, new Point(321, 65)));
                    customSpawns.add(new CustomLifeSpawn(9300010, new Point(3833, 454)));
                    customSpawns.add(new CustomLifeSpawn(9220020, new Point(2759, 184)));
                }
                case 101000000 -> customSpawns.add(new CustomLifeSpawn(9201142, new Point(1114, -1896)));
                case 102000000 -> {
                    customSpawns.add(new CustomLifeSpawn(1052015, new Point(646, 598)));
                    customSpawns.add(new CustomLifeSpawn(9201117, new Point(1900, 662)));
                }
                case 103000000 -> customSpawns.add(new CustomLifeSpawn(9201107, new Point(2218, -204)));
                case 108010600 -> customSpawns.add(new CustomLifeSpawn(1104102, new Point(2643, 88)));
                case 108010610 -> customSpawns.add(new CustomLifeSpawn(1104104, new Point(3505, 88)));
                case 108010620 -> customSpawns.add(new CustomLifeSpawn(1104100, new Point(179, 88)));
                case 108010640 -> customSpawns.add(new CustomLifeSpawn(1104101, new Point(536, 88)));
                case 108010630 -> customSpawns.add(new CustomLifeSpawn(1104103, new Point(-2170, 88)));
                case 913030000 -> customSpawns.add(new CustomLifeSpawn(1104002, new Point(-268, 88)));
                case 926100201 -> customSpawns.add(new CustomLifeSpawn(9000035, new Point(1361, 229)));
                case 926100203, 926110203 -> customSpawns.add(new CustomLifeSpawn(9000035, new Point(-309, 243)));
                case 970030020 -> customSpawns.add(new CustomLifeSpawn(1052013, new Point(277, 187)));
                case 980000000 -> customSpawns.add(new CustomLifeSpawn(9000037, new Point(121, -168)));
            }
            customSpawns.trimToSize();
            for (CustomLifeSpawn cSpawn : customSpawns) {
                if (cSpawn.getMobTime() == -1) {
                    MapleNPC npc = MapleLifeFactory.getNPC(cSpawn.getId());
                    if (npc != null && !npc.getName().equals("MISSINGNO")) {
                        npc.setPosition(cSpawn.getPoint());
                        npc.setCy(cSpawn.getPoint().x);
                        npc.setRx0(cSpawn.getPoint().x + 50);
                        npc.setRx1(cSpawn.getPoint().x - 50);
                        npc.setFh(newMap.getFootholds().findBelow(cSpawn.getPoint()).getId());
                        npc.setCustom(true);
                        newMap.addMapObject(npc);
                        newMap.broadcastMessage(MaplePacketCreator.spawnNPC(npc));
                    }
                } else if (channel == 1) {
                    MapleMonster mob = MapleLifeFactory.getMonster(cSpawn.getId());
                    if (mob != null) {
                        mob.setPosition(cSpawn.getPoint());
                        mob.setCy(cSpawn.getPoint().x);
                        mob.setRx0(cSpawn.getPoint().x + 50);
                        mob.setRx1(cSpawn.getPoint().x - 50);
                        mob.setFh(newMap.getFootholds().findBelow(cSpawn.getPoint()).getId());
                        newMap.addMonsterSpawn(mob, cSpawn.getMobTime());
                    }
                }
            }
            newMap.loadMonsterRate();
            // load areas (EG PQ platforms)
            if (mapData.getChild("area") != null) {
                for (MapleData area : mapData.getChild("area")) {
                    int x1 = DataUtil.toInt(area.getChild("x1"));
                    int y1 = DataUtil.toInt(area.getChild("y1"));
                    int x2 = DataUtil.toInt(area.getChild("x2"));
                    int y2 = DataUtil.toInt(area.getChild("y2"));
                    Rectangle mapArea = new Rectangle(x1, y1, (x2 - x1), (y2 - y1));
                    newMap.addMapleArea(mapArea);
                }
            }
            if (reactors && mapData.getChild("reactor") != null) {
                for (MapleData reactor : mapData.getChild("reactor")) {
                    String id = DataUtil.toString(reactor.getChild("id"));
                    if (id != null) {
                        newMap.spawnReactor(loadReactor(reactor, id));
                    }
                }
            }
            /*if (mapData.getChildByName("BuffZone") != null) {
				MapleData area = mapData.getChildByPath("BuffZone/hill");
				int x1 = MapleDataTool.getInt(area.getChildByName("x1"));
				int y1 = MapleDataTool.getInt(area.getChildByName("y1"));
				int x2 = MapleDataTool.getInt(area.getChildByName("x2"));
				int y2 = MapleDataTool.getInt(area.getChildByName("y2"));
				Rectangle mapArea = new Rectangle(x1, y1, (x2 - x1), (y2 - y1));
				int itemId = MapleDataTool.getInt(area.getChildByName("ItemID"));
				int duration = MapleDataTool.getInt(area.getChildByName("Interval"));
				int interval = MapleDataTool.getInt(area.getChildByName("Duration"));
				MapleBuffZone zone = new MapleBuffZone(mapArea, itemId, duration, interval);
				map.setBuffZone(zone);
			}*/
            if (mapData.getChild("seat") != null) {
                for (MapleData seat : mapData.getChild("seat")) {
                    newMap.addSeat(Integer.parseInt(seat.getName()), DataUtil.toPoint(seat));
                }
            }
            if (portals) {
                PortalFactory portalFactory = new PortalFactory();
                for (MapleData portal : mapData.getChild("portal")) {
                    int type = DataUtil.toInt(portal.getChild("pt"));
                    MaplePortal myPortal = portalFactory.makePortal(type, portal);
                    newMap.addPortal(myPortal);
                }
            }
            if (channel > 0) {
                ChannelServer cs = ChannelServer.getInstance(channel);
                if (cs != null && cs.getShutdownTimer() != null) {
                    MapleMapTimer mmt = cs.getShutdownTimer();
                    newMap.addMapTimer(mmt.getTimeLeft(), mmt.getTimeLeft(), new String[0], false, true, null);
                }
            }
            map = maps.putIfAbsent(mapid, newMap);
            if (map == null) {
                map = newMap;
            }
        }
        return map;
    }

    public Collection<MapleMap> getMaps() {
        return maps.values();
    }

    public boolean destroyMap(int mapid) {
        return maps.remove(mapid) != null;
    }

    private AbstractLoadedMapleLife loadLife(MapleData life, String id, String type) {
        AbstractLoadedMapleLife myLife = MapleLifeFactory.getLife(Integer.parseInt(id), type);
        myLife.setCy(DataUtil.toInt(life.getChild("cy")));
        MapleData dF = life.getChild("f");
        if (dF != null) {
            myLife.setF(DataUtil.toInt(dF));
        }
        myLife.setFh(DataUtil.toInt(life.getChild("fh")));
        myLife.setRx0(DataUtil.toInt(life.getChild("rx0")));
        myLife.setRx1(DataUtil.toInt(life.getChild("rx1")));
        int x = DataUtil.toInt(life.getChild("x"));
        int y = DataUtil.toInt(life.getChild("y"));
        myLife.setPosition(new Point(x, y));

        int hide = DataUtil.toInt(life.resolve("hide"), 0);
        if (hide == 1) {
            myLife.setHide(true);
        } else if (hide > 1) {
            log.warn("Hide > 1 ({})", hide);
        }
        return myLife;
    }

    private MapleReactor loadReactor(MapleData reactor, String id) {
        MapleReactor myReactor = new MapleReactor(MapleReactorFactory.getReactor(Integer.parseInt(id)), Integer.parseInt(id));

        int x = DataUtil.toInt(reactor.getChild("x"));
        int y = DataUtil.toInt(reactor.getChild("y"));
        myReactor.setPosition(new Point(x, y));

        myReactor.setDelay(DataUtil.toInt(reactor.getChild("reactorTime")) * 1000);
        myReactor.setState((byte) 0);
        myReactor.setName(DataUtil.toString(reactor.getChild("name"), ""));

        return myReactor;
    }

    private String getMapName(int mapid) {
        String mapName = StringUtil.getLeftPaddedStr(Integer.toString(mapid), '0', 9);
        int area = mapid / 100000000;

        return "Map/Map" + area + "/" + mapName + ".img";
    }

    private String getMapStringName(int mapid) {
        StringBuilder builder = new StringBuilder();

        if (mapid < 100000000) {
            builder.append("maple");
        } else if (mapid >= 100000000 && mapid < 200000000) {
            builder.append("victoria");
        } else if (mapid >= 200000000 && mapid < 300000000) {
            builder.append("ossyria");
        } else if (mapid >= 300000000 && mapid < 400000000) {
            builder.append("elin");
        } else if (mapid >= 540000000 && mapid < 560000000) {
            builder.append("singapore");
        } else if (mapid >= 600000000 && mapid < 620000000) {
            builder.append("MasteriaGL");
        } else if (mapid >= 670000000 && mapid < 671000000 || mapid >= 680000000 && mapid < 680100000 || mapid == 681000000) {
            builder.append("weddingGL");
        } else if (mapid >= 677000000 && mapid < 678000000) {
            builder.append("Episode1GL");
        } else if (mapid >= 682000000 && mapid < 683000000) {
            builder.append("HalloweenGL");
        } else if (mapid >= 683000000 && mapid < 684000000) {
            builder.append("event");
        } else if (mapid >= 800000000 && mapid < 802000000 || mapid >= 809000000 && mapid <= 810000000 || mapid >= 880000000 && mapid < 882000000 || mapid >= 890000000 && mapid < 891000000) {
            builder.append("jp");
        } else {
            builder.append("etc");
        }
        builder.append("/");
        builder.append(mapid);

        return builder.toString();
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public void respawnReactors(MapleMap map) {
        MapleData mapData = source.getData(getMapName(map.getId()));
        if (mapData != null && mapData.getChild("reactor") != null) {
            map.destroyReactors();
            for (MapleData reactor : mapData.getChild("reactor")) {
                String id = DataUtil.toString(reactor.getChild("id"));
                if (id != null) {
                    map.spawnReactor(loadReactor(reactor, id));
                }
            }
        }
    }

    private static class CustomLifeSpawn {

        private final int npcId;
        private final Point pos;
        private final int mobTime;

        public CustomLifeSpawn(int npcId, Point pos) {
            this.npcId = npcId;
            this.pos = pos;
            mobTime = -1;
        }

        public CustomLifeSpawn(int npcId, Point pos, int mobTime) {
            this.npcId = npcId;
            this.pos = pos;
            this.mobTime = mobTime;
        }

        public int getId() {
            return npcId;
        }

        public Point getPoint() {
            return pos;
        }

        public int getMobTime() {
            return mobTime;
        }
    }
}