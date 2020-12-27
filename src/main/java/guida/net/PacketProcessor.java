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

package guida.net;

import guida.net.channel.handler.*;
import guida.net.handler.KeepAliveHandler;
import guida.net.handler.NoOpHandler;
import guida.net.login.handler.AfterLoginHandler;
import guida.net.login.handler.CharSelectedHandler;
import guida.net.login.handler.CharSelectedRegisterPic;
import guida.net.login.handler.CharSelectedWithPic;
import guida.net.login.handler.CharlistRequestHandler;
import guida.net.login.handler.CheckCharNameHandler;
import guida.net.login.handler.CreateCharHandler;
import guida.net.login.handler.DeleteCharHandler;
import guida.net.login.handler.LoginPasswordHandler;
import guida.net.login.handler.PickCharHandler;
import guida.net.login.handler.RelogRequestHandler;
import guida.net.login.handler.ServerStatusRequestHandler;
import guida.net.login.handler.ServerlistRequestHandler;
import guida.net.login.handler.SetGenderHandler;
import guida.net.login.handler.SetPinHandler;
import guida.net.login.handler.ToWorldListHandler;
import guida.net.login.handler.ViewAllCharConnectRegisterPic;
import guida.net.login.handler.ViewAllCharConnectWithPic;
import guida.net.login.handler.ViewAllCharHandler;
import guida.net.login.handler.ViewCharHandler;

