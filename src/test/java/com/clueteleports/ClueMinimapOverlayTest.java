package com.clueteleports;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClueMinimapOverlayTest
{
	@Test
	public void projectsTheWaypointAndClipsTheArrowToTheNativeMask()
	{
		Fixture fixture = new Fixture();
		BufferedImage complete = fixture.draw();
		assertEquals(SpriteID.FIXED_MAP_MASK, fixture.lastSpriteId);
		assertTrue(alpha(complete, 78, 72) > 0);
		assertTrue(alpha(complete, 82, 72) > 0);

		Fixture clipped = new Fixture();
		for (int y = 0; y < Fixture.BOUNDS.height; y++)
		{
			Arrays.fill(clipped.mask, y * Fixture.BOUNDS.width, y * Fixture.BOUNDS.width + 60, 1);
		}
		BufferedImage masked = clipped.draw();
		assertEquals(0, alpha(masked, 78, 72));
		assertTrue(alpha(masked, 82, 72) > 0);
	}

	@Test
	public void usesBothResizableLayoutsAndPulsesWithoutHidingTheWaypoint()
	{
		Fixture fixture = new Fixture();
		fixture.resized = true;
		int bright = alpha(fixture.draw(), 82, 72);
		assertEquals(SpriteID.RESIZE_MAP_MASK, fixture.lastSpriteId);
		fixture.stones = 1;
		fixture.cycle = 30;
		int dim = alpha(fixture.draw(), 82, 72);
		assertTrue(dim > 0 && dim < bright);
	}

	@Test
	public void neverDrawsOverHiddenBlackedOutOrCoveredMinimaps()
	{
		Fixture fixture = new Fixture();
		fixture.hidden = true;
		assertBlank(fixture.draw());
		fixture.hidden = false;
		fixture.mapToggle = 1;
		assertBlank(fixture.draw());
		fixture.mapToggle = 0;
		fixture.mapState = 2;
		assertBlank(fixture.draw());
		fixture.mapState = 0;
		fixture.worldMap = true;
		assertBlank(fixture.draw());
	}

	@Test
	public void onlyDrawsNearbyWaypointsOnTheCurrentWorldViewAndPlane()
	{
		Fixture fixture = new Fixture();
		fixture.waypoint = null;
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3310, 3210, 0);
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3250, 3210, 0);
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3213, 3210, 1);
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3213, 3210, 0);
		fixture.instance = true;
		assertBlank(fixture.draw());
	}

	private static int alpha(BufferedImage image, int x, int y)
	{
		return image.getRGB(x, y) >>> 24;
	}

	private static void assertBlank(BufferedImage image)
	{
		int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		assertTrue(Arrays.stream(pixels).allMatch(pixel -> pixel == 0));
	}

	private static final class Fixture
	{
		private static final Rectangle BOUNDS = new Rectangle(20, 20, 104, 104);
		private final int[] mask = new int[BOUNDS.width * BOUNDS.height];
		private WorldPoint waypoint = new WorldPoint(3213, 3210, 0);
		private boolean instance;
		private boolean resized;
		private boolean hidden;
		private boolean worldMap;
		private int mapToggle;
		private int mapState;
		private int stones;
		private int cycle;
		private int lastSpriteId;
		private final WorldView view = proxy(WorldView.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "getId": return WorldView.TOPLEVEL;
				case "getBaseX":
				case "getBaseY": return 3200;
				case "getSizeX":
				case "getSizeY": return 104;
				case "getPlane": return 0;
				case "isInstance": return instance;
				default: throw new AssertionError(method.getName());
			}
		});
		private final Player player = proxy(Player.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "getWorldView": return view;
				case "getCameraFocus":
				case "getLocalLocation": return LocalPoint.fromScene(10, 10, view);
				default: throw new AssertionError(method.getName());
			}
		});
		private final Widget minimap = proxy(Widget.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "isHidden": return hidden;
				case "getBounds": return new Rectangle(BOUNDS);
				case "getCanvasLocation": return new Point(BOUNDS.x, BOUNDS.y);
				case "getWidth": return BOUNDS.width;
				case "getHeight": return BOUNDS.height;
				default: throw new AssertionError(method.getName());
			}
		});
		private final Widget map = proxy(Widget.class, (ignored, method, args) ->
		{
			if ("isHidden".equals(method.getName())) return !worldMap;
			throw new AssertionError(method.getName());
		});
		private final SpritePixels sprite = proxy(SpritePixels.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "getWidth": return BOUNDS.width;
				case "getHeight": return BOUNDS.height;
				case "getPixels": return mask;
				default: throw new AssertionError(method.getName());
			}
		});
		private final Client client = proxy(Client.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "getGameState": return GameState.LOGGED_IN;
				case "getLocalPlayer":
				case "getCameraFocusEntity": return player;
				case "isResized": return resized;
				case "getCameraYawTarget": return 0;
				case "getMinimapZoom": return 4.0;
				case "getGameCycle": return cycle;
				case "getIndexSprites": return null;
				case "getSprites":
					lastSpriteId = (int) args[1];
					return new SpritePixels[]{sprite};
				case "getWidget":
					return widget(args[0] instanceof WidgetInfo ? ((WidgetInfo) args[0]).getPackedId() : (int) args[0]);
				case "getVarbitValue": return varbit((int) args[0]);
				default: throw new AssertionError(method.getName());
			}
		});
		private final ClueMinimapOverlay overlay = new ClueMinimapOverlay(client, () -> waypoint);

		private Widget widget(int id)
		{
			if (id == InterfaceID.Worldmap.MAP_CONTAINER) return map;
			int active = !resized ? InterfaceID.Toplevel.MINIMAP
				: stones == 1 ? InterfaceID.ToplevelPreEoc.MINIMAP : InterfaceID.ToplevelOsrsStretch.MINIMAP;
			return id == active ? minimap : null;
		}

		private int varbit(int id)
		{
			switch (id)
			{
				case VarbitID.RESIZABLE_STONE_ARRANGEMENT: return stones;
				case VarbitID.MINIMAP_STATE: return mapState;
				case VarbitID.MINIMAP_TOGGLE: return mapToggle;
				default: throw new AssertionError(id);
			}
		}

		private BufferedImage draw()
		{
			BufferedImage image = new BufferedImage(145, 145, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = image.createGraphics();
			Rectangle originalClip = new Rectangle(0, 0, image.getWidth(), image.getHeight());
			graphics.setClip(originalClip);
			try
			{
				assertNull(overlay.render(graphics));
				assertEquals(originalClip, graphics.getClip());
				return image;
			}
			finally
			{
				graphics.dispose();
			}
		}
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
