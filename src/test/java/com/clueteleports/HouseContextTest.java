package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HouseContextTest
{
	private final HouseContext context = new HouseContext();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private GameState gameState = GameState.LOGGED_IN;
	private int tick;
	private boolean instanced;
	private int baseX = 1920;
	private int baseY = 7040;
	private int[][][] chunks;
	private LocalPoint local = new LocalPoint(64, 64, 0);
	private Player player = proxy(Player.class, (method, args) ->
	{
		if ("getLocalLocation".equals(method))
		{
			return local;
		}
		throw new UnsupportedOperationException(method);
	});
	private WorldView worldView = proxy(WorldView.class, (method, args) ->
	{
		switch (method)
		{
			case "isInstance": return instanced;
			case "getPlane": return 0;
			case "getBaseX": return baseX;
			case "getBaseY": return baseY;
			case "getInstanceTemplateChunks": return chunks;
			case "getId": return 0;
			default: throw new UnsupportedOperationException(method);
		}
	});
	private final Client client = proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "getGameState": return gameState;
			case "getTickCount": return tick;
			case "getLocalPlayer": return player;
			case "getWorldView":
			case "getTopLevelWorldView": return worldView;
			case "getBaseX": return baseX;
			case "getBaseY": return baseY;
			case "getVarbitValue": return varbits.getOrDefault((Integer) args[0], 0);
			default: throw new UnsupportedOperationException(method);
		}
	});

	@Test
	public void homeThenLoadingLearnsNormalNonInstancedHouseWithoutAWelcome()
	{
		click(MenuAction.GAME_OBJECT_SECOND_OPTION, "Home", "<col=ffff>Portal</col>", 0, -1, 0);
		load();

		assertFalse(instanced);
		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void buildModePortalActionAlsoIdentifiesOwnEntry()
	{
		click(MenuAction.GAME_OBJECT_THIRD_OPTION, "Build mode", "Portal", 0, -1, 0);
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void castUsesTheSpecificHouseSpellWidget()
	{
		click(MenuAction.CC_OP, "Cast", "Teleport to House",
			InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE, -1, 1);
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void houseTabletRequiresItsActualItemId()
	{
		click(MenuAction.ITEM_FIRST_OPTION, "Break", "Teleport to house", 0,
			ItemID.POH_TABLET_TELEPORTTOHOUSE, ItemID.POH_TABLET_TELEPORTTOHOUSE);
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void constructionCapeTeleportRequiresConstructionCapeItem()
	{
		click(MenuAction.CC_OP, "Tele to POH", "Construct. cape", 0, ItemID.SKILLCAPE_CONSTRUCTION, 3);
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void fairyDiqRequiresItsConfirmWidgetAndTheCurrentDialValues()
	{
		varbits.put(VarbitID.FAIRYRING_1, 1);
		varbits.put(VarbitID.FAIRYRING_2, 0);
		varbits.put(VarbitID.FAIRYRING_3, 3);
		click(MenuAction.CC_OP, "Teleport", "", InterfaceID.Fairyrings.CONFIRM, -1, 1);
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void anotherFairyCodeDoesNotAuthorizeHouseLearning()
	{
		varbits.put(VarbitID.FAIRYRING_1, 1);
		varbits.put(VarbitID.FAIRYRING_2, 0);
		varbits.put(VarbitID.FAIRYRING_3, 2);
		click(MenuAction.CC_OP, "Teleport", "", InterfaceID.Fairyrings.CONFIRM, -1, 1);
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void arbitraryWidgetOrRuneliteMenuLabelsDoNotProveEntry()
	{
		click(MenuAction.RUNELITE, "Home", "Portal", 0, -1, 0);
		click(MenuAction.CC_OP, "Cast", "Teleport to House", 12345, -1, 1);
		click(MenuAction.ITEM_FIRST_OPTION, "Break", "Teleport to house", 0, 1, 1);
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void actualHouseRegionAloneNeverProvesOwnership()
	{
		assertFalse(context.isOwnHouse(client));
		load();
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void actionAloneDoesNotAuthorizeTheAlreadyLoadedHouse()
	{
		home();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void arbitraryInstancesAreRejectedEvenAfterAHomeAction()
	{
		instanced = true;
		mapInstanceTo(3200, 3200);
		home();
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void instanceTemplateMappedToHouseRegionCanConfirmEntry()
	{
		instanced = true;
		mapInstanceTo(1920, 7040);
		home();
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void buildingModeCanConfirmAnExistingHouseVisit()
	{
		varbits.put(VarbitID.POH_BUILDING_MODE, 1);

		assertTrue(context.isOwnHouse(client));
		varbits.put(VarbitID.POH_BUILDING_MODE, 0);
		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void staleBuildingFlagOutsideHouseCannotConfirmOwnership()
	{
		baseX = 3200;
		baseY = 3200;
		varbits.put(VarbitID.POH_BUILDING_MODE, 1);

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void entryIntentExpiresBeforeADeferredHouseArrival()
	{
		home();
		tick = HouseContext.ENTRY_WINDOW_TICKS + 1;
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void failedOutsideArrivalCannotAuthorizeALaterHouseScene()
	{
		home();
		baseX = 3200;
		baseY = 3200;
		load();
		assertFalse(context.isOwnHouse(client));
		tick++;
		assertFalse(context.isOwnHouse(client));
		baseX = 1920;
		baseY = 7040;
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void twoStagePortalThenHouseLoadingPreservesTheEntryIntent()
	{
		home();
		baseX = 2752;
		baseY = 3200;
		load();
		assertFalse(context.isOwnHouse(client));
		baseX = 1920;
		baseY = 7040;
		load();

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void friendsHouseAndAdvertisementInvalidateEarlierOwnEntryIntent()
	{
		home();
		click(MenuAction.GAME_OBJECT_THIRD_OPTION, "Friend's house", "Portal", 0, -1, 0);
		load();
		assertFalse(context.isOwnHouse(client));
		home();
		click(MenuAction.GAME_OBJECT_FIRST_OPTION, "View", "House Advertisement", 0, -1, 0);
		load();
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void groupHouseSpellCannotReuseAnOwnHouseIntent()
	{
		home();
		click(MenuAction.CC_OP, "Group: Previous", "Teleport to House",
			InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE, -1, 5);
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void walkingAfterAFailedTeleportCancelsPendingOwnership()
	{
		home();
		click(MenuAction.WALK, "Walk here", "", 0, -1, 0);
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void chainedHouseLoadsKeepEvidenceWhenATickFallsBetweenThem()
	{
		home();
		load();
		assertTrue(context.isOwnHouse(client));
		tick++;
		load();
		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void houseObjectClickBeforeTheFirstTickSettlesTheLoadedEntry()
	{
		home();
		load();
		click(MenuAction.GAME_OBJECT_FIRST_OPTION, "Travel", "Spirit tree", 0, -1, 0);

		assertTrue(context.isOwnHouse(client));
	}

	@Test
	public void houseObjectClickBeforeLoadingCannotSettleEntry()
	{
		home();
		click(MenuAction.GAME_OBJECT_FIRST_OPTION, "Travel", "Spirit tree", 0, -1, 0);
		load();

		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void laterSceneLoadingInvalidatesOwnershipAfterTheEntryWindow()
	{
		home();
		load();
		assertTrue(context.isOwnHouse(client));
		tick = HouseContext.ENTRY_WINDOW_TICKS + 1;
		assertTrue(context.isOwnHouse(client));
		load();
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void guestEntryOverridesConfirmedOwnershipWithinTheChainedLoadWindow()
	{
		home();
		load();
		assertTrue(context.isOwnHouse(client));
		click(MenuAction.GAME_OBJECT_THIRD_OPTION, "Friend's house", "Portal", 0, -1, 0);
		load();
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void resetLogoutHopAndConnectionLossClearEntryAndOwnership()
	{
		for (GameState state : new GameState[]{GameState.LOGIN_SCREEN, GameState.HOPPING, GameState.CONNECTION_LOST})
		{
			home();
			context.onGameStateChanged(state);
			load();
			assertFalse(context.isOwnHouse(client));
		}
		home();
		context.reset();
		load();
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void welcomeMessagesCannotReplaceActualEntryEvidence()
	{
		context.onChatMessage(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "Welcome to your house.", "", 0));
		assertFalse(context.isOwnHouse(client));
		context.onChatMessage(new ChatMessage(null, ChatMessageType.PUBLICCHAT, "", "Welcome to your house.", "", 0));
		assertFalse(context.isOwnHouse(client));
	}

	@Test
	public void incompleteLocationAndInstanceDataAreRejected()
	{
		home();
		load();
		instanced = true;
		assertFalse(context.isOwnHouse(client));
		instanced = false;
		local = null;
		assertFalse(context.isOwnHouse(client));
		player = null;
		assertFalse(context.isOwnHouse(client));
		worldView = null;
		assertFalse(context.isOwnHouse(client));
		assertFalse(context.isOwnHouse(null));
	}

	private void home()
	{
		click(MenuAction.GAME_OBJECT_SECOND_OPTION, "Home", "Portal", 0, -1, 0);
	}

	private void load()
	{
		gameState = GameState.LOADING;
		context.onGameStateChanged(gameState);
		gameState = GameState.LOGGED_IN;
		context.onGameStateChanged(gameState);
	}

	private void mapInstanceTo(int x, int y)
	{
		chunks = new int[4][13][13];
		for (int[][] plane : chunks)
		{
			for (int[] column : plane)
			{
				Arrays.fill(column, -1);
			}
		}
		chunks[0][0][0] = ((x / 8) << 14) | ((y / 8) << 3);
	}

	private void click(MenuAction action, String option, String target, int widget, int item, int id)
	{
		MenuEntry entry = proxy(MenuEntry.class, (method, args) ->
		{
			switch (method)
			{
				case "getType": return action;
				case "getOption": return option;
				case "getTarget": return target;
				case "getParam1": return widget;
				case "getItemId": return item;
				case "getIdentifier": return id;
				case "getWidget": return null;
				default: throw new UnsupportedOperationException(method);
			}
		});
		context.onMenuOptionClicked(new MenuOptionClicked(entry), client);
	}

	private static <T> T proxy(Class<T> type, Access access)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(object, method, args) -> access.get(method.getName(), args)));
	}

	private interface Access
	{
		Object get(String method, Object[] args);
	}
}
