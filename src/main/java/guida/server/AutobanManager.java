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

import guida.client.MapleCharacterUtil;
import guida.client.MapleClient;
import guida.tools.MaplePacketCreator;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Matze
 */
public class AutobanManager implements Runnable {

    private static final int AUTOBAN_POINTS = 1000;
    private static volatile AutobanManager instance = null;
    private final Map<Integer, Integer> points = new HashMap<>();
    private final Map<Integer, List<String>> reasons = new HashMap<>();
    private final Set<ExpirationEntry> expirations = new TreeSet<>();

    public static AutobanManager getInstance() {
        if (instance == null) {
            instance = new AutobanManager();
        }
        return instance;
    }

    public void autoban(MapleClient c, String reason) {
        if (c.getPlayer().isGM()) {
            return;
        }
        addPoints(c, AUTOBAN_POINTS, 0, reason);
    }

    public void broadcastMessage(MapleClient c, String s) {
        try {
            c.getChannelServer().getWorldInterface().broadcastMessage(null, MaplePacketCreator.serverNotice(0, s).getBytes());
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }
    }

    public synchronized void addPoints(MapleClient c, int points, long expiration, String reason) {
        if (c.getPlayer().isGM()) {
            return;
        }
        int acc = c.getPlayer().getAccountID();
        List<String> reasonList;
        if (this.points.containsKey(acc)) {
            if (this.points.get(acc) >= AUTOBAN_POINTS) {
                return;
            }
            this.points.put(acc, this.points.get(acc) + points);
            reasonList = reasons.get(acc);
            reasonList.add(reason);
        } else {
            this.points.put(acc, points);
            reasonList = new LinkedList<>();
            reasonList.add(reason);
            reasons.put(acc, reasonList);
        }
        if (this.points.get(acc) >= AUTOBAN_POINTS) {
            final String name = c.getPlayer().getName();
            /*StringBuilder banReason = new StringBuilder("Autoban for Character ");
			banReason.append(name);
			banReason.append(" (IP ");
			banReason.append(c.getSession().getRemoteAddress().toString());
			banReason.append("): ");
			for (String s : reasons.get(acc)) {
				banReason.append(s);
				banReason.append(", ");
			}*/

            if (!c.getPlayer().isGM()) {
                try {
                    c.getChannelServer().getWorldInterface().broadcastGMMessage(null, MaplePacketCreator.serverNotice(6, "[Warning] " + MapleCharacterUtil.makeMapleReadable(name) + " is suspected of hacking. (Last reason: " + reason + ")").getBytes());
                } catch (RemoteException e) {
                    c.getChannelServer().reconnectWorld();
                }
            }
            return;
        }
        if (expiration > 0) {
            expirations.add(new ExpirationEntry(System.currentTimeMillis() + expiration, acc, points));
        }
    }

    public void run() {
        long now = System.currentTimeMillis();
        Set<ExpirationEntry> expirationsCopy = new TreeSet<>(expirations);
        for (ExpirationEntry e : expirationsCopy) {
            if (e.time <= now) {
                points.put(e.acc, points.get(e.acc) - e.points);
            } else {
                return;
            }
        }
    }

    private static class ExpirationEntry implements Comparable<ExpirationEntry> {

        public final long time;
        public final int acc;
        public final int points;

        public ExpirationEntry(long time, int acc, int points) {
            this.time = time;
            this.acc = acc;
            this.points = points;
        }

        public int compareTo(AutobanManager.ExpirationEntry o) {
            return (int) (time - o.time);
        }
    }
}