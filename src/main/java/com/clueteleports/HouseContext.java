package com.clueteleports;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/**
 * Tracks positively identified own-house visits on the client thread.
 *
 * Entry-action classification follows the tested pattern in
 * elzerma/farm-run-autopilot PohDetector/PohDetectorTest at
 * 8fce4102559f9d2aa095dd7eb8fb87884f7cda51, with narrower action scopes and
 * confirmation against RuneLite Discord's POH regions after scene loading.
 * A POH region identifies a house, never its owner by itself.
 */
public final class HouseContext
{
	static final int ENTRY_WINDOW_TICKS = 30;
	// runelite/runelite DiscordGameEventType.REGION_POH at
	// 9fad2c588ca957ae47d883b09544725a796ed9fe.
	private static final Set<Integer> HOUSE_REGIONS = Set.of(7534, 7535, 7790, 7791, 8046, 8047, 8302, 8303);
	private boolean ownHouseConfirmed;
	private int entryTick = -1;
	private boolean entrySawLoading;
	private int outsideAfterLoadingTick = -1;

	public void onGameStateChanged(GameState gameState)
	{
		if (gameState == GameState.LOADING)
		{
			ownHouseConfirmed = false;
			if (entryTick >= 0)
			{
				entrySawLoading = true;
			}
		}
		else if (gameState != GameState.LOGGED_IN)
		{
			reset();
		}
	}

	/** Kept for existing callers; chat is not reliable ownership evidence. */
	public void onChatMessage(ChatMessage event)
	{
	}

