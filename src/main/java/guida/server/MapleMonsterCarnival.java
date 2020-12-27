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

package guida.server;

import guida.client.MapleCharacter;
import guida.net.channel.ChannelServer;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;

import java.util.concurrent.ScheduledFuture;

public class MapleMonsterCarnival {

    public static final int D = 3;
    public static final int C = 2;
    public static final int B = 1;
    public static final int A = 0;
    private final MapleMap map;
    private MapleParty p1, p2;
    private ScheduledFuture<?> timer;
    private ScheduledFuture<?> effectTimer;
    private long startTime;
    private MapleCharacter leader1, leader2;
    private int redCP, blueCP, redTotalCP, blueTotalCP;

    public MapleMonsterCarnival(MapleParty p1, MapleParty p2, int mapid) {
        this.p1 = p1;
        this.p2 = p2;
        int chnl = p1.getLeader().getChannel();
        int chnl1 = p2.getLeader().getChannel();
        if (chnl != chnl1) {
            throw new RuntimeException("ERROR: CPQ leaders are on different channels.");
        }
        ChannelServer cs = ChannelServer.getInstance(chnl);
        p1.setEnemy(p2);
        p2.setEnemy(p1);
        cs.getMapFactory().destroyMap(mapid);
        map = cs.getMapFactory().getMap(mapid);
        int redPortal = 0;
        int bluePortal = 0;
        if (map.isPurpleCPQMap()) {
            redPortal = 2;
            bluePortal = 1;
        }
        for (MaplePartyCharacter mpc : p1.getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                mc.setMonsterCarnival(this);
                mc.changeMap(map, map.getPortal(redPortal));
                mc.setTeam(0);
                if (p1.getLeader().getId() == mc.getId()) {
                    leader1 = mc;
                }
            }
        }
        for (MaplePartyCharacter mpc : p2.getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                mc.setMonsterCarnival(this);
                mc.changeMap(map, map.getPortal(bluePortal));
                mc.setTeam(1);
                if (p2.getLeader().getId() == mc.getId()) {
                    leader2 = mc;
                }
            }
        }
        startTime = System.currentTimeMillis() + 60 * 10000;
        timer = TimerManager.getInstance().schedule(() -> timeUp(), 10 * 60 * 1000);
        effectTimer = TimerManager.getInstance().schedule(() -> complete(), 10 * 60 * 1000 - 10 * 1000);
        TimerManager.getInstance().schedule(() -> map.addClock(60 * 10), 2000);
    }

    public void playerDisconnected(int charid) {
        if (leader1.getId() == charid || leader2.getId() == charid) {
            earlyFinish();
            int team = -1;
            for (MaplePartyCharacter mpc : leader1.getParty().getMembers()) {
                if (mpc.getId() == charid) {
                    team = 0;
                    break;
                }
            }
            for (MaplePartyCharacter mpc : leader2.getParty().getMembers()) {
                if (mpc.getId() == charid) {
                    team = 1;
                    break;
                }
            }
            if (team == -1) {
                team = 1;
            }
            String teamS = switch (team) {
                case 0 -> "Red";
                case 1 -> "Blue";
                default -> "undefined";
            };
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, "Maple " + teamS + " has quitted the Monster Carnival."));
        } else {
            map.broadcastMessage(MaplePacketCreator.serverNotice(5, ChannelServer.getInstance(1).getPlayerStorage().getCharacterById(charid).getName() + " has quitted the Monster Carnival."));
        }
    }

    public void earlyFinish() {
        dispose(true);
    }

    public void leftParty(int charid) {
        playerDisconnected(charid);
    }

    protected int getRankByCP(int cp) {
        if (cp < 50) {
            return D;
        } else if (cp > 50 && cp < 100) {
            return C;
        } else if (cp > 100 && cp < 300) {
            return B;
        } else if (cp > 300) {
            return A;
        }
        return D;
    }

    protected void dispose() {
        dispose(false);
    }

    protected void dispose(boolean warpout) {
        int chnl = p1.getLeader().getChannel();
        ChannelServer cs = ChannelServer.getInstance(chnl);
        MapleMap out = cs.getMapFactory().getMap(980000000);
        for (MaplePartyCharacter mpc : leader1.getParty().getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                mc.setCPQRanking(getRankByCP(redTotalCP));
                mc.resetCP();
                if (warpout) {
                    mc.changeMap(out, out.getPortal(0));
                }
            }
        }
        for (MaplePartyCharacter mpc : leader2.getParty().getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                mc.setCPQRanking(getRankByCP(blueTotalCP));
                mc.resetCP();
                if (warpout) {
                    mc.changeMap(out, out.getPortal(0));
                }
            }
        }
        timer.cancel(false);
        effectTimer.cancel(false);
        redTotalCP = 0;
        blueTotalCP = 0;
        leader1.getParty().setEnemy(null);
        leader2.getParty().setEnemy(null);
    }

    public void exit() {
        dispose();
    }

    public ScheduledFuture<?> getTimer() {
        return timer;
    }

    public void finish(int winningTeam) {
        int chnl = leader1.getClient().getChannel();
        int chnl1 = leader2.getClient().getChannel();
        if (chnl != chnl1) {
            throw new RuntimeException("CPQ leaders are on different channels..");
        }
        ChannelServer cs = ChannelServer.getInstance(chnl);
        if (winningTeam == 0) {
            for (MaplePartyCharacter mpc : leader1.getParty().getMembers()) {
                MapleCharacter mc;
                mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
                if (mc != null) {
                    mc.setCPQRanking(getRankByCP(redTotalCP));
                    mc.changeMap(cs.getMapFactory().getMap(map.getId() + 2), cs.getMapFactory().getMap(map.getId() + 2).getPortal(0));
                    mc.setTeam(-1);
                }
            }
            for (MaplePartyCharacter mpc : leader2.getParty().getMembers()) {
                MapleCharacter mc;
                mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
                if (mc != null) {
                    mc.setCPQRanking(getRankByCP(blueTotalCP));
                    mc.changeMap(cs.getMapFactory().getMap(map.getId() + 3), cs.getMapFactory().getMap(map.getId() + 3).getPortal(0));
                    mc.setTeam(-1);
                }
            }
        } else if (winningTeam == 1) {
            for (MaplePartyCharacter mpc : leader2.getParty().getMembers()) {
                MapleCharacter mc;
                mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
                if (mc != null) {
                    mc.changeMap(cs.getMapFactory().getMap(map.getId() + 2), cs.getMapFactory().getMap(map.getId() + 2).getPortal(0));
                    mc.setTeam(-1);
                }
            }
            for (MaplePartyCharacter mpc : leader1.getParty().getMembers()) {
                MapleCharacter mc;
                mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
                if (mc != null) {
                    mc.changeMap(cs.getMapFactory().getMap(map.getId() + 3), cs.getMapFactory().getMap(map.getId() + 3).getPortal(0));
                    mc.setTeam(-1);
                }
            }
        }
        dispose();
    }

    public void timeUp() {
        int cp1 = redTotalCP;
        int cp2 = blueTotalCP;
        if (cp1 == cp2) {
            extendTime();
            return;
        }
        if (cp1 > cp2) {
            finish(0);
        } else {
            finish(1);
        }
    }

    public long getTimeLeft() {
        return startTime - System.currentTimeMillis();
    }

    public int getTimeLeftSeconds() {
        return (int) (getTimeLeft() / 1000);
    }

    public void extendTime() {
        map.broadcastMessage(MaplePacketCreator.serverNotice(5, "The time has been extended."));
        startTime = System.currentTimeMillis() + 3 * 1000;
        map.addClock(3 * 60);
        timer = TimerManager.getInstance().schedule(() -> timeUp(), 3 * 60 * 1000);
        effectTimer = TimerManager.getInstance().schedule(() -> complete(), 3 * 60 * 1000 - 10);
    }

    public void complete() {
        int cp1 = redTotalCP;
        int cp2 = blueTotalCP;
        if (cp1 == cp2) {
            return;
        }
        boolean redWin = cp1 > cp2;
        int chnl = leader1.getClient().getChannel();
        int chnl1 = leader2.getClient().getChannel();
        if (chnl != chnl1) {
            throw new RuntimeException("CPQ leaders are on different channels..");
        }
        ChannelServer cs = ChannelServer.getInstance(chnl);
        map.killAllMonsters(false);
        for (MaplePartyCharacter mpc : leader1.getParty().getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                if (redWin) {
                    mc.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/win"));
                    mc.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Win"));
                } else {
                    mc.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/lose"));
                    mc.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Lose"));
                }
            }
        }
        for (MaplePartyCharacter mpc : leader2.getParty().getMembers()) {
            MapleCharacter mc;
            mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
            if (mc != null) {
                if (!redWin) {
                    mc.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/win"));
                    mc.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Win"));
                } else {
                    mc.getClient().sendPacket(MaplePacketCreator.showEffect("quest/carnival/lose"));
                    mc.getClient().sendPacket(MaplePacketCreator.playSound("MobCarnival/Lose"));
                }
            }
        }
    }

    public MapleParty getRed() {
        return p1;
    }

    public void setRed(MapleParty p1) {
        this.p1 = p1;
    }

    public MapleParty getBlue() {
        return p2;
    }

    public void setBlue(MapleParty p2) {
        this.p2 = p2;
    }

    public MapleCharacter getLeader1() {
        return leader1;
    }

    public void setLeader1(MapleCharacter leader1) {
        this.leader1 = leader1;
    }

    public MapleCharacter getLeader2() {
        return leader2;
    }

    public void setLeader2(MapleCharacter leader2) {
        this.leader2 = leader2;
    }

    public MapleCharacter getEnemyLeader(int team) {
        return switch (team) {
            case 0 -> leader2;
            case 1 -> leader1;
            default -> null;
        };
    }

    public int getBlueCP() {
        return blueCP;
    }

    public void setBlueCP(int blueCP) {
        this.blueCP = blueCP;
    }

    public int getBlueTotalCP() {
        return blueTotalCP;
    }

    public void setBlueTotalCP(int blueTotalCP) {
        this.blueTotalCP = blueTotalCP;
    }

    public int getRedCP() {
        return redCP;
    }

    public void setRedCP(int redCP) {
        this.redCP = redCP;
    }

    public int getRedTotalCP() {
        return redTotalCP;
    }

    public void setRedTotalCP(int redTotalCP) {
        this.redTotalCP = redTotalCP;
    }

    public int getTotalCP(int team) {
        if (team == 0) {
            return redTotalCP;
        } else if (team == 1) {
            return blueTotalCP;
        } else {
            throw new RuntimeException("Unknown team");
        }
    }

    public void setTotalCP(int totalCP, int team) {
        if (team == 0) {
            redTotalCP = totalCP;
        } else if (team == 1) {
            blueTotalCP = totalCP;
        }
    }

    public int getCP(int team) {
        if (team == 0) {
            return redCP;
        } else if (team == 1) {
            return blueCP;
        } else {
            throw new RuntimeException("Unknown team");
        }
    }

    public void setCP(int CP, int team) {
        if (team == 0) {
            redCP = CP;
        } else if (team == 1) {
            blueCP = CP;
        }
    }
}