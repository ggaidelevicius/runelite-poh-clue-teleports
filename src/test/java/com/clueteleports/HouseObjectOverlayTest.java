package com.clueteleports;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HouseObjectOverlayTest
{
	private final Fixture fixture = new Fixture();

	@Test
	public void outlinesOnlyTheFixtureOfferingTheRecommendedDestination()
	{
		GameObject nexus = fixture.game(ObjectID.POH_TELENEXUS_3);
		GameObject box = fixture.game(ObjectID.POH_JEWELLERY_BOX_3);
		fixture.place(0, 1, 1, nexus);
		fixture.place(0, 2, 2, box);

		assertEquals(List.of(box), targets(TeleportSource.JEWELLERY_BOX, "jewellery-fishing-guild"));
		assertEquals(List.of(nexus), targets(TeleportSource.PORTAL_NEXUS, "nexus-varrock"));
	}

	@Test
	public void jewelleryBoxMustMeetTheRecommendedDestinationsTier()
	{
		GameObject basic = fixture.game(ObjectID.POH_JEWELLERY_BOX_1_BASE);
		fixture.place(0, 1, 1, basic);
		assertTrue(targets(TeleportSource.JEWELLERY_BOX, "jewellery-fishing-guild").isEmpty());
		assertEquals(List.of(basic), targets(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars"));

		GameObject fancy = fixture.game(ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM);
		fixture.place(0, 1, 1, fancy);
		assertEquals(List.of(fancy), targets(TeleportSource.JEWELLERY_BOX, "jewellery-fishing-guild"));
	}

	@Test
	public void choosesTheRightPortalAmongSeveralChamberPortals()
	{
		GameObject lumbridge = fixture.game(ObjectID.POH_PORTAL_TEAK_LUMBRIDGE);
		GameObject falador = fixture.game(ObjectID.POH_PORTAL_MAG_FALADOR);
		fixture.place(0, 0, 0, lumbridge);
		fixture.place(0, 3, 0, falador);

		assertEquals(List.of(falador), targets(TeleportSource.PORTAL_CHAMBER, "portal-falador"));
		assertEquals(List.of(lumbridge), targets(TeleportSource.PORTAL_CHAMBER, "portal-lumbridge"));
	}

	@Test
	public void diaryAlternativePortalNeedsItsCollectedReward()
	{
		int raw = 999002;
		fixture.definitions.put(raw, definition(raw, true, definition(ObjectID.POH_PORTAL_TEAK_VARROCK_GE1ST)));
		GameObject portal = game(raw);
		fixture.place(0, 0, 0, portal);

		assertTrue(targets(TeleportSource.PORTAL_CHAMBER, "portal-grand-exchange").isEmpty());
		fixture.varbits.put(VarbitID.VARROCK_MEDIUM_REWARD, 1);
		fixture.tick++;
		assertEquals(List.of(portal), targets(TeleportSource.PORTAL_CHAMBER, "portal-grand-exchange"));
	}

	@Test
	public void outlinesWallAndDecorativeMountsAndCombinedRingOnce()
	{
		WallObject mythical = fixture.wall(ObjectID.POH_TROPHY_MYTHICAL_CAPE);
		DecorativeObject glory = fixture.decorative(ObjectID.POH_TROPHY_AMULETOFGLORY_4);
		GameObject ring = fixture.game(ObjectID.POH_SPIRIT_RING);
		fixture.tiles[0][0][0] = tile(new GameObject[]{ring}, mythical, glory);
		// A large object is listed on every tile it covers.
		fixture.tiles[0][1][0] = tile(new GameObject[]{ring}, null, null);

		assertEquals(List.of(mythical), targets(TeleportSource.MOUNTED_MYTHICAL_CAPE, "mythical-myths-guild"));
		assertEquals(List.of(glory), targets(TeleportSource.MOUNTED_GLORY, "glory-karamja"));
		assertEquals(List.of(ring), targets(TeleportSource.FAIRY_RING, "fairy-ckr"));
		assertEquals(List.of(ring), targets(TeleportSource.SPIRIT_TREE, "tree-gnome-stronghold"));
	}

	@Test
	public void outlinesObeliskAndExitPortalButNeverForAnotherSource()
	{
		GameObject obelisk = fixture.game(ObjectID.POH_WILDERNESS_OBELISK);
		GameObject exit = fixture.game(ObjectID.POH_EXIT_PORTAL);
		fixture.place(0, 0, 0, obelisk);
		fixture.place(0, 1, 0, exit);

		assertEquals(List.of(obelisk), targets(TeleportSource.WILDERNESS_OBELISK, "obelisk-44"));
		assertEquals(List.of(exit), targets(TeleportSource.HOUSE_EXIT, "exit-rimmington"));
		assertTrue(targets(TeleportSource.MOUNTED_GLORY, "glory-karamja").isEmpty());
	}

	@Test
	public void unbuiltOrUnresolvedObjectsAreNeverOutlined()
	{
		fixture.place(0, 0, 0, game(ObjectID.POH_TELENEXUS_3));
		int raw = 999003;
		fixture.definitions.put(raw, definition(raw, true, null));
		fixture.place(0, 1, 0, game(raw));

		assertTrue(targets(TeleportSource.PORTAL_NEXUS, "nexus-varrock").isEmpty());
	}

	@Test
	public void noOutlineOutsideTheOwnHouseOrWithoutARecommendation()
	{
		fixture.place(0, 0, 0, fixture.game(ObjectID.POH_TELENEXUS_3));
		HouseObjectOverlay overlay = fixture.overlay();

		assertTrue(overlay.targets(null).isEmpty());
		assertTrue(overlay.targets(new HouseLearningOverlay.Guidance(false, Collections.emptySet(),
			TeleportSource.PORTAL_NEXUS, "nexus-varrock")).isEmpty());
		assertTrue(overlay.targets(new HouseLearningOverlay.Guidance(true, Collections.emptySet(),
			null, null)).isEmpty());
	}

	@Test
	public void onlyTheCurrentFloorIsOutlined()
	{
		GameObject downstairs = fixture.game(ObjectID.POH_JEWELLERY_BOX_1);
		fixture.place(1, 0, 0, downstairs);
		assertTrue(targets(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars").isEmpty());

		fixture.plane = 1;
		assertEquals(List.of(downstairs), targets(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars"));
	}

	@Test
	public void rescansOnlyWhenTheTickOrRecommendationChanges()
	{
		fixture.place(0, 0, 0, fixture.game(ObjectID.POH_TELENEXUS_3));
		HouseObjectOverlay overlay = fixture.overlay();
		HouseLearningOverlay.Guidance nexus = guidance(TeleportSource.PORTAL_NEXUS, "nexus-varrock");
		List<TileObject> first = overlay.targets(nexus);
		fixture.tiles[0][0][0] = tile(null, null, null);

		assertSame(first, overlay.targets(nexus));
		assertTrue(overlay.targets(guidance(TeleportSource.PORTAL_NEXUS, "nexus-varrock")).isEmpty());
		fixture.place(0, 0, 0, fixture.game(ObjectID.POH_TELENEXUS_3));
		fixture.tick++;
		assertEquals(1, overlay.targets(nexus).size());
	}

	@Test
	public void renderPaintsWithTheConfiguredColourAndStopsWhenDisabled()
	{
		GameObject box = fixture.game(ObjectID.POH_JEWELLERY_BOX_2);
		fixture.place(0, 0, 0, box);
		fixture.guidance = guidance(TeleportSource.JEWELLERY_BOX, "jewellery-fishing-guild");
		HouseObjectOverlay overlay = fixture.overlay();
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

		overlay.render(image.createGraphics());
		assertEquals(List.of(box), fixture.painted);
		assertEquals(List.of(Color.MAGENTA), fixture.paintedColors);

		fixture.color = null;
		overlay.render(image.createGraphics());
		assertEquals(1, fixture.painted.size());
	}

	private List<TileObject> targets(TeleportSource source, String destination)
	{
		return fixture.overlay().targets(guidance(source, destination));
	}

	private static HouseLearningOverlay.Guidance guidance(TeleportSource source, String destination)
	{
		return new HouseLearningOverlay.Guidance(true, Collections.emptySet(), source, destination);
	}

	private static Tile tile(GameObject[] games, WallObject wall, DecorativeObject decorative)
	{
		return proxy(Tile.class, (name, args) ->
		{
			switch (name)
			{
				case "getGameObjects": return games;
				case "getWallObject": return wall;
				case "getDecorativeObject": return decorative;
				default: throw new UnsupportedOperationException(name);
			}
		});
	}

	private static GameObject game(int id)
	{
		return proxy(GameObject.class, (name, args) ->
		{
			if ("getId".equals(name)) return id;
			throw new UnsupportedOperationException(name);
		});
	}

	private static ObjectComposition definition(int id)
	{
		return definition(id, false, null);
	}

	private static ObjectComposition definition(int id, boolean transformed, ObjectComposition live)
	{
		return proxy(ObjectComposition.class, (name, args) ->
		{
			switch (name)
			{
				case "getId": return id;
				case "getImpostorIds": return transformed ? new int[]{-1, id} : null;
				case "getImpostor": return live;
				case "getActions": return new String[0];
				default: throw new UnsupportedOperationException(name);
			}
		});
	}

	private static <T> T proxy(Class<T> type, Calls calls)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) ->
			{
				// Identity semantics let the overlay deduplicate large objects.
				if ("equals".equals(method.getName())) return instance == args[0];
				if ("hashCode".equals(method.getName())) return System.identityHashCode(instance);
				return calls.call(method.getName(), args);
			}));
	}

	private interface Calls
	{
		Object call(String name, Object[] args);
	}

	private static final class Fixture
	{
		private final Tile[][][] tiles = new Tile[2][4][4];
		private final Map<Integer, ObjectComposition> definitions = new HashMap<>();
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final List<TileObject> painted = new ArrayList<>();
		private final List<Color> paintedColors = new ArrayList<>();
		private int plane;
		private int tick;
		private Color color = Color.MAGENTA;
		private HouseLearningOverlay.Guidance guidance;
		private final Scene scene = proxy(Scene.class, (name, args) ->
		{
			if ("getTiles".equals(name)) return tiles;
			throw new UnsupportedOperationException(name);
		});
		private final WorldView world = proxy(WorldView.class, (name, args) ->
		{
			switch (name)
			{
				case "getScene": return scene;
				case "getPlane": return plane;
				default: throw new UnsupportedOperationException(name);
			}
		});
		private final Client client = proxy(Client.class, (name, args) ->
		{
			switch (name)
			{
				case "getTopLevelWorldView": return world;
				case "getTickCount": return tick;
				case "getObjectDefinition": return definitions.get(args[0]);
				case "getVarbitValue": return varbits.getOrDefault(args[0], 0);
				default: throw new UnsupportedOperationException(name);
			}
		});

		private HouseObjectOverlay overlay()
		{
			return new HouseObjectOverlay(client, () -> guidance, () -> color, (object, outline) ->
			{
				painted.add(object);
				paintedColors.add(outline);
			});
		}

		private void place(int level, int x, int y, GameObject object)
		{
			tiles[level][x][y] = tile(new GameObject[]{object}, null, null);
		}

		private GameObject game(int id)
		{
			definitions.put(id, definition(id));
			return HouseObjectOverlayTest.game(id);
		}

		private WallObject wall(int id)
		{
			definitions.put(id, definition(id));
			return proxy(WallObject.class, (name, args) ->
			{
				if ("getId".equals(name)) return id;
				throw new UnsupportedOperationException(name);
			});
		}

		private DecorativeObject decorative(int id)
		{
			definitions.put(id, definition(id));
			return proxy(DecorativeObject.class, (name, args) ->
			{
				if ("getId".equals(name)) return id;
				throw new UnsupportedOperationException(name);
			});
		}
	}
}
