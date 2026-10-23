package com.clueteleports.routing;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.VarbitComposition;
import net.runelite.api.gameval.InventoryID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AccountRouteStateTest
{
	private final Fixture fixture = new Fixture();

	@Test(expected = IllegalStateException.class)
	public void captureMustRunOnClientThread()
	{
		fixture.clientThread = false;
		AccountRouteState.capture(fixture.client, List.of(RouteRequirements.none()));
	}

	@Test
	public void capturesNativeRequirementsAndReadyInventoryEquipment()
	{
		RouteRequirements required = required("70 Agility", "CROSSBOW=1&MITH_GRAPPLE=1", "Tree Gnome Village", "10=1", "20>3");
		fixture.real.put(Skill.AGILITY, 65);
		fixture.boosted.put(Skill.AGILITY, 70);
		fixture.bits.put(10, 1);
		fixture.nativeVarps[20] = 4;
		fixture.finishedQuests.add(Quest.TREE_GNOME_VILLAGE.getId());
		fixture.inventory = items(new Item(9419, 1));
		fixture.equipment = items(new Item(837, 1));

		AccountRouteState state = AccountRouteState.capture(fixture.client, List.of(required));

		assertTrue(required.isSatisfied(state));
		assertEquals(Set.of(10), fixture.readVarbits);
		assertEquals(Set.of(Quest.TREE_GNOME_VILLAGE.getId()), fixture.readQuests);
		assertEquals(Set.of(InventoryID.INV, InventoryID.WORN), fixture.readContainers);
	}

	@Test
	public void snapshotRemainsIndependentOfLaterClientChangesAndNoClientCallsEscape()
	{
		RouteRequirements required = required("70 Agility", "995=5", "", "10=1", "20=4");
		fixture.boosted.put(Skill.AGILITY, 70);
		fixture.bits.put(10, 1);
		fixture.nativeVarps[20] = 4;
		fixture.inventory = items(new Item(995, 5));
		AccountRouteState state = AccountRouteState.capture(fixture.client, List.of(required));
		fixture.boosted.clear();
		fixture.bits.clear();
		fixture.nativeVarps[20] = 0;
		fixture.inventory = items();
		fixture.allowClientCalls = false;

		assertTrue(required.isSatisfied(state));
		assertEquals(state, state);
		state.hashCode();
	}

	@Test
	public void missingVarbitDefinitionNeverLooksLikeKnownZero()
	{
		RouteRequirements required = required("", "", "", "10=0", "");
		assertFalse(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
		fixture.bits.put(10, 0);
		assertTrue(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
	}

	@Test
	public void outOfRangeOrMissingVarpsFailClosed()
	{
		RouteRequirements required = required("", "", "", "", "9000=0");
		assertFalse(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
		fixture.nativeVarps = null;
		assertFalse(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
	}

	@Test
	public void unknownItemContainersCannotProveAnItemAbsent()
	{
		RouteRequirements absence = required("", "2376=0", "", "", "");
		fixture.inventory = null;
		assertFalse(absence.isSatisfied(AccountRouteState.capture(fixture.client, List.of(absence))));
		fixture.inventory = items();
		fixture.equipment = null;
		assertFalse(absence.isSatisfied(AccountRouteState.capture(fixture.client, List.of(absence))));
	}

	@Test
	public void quantitiesCombineInventoryAndEquipmentAndIgnoreEmptySlots()
	{
		RouteRequirements required = required("", "995=5", "", "", "");
		fixture.inventory = items(null, new Item(-1, 0), new Item(995, 3));
		fixture.equipment = items(new Item(995, 2));
		assertTrue(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
	}

	@Test
	public void unavailableQuestStateFailsClosed()
	{
		RouteRequirements required = required("", "", "Tree Gnome Village", "", "");
		fixture.missingQuestStack = true;
		assertFalse(required.isSatisfied(AccountRouteState.capture(fixture.client, List.of(required))));
	}

	@Test
	public void equalCapturedStatesHaveEqualHashCodesAndChangesInvalidateCacheKey()
	{
		RouteRequirements required = required("70 Agility", "995=5", "", "10=1", "");
		fixture.boosted.put(Skill.AGILITY, 70);
		fixture.bits.put(10, 1);
		fixture.inventory = items(new Item(995, 5));
		AccountRouteState first = AccountRouteState.capture(fixture.client, List.of(required));
		AccountRouteState same = AccountRouteState.capture(fixture.client, List.of(required));
		assertEquals(first, same);
		assertEquals(first.hashCode(), same.hashCode());
		fixture.boosted.put(Skill.AGILITY, 69);
		assertNotEquals(first, AccountRouteState.capture(fixture.client, List.of(required)));
		fixture.boosted.put(Skill.AGILITY, 70);
		fixture.bits.put(10, 0);
		assertNotEquals(first, AccountRouteState.capture(fixture.client, List.of(required)));
	}

	@Test
	public void equipmentReadinessParticipatesInEqualityEvenWithSameTotalItems()
	{
		RouteRequirements required = required("", "CROSSBOW=1", "", "", "");
		fixture.inventory = items(new Item(837, 1));
		AccountRouteState carried = AccountRouteState.capture(fixture.client, List.of(required));
		fixture.inventory = items();
		fixture.equipment = items(new Item(837, 1));
		AccountRouteState worn = AccountRouteState.capture(fixture.client, List.of(required));

		assertNotEquals(carried, worn);
		assertFalse(required.isSatisfied(carried));
		assertTrue(required.isSatisfied(worn));
	}

	@Test
	public void emptyGraphsDoNotCaptureUnrelatedQuestOrNativeVars()
	{
		AccountRouteState.capture(fixture.client, List.of(RouteRequirements.none()));
		assertTrue(fixture.readQuests.isEmpty());
		assertTrue(fixture.readVarbits.isEmpty());
	}

	private static RouteRequirements required(String skills, String items, String quests, String bits, String players)
	{
		return RouteRequirements.parse(skills, items, quests, bits, players).orElseThrow();
	}

	private static ItemContainer items(Item... contents)
	{
		return proxy(ItemContainer.class, (name, args) ->
		{
			if ("getItems".equals(name)) return contents;
			throw new UnsupportedOperationException(name);
		});
	}

	private static <T> T proxy(Class<T> type, Calls calls)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> calls.call(method.getName(), args)));
	}

	private interface Calls
	{
		Object call(String name, Object[] args);
	}

	private static final class Fixture
	{
		private boolean clientThread = true;
		private boolean allowClientCalls = true;
		private boolean missingQuestStack;
		private final Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		private final Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		private final Map<Integer, Integer> bits = new HashMap<>();
		private int[] nativeVarps = new int[100];
		private final Set<Integer> finishedQuests = new HashSet<>();
		private final Set<Integer> readQuests = new HashSet<>();
		private final Set<Integer> readVarbits = new HashSet<>();
		private final Set<Integer> readContainers = new HashSet<>();
		private final int[] intStack = new int[1];
		private ItemContainer inventory = items();
		private ItemContainer equipment = items();
		private final Client client = proxy(Client.class, (name, args) ->
		{
			if (!allowClientCalls) throw new AssertionError("Client call after capture: " + name);
			switch (name)
			{
				case "isClientThread": return clientThread;
				case "getRealSkillLevel": return real.getOrDefault(args[0], 1);
				case "getBoostedSkillLevel": return boosted.getOrDefault(args[0], 1);
				case "getVarbit":
					readVarbits.add((Integer) args[0]);
					return bits.containsKey(args[0]) ? proxy(VarbitComposition.class,
						(method, arguments) -> { throw new UnsupportedOperationException(method); }) : null;
				case "getVarbitValue": return bits.get(args[0]);
				case "getVarps": return nativeVarps;
				case "runScript":
					Object[] script = (Object[]) args[0];
					int questId = (Integer) script[1];
					readQuests.add(questId);
					intStack[0] = finishedQuests.contains(questId) ? 2 : 1;
					return null;
				case "getIntStack": return missingQuestStack ? null : intStack;
				case "getItemContainer":
					readContainers.add((Integer) args[0]);
					if ((Integer) args[0] == InventoryID.INV) return inventory;
					if ((Integer) args[0] == InventoryID.WORN) return equipment;
					throw new AssertionError("Unexpected bank/container read: " + args[0]);
				default: throw new UnsupportedOperationException(name);
			}
		});
	}
}