	public void onMenuOptionClicked(MenuOptionClicked event, Client client)
	{
		if (event == null || client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		MenuAction action = event.getMenuAction();
		String option = plain(event.getMenuOption());
		String target = plain(event.getMenuTarget());
		boolean objectAction = isObjectAction(action);
		boolean widgetAction = isWidgetAction(action);
		boolean spell = widgetAction && event.getParam1() == InterfaceID.MagicSpellbook.TELEPORT_TO_YOUR_HOUSE;
		String dialog = "";
		if (action == MenuAction.WIDGET_CONTINUE && (event.getParam1() >>> 16) == InterfaceID.CHATMENU)
		{
			Widget widget = event.getWidget();
			dialog = widget == null ? "" : plain(widget.getText());
		}

		if ((objectAction && ("friend's house".equals(option) || "house advertisement".equals(target)))
			|| (spell && option.startsWith("group:")) || dialog.contains("friend's house"))
		{
			reset();
			return;
		}

		boolean portal = objectAction && "portal".equals(target)
			&& ("home".equals(option) || "build mode".equals(option));
		boolean tablet = (isItemAction(action) || widgetAction) && "break".equals(option)
			&& (event.getItemId() == ItemID.POH_TABLET_TELEPORTTOHOUSE
				|| (isItemAction(action) && event.getId() == ItemID.POH_TABLET_TELEPORTTOHOUSE));
		boolean cape = (isItemAction(action) || widgetAction) && "tele to poh".equals(option)
			&& (event.getItemId() == ItemID.SKILLCAPE_CONSTRUCTION
				|| event.getItemId() == ItemID.SKILLCAPE_CONSTRUCTION_TRIMMED);
		boolean ownDialog = "go to your house.".equals(dialog) || "go to your house".equals(dialog)
			|| "go to your house (building mode).".equals(dialog);
		// RuneLite FairyRingPlugin maps the dials as A/D/C/B, I/L/K/J, P/S/R/Q.
		boolean fairyHouse = widgetAction && event.getParam1() == InterfaceID.Fairyrings.CONFIRM
			&& event.getId() == 1 && client.getVarbitValue(VarbitID.FAIRYRING_1) == 1
			&& client.getVarbitValue(VarbitID.FAIRYRING_2) == 0
			&& client.getVarbitValue(VarbitID.FAIRYRING_3) == 3;

		if (portal || (spell && "cast".equals(option)) || tablet || cape || ownDialog || fairyHouse)
		{
			ownHouseConfirmed = false;
			entryTick = client.getTickCount();
			entrySawLoading = false;
			outsideAfterLoadingTick = -1;
		}
		else if (entryTick >= 0 && (objectAction || action == MenuAction.WALK || action == MenuAction.WIDGET_CONTINUE))
		{
			// A player can open a house travel menu before the next game tick.
			// Settle the loaded arrival before consuming its entry evidence.
			if (entrySawLoading)
			{
				isOwnHouse(client);
			}
			// A failed teleport must not authorize a later unrelated entrance.
			clearEntry();
		}
	}

	public boolean isOwnHouse(Client client)
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return false;
		}
		int tick = client.getTickCount();
		if (entryTick >= 0 && (tick < entryTick || tick - entryTick > ENTRY_WINDOW_TICKS))
		{
			clearEntry();
		}
		if (!isHouse(client))
		{
			ownHouseConfirmed = false;
			if (entrySawLoading)
			{
				// Entry can briefly load the outside portal before the house rooms.
				// Allow that first tick, but discard a settled outside arrival.
				if (outsideAfterLoadingTick < 0)
				{
					outsideAfterLoadingTick = tick;
				}
				else if (tick != outsideAfterLoadingTick)
				{
					clearEntry();
				}
			}
			return false;
		}
		if (client.getVarbitValue(VarbitID.POH_BUILDING_MODE) == 1 || (entryTick >= 0 && entrySawLoading))
		{
			ownHouseConfirmed = true;
			// Keep the bounded entry evidence through chained house loads. A tick
			// can occur between the two LOADING/LOGGED_IN cycles on real entry.
		}
		return ownHouseConfirmed;
	}

	public void reset()
	{
		ownHouseConfirmed = false;
		clearEntry();
	}

	private void clearEntry()
	{
		entryTick = -1;
		entrySawLoading = false;
		outsideAfterLoadingTick = -1;
	}

	private static boolean isHouse(Client client)
	{
		Player player = client.getLocalPlayer();
		LocalPoint local = player == null ? null : player.getLocalLocation();
		if (local == null)
		{
			return false;
		}
		WorldView view = client.getWorldView(local.getWorldView());
		if (view == null)
		{
			return false;
		}
		if (view.isInstance())
		{
			int[][][] chunks = view.getInstanceTemplateChunks();
			int plane = view.getPlane();
			int x = local.getSceneX() / 8;
			int y = local.getSceneY() / 8;
			if (chunks == null || plane < 0 || plane >= chunks.length || chunks[plane] == null
				|| x < 0 || x >= chunks[plane].length || chunks[plane][x] == null
				|| y < 0 || y >= chunks[plane][x].length || chunks[plane][x][y] == -1)
			{
				return false;
			}
		}
		return HOUSE_REGIONS.contains(WorldPoint.fromLocalInstance(client, local).getRegionID());
	}

	private static boolean isObjectAction(MenuAction action)
	{
		return action == MenuAction.GAME_OBJECT_FIRST_OPTION || action == MenuAction.GAME_OBJECT_SECOND_OPTION
			|| action == MenuAction.GAME_OBJECT_THIRD_OPTION || action == MenuAction.GAME_OBJECT_FOURTH_OPTION
			|| action == MenuAction.GAME_OBJECT_FIFTH_OPTION;
	}

	private static boolean isWidgetAction(MenuAction action)
	{
		return action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY
			|| action == MenuAction.WIDGET_TYPE_1;
	}

	private static boolean isItemAction(MenuAction action)
	{
		return action == MenuAction.ITEM_FIRST_OPTION || action == MenuAction.ITEM_SECOND_OPTION
			|| action == MenuAction.ITEM_THIRD_OPTION || action == MenuAction.ITEM_FOURTH_OPTION
			|| action == MenuAction.ITEM_FIFTH_OPTION;
	}

	private static String plain(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim().toLowerCase(Locale.ROOT);
	}
}
