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
import guida.client.MapleStat;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;

/**
 * @author Jay Estrella
 */
public class MapleOxQuiz {

    private static final long DEFAULT_DELAY = 5 * 1000;
    private static final int DEFAULT_EXP_REWARD = 200;
    private int round = 1;
    private int question = 1;
    private MapleMap map = null;

    public MapleOxQuiz(MapleMap map, int round, int question) {
        this.map = map;
        this.round = round;
        this.question = question;
    }

    public void checkAnswers() {
        for (MapleCharacter chr : map.getCharacters()) {
            double x = chr.getPosition().getX();
            double y = chr.getPosition().getY();
            int answer = MapleOxQuizFactory.getOXAnswer(round, question);
            boolean correct = false;
            if (x > -234 && y > -26) { // False
                if (answer == 0) {
                    chr.dropMessage("Correct!");
                    correct = true;
                } else {
                    chr.dropMessage("Incorrect!");
                }
            } else if (x < -234 && y > -26) { // True
                if (answer == 1) {
                    chr.dropMessage("Correct!");
                    correct = true;
                } else {
                    chr.dropMessage("Incorrect!");
                }
            }
            if (correct) {
                chr.gainExp(DEFAULT_EXP_REWARD * chr.getClient().getChannelServer().getExpRate(), true, false);
            } else {
                chr.setHp(0);
                chr.updateSingleStat(MapleStat.HP, 0);
            }
        }
    }

    public void scheduleOx() {
        TimerManager.getInstance().schedule(() -> {
            map.broadcastMessage(MaplePacketCreator.serverNotice(6, MapleOxQuizFactory.getOXQuestion(round, question)));
            TimerManager.getInstance().schedule(() -> {
                checkAnswers();
                scheduleAnswer(map);
            }, 15 * 1000); // 15 Seconds to respond
        }, DEFAULT_DELAY);
    }

    public void scheduleAnswer(final MapleMap map) {
        TimerManager.getInstance().schedule(() -> {
            map.broadcastMessage(MaplePacketCreator.serverNotice(6, MapleOxQuizFactory.getOXExplain(round, question)));
            if (map.getOx() != null) { // Set next one if Ox Quiz is still active.
                scheduleOx();
            } else {
                map.broadcastMessage(MaplePacketCreator.serverNotice(6, "Ox Quiz Deactivated"));
            }
        }, 1000);
        doQuestion(); // After we give the response, next question
    }

    public void doQuestion() {
        if (round == 1 && question == 29) { // Weird case, it jumps to 100
            question = 100; // Set it to 100, even if the inc is higher.
        } else if (round == 2 && question == 17) {
            question = 100;
        } else if (round == 3 && question == 17) {
            question = 100;
        } else if (round == 4 && question == 12) {
            question = 100;
        } else if (round == 5 && question == 26) {
            question = 100;
        } else if (round == 6 && question == 16) {
            question = 100;
        } else if (round == 7 && question == 16) {
            question = 100;
        } else if (round == 8 && question == 12) {
            question = 100;
        } else if (round == 9 && question == 44) {
            question = 100;
        } else {
            question++;
        }
    }

    public int getRound() {
        return round;
    }

    public int getQuestion() {
        return question;
    }

    public MapleMap getMap() {
        return map;
    }
}