package com.clueteleports.routing;

import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;

/** Immutable account evidence captured on the client thread for worker searches. */
public final class AccountRouteState
{
	private final Map<Skill, Integer> realSkills;
	private final Map<Skill, Integer> boostedSkills;
	private final Map<Quest, QuestState> quests;
	private final Map<Integer, Integer> varbits;
	private final Map<Integer, Integer> varps;
	private final Map<Integer, Integer> items;
	private final Map<Integer, Integer> equipped;
	private final boolean itemsKnown;

	AccountRouteState(Map<Skill, Integer> realSkills, Map<Skill, Integer> boostedSkills,
		Map<Quest, QuestState> quests, Map<Integer, Integer> varbits, Map<Integer, Integer> varps,
		Map<Integer, Integer> items, Map<Integer, Integer> equipped, boolean itemsKnown)
	{
		this.realSkills = Map.copyOf(realSkills);
		this.boostedSkills = Map.copyOf(boostedSkills);
		this.quests = Map.copyOf(quests);
		this.varbits = Map.copyOf(varbits);
		this.varps = Map.copyOf(varps);
		this.items = Map.copyOf(items);
		this.equipped = Map.copyOf(equipped);
		this.itemsKnown = itemsKnown;
	}

	/**
	 * Captures only the quest/variable IDs requested by the physical graph,
	 * plus both skill arrays and carried inventory/equipment. Bank items are
	 * intentionally excluded. No Client reference escapes into the snapshot.
	 */
	public static AccountRouteState capture(Client client, Collection<RouteRequirements> requirements)
	{
		if (client == null || !client.isClientThread())
		{
			throw new IllegalStateException("Route state must be captured on the client thread");
		}
		Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			int level = client.getRealSkillLevel(skill);
			if (level >= 0) real.put(skill, level);
			level = client.getBoostedSkillLevel(skill);
			if (level >= 0) boosted.put(skill, level);
		}
		Set<Quest> requestedQuests = EnumSet.noneOf(Quest.class);
		Set<Integer> requestedBits = new LinkedHashSet<>();
		Set<Integer> requestedPlayers = new LinkedHashSet<>();
		for (RouteRequirements requirement : requirements)
		{
			requestedQuests.addAll(requirement.getRequiredQuests());
			requestedBits.addAll(requirement.getRequiredVarbits());
			requestedPlayers.addAll(requirement.getRequiredVarps());
		}
		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : requestedQuests)
		{
			try
			{
				QuestState state = quest.getState(client);
				if (state != null) quests.put(quest, state);
			}
			catch (NullPointerException exception)
			{
				// An unavailable native quest state remains unknown, failing closed.
			}
		}
		Map<Integer, Integer> bits = new HashMap<>();
		for (int id : requestedBits)
		{
			if (client.getVarbit(id) != null) bits.put(id, client.getVarbitValue(id));
		}
		Map<Integer, Integer> players = new HashMap<>();
		int[] nativePlayers = client.getVarps();
		for (int id : requestedPlayers)
		{
			if (nativePlayers != null && id >= 0 && id < nativePlayers.length) players.put(id, nativePlayers[id]);
		}
		Map<Integer, Integer> carried = new HashMap<>();
		Map<Integer, Integer> worn = new HashMap<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		boolean known = addItems(inventory, carried) & addItems(equipment, worn);
		worn.forEach((id, quantity) -> carried.merge(id, quantity, AccountRouteState::sum));
		return new AccountRouteState(real, boosted, quests, bits, players, carried, worn, known);
	}

	int realLevel(Skill skill)
	{
		return realSkills.getOrDefault(skill, -1);
	}

	int boostedLevel(Skill skill)
	{
		return boostedSkills.getOrDefault(skill, -1);
	}

	QuestState questState(Quest quest)
	{
		return quests.get(quest);
	}

	Integer varbit(int id)
	{
		return varbits.get(id);
	}

	Integer varp(int id)
	{
		return varps.get(id);
	}

	boolean hasItems(Set<Integer> ids, int quantity, boolean equippedOnly)
	{
		if (!itemsKnown) return false;
		Map<Integer, Integer> owned = equippedOnly ? equipped : items;
		long count = 0;
		for (int id : ids) count += owned.getOrDefault(id, 0);
		return quantity == 0 ? count == 0 : count >= quantity;
	}

	private static boolean addItems(ItemContainer container, Map<Integer, Integer> items)
	{
		if (container == null || container.getItems() == null) return false;
		for (Item item : container.getItems())
		{
			if (item != null && item.getId() > 0 && item.getQuantity() > 0)
			{
				items.merge(item.getId(), item.getQuantity(), AccountRouteState::sum);
			}
		}
		return true;
	}

	private static int sum(int first, int second)
	{
		return (int) Math.min(Integer.MAX_VALUE, (long) first + second);
	}

	@Override
	public boolean equals(Object object)
	{
		if (this == object) return true;
		if (!(object instanceof AccountRouteState)) return false;
		AccountRouteState other = (AccountRouteState) object;
		return itemsKnown == other.itemsKnown && realSkills.equals(other.realSkills)
			&& boostedSkills.equals(other.boostedSkills) && quests.equals(other.quests)
			&& varbits.equals(other.varbits) && varps.equals(other.varps)
			&& items.equals(other.items) && equipped.equals(other.equipped);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(realSkills, boostedSkills, quests, varbits, varps, items, equipped, itemsKnown);
	}
}
