package com.clueteleports;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.SpritePixels;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClueMinimapOverlayTest
{
	@Test
	public void fitsTheWholeYellowArrowInsideTheNativeRim()
	{
		Fixture fixture = new Fixture();
		BufferedImage complete = fixture.draw();
		assertEquals(SpriteID.FIXED_MAP_MASK, fixture.lastSpriteId);
		Rectangle normalArrow = paintedBounds(complete);
		assertTrue(normalArrow.x > 100);
		assertTrue(Arrays.stream(pixels(complete)).anyMatch(pixel -> pixel == Color.YELLOW.getRGB()));

		Fixture clipped = new Fixture();
		int inset = 6;
		for (int y = 0; y < Fixture.BOUNDS.height; y++)
		{
			int end = (y + 1) * Fixture.BOUNDS.width;
			Arrays.fill(clipped.mask, end - inset, end, 1);
		}
		Rectangle insetArrow = paintedBounds(clipped.draw());
		assertEquals(normalArrow.width, insetArrow.width);
		assertEquals(normalArrow.height, insetArrow.height);
		assertTrue(insetArrow.x + insetArrow.width <= Fixture.BOUNDS.x + Fixture.BOUNDS.width - inset);
		assertTrue(insetArrow.x < normalArrow.x);
	}

	@Test
	public void keepsTheArrowOnTheSameRimPositionAsTheWaypointGetsCloser()
	{
		Fixture fixture = new Fixture();
		fixture.waypoint = new WorldPoint(3310, 3210, 0);
		int[] distant = pixels(fixture.draw());
		assertTrue(Arrays.stream(distant).anyMatch(pixel -> pixel != 0));
		fixture.waypoint = new WorldPoint(3213, 3210, 0);
		assertArrayEquals(distant, pixels(fixture.draw()));
		fixture.playerTile = new WorldPoint(3212, 3210, 0);
		assertArrayEquals(distant, pixels(fixture.draw()));
	}

	@Test
	public void rotatesTheArrowToTheCorrespondingRimWithTheMinimap()
	{
		Fixture fixture = new Fixture();
		Rectangle east = paintedBounds(fixture.draw());
		assertTrue(east.x > 100 && east.y >= 65 && east.y + east.height <= 80);
		fixture.yaw = 4096;
		Rectangle south = paintedBounds(fixture.draw());
		assertTrue(south.y > 100 && south.x >= 65 && south.x + south.width <= 80);
	}

	@Test
	public void blinksAtTheSamePositionInBothResizableLayouts()
	{
		Fixture fixture = new Fixture();
		fixture.resized = true;
		int[] visible = pixels(fixture.draw());
		assertTrue(Arrays.stream(visible).anyMatch(pixel -> pixel != 0));
		assertEquals(SpriteID.RESIZE_MAP_MASK, fixture.lastSpriteId);
		fixture.stones = 1;
		fixture.cycle = 40;
		assertBlank(fixture.draw());
		fixture.cycle = 60;
		assertArrayEquals(visible, pixels(fixture.draw()));
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
	public void requiresAnUnreachedWaypointOnTheCurrentWorldViewAndPlane()
	{
		Fixture fixture = new Fixture();
		fixture.waypoint = null;
		assertBlank(fixture.draw());
		fixture.waypoint = fixture.playerTile;
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3213, 3210, 1);
		assertBlank(fixture.draw());
		fixture.waypoint = new WorldPoint(3213, 3210, 0);
		fixture.instance = true;
		assertBlank(fixture.draw());
	}

	private static Rectangle paintedBounds(BufferedImage image)
	{
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == 0) continue;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}
		assertTrue("Expected an arrow", maxX >= minX);
		return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static int[] pixels(BufferedImage image)
	{
		return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	private static void assertBlank(BufferedImage image)
	{
		assertTrue(Arrays.stream(pixels(image)).allMatch(pixel -> pixel == 0));
	}

	private static final class Fixture
	{
		private static final Rectangle BOUNDS = new Rectangle(20, 20, 104, 104);
		private final int[] mask = new int[BOUNDS.width * BOUNDS.height];
		private WorldPoint waypoint = new WorldPoint(3213, 3210, 0);
		private WorldPoint playerTile = new WorldPoint(3210, 3210, 0);
		private boolean instance;
		private boolean resized;
		private boolean hidden;
		private boolean worldMap;
		private int mapToggle;
		private int mapState;
		private int stones;
		private int cycle;
		private int yaw;
		private int lastSpriteId;
		private final WorldView view = proxy(WorldView.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "getId": return WorldView.TOPLEVEL;
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
				case "getWorldLocation": return playerTile;
				default: throw new AssertionError(method.getName());
			}
		});
		private final Widget minimap = proxy(Widget.class, (ignored, method, args) ->
		{
			switch (method.getName())
			{
				case "isHidden": return hidden;
				case "getBounds": return new Rectangle(BOUNDS);
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
				case "getLocalPlayer": return player;
				case "isResized": return resized;
				case "getCameraYawTarget": return yaw;
				case "getGameCycle": return cycle;
				case "getIndexSprites": return null;
				case "getSprites":
					lastSpriteId = (int) args[1];
					return new SpritePixels[]{sprite};
				case "getWidget": return widget((int) args[0]);
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
