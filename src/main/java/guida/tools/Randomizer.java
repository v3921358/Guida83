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

package guida.tools;

import java.util.Random;

public class Randomizer {

    private static final Random rand = new Random();

    public static int nextInt() {
        return rand.nextInt();
    }

    public static int nextInt(int arg0) {
        return rand.nextInt(arg0);
    }

    public static void nextBytes(byte[] bytes) {
        rand.nextBytes(bytes);
    }

    public static byte[] nextBytes(int length) {
        byte[] ret = new byte[length];
        rand.nextBytes(ret);
        return ret;
    }

    public static boolean nextBoolean() {
        return rand.nextBoolean();
    }

    public static double nextDouble() {
        return rand.nextDouble();
    }

    public static float nextFloat() {
        return rand.nextFloat();
    }

    public static long nextLong() {
        return rand.nextLong();
    }

    public static int nextInt(int lbound, int ubound) {
        return (int) (rand.nextDouble() * (ubound - lbound + 1) + lbound);
    }
}