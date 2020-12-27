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

package guida.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * All OdinMS servers maintain a Database Connection. This class therefore "singletonices" the connection per process.
 *
 * @author Frz
 */
public class DatabaseConnection {

    private static final ThreadLocal<Connection> con = new ThreadLocalConnection();
    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DatabaseConnection.class);
    private static Properties props = null;

    public static Connection getConnection() {
        if (props == null) {
            throw new RuntimeException("DatabaseConnection not initialized");
        }
        try {
            Connection c = con.get();
            if (c == null || !c.isValid(0)) {
                con.set(null);
                con.remove();
            }
        } catch (SQLException ex) {
            log.error("Error while getting connection", ex);
        }

        return con.get();
    }

    public static void setProps(Properties aProps) {
        props = aProps;
    }

    private static class ThreadLocalConnection extends ThreadLocal<Connection> {

        @Override
        protected Connection initialValue() {
            final String driver = props.getProperty("driver");
            final String url = props.getProperty("url");
            final String user = props.getProperty("user");
            final String password = props.getProperty("password");
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                log.error("ERROR", e);
            }
            try {
                return DriverManager.getConnection(url, user, password);
            } catch (SQLException e) {
                log.error("ERROR", e);
                return null;
            }
        }
    }
}