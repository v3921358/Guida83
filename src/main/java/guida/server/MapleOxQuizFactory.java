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

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;

import java.util.HashMap;
import java.util.Map;

public class MapleOxQuizFactory {

    private static final MapleDataProvider stringData = MapleDataProviderFactory.getDataProvider("Etc");
    private static final Map<Pair<Integer, Integer>, String[]> questions = new HashMap<>(); // <<img dir, id> / [Question, answer, explanation]>>

    public static String getOXQuestion(int imgdir, int id) {
        String ret = questions.get(new Pair<>(imgdir, id))[0];
        if (ret == null) {
            synchronized (questions) {
                MapleData itemsData = stringData.getData("OXQuiz.img").getChild(String.valueOf(imgdir));
                MapleData itemFolder = itemsData.getChild(String.valueOf(id));
                String itemName = DataUtil.toString(itemFolder.resolve("q"), "NO-NAME");
                questions.put(new Pair<>(imgdir, id), new String[] {itemName, String.valueOf(DataUtil.toInt(itemFolder.getChild("a"))), DataUtil.toString(itemFolder.resolve("d"), "NO-NAME")});
                ret = id + " " + itemName; // No idea if it's like that or not.
            }
        }
        return ret;
    }

    public static int getOXAnswer(int imgdir, int id) {
        return Integer.parseInt(questions.get(new Pair<>(imgdir, id))[1]);
    }

    public static String getOXExplain(int imgdir, int id) {
        return questions.get(new Pair<>(imgdir, id))[2];
    }
}