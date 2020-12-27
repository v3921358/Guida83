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

public enum MapleJob {

    BEGINNER(0),
    WARRIOR(100),
    FIGHTER(110),
    CRUSADER(111),
    HERO(112),
    PAGE(120),
    WHITEKNIGHT(121),
    PALADIN(122),
    SPEARMAN(130),
    DRAGONKNIGHT(131),
    DARKKNIGHT(132),
    MAGICIAN(200),
    FP_WIZARD(210),
    FP_MAGE(211),
    FP_ARCHMAGE(212),
    IL_WIZARD(220),
    IL_MAGE(221),
    IL_ARCHMAGE(222),
    CLERIC(230),
    PRIEST(231),
    BISHOP(232),
    BOWMAN(300),
    HUNTER(310),
    RANGER(311),
    BOWMASTER(312),
    CROSSBOWMAN(320),
    SNIPER(321),
    CROSSBOWMASTER(322),
    THIEF(400),
    ASSASSIN(410),
    HERMIT(411),
    NIGHTLORD(412),
    BANDIT(420),
    CHIEFBANDIT(421),
    SHADOWER(422),
    PIRATE(500),
    BRAWLER(510),
    MARAUDER(511),
    BUCCANEER(512),
    GUNSLINGER(520),
    OUTLAW(521),
    CORSAIR(522),
    //MAPLELEAF_BRIGADIER(800),
    GM(900),
    SUPERGM(910),
    NOBLESSE(1000),
    DAWNWARRIOR1(1100),
    DAWNWARRIOR2(1110),
    DAWNWARRIOR3(1111),
    BLAZEWIZARD1(1200),
    BLAZEWIZARD2(1210),
    BLAZEWIZARD3(1211),
    WINDARCHER1(1300),
    WINDARCHER2(1310),
    WINDARCHER3(1311),
    NIGHTWALKER1(1400),
    NIGHTWALKER2(1410),
    NIGHTWALKER3(1411),
    THUNDERBREAKER1(1500),
    THUNDERBREAKER2(1510),
    THUNDERBREAKER3(1511),
    LEGEND(2000),
    //EVAN(2001), // all Evans d/c at char select
    ARAN(2100),
    ARAN1(2110),
    ARAN2(2111),
    ARAN3(2112)/*,
    EVAN1(2200),
	EVAN2(2210),
	EVAN3(2211),
	EVAN4(2212),
	EVAN5(2213),
	EVAN6(2214),
	EVAN7(2215),
	EVAN8(2216),
	EVAN9(2217),
	EVAN10(2218),
	UNKNOWN(9000)*/;

    final int jobid;

    MapleJob(int id) {
        jobid = id;
    }

    public static MapleJob getById(int id) {
        for (MapleJob l : MapleJob.values()) {
            if (l.jobid == id) {
                return l;
            }
        }
        return null;
    }

    public static int getIdentifier(MapleJob job) {
        return switch (getBaseJob(job)) {
            case BEGINNER -> 1;
            case WARRIOR, DAWNWARRIOR1 -> 2;
            case MAGICIAN, BLAZEWIZARD1 -> 4;
            case BOWMAN, WINDARCHER1 -> 8;
            case THIEF, NIGHTWALKER1 -> 16;
            case PIRATE, THUNDERBREAKER1 -> 32;
            case NOBLESSE -> 1024;
            case ARAN -> 2048;
            default -> 0;
        };
    }

    public static MapleJob getBaseJob(MapleJob basejob) {
        if (basejob.jobid % 100 == 0) {
            return basejob;
        }
        return getById(basejob.jobid / 100 * 100);
    }

    public int getId() {
        return jobid;
    }

    public boolean isA(MapleJob basejob) {
        if (basejob.jobid == 1000) {
            return jobid >= basejob.jobid && jobid / 1000 == basejob.jobid / 1000;
        }
        return jobid >= basejob.jobid && jobid / 100 == basejob.jobid / 100;
    }

    public String getJobNameAsString() {
        MapleJob job = this;
        if (job == BEGINNER) {
            return "Beginner";
        } else if (job == THIEF) {
            return "Thief";
        } else if (job == WARRIOR) {
            return "Warrior";
        } else if (job == MAGICIAN) {
            return "Magician";
        } else if (job == BOWMAN) {
            return "Bowman";
        } else if (job == PIRATE) {
            return "Pirate";
        } else if (job == BANDIT) {
            return "Bandit";
        } else if (job == ASSASSIN) {
            return "Assasin";
        } else if (job == SPEARMAN) {
            return "Spearman";
        } else if (job == PAGE) {
            return "Page";
        } else if (job == FIGHTER) {
            return "Fighter";
        } else if (job == CLERIC) {
            return "Cleric";
        } else if (job == IL_WIZARD) {
            return "Ice/Lightning Wizard";
        } else if (job == FP_WIZARD) {
            return "Fire/Poison Wizard";
        } else if (job == HUNTER) {
            return "Hunter";
        } else if (job == CROSSBOWMAN) {
            return "Crossbow Man";
        } else if (job == GUNSLINGER) {
            return "Gunslinger";
        } else if (job == BRAWLER) {
            return "Brawler";
        } else if (job == CHIEFBANDIT) {
            return "Chief Bandit";
        } else if (job == HERMIT) {
            return "Hermit";
        } else if (job == DRAGONKNIGHT) {
            return "Dragon Knight";
        } else if (job == WHITEKNIGHT) {
            return "White Night";
        } else if (job == CRUSADER) {
            return "Crusader";
        } else if (job == PALADIN) {
            return "Paladin";
        } else if (job == PRIEST) {
            return "Priest";
        } else if (job == IL_MAGE) {
            return "Ice/Lightning Mage";
        } else if (job == FP_MAGE) {
            return "Fire/Poison Mage";
        } else if (job == RANGER) {
            return "Ranger";
        } else if (job == SNIPER) {
            return "Sniper";
        } else if (job == MARAUDER) {
            return "Marauder";
        } else if (job == OUTLAW) {
            return "Outlaw";
        } else if (job == SHADOWER) {
            return "Shadower";
        } else if (job == NIGHTLORD) {
            return "Night Lord";
        } else if (job == DARKKNIGHT) {
            return "Dark Knight";
        } else if (job == HERO) {
            return "Hero";
        } else if (job == PALADIN) {
            return "Paladin";
        } else if (job == IL_ARCHMAGE) {
            return "Ice/Lightning Arch Mage";
        } else if (job == FP_ARCHMAGE) {
            return "Fire/Poison Arch Mage";
        } else if (job == BOWMASTER) {
            return "Bow Master";
        } else if (job == CROSSBOWMASTER) {
            return "Crossbow Master";
        } else if (job == BUCCANEER) {
            return "Buccaneer";
        } else if (job == CORSAIR) {
            return "Corsair";
        } else {
            return "GameMaster";
        }
    }
}