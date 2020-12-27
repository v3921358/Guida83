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

import guida.client.MapleClient;
import guida.net.MaplePacket;
import guida.scripting.reactor.ReactorScriptManager;
import guida.server.TimerManager;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;

import java.awt.Rectangle;

/**
 * @author Lerk
 */
public class MapleReactor extends AbstractMapleMapObject {
    // private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleReactor.class);

    private final int rid;
    private final MapleReactorStats stats;
    private byte state;
    private int delay;
    private MapleMap map;
    private boolean alive;
    private String name;
    private boolean timerActive;
    private MapleMapItem reactingWith;

    public MapleReactor(MapleReactorStats stats, int rid) {
        this.stats = stats;
        this.rid = rid;
        alive = true;
    }

    public void setTimerActive(boolean active) {
        timerActive = active;
    }

    public boolean isTimerActive() {
        return timerActive;
    }

    public int getReactorId() {
        return rid;
    }

    public void setState(byte state) {
        this.state = state;
    }

    public byte getState() {
        return state;
    }

    public int getId() {
        return rid;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public MapleMapItem getReactingWith() {
        return reactingWith;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.REACTOR;
    }

    public int getReactorType() {
        return stats.getType(state);
    }

    public void setMap(MapleMap map) {
        this.map = map;
    }

    public MapleMap getMap() {
        return map;
    }

    public Pair<Integer, Integer> getReactItem() {
        return stats.getReactItem(state);
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public void setReactingWith(MapleMapItem reactingWith) {
        this.reactingWith = reactingWith;
    }

    public boolean isBroken() {
        return state >= stats.getNoStates();
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(makeDestroyData());
    }

    public MaplePacket makeDestroyData() {
        return MaplePacketCreator.destroyReactor(this);
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        client.sendPacket(makeSpawnData());
    }

    public MaplePacket makeSpawnData() {
        return MaplePacketCreator.spawnReactor(this);
    }

    public void delayedHitReactor(final MapleClient c, long delay) {
        TimerManager.getInstance().schedule(() -> hitReactor(c), delay);
    }

    //hitReactor command for item-triggered reactors
    public void hitReactor(MapleClient c) {
        hitReactor(0, (short) 0, c);
    }

    public void hitReactor(int charPos, short stance, MapleClient c) {
        if (isBroken()) {
            return;
        }
        if (stats.getType(state) < 999 && stats.getType(state) != -1) {
            //type 2 = only hit from right (kerning swamp plants), 00 is air left 02 is ground left
            if (!(stats.getType(state) == 2 && (charPos == 0 || charPos == 2))) {
                //get next state
                state = stats.getNextState(state);

                if (stats.getNextState(state) == -1) {//end of reactor
                    if (stats.getType(state) < 100) { //reactor broken
                        if (delay > 0) {
                            map.destroyReactor(getObjectId());
                        } else {//trigger as normal
                            map.broadcastMessage(MaplePacketCreator.triggerReactor(this, stance));
                        }
                    } else { //item-triggered on final step
                        map.broadcastMessage(MaplePacketCreator.triggerReactor(this, stance));
                    }
                    ReactorScriptManager.getInstance().act(c, this);
                } else { //reactor not broken yet
                    map.broadcastMessage(c.getPlayer(), MaplePacketCreator.triggerReactor(this, stance), true);
                    if (state == stats.getNextState(state)) { //current state = next state, looping reactor
                        ReactorScriptManager.getInstance().act(c, this);
                    }
                }
            }
        } else {
            state++;
            map.broadcastMessage(MaplePacketCreator.triggerReactor(this, stance));
            ReactorScriptManager.getInstance().act(c, this);
        }
    }

    public Rectangle getArea() {
        int height = stats.getBR().y - stats.getTL().y;
        int width = stats.getBR().x - stats.getTL().x;
        int origX = getPosition().x + stats.getTL().x;
        int origY = getPosition().y + stats.getTL().y;

        return new Rectangle(origX, origY, width, height);

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "Reactor " + getObjectId() + " of id " + rid + " at position " + getPosition().toString() + " state" + state + " type " + stats.getType(state);
    }
}