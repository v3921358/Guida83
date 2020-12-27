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

package guida.client;

import java.lang.ref.WeakReference;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Xterminator
 */
public class SkillCooldown {

    private int skillId;
    private long startTime;
    private long length;
    private ScheduledFuture<?> timer;

    public SkillCooldown(int skillId, long startTime, long length, ScheduledFuture<?> timer) {
        this.skillId = skillId;
        this.startTime = startTime;
        this.length = length;
        this.timer = timer;
    }

    public void setSkillId(int skillId) {
        this.skillId = skillId;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public void setTime(ScheduledFuture<?> timer) {
        this.timer = timer;
    }

    public int getSkillId() {
        return skillId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getLength() {
        return length;
    }

    public ScheduledFuture<?> getTime() {
        return timer;
    }

    public static class CancelCooldownAction implements Runnable {

        private final int skillId;
        private final WeakReference<MapleCharacter> target;

        public CancelCooldownAction(MapleCharacter target, int skillId) {
            this.target = new WeakReference<>(target);
            this.skillId = skillId;
        }

        @Override
        public void run() {
            final MapleCharacter realTarget = target.get();
            if (realTarget != null) {
                realTarget.removeCooldown(skillId);
            }
        }
    }
}