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

/**
 * @author Xterminator
 */
public final class ReadableMillisecondFormat {

    private final long milliseconds;

    public ReadableMillisecondFormat(long milliseconds) {
        this.milliseconds = milliseconds;
    }

    public int getSeconds(boolean totalSeconds) {
        final int seconds = (int) (milliseconds / 1000);
        if (totalSeconds) {
            return seconds;
        }
        return seconds % 60;
    }

    public int getMinutes(boolean totalMinutes) {
        final int minutes = (int) (milliseconds / 1000 / 60);
        if (totalMinutes) {
            return minutes;
        }
        return minutes % 60;
    }

    public int getHours(boolean totalHours) {
        final int hours = (int) (milliseconds / 1000 / 60 / 60);
        if (totalHours) {
            return hours;
        }
        return hours % 24;
    }

    public int getDays() {
        return (int) (milliseconds / 1000 / 60 / 60 / 24);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final String[] strings = {"", "", "", ""};
        final int days = getDays();
        if (days != 0) {
            String day = days + " day";
            if (days != 1) {
                day += "s";
            }
            strings[0] = day;
        }
        final int hours = getHours(false);
        if (hours != 0) {
            String hour = hours + " hour";
            if (hours != 1) {
                hour += "s";
            }
            strings[1] = hour;
        }
        final int minutes = getMinutes(false);
        if (minutes != 0) {
            String minute = minutes + " minute";
            if (minutes != 1) {
                minute += "s";
            }
            strings[2] = minute;
        }
        final int seconds = getSeconds(false);
        if (seconds != 0) {
            String second = seconds + " second";
            if (seconds != 1) {
                second += "s";
            }
            strings[3] = second;
        }
        if (strings[0].length() != 0) {
            sb.append(strings[0]);
            if (hours != 0 && minutes != 0 || minutes != 0 && seconds != 0 || hours != 0 && seconds != 0) {
                sb.append(", ");
            }
            if (hours != 0 && minutes == 0 && seconds == 0 || hours == 0 && minutes != 0 && seconds == 0 || hours == 0 && minutes == 0 && seconds != 0) {
                sb.append(" and ");
            }
        }
        if (strings[1].length() != 0) {
            sb.append(strings[1]);
            if (minutes != 0 && seconds != 0) {
                sb.append(", ");
            }
            if (minutes != 0 && seconds == 0 || minutes == 0 && seconds != 0) {
                sb.append(" and ");
            }
        }
        if (strings[2].length() != 0) {
            sb.append(strings[2]);
            if (seconds != 0) {
                sb.append(" and ");
            }
        }
        if (seconds != 0) {
            sb.append(strings[3]);
        }
        if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            sb.append("0 seconds");
        }
        return sb.toString();
    }
}