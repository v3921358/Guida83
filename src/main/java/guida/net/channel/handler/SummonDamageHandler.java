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
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleJob;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.client.status.MonsterStatusEffect;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleStatEffect;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleSummon;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SummonDamageHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        //System.out.println("\nPacket:\n" + slea.toString() + "\n");
        //int summonSkillId = slea.readInt();
        final int oid = slea.readInt();

        final MapleCharacter player = c.getPlayer();
        final Collection<MapleSummon> summons = player.getSummons().values();
        MapleSummon summon = null;
        for (MapleSummon sum : summons) {
            if (sum.getObjectId() == oid) {
                summon = sum;
            }
        }
        if (summon == null) {
            //log.info(MapleClient.getLogMessage(c, "Using summon attack without a summon"));
            return; // attacking with a nonexistant summon
        }
        final ISkill summonSkill = SkillFactory.getSkill(summon.getSkill());
        if (player.getSkillLevel(summonSkill) == 0) {
            return;
        }
        final MapleStatEffect summonEffect = summonSkill.getEffect(summon.getSkillLevel());
        slea.skip(5);
        final List<SummonAttackEntry> allDamage = new ArrayList<>();
        final int numAttacked = slea.readByte();
        slea.skip(8);
        player.getCheatTracker().checkSummonAttack();
        for (int x = 0; x < numAttacked; x++) {
            final int monsterOid = slea.readInt(); // attacked oid
            slea.skip(18); // who knows
            final int damage = slea.readInt();

            allDamage.add(new SummonAttackEntry(monsterOid, damage));
        }

        if (!player.isAlive()) {
            player.getCheatTracker().registerOffense(CheatingOffense.ATTACKING_WHILE_DEAD);
            return;
        }
        int maxdamage;
        if (c.getPlayer().getJob().isA(MapleJob.MAGICIAN) || c.getPlayer().getJob().isA(MapleJob.BLAZEWIZARD1)) {
            final int matk = player.getTotalMagic();
            maxdamage = (int) ((Math.pow(matk * 0.058, 2.0) + matk * 3.3 * 1.0 + player.getTotalInt() * 0.5) * summonEffect.getMatk() / 100.0);
        } else {
            maxdamage = (int) (c.getPlayer().getCurrentMaxBaseDamage() * summonEffect.getWatk() / 100.0);
        }
        maxdamage += 100;
        maxdamage = (int) (Math.min(maxdamage, 80000) * 1.65);
        player.getMap().broadcastMessage(player, MaplePacketCreator.summonAttack(player.getId(), summon.getSkill(), 4, allDamage), summon.getPosition());
        for (SummonAttackEntry attackEntry : allDamage) {
            final int damage = attackEntry.getDamage();
            final MapleMonster target = player.getMap().getMonsterByOid(attackEntry.getMonsterOid());

            if (target != null) {
                /*if (damage > maxdamage) {
					c.disconnect();
					return;
				}*/
                if (damage > 0 && !summonEffect.getMonsterStati().isEmpty()) {
                    if (summonEffect.makeChanceResult()) {
                        final MonsterStatusEffect monsterStatusEffect = new MonsterStatusEffect(summonEffect.getMonsterStati(), summonSkill, false);
                        target.applyStatus(player, monsterStatusEffect, summonEffect.isPoison(), 4000);
                    }
                }

                if (damage > maxdamage) {
                    player.getCheatTracker().registerOffense(CheatingOffense.SUMMON_HIGH_DAMAGE);
                    if (player.getCheatTracker().getOffenses().get(CheatingOffense.SUMMON_HIGH_DAMAGE).getCount() > 15) {
                        try {
                            player.getClient().getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, player.getName() + " is suspected of summon damage hacking: " + damage + " damage with " + SkillFactory.getSkillName(summonEffect.getSourceId()) + " at level " + player.getLevel() + " (Calculated: " + maxdamage + ")").getBytes());
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }
                }
                player.getMap().damageMonster(player, target, damage);
                player.checkMonsterAggro(target);
            }
        }
    }

    public static class SummonAttackEntry {

        private final int monsterOid;
        private final int damage;

        public SummonAttackEntry(int monsterOid, int damage) {
            super();
            this.monsterOid = monsterOid;
            this.damage = damage;
        }

        public int getMonsterOid() {
            return monsterOid;
        }

        public int getDamage() {
            return damage;
        }
    }
}