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
import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.server.MapleItemInformationProvider;
import guida.tools.Pair;
import guida.tools.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Raz
 */
public class SearchCommands implements Command {

    private final static Map<SearchQuery, List<SearchResult>> searchCache = new HashMap<>();
    private final MapleDataProvider dataProvider = MapleDataProviderFactory.getDataProvider("String");

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted.length == 1) {
            mc.dropMessage(splitted[0] + ": <NPC> <MOB> <ITEM> <MAP> <SKILL>");
        } else {
            String type = splitted[1];
            String search = StringUtil.joinStringFrom(splitted, 2).toLowerCase();
            MapleData data;
            int searchType = -1;
            if (type.equalsIgnoreCase("NPC") || type.equalsIgnoreCase("NPCS")) {
                searchType = 0;
                type = "NPCs";
            } else if (type.equalsIgnoreCase("MAP") || type.equalsIgnoreCase("MAPS")) {
                searchType = 1;
                type = "Maps";
            } else if (type.equalsIgnoreCase("MOB") || type.equalsIgnoreCase("MOBS") || type.equalsIgnoreCase("MONSTER") || type.equalsIgnoreCase("MONSTERS")) {
                searchType = 2;
                type = "Mobs";
            } else if (type.equalsIgnoreCase("REACTOR") || type.equalsIgnoreCase("REACTORS")) {
                searchType = 3;
                type = "Reactors";
            } else if (type.equalsIgnoreCase("ITEM") || type.equalsIgnoreCase("ITEMS")) {
                searchType = 4;
                type = "Items";
            } else if (type.equalsIgnoreCase("SKILL") || type.equalsIgnoreCase("SKILLS")) {
                searchType = 5;
                type = "Skills";
            }
            if (!type.matches("NPCs") && !type.matches("Maps") && !type.matches("Mobs") && !type.matches("Reactors") && !type.matches("Items") && !type.matches("Skills")) {
                mc.dropMessage("Type '" + type + "' does not exist.");
                return;
            }
            if (search.length() > 1) {
                mc.dropMessage("Searching for " + type + " that contains the phrase '" + search + "'...");
            } else {
                mc.dropMessage("Please enter a phrase to search for.");
                return;
            }

            List<SearchResult> results = null;
            SearchQuery query = new SearchQuery(search, searchType);
            for (SearchQuery sQuery : searchCache.keySet()) {
                if (sQuery.getSearchType() == searchType && sQuery.getSearch().equals(search)) {
                    results = searchCache.get(sQuery);
                    break;
                }
            }
            if (results == null) {
                results = new ArrayList<>();
                String[] searches = search.split(" ");
                switch (searchType) {
                    case 0: // NPC
                        data = dataProvider.getData("Npc.img");
                        for (MapleData searchData : data) {
                            int resultId = Integer.parseInt(searchData.getName());
                            String resultName = DataUtil.toString(searchData.getChild("name"), "MISSING-NAME");
                            if (containsWords(resultName, searches)) {
                                results.add(new SearchResult(resultId, resultName, searchType));
                            }
                        }
                        break;
                    case 1: // MAP
                        data = dataProvider.getData("Map.img");
                        for (MapleData mapAreaData : data) {
                            for (MapleData mapIdData : mapAreaData) {
                                int resultId = Integer.parseInt(mapIdData.getName());
                                String resultName = DataUtil.toString(mapIdData.getChild("streetName"), "NO-NAME") + " - " + DataUtil.toString(mapIdData.getChild("mapName"), "NO-NAME");
                                if (containsWords(resultName, searches)) {
                                    results.add(new SearchResult(resultId, resultName, searchType));
                                }
                            }
                        }
                        break;
                    case 2: // MOB
                        data = dataProvider.getData("Mob.img");
                        for (MapleData mobIdData : data) {
                            int resultId = Integer.parseInt(mobIdData.getName());
                            String resultName = DataUtil.toString(mobIdData.getChild("name"), "NO-NAME");
                            if (containsWords(resultName, searches)) {
                                results.add(new SearchResult(resultId, resultName, searchType));
                            }
                        }
                        break;
                    case 3: // REACTOR TODO
                        mc.dropMessage("ERROR: NOT ADDED YET");
                        break;
                    case 4: // ITEM
                        for (Pair<Integer, String> itemPair : MapleItemInformationProvider.getInstance().getAllItems()) {
                            if (containsWords(itemPair.getRight(), searches)) {
                                results.add(new SearchResult(itemPair.getLeft(), itemPair.getRight(), searchType));
                            }
                        }
                        break;
                    case 5: // SKILL
                        data = dataProvider.getData("Skill.img");
                        for (MapleData skillIdData : data) {
                            int resultId = Integer.parseInt(skillIdData.getName());
                            String resultName = DataUtil.toString(skillIdData.getChild("name"), "NO-NAME");
                            if (containsWords(resultName, searches)) {
                                results.add(new SearchResult(resultId, resultName, searchType));
                            }
                        }
                }
                searchCache.put(query, results);
            }
            outputResults(c, results, mc);
        }
    }

    private void outputResults(MapleClient c, List<SearchResult> results, MessageCallback mc) {
        for (int i = 0; i < results.size(); i++) {
            if (c == null) {
                if (i == 0 && results.size() > 10) {
                    mc.dropMessage("Showing only 10 results of " + results.size() + " to reduce spam.");
                }
                if (i > 10) {
                    break;
                }
            }
            SearchResult result = results.get(i);
            mc.dropMessage(result.getId() + " - " + result.getName());
        }
        if (results.isEmpty()) {
            mc.dropMessage("No results found");
        }
    }

    private boolean containsWords(String result, String[] searches) {
        result = result.toLowerCase();
        for (String search : searches) {
            if (!result.contains(search.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("find", "", "", 4),
                new CommandDefinition("lookup", "", "", 4),
                new CommandDefinition("search", "", "", 4)
        };
    }

    private static class SearchQuery {

        private final String search;
        private final int searchType;

        public SearchQuery(String search, int searchType) {
            this.search = search;
            this.searchType = searchType;
        }

        public String getSearch() {
            return search;
        }

        public int getSearchType() {
            return searchType;
        }
    }

    private static class SearchResult {

        private final int id;
        private final String name;
        private final int searchType;

        public SearchResult(int id, String name, int searchType) {
            this.id = id;
            this.name = name;
            this.searchType = searchType;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getSearchType() {
            return searchType;
        }
    }
}