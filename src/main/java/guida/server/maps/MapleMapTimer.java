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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.SkillFactory;
import guida.client.messages.CommandProcessor;
import guida.client.messages.MessageCallback;
import guida.client.messages.StringMessageCallback;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.server.TimerManager;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;

import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;

public class MapleMapTimer {

    private final int durationmin;
    private final int durationmax;
    private final String[] commands;
    private final boolean repeat;
    private final boolean shown;
    private final MapleCharacter fakechar;
    private final MapleMap map;
    private final int chanid;
    private Calendar startTime;
    private Calendar predictedStopTime;
    private ScheduledFuture<?> sf0F;

    public MapleMapTimer(int nDurationmin, int nDurationmax, String[] newCommands, boolean newRepeat, boolean newShown, MapleCharacter faek, MapleMap nmap, int nchanid) {
        durationmin = nDurationmin;
        durationmax = nDurationmax;

        commands = newCommands;
        repeat = newRepeat;
        map = nmap;
        chanid = nchanid;
        shown = newShown;
        fakechar = faek;
        init();
    }

    public MaplePacket makeSpawnData() {
        int timeLeft;
        long StopTimeStamp = predictedStopTime.getTimeInMillis();
        long CurrentTimeStamp = Calendar.getInstance().getTimeInMillis();
        timeLeft = (int) (StopTimeStamp - CurrentTimeStamp) / 1000;
        return MaplePacketCreator.getClock(timeLeft);
    }

    public void sendSpawnData(MapleClient c) {
        if (!shown) {
            return;
        }
        c.sendPacket(makeSpawnData());
    }

    public ScheduledFuture<?> getSF0F() {
        return sf0F;
    }

    public Calendar getStartTime() {
        return startTime;
    }

    public String[] getCommands() {
        return commands;
    }

    public boolean getShown() {
        return shown;
    }

    public boolean getRepeat() {
        return repeat;
    }

    private void init() {
        int duration = Randomizer.nextInt(durationmin, durationmax);
        sf0F = TimerManager.getInstance().schedule(new Activator(), duration * 1000);
        startTime = Calendar.getInstance();
        predictedStopTime = Calendar.getInstance();
        predictedStopTime.add(Calendar.SECOND, duration);
        if (shown) {
            map.broadcastMessage(makeSpawnData());
        }

    }

    public int getTimeLeft() {
        int timeLeft;
        long StopTimeStamp = predictedStopTime.getTimeInMillis();
        long CurrentTimeStamp = Calendar.getInstance().getTimeInMillis();
        timeLeft = (int) (StopTimeStamp - CurrentTimeStamp) / 1000;
        return timeLeft;
    }

    private class Activator implements Runnable {

        @Override
        public void run() {
            if (commands.length != 0) {
                ChannelServer cserv = ChannelServer.getInstance(chanid);
                if (map != null) {
                    fakechar.setMap(map);
                    if (!fakechar.isHidden()) {
                        fakechar.setHidden(true);
                        SkillFactory.getSkill(9101004).getEffect(1).applyTo(fakechar);
                    }
                    map.addPlayer(fakechar);
                }
                cserv.addPlayer(fakechar);
                MessageCallback mc = new StringMessageCallback();
                try {
                    for (String command : commands) {
                        CommandProcessor.getInstance().processCommandInternal(fakechar.getClient(), mc, fakechar.getGMLevel(), command);
                    }
                } finally {
                    if (map != null) {
                        map.removePlayer(fakechar);
                    }
                    cserv.removePlayer(fakechar);
                }
            }
            if (repeat) {
                init();
            } else if (shown) {
                map.clearShownMapTimer();
            } else {
                map.clearHiddenMapTimer(MapleMapTimer.this);
            }
        }
    }
}