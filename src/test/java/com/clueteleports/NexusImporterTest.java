package com.clueteleports;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NexusImporterTest
{
	@Test
	public void closedAndHiddenMenusCannotImportStaleHouseVarbits()
	{
		Fixture fixture = new Fixture();
		fixture.varbits.put(6654, 1);
		fixture.menuPresent = false;
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
		fixture.menuPresent = true;
		fixture.menuHidden = true;
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
		assertEquals(0, fixture.cacheReads);
	}

	@Test
	public void importUsesInstalledSlotValuesAndLiveCacheNames()
	{
		Fixture fixture = new Fixture();
		fixture.slot(6654, 7, 901, "Canifis (Kharyrll)");
		fixture.slot(20120, 92, 907, "Ourania Cave");
		fixture.slot(6656, 88, 909, "Boat"); // Moving endpoint has no fixed location.

		Set<String> imported = NexusImporter.readOpenMenu(fixture.client).get();
		assertEquals(2, imported.size());
		assertTrue(imported.contains("nexus-kharyrll"));
		assertTrue(imported.contains("nexus-ourania"));
	}

	@Test
	public void diaryAlternatesAreImportedOnlyAfterARewardIsClaimed()
	{
		Fixture fixture = new Fixture();
		fixture.slot(6654, 1, 100, "Varrock");
		fixture.struct(100, "Varrock", 101, -1);
		fixture.struct(101, "Grand Exchange", -1, 100);
		assertEquals(Collections.singleton("nexus-varrock"), NexusImporter.readOpenMenu(fixture.client).get());

		fixture.varbits.put(VarbitID.VARROCK_ELITE_REWARD, 1);
		Set<String> imported = NexusImporter.readOpenMenu(fixture.client).get();
		assertEquals(2, imported.size());
		assertTrue(imported.contains("nexus-grand-exchange"));
	}

	@Test
	public void seersAndYanilleKeepTheirBaseTeleports()
	{
		Fixture fixture = new Fixture();
		fixture.slot(6654, 4, 200, "Camelot");
		fixture.struct(200, "Camelot", 201, -1);
		fixture.struct(201, "Seers' Village", -1, 200);
		fixture.slot(6655, 6, 300, "Watchtower");
		fixture.struct(300, "Watchtower", 301, -1);
		fixture.struct(301, "Yanille", -1, 300);
		fixture.varbits.put(VarbitID.KANDARIN_HARD_REWARD, 1);
		fixture.varbits.put(VarbitID.ARDOUGNE_ELITE_REWARD, 1);

		Set<String> imported = NexusImporter.readOpenMenu(fixture.client).get();
		assertEquals(4, imported.size());
		assertTrue(imported.containsAll(Arrays.asList(
			"nexus-camelot", "nexus-seers-village", "nexus-watchtower", "nexus-yanille")));
	}

	@Test
	public void alternateCacheEntryCanResolveItsPrimary()
	{
		Fixture fixture = new Fixture();
		fixture.slot(6654, 151, 101, "Grand Exchange");
		fixture.struct(101, "Grand Exchange", -1, 100);
		fixture.struct(100, "Varrock", 101, -1);
		fixture.varbits.put(VarbitID.VARROCK_MEDIUM_REWARD, 1);
		Set<String> imported = NexusImporter.readOpenMenu(fixture.client).get();
		assertTrue(imported.contains("nexus-varrock"));
		assertTrue(imported.contains("nexus-grand-exchange"));
	}

	@Test
	public void missingCacheDataDoesNotProduceAnEmptyImportThatCouldEraseSelections()
	{
		Fixture fixture = new Fixture();
		fixture.enumPresent = false;
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void missingMappingsAndStructsCannotEraseConfirmedSelections()
	{
		Fixture fixture = new Fixture();
		fixture.varbits.put(6654, 999);
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
		fixture.enumValues.put(999, 123);
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
		fixture.struct(123, "Varrock", 124, -1);
		assertFalse(NexusImporter.readOpenMenu(fixture.client).isPresent());
	}

	private static final class Fixture
	{
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final Map<Integer, Integer> enumValues = new HashMap<>();
		private final Map<Integer, StructComposition> structs = new HashMap<>();
		private boolean menuPresent = true;
		private boolean menuHidden;
		private boolean enumPresent = true;
		private int cacheReads;
		private final Widget menu = proxy(Widget.class, (proxy, method, args) ->
		{
			if (method.getName().equals("isHidden"))
			{
				return menuHidden;
			}
			throw new AssertionError("Unexpected widget call: " + method.getName());
		});
		private final EnumComposition destinationEnum = proxy(EnumComposition.class, (proxy, method, args) ->
		{
			if (method.getName().equals("getIntValue"))
			{
				return enumValues.getOrDefault((Integer) args[0], -1);
			}
			throw new AssertionError("Unexpected enum call: " + method.getName());
		});
		private final Client client = proxy(Client.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getWidget":
					return menuPresent ? menu : null;
				case "getEnum":
					assertEquals(1377, args[0]);
					cacheReads++;
					return enumPresent ? destinationEnum : null;
				case "getStructComposition":
					return structs.get((Integer) args[0]);
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				default:
					throw new AssertionError("Unexpected client call: " + method.getName());
			}
		});

		private void slot(int slotVarbit, int key, int structId, String name)
		{
			varbits.put(slotVarbit, key);
			enumValues.put(key, structId);
			struct(structId, name, -1, -1);
		}

		private void struct(int id, String name, int alternateId, int baseId)
		{
			structs.put(id, proxy(StructComposition.class, (proxy, method, args) ->
			{
				if (method.getName().equals("getStringValue"))
				{
					assertEquals(660, args[0]);
					return name;
				}
				if (method.getName().equals("getIntValue"))
				{
					return (Integer) args[0] == 679 ? alternateId : (Integer) args[0] == 680 ? baseId : -1;
				}
				throw new AssertionError("Unexpected struct call: " + method.getName());
			}));
		}
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
