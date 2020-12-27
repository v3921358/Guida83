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
import guida.tools.MaplePacketCreator;

import java.rmi.RemoteException;

/**
 * @author Patrick/PurpleMadness
 */
public class MapleAchievement {

    private final boolean notice;
    private String name;
    private int reward;
    private boolean repeatable = false;

    public MapleAchievement(String name, int reward) {
        this.name = name;
        this.reward = reward;
        notice = true;
    }

    public MapleAchievement(String name, int reward, boolean notice) {
        this.name = name;
        this.reward = reward;
        this.notice = notice;
    }

    public MapleAchievement(String name, int reward, boolean notice, boolean repeatable) {
        this.name = name;
        this.reward = reward;
        this.notice = notice;
        this.repeatable = repeatable;
    }

    public String getName() {
        return name;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getReward() {
        return reward;
    }

    public void setReward(int reward) {
        this.reward = reward;
    }

    public void finishAchievement(MapleCharacter player) {
        if (player.hasGMLevel(2)) {
            return;
        }
        final int achievementId = MapleAchievements.getInstance().getByMapleAchievement(this);
        try {
            String personalName = name.replace("#pp", player.getPossessivePronoun());
            player.modifyCSPoints(4, reward);
            player.setAchievementFinished(achievementId);
            player.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "[Achievement] You've gained " + reward + " NX as you " + personalName + "."));
            if (notice && !player.isBanned()) {
                String achievement = "[Achievement] Congratulations to " + player.getName() + " as " + player.getPronoun() + " just " + personalName + (personalName.contains("reached level") ? "" : " at level " + player.getLevel()) + "!";
                if (achievementId == 22) {
                    achievement = "[Congrats] " + player.getName() + " has reached Level 200! Congratulate " + player.getName() + " on such an amazing achievement!";
                }
                player.getClient().getChannelServer().getWorldInterface().broadcastMessage(player.getName(), MaplePacketCreator.serverNotice(6, achievement).getBytes());
            }
        } catch (RemoteException e) {
            player.getClient().getChannelServer().reconnectWorld();
        }
    }
}