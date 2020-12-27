/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 *
 * Guida is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * Guida is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Guida.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.provider;

import java.awt.Point;

public class DataUtil {

    private DataUtil() {
    }

    public static Point toPoint(MapleData data, Point def) {
        if (data == null) {
            return def;
        }
        try {
            return toPoint(data);
        } catch (Exception e) {
            return def;
        }
    }

    public static Point toPoint(MapleData data) {
        return (Point) data.getData();
    }

    public static int toInt(MapleData data, int def) {
        if (data == null) {
            return def;
        }
        try {
            return toInt(data);
        } catch (Exception e) {
            return def;
        }
    }

    public static int toInt(MapleData data) {
        Object ret = data.getData();
        if (ret instanceof Long) {
            return ((Long) ret).intValue();
        } else if (ret instanceof String) {
            return Integer.parseInt((String) ret);
        } else {
            return (Integer) ret;
        }
    }

    public static float toFloat(MapleData data, float def) {
        if (data == null) {
            return def;
        }
        try {
            return toFloat(data);
        } catch (Exception e) {
            return def;
        }
    }

    public static float toFloat(MapleData data) {
        Object ret = data.getData();
        if (ret instanceof Double) {
            return ((Double) ret).floatValue();
        } else {
            return (Float) ret;
        }
    }

    public static double toDouble(MapleData data, double def) {
        if (data == null) {
            return def;
        }
        try {
            return toDouble(data);
        } catch (Exception e) {
            return def;
        }
    }

    public static double toDouble(MapleData data) {
        return (Double) data.getData();
    }

    public static String toString(MapleData data, String def) {
        if (data == null) {
            return def;
        }
        try {
            return toString(data);
        } catch (Exception e) {
            return def;
        }
    }

    public static String toString(MapleData data) {
        return (String) data.getData();
    }
}