public final class PacketProcessor {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PacketProcessor.class);
    private static volatile PacketProcessor instance;

    public enum Mode {
        LOGINSERVER,
        CHANNELSERVER
    }
    private MaplePacketHandler[] handlers;

    private PacketProcessor() {
        int maxRecvOp = 0;
        for (RecvPacketOpcode op : RecvPacketOpcode.values()) {
            if (op.getValue() > maxRecvOp) {
                maxRecvOp = op.getValue();
            }
        }
        handlers = new MaplePacketHandler[maxRecvOp + 1];
    }

    public static PacketProcessor getProcessor() {
        if (instance == null) {
            throw new IllegalStateException("PacketProcessor#getProcessor called before PacketProcessor#initialise.");
        }
        return instance;
    }

    public static void initialise(Mode mode) {
        instance = new PacketProcessor();
        instance.reset(mode);
    }

    public MaplePacketHandler getHandler(short packetId) {
        if (packetId > handlers.length) {
            return null;
        }
        return handlers[packetId];
    }

    public void registerHandler(RecvPacketOpcode code, MaplePacketHandler handler) {
        try {
            handlers[code.getValue()] = handler;
        } catch (ArrayIndexOutOfBoundsException aiobe) {
            log.error("Check your Recv Packet Opcodes - Something is wrong. " + aiobe);
        }
    }

    public void reset(Mode mode) {
        handlers = new MaplePacketHandler[handlers.length];
        registerHandler(RecvPacketOpcode.PONG, new KeepAliveHandler());
        registerHandler(RecvPacketOpcode.CLIENT_ERROR, NoOpHandler.getInstance());
        registerHandler(RecvPacketOpcode.STRANGE_DATA, NoOpHandler.getInstance());
        registerHandler(RecvPacketOpcode.MAP_CHANGED, NoOpHandler.getInstance());
        registerHandler(RecvPacketOpcode.PARTY_ACTION, NoOpHandler.getInstance());
        if (mode == Mode.LOGINSERVER) {
            registerHandler(RecvPacketOpcode.LOGIN_PASSWORD, new LoginPasswordHandler());
            registerHandler(RecvPacketOpcode.GUEST_LOGIN, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.SERVERLIST_REREQUEST, new ServerlistRequestHandler());
            registerHandler(RecvPacketOpcode.CHARLIST_REQUEST, new CharlistRequestHandler());
            registerHandler(RecvPacketOpcode.SERVERSTATUS_REQUEST, new ServerStatusRequestHandler());
            registerHandler(RecvPacketOpcode.SET_GENDER, new SetGenderHandler());
            registerHandler(RecvPacketOpcode.AFTER_LOGIN, new AfterLoginHandler());
            registerHandler(RecvPacketOpcode.REGISTER_PIN, new SetPinHandler());
            registerHandler(RecvPacketOpcode.SERVERLIST_REQUEST, new ServerlistRequestHandler());
            registerHandler(RecvPacketOpcode.TO_WORLDLIST, new ToWorldListHandler());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_REQUEST, new ViewCharHandler());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_CONNECT, new PickCharHandler());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR, new ViewAllCharHandler());
            registerHandler(RecvPacketOpcode.CHAR_SELECT, new CharSelectedHandler());
            registerHandler(RecvPacketOpcode.CHECK_CHAR_NAME, new CheckCharNameHandler());
            registerHandler(RecvPacketOpcode.CREATE_CHAR, new CreateCharHandler());
            registerHandler(RecvPacketOpcode.DELETE_CHAR, new DeleteCharHandler());
            registerHandler(RecvPacketOpcode.CLIENT_START, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CLIENT_ON_LOGIN, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.ERROR_REPORT, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CHAR_SELECT_CREATE_PIC, new CharSelectedRegisterPic());
            registerHandler(RecvPacketOpcode.CHAR_SELECT_PIC, new CharSelectedWithPic());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_CONNECT_CREATE_PIC, new ViewAllCharConnectRegisterPic());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_CONNECT_PIC, new ViewAllCharConnectWithPic());
            registerHandler(RecvPacketOpcode.RELOG, new RelogRequestHandler());
        } else if (mode == Mode.CHANNELSERVER) {
            registerHandler(RecvPacketOpcode.SERVERLIST_REREQUEST, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.TO_WORLDLIST, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CHAR_SELECT, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CHAR_SELECT_CREATE_PIC, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CHAR_SELECT_PIC, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_CONNECT_CREATE_PIC, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.VIEW_ALL_CHAR_CONNECT_PIC, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.NAME_CHANGE, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.PLAYER_LOGGEDIN, new PlayerLoggedinHandler());
            registerHandler(RecvPacketOpcode.CHANGE_MAP, new ChangeMapHandler());
            registerHandler(RecvPacketOpcode.CHANGE_CHANNEL, new ChangeChannelHandler());
            registerHandler(RecvPacketOpcode.ENTER_CASH_SHOP, new EnterCashShopHandler());
            registerHandler(RecvPacketOpcode.MOVE_PLAYER, new MovePlayerHandler());
            registerHandler(RecvPacketOpcode.CANCEL_CHAIR, new CancelChairHandler());
            registerHandler(RecvPacketOpcode.USE_CHAIR, new UseChairHandler());
            registerHandler(RecvPacketOpcode.CLOSE_RANGE_ATTACK, new CloseRangeDamageHandler());
            registerHandler(RecvPacketOpcode.RANGED_ATTACK, new RangedAttackHandler());
            registerHandler(RecvPacketOpcode.MAGIC_ATTACK, new MagicDamageHandler());
            registerHandler(RecvPacketOpcode.PASSIVE_ENERGY, new PassiveEnergyHandler());
            registerHandler(RecvPacketOpcode.TAKE_DAMAGE, new TakeDamageHandler());
            registerHandler(RecvPacketOpcode.GENERAL_CHAT, new GeneralchatHandler());
            registerHandler(RecvPacketOpcode.CLOSE_CHALKBOARD, new CloseChalkboardHandler());
            registerHandler(RecvPacketOpcode.FACE_EXPRESSION, new FaceExpressionHandler());
            registerHandler(RecvPacketOpcode.USE_ITEMEFFECT, new UseItemEffectHandler());
            registerHandler(RecvPacketOpcode.USE_DEATH_ITEM, new UseDeathItemHandler());
            registerHandler(RecvPacketOpcode.MONSTER_BOOK_COVER, new MonsterBookCoverHandler());
            registerHandler(RecvPacketOpcode.NPC_TALK, new NPCTalkHandler());
            registerHandler(RecvPacketOpcode.REMOTE_MERCHANT, new RemoteHiredMerchantHandler());
            registerHandler(RecvPacketOpcode.NPC_TALK_MORE, new NPCMoreTalkHandler());
            registerHandler(RecvPacketOpcode.NPC_SHOP, new NPCShopHandler());
            registerHandler(RecvPacketOpcode.STORAGE, new StorageHandler());
            registerHandler(RecvPacketOpcode.HIRED_MERCHANT_REQUEST, new HiredMerchantRequestHandler());
            registerHandler(RecvPacketOpcode.DUEY_ACTION, new DueyActionHandler());
            registerHandler(RecvPacketOpcode.MINERVA_WARP, new MinervaHandler());
            registerHandler(RecvPacketOpcode.SLOT_MERGE, new SlotMergeHandler());
            registerHandler(RecvPacketOpcode.ITEM_SORT, new ItemSortHandler());
            registerHandler(RecvPacketOpcode.ITEM_MOVE, new ItemMoveHandler());
            registerHandler(RecvPacketOpcode.USE_ITEM, new UseItemHandler());
            registerHandler(RecvPacketOpcode.CANCEL_ITEM_EFFECT, new CancelItemEffectHandler());
            registerHandler(RecvPacketOpcode.TOUCHING_REACTOR, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.USE_SUMMON_BAG, new UseSummonBagHandler());
            registerHandler(RecvPacketOpcode.PET_FOOD, new PetFoodHandler());
            registerHandler(RecvPacketOpcode.USE_MOUNT_FOOD, new MountFoodHandler());
            registerHandler(RecvPacketOpcode.USE_CASH_ITEM, new UseCashItemHandler());
            registerHandler(RecvPacketOpcode.USE_CATCH_ITEM, new UseCatchItemHandler());
            registerHandler(RecvPacketOpcode.USE_SKILL_BOOK, new SkillBookHandler());
            registerHandler(RecvPacketOpcode.USE_RETURN_SCROLL, new UseItemHandler());
            registerHandler(RecvPacketOpcode.USE_UPGRADE_SCROLL, new ScrollHandler());
            registerHandler(RecvPacketOpcode.DISTRIBUTE_AP, new DistributeAPHandler());
            registerHandler(RecvPacketOpcode.AUTO_DISTRIBUTE_AP, new AutoDistributeAPHandler());
            registerHandler(RecvPacketOpcode.HEAL_OVER_TIME, new HealOvertimeHandler());
            registerHandler(RecvPacketOpcode.DISTRIBUTE_SP, new DistributeSPHandler());
            registerHandler(RecvPacketOpcode.SPECIAL_MOVE, new SpecialMoveHandler());
            registerHandler(RecvPacketOpcode.CANCEL_BUFF, new CancelBuffHandler());
            registerHandler(RecvPacketOpcode.SKILL_EFFECT, new SkillEffectHandler());
            registerHandler(RecvPacketOpcode.MESO_DROP, new MesoDropHandler());
            registerHandler(RecvPacketOpcode.GIVE_FAME, new GiveFameHandler());
            registerHandler(RecvPacketOpcode.CHAR_INFO_REQUEST, new CharInfoRequestHandler());
            registerHandler(RecvPacketOpcode.SPAWN_PET, new SpawnPetHandler());
            registerHandler(RecvPacketOpcode.CANCEL_DEBUFF, new CancelDebuffHandler());
            registerHandler(RecvPacketOpcode.CHANGE_MAP_SPECIAL, new ChangeMapSpecialHandler());
            registerHandler(RecvPacketOpcode.USE_INNER_PORTAL, new InnerPortalHandler());
            registerHandler(RecvPacketOpcode.TELEPORT_ROCK, new TeleportRockHandler());
            registerHandler(RecvPacketOpcode.ALERT_GM, new AlertGMHandler());
            registerHandler(RecvPacketOpcode.QUEST_ACTION, new QuestActionHandler());
            registerHandler(RecvPacketOpcode.EFFECT_ON_OFF, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.THROW_BOMB, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.SKILL_MACRO, new SkillMacroHandler());
            registerHandler(RecvPacketOpcode.PINKBOX, new UsePinkBoxHandler());
            registerHandler(RecvPacketOpcode.MAKER, new MakerHandler());
            registerHandler(RecvPacketOpcode.TREASURE_CHEST, new TreasureChestHandler());
            registerHandler(RecvPacketOpcode.REMOTE_GACHAPON, new RemoteGachaponHandler());
            registerHandler(RecvPacketOpcode.MULTI_CHAT, new MultiChatHandler());
            registerHandler(RecvPacketOpcode.WHISPER, new WhisperHandler());
            registerHandler(RecvPacketOpcode.SPOUSE_CHAT, new SpouseChatHandler());
            registerHandler(RecvPacketOpcode.MESSENGER, new MessengerHandler());
            registerHandler(RecvPacketOpcode.PLAYER_INTERACTION, new PlayerInteractionHandler());
            registerHandler(RecvPacketOpcode.PARTY_OPERATION, new PartyOperationHandler());
            registerHandler(RecvPacketOpcode.DENY_PARTY_REQUEST, new DenyPartyRequestHandler());
            registerHandler(RecvPacketOpcode.GUILD_OPERATION, new GuildOperationHandler());
            registerHandler(RecvPacketOpcode.DENY_GUILD_REQUEST, new DenyGuildRequestHandler());
            registerHandler(RecvPacketOpcode.BUDDYLIST_MODIFY, new BuddylistModifyHandler());
            registerHandler(RecvPacketOpcode.NOTE_ACTION, new NoteActionHandler());
            registerHandler(RecvPacketOpcode.USE_DOOR, new DoorHandler());
            registerHandler(RecvPacketOpcode.CHANGE_KEYMAP, new KeymapChangeHandler());
            registerHandler(RecvPacketOpcode.RING_ACTION, new RingActionHandler());
            registerHandler(RecvPacketOpcode.VIEW_FAMILY_PEDIGREE, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.VIEW_FAMILY, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.FAMILY_ADD, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.HACK, new AutoBanHandler());
            registerHandler(RecvPacketOpcode.ALLIANCE_OPERATION, new AllianceOperationsHandler());
            registerHandler(RecvPacketOpcode.BBS_OPERATION, new BBSOperationHandler());
            registerHandler(RecvPacketOpcode.ENTER_MTS, new EnterMTSHandler());
            registerHandler(RecvPacketOpcode.UNKNOWN_STATE_RESPONSE, new UnknownStateHandler());
            registerHandler(RecvPacketOpcode.SOLOMON, new SolomonHandler());
            registerHandler(RecvPacketOpcode.NEW_YEARS_CARD, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.MAPLEMAS_POTION, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.CYGNUS_TUTOR_CHAT, new TutorChatHandler());
            registerHandler(RecvPacketOpcode.ARAN_COMBO_COUNTER, new AranComboHandler());
            registerHandler(RecvPacketOpcode.UNKNOWN_HEADER, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.MOVE_PET, new MovePetHandler());
            registerHandler(RecvPacketOpcode.PET_CHAT, new PetChatHandler());
            registerHandler(RecvPacketOpcode.PET_COMMAND, new PetCommandHandler());
            registerHandler(RecvPacketOpcode.PET_LOOT, new PetLootHandler());
            registerHandler(RecvPacketOpcode.PET_AUTO_POT, new PetAutoPotHandler());
            registerHandler(RecvPacketOpcode.PET_ITEM_IGNORE, new PetItemIgnoreHandler());
            registerHandler(RecvPacketOpcode.MOVE_SUMMON, new MoveSummonHandler());
            registerHandler(RecvPacketOpcode.SUMMON_ATTACK, new SummonDamageHandler());
            registerHandler(RecvPacketOpcode.DAMAGE_SUMMON, new DamageSummonHandler());
            registerHandler(RecvPacketOpcode.KEYMAP_OPEN, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.MOVE_LIFE, new MoveLifeHandler());
            registerHandler(RecvPacketOpcode.AUTO_AGGRO, new AutoAggroHandler());
            registerHandler(RecvPacketOpcode.MOB_DAMAGE_MOB, new FriendlyMobDamagedHandler());
            registerHandler(RecvPacketOpcode.MONSTER_BOMB, new MonsterBombHandler());
            registerHandler(RecvPacketOpcode.HYPNOTIZE, new HypnotizeHandler());
            registerHandler(RecvPacketOpcode.NPC_ACTION, new NPCAnimation());
            registerHandler(RecvPacketOpcode.ITEM_PICKUP, new ItemPickupHandler());
            registerHandler(RecvPacketOpcode.DAMAGE_REACTOR, new ReactorHitHandler());
            registerHandler(RecvPacketOpcode.SNOWBALL, new SnowBallHandler());
            registerHandler(RecvPacketOpcode.MONSTER_CARNIVAL, new MonsterCarnivalHandler());
            registerHandler(RecvPacketOpcode.OBJECT_REQUEST, NoOpHandler.getInstance());
            registerHandler(RecvPacketOpcode.PARTY_SEARCH_REGISTER, new PartySearchRegisterHandler());
            registerHandler(RecvPacketOpcode.PARTY_SEARCH_START, new PartySearchStartHandler());
            registerHandler(RecvPacketOpcode.MAPLETV, new MapleTVHandler());
            registerHandler(RecvPacketOpcode.TOUCHING_CS, new TouchingCashShopHandler());
            registerHandler(RecvPacketOpcode.CASH_SHOP, new CashShopHandler());
            registerHandler(RecvPacketOpcode.COUPON_CODE, new CouponCodeHandler());
            registerHandler(RecvPacketOpcode.MTS_OP, new MTSHandler());
            registerHandler(RecvPacketOpcode.VICIOUS_HAMMER, new ViciousHammerHandler());
            registerHandler(RecvPacketOpcode.EIGHT_EFF, NoOpHandler.getInstance());
        } else {
            throw new RuntimeException("Unknown packet processor mode");
        }
    }
}