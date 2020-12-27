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

package guida.net.channel.handler;

import guida.client.ISkill;
import guida.client.MapleClient;
import guida.client.MapleJob;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.List;

public class DistributeAPHandler extends AbstractMaplePacketHandler {

    private static int rand(int lbound, int ubound) {
        return (int) (Math.random() * (ubound - lbound + 1) + lbound);
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(2);
        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        final int action = slea.readInt();
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.setLastAction(action);
        final int update = slea.readInt();
        if (c.getPlayer().getRemainingAp() > 0) {
            final MapleJob job = c.getPlayer().getJob();
            switch (update) {
                case 64: // Str
                    if (c.getPlayer().getStr() >= 999) {
                        return;
                    }
                    c.getPlayer().setStr(c.getPlayer().getStr() + 1);
                    statupdate.add(new Pair<>(MapleStat.STR, c.getPlayer().getStr()));
                    break;
                case 128: // Dex
                    if (c.getPlayer().getDex() >= 999) {
                        return;
                    }
                    c.getPlayer().setDex(c.getPlayer().getDex() + 1);
                    statupdate.add(new Pair<>(MapleStat.DEX, c.getPlayer().getDex()));
                    break;
                case 256: // Int
                    if (c.getPlayer().getInt() >= 999) {
                        return;
                    }
                    c.getPlayer().setInt(c.getPlayer().getInt() + 1);
                    statupdate.add(new Pair<>(MapleStat.INT, c.getPlayer().getInt()));
                    break;
                case 512: // Luk
                    if (c.getPlayer().getLuk() >= 999) {
                        return;
                    }
                    c.getPlayer().setLuk(c.getPlayer().getLuk() + 1);
                    statupdate.add(new Pair<>(MapleStat.LUK, c.getPlayer().getLuk()));
                    break;
                case 2048: // HP
                    int MaxHP = c.getPlayer().getMaxHp();
                    if (c.getPlayer().getHpApUsed() == 10000 || MaxHP == 30000) {
                        return;
                    }
                    ISkill improvingMaxHP;
                    int improvingMaxHPLevel;
                    if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                        MaxHP += rand(8, 12);
                    } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                        MaxHP += rand(20, 24);
                        if (job.isA(MapleJob.WARRIOR)) {
                            improvingMaxHP = SkillFactory.getSkill(1000001);
                        } else {
                            improvingMaxHP = SkillFactory.getSkill(11000000);
                        }
                        improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        if (improvingMaxHPLevel >= 1) {
                            MaxHP += improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                        }
                    } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                        MaxHP += rand(6, 10);
                    } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                        MaxHP += rand(16, 20);
                    } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                        MaxHP += rand(18, 22);
                        if (job.isA(MapleJob.PIRATE)) {
                            improvingMaxHP = SkillFactory.getSkill(5100000);
                        } else {
                            improvingMaxHP = SkillFactory.getSkill(15100000);
                        }
                        improvingMaxHPLevel = c.getPlayer().getSkillLevel(improvingMaxHP);
                        if (improvingMaxHPLevel >= 1) {
                            MaxHP += improvingMaxHP.getEffect(improvingMaxHPLevel).getY();
                        }
                    }
                    MaxHP = Math.min(30000, MaxHP);
                    c.getPlayer().setHpApUsed(c.getPlayer().getHpApUsed() + 1);
                    c.getPlayer().setMaxHp(MaxHP);
                    statupdate.add(new Pair<>(MapleStat.MAXHP, MaxHP));
                    break;
                case 8192: // MP
                    int MaxMP = c.getPlayer().getMaxMp();
                    ISkill improvingMaxMP;
                    if (c.getPlayer().getMpApUsed() == 10000 || c.getPlayer().getMaxMp() == 30000) {
                        return;
                    }
                    if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE) {
                        MaxMP += rand(6, 8);
                    } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
                        MaxMP += rand(2, 4);
                    } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
                        MaxMP += rand(18, 20);
                        if (job.isA(MapleJob.MAGICIAN)) {
                            improvingMaxMP = SkillFactory.getSkill(2000001);
                        } else {
                            improvingMaxMP = SkillFactory.getSkill(12000000);
                        }
                        final int improvingMaxMPLevel = c.getPlayer().getSkillLevel(improvingMaxMP);
                        if (improvingMaxMPLevel >= 1) {
                            MaxMP += improvingMaxMP.getEffect(improvingMaxMPLevel).getY();
                        }
                    } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) {
                        MaxMP += rand(10, 12);
                    } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
                        MaxMP += rand(14, 16);
                    }
                    MaxMP += c.getPlayer().getTotalInt() * 0.075;
                    MaxMP = Math.min(30000, MaxMP);
                    c.getPlayer().setMpApUsed(c.getPlayer().getMpApUsed() + 1);
                    c.getPlayer().setMaxMp(MaxMP);
                    statupdate.add(new Pair<>(MapleStat.MAXMP, MaxMP));
                    break;
                default:
                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                    return;
            }
            c.getPlayer().setRemainingAp(c.getPlayer().getRemainingAp() - 1);
            statupdate.add(new Pair<>(MapleStat.AVAILABLEAP, c.getPlayer().getRemainingAp()));
            c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        }
    }
}