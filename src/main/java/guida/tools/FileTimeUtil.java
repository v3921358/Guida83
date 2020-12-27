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

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

public final class FileTimeUtil {

    private static final long FT_UT_OFFSET;
    private static final long DEFAULT_TIMESTAMP;
    private static final long DEFAULT_PET_TIMESTAMP;
    private static final TimeZone tz = TimeZone.getDefault();

    static {
        final Calendar cal = Calendar.getInstance();
        FT_UT_OFFSET = 11644473600000L + tz.getOffset(System.currentTimeMillis());
        cal.clear();
        cal.set(Calendar.YEAR, 2079);
        cal.set(Calendar.DATE, 1);
        DEFAULT_TIMESTAMP = cal.getTimeInMillis();
        cal.set(Calendar.DATE, 0);
        DEFAULT_PET_TIMESTAMP = cal.getTimeInMillis();
    }

    /**
     * Converts a Unix Timestamp into File Time
     *
     * @param timeStampinMillis The actual timestamp in milliseconds.
     * @return A 64-bit long giving a filetime timestamp
     */
    public static long getFileTimestamp(long timeStampinMillis) {
        return timeStampinMillis * 10000 + (FT_UT_OFFSET - (tz.inDaylightTime(Calendar.getInstance().getTime()) ? tz.getDSTSavings() : 0)) * 10000;
    }

    public static Timestamp getDefaultTimestamp() {
        return new Timestamp(DEFAULT_TIMESTAMP);
    }

    public static Timestamp getDefaultPetTimestamp() {
        return new Timestamp(DEFAULT_PET_TIMESTAMP);
    }
}