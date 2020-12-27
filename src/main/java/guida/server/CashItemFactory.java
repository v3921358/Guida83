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

import guida.database.DatabaseConnection;
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProviderFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lerk
 */
public class CashItemFactory {

    private final static Map<Integer, CashItemInfo> itemStats = new HashMap<>(11922);
    private final static Map<Integer, List<CashItemInfo>> cashPackages = new HashMap<>();
    private final static MapleData packageInformationProvider = MapleDataProviderFactory.getDataProvider("Etc").getData("CashPackage.img");

    public static void loadCommodities() {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = con.prepareStatement("SELECT * FROM commodities");
            rs = ps.executeQuery();
            while (rs.next()) {
                final int SN = rs.getInt("SN");
                itemStats.put(SN, new CashItemInfo(SN, rs.getInt("ItemId"), rs.getInt("Count"), rs.getInt("Price"), rs.getInt("Period"), rs.getInt("Gender"), rs.getInt("OnSale") == 1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (ps != null) {
                    ps.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static CashItemInfo getItem(int sn) {
        return itemStats.get(sn);
    }

    public static List<CashItemInfo> getPackageItems(int itemId) {
        if (cashPackages.containsKey(itemId)) {
            return cashPackages.get(itemId);
        }
        final ArrayList<CashItemInfo> packageItems = new ArrayList<>();
        final MapleData packageItem = packageInformationProvider.resolve(itemId + "/SN");
        if (packageItem != null) {
            for (MapleData item : packageItem) {
                packageItems.add(getItem(DataUtil.toInt(packageItem.resolve(item.getName()))));
            }
        }
        packageItems.trimToSize();
        cashPackages.put(itemId, packageItems);
        return packageItems;
    }
}