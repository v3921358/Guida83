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

package guida.client.messages.commands;

import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleMonsterStats;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import static guida.client.messages.CommandProcessor.getNamedDoubleArg;
import static guida.client.messages.CommandProcessor.getNamedIntArg;
import static guida.client.messages.CommandProcessor.getOptionalIntArg;

public class MonsterSpawningCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        switch (splitted[0]) {
            case "!spawn":
                if (splitted.length > 1) {
                    if (!StringUtil.isValidIntegerString(splitted[1]) || MapleLifeFactory.getMonster(Integer.parseInt(splitted[1])) == null) {
                        mc.dropMessage("Please enter a valid Monster ID.");
                        return;
                    }
                    int mid = Integer.parseInt(splitted[1]);
                    int num = Math.min(getOptionalIntArg(splitted, 2, 1), 500);

                    Integer hp = getNamedIntArg(splitted, 1, "hp");
                    Integer exp = getNamedIntArg(splitted, 1, "exp");
                    Integer removeAfter = getNamedIntArg(splitted, 1, "removeafter");
                    Double php = getNamedDoubleArg(splitted, 1, "php");
                    Double pexp = getNamedDoubleArg(splitted, 1, "pexp");
                    // !spawn 100100 hplock
                    boolean hpLock = false;
                    boolean moveLock = false;
                    for (String s : splitted) {
                        if (s.equalsIgnoreCase("hpLock")) {
                            hpLock = true;
                        }
                        if (s.equalsIgnoreCase("moveLock")) {
                            moveLock = true;
                        }
                    }
                    MapleMonster onemob = MapleLifeFactory.getMonster(mid);
                    int newhp = 0;
                    int newexp = 0;
                    double oldExpRatio = (double) onemob.getHp() / onemob.getExp();

                    if (hp != null) {
                        newhp = hp;
                    } else if (php != null) {
                        newhp = (int) (onemob.getMaxHp() * php / 100);
                    } else {
                        newhp = onemob.getMaxHp();
                    }
                    if (exp != null) {
                        newexp = exp;
                    } else if (pexp != null) {
                        newexp = (int) (onemob.getExp() * pexp / 100);
                    } else {
                        newexp = onemob.getExp();
                    }
                    if (removeAfter == null) {
                        removeAfter = onemob.getRemoveAfter();
                    }
                    if (newhp < 1) {
                        newhp = 1;
                    }
                    double newExpRatio = (double) newhp / newexp;
                    if (newExpRatio < oldExpRatio && newexp > 0) {
                        mc.dropMessage("You cannot spawn this monster! The new hp/exp ratio is better than the old one. (" + newExpRatio + " < " + oldExpRatio + ")");
                        return;
                    }

                    MapleMonsterStats overrideStats = new MapleMonsterStats();
                    overrideStats.setHp(newhp);
                    overrideStats.setExp(newexp);
                    overrideStats.setMp(onemob.getMaxMp());
                    overrideStats.setRemoveAfter(removeAfter);
                    for (int i = 0; i < num; i++) {
                        MapleMonster mob = MapleLifeFactory.getMonster(mid);
                        mob.setHp(newhp);
                        mob.setOverrideStats(overrideStats);
                        mob.setHpLock(hpLock);
                        mob.setMoveLocked(moveLock);
                        c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob, c.getPlayer().getPosition());
                    }
                } else {
                    mc.dropMessage("Please enter the Monster ID of the monster you want to spawn.");
                }
                break;
            case "!papulatus": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(8500001);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!nxslimes":
                for (int i = 0; i <= 10; i++) {
                    MapleMonster mob = MapleLifeFactory.getMonster(9400202);
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob, c.getPlayer().getPosition());
                }
                break;
            case "!jrbalrog": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(8130100);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!balrog": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(8150000);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!bossfamily":
                for (int i = 9400100; i <= 9400103; i++) {
                    MapleMonster mob0 = MapleLifeFactory.getMonster(i);
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                }
                for (int i = 9400110; i <= 9400113; i++) {
                    MapleMonster mob2 = MapleLifeFactory.getMonster(i);
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob2, c.getPlayer().getPosition());
                }
                for (int i = 9400121; i <= 9400122; i++) {
                    MapleMonster mob2 = MapleLifeFactory.getMonster(i);
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob2, c.getPlayer().getPosition());
                }
                MapleMonster mob3 = MapleLifeFactory.getMonster(9400300);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob3, c.getPlayer().getPosition());
                break;
            case "!mushmom": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(6130101);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!zombiemushmom": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(6300005);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!bluemushmom": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(9400205);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!theboss": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(9400300);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!shark": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(8150100);
                MapleMonster mob1 = MapleLifeFactory.getMonster(8150101);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob1, c.getPlayer().getPosition());
                break;
            }
            case "!pianus": {
                MapleMonster mob0 = MapleLifeFactory.getMonster(8510000);
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob0, c.getPlayer().getPosition());
                break;
            }
            case "!zakum":
                c.getPlayer().getMap().spawnFakeMonsterOnGroundBelow(MapleLifeFactory.getMonster(8800000), c.getPlayer().getPosition());
                for (int i = 8800003; i <= 8800010; i++) {
                    c.getPlayer().getMap().spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(i), c.getPlayer().getPosition());
                }
                break;
            case "!horntail":
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.musicChange("Bgm14/HonTale"));
                c.getPlayer().getMap().spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(8810026), c.getPlayer().getPosition());
                break;
            case "!hplock": {
                int oid = Integer.parseInt(splitted[1]);
                MapleMonster mmo = c.getPlayer().getMap().getMonsterByOid(oid);
                if (mmo != null) {
                    mmo.setHpLock(!mmo.isHpLocked());
                    mc.dropMessage("Monster with oID " + oid + " is " + (mmo.isHpLocked() ? "" : "no longer ") + "HP Locked.");
                }
                break;
            }
            case "!unfreezeoid": {
                int oid = Integer.parseInt(splitted[1]);
                MapleMonster mmo = c.getPlayer().getMap().getMonsterByOid(oid);
                if (mmo != null) {
                    if (mmo.isMoveLocked()) {
                        mmo.setMoveLocked(false);
                        c.getPlayer().getMap().updateMonsterController(mmo);
                    }
                    mc.dropMessage("Monster with oID " + oid + " is " + (mmo.isMoveLocked() ? "" : "no longer ") + "Move Locked.");
                }
                break;
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("spawn", "", "Spawns the monster with the given id", 4),
                new CommandDefinition("papulatus", "", "", 4),
                new CommandDefinition("nxslimes", "", "", 4),
                new CommandDefinition("jrbalrog", "", "", 4),
                new CommandDefinition("balrog", "", "", 4),
                new CommandDefinition("bossfamily", "", "", 4),
                new CommandDefinition("mushmom", "", "", 4),
                new CommandDefinition("zombiemushmom", "", "", 4),
                new CommandDefinition("bluemushmom", "", "", 4),
                new CommandDefinition("theboss", "", "", 4),
                new CommandDefinition("shark", "", "", 4),
                new CommandDefinition("pianus", "", "", 4),
                new CommandDefinition("zakum", "", "", 4),
                new CommandDefinition("horntail", "", "", 4),
                new CommandDefinition("hplock", "", "", 4),
                new CommandDefinition("unfreezeoid", "", "", 4)
        };
    }
}