package com.clueteleports.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;

/** Fail-closed account requirements for physical actions in the pinned route data. */
public final class RouteRequirements
{
	private static final Pattern SKILL = Pattern.compile("([0-9]+)\\s+(.+)");
	private static final Pattern VARIABLE = Pattern.compile("([0-9]+)\\s*([=><&])\\s*(-?[0-9]+)");
	private static final Pattern ITEM = Pattern.compile("([A-Z_0-9]+)=([0-9]+)");
	private static final Map<String, Set<Integer>> ITEM_ALIASES = loadAliases();
	private static final RouteRequirements NONE = new RouteRequirements(Collections.emptyMap(),
		Collections.emptySet(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	private final Map<Skill, Integer> skills;
	private final Set<Quest> quests;
	private final List<Variable> varbits;
	private final List<Variable> varps;
	private final List<List<ItemChoice>> items;
	private final Set<Integer> requiredVarbits;
	private final Set<Integer> requiredVarps;

	private RouteRequirements(Map<Skill, Integer> skills, Set<Quest> quests,
		List<Variable> varbits, List<Variable> varps, List<List<ItemChoice>> items)
	{
		this.skills = Map.copyOf(skills);
		this.quests = Set.copyOf(quests);
		this.varbits = List.copyOf(varbits);
		this.varps = List.copyOf(varps);
		this.items = List.copyOf(items);
		Set<Integer> bits = new LinkedHashSet<>();
		varbits.forEach(variable -> bits.add(variable.id));
		for (List<ItemChoice> alternatives : items)
		{
			if (alternatives.stream().anyMatch(item -> "UNLOCK_KARAMJA_ELITE".equals(item.unlock)))
			{
				bits.add(VarbitID.KARAMJA_ELITE_REWARD);
			}
		}
		requiredVarbits = Collections.unmodifiableSet(bits);
		Set<Integer> players = new LinkedHashSet<>();
		varps.forEach(variable -> players.add(variable.id));
		requiredVarps = Collections.unmodifiableSet(players);
	}

	public static RouteRequirements none()
	{
		return NONE;
	}

	/**
	 * Grammar follows the pinned Shortest Path physical TSV columns. Unknown
	 * quests, aliases, operators, and malformed terms reject the complete row;
	 * they never silently remove its account requirement.
	 */
	public static Optional<RouteRequirements> parse(String skillText, String itemText,
		String questText, String varbitText, String varpText)
	{
		try
		{
			Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
			for (String term : terms(skillText))
			{
				Matcher matcher = SKILL.matcher(term);
				if (!matcher.matches()) return Optional.empty();
				Skill matched = null;
				for (Skill skill : Skill.values())
				{
					if (skill.getName().equalsIgnoreCase(matcher.group(2))) matched = skill;
				}
				if (matched == null) return Optional.empty();
				skills.merge(matched, Integer.parseInt(matcher.group(1)), Math::max);
			}
			Set<Quest> quests = EnumSet.noneOf(Quest.class);
			for (String term : terms(questText))
			{
				Quest matched = null;
				for (Quest quest : Quest.values())
				{
					if (quest.getName().equalsIgnoreCase(term)) matched = quest;
				}
				if (matched == null) return Optional.empty();
				quests.add(matched);
			}
			List<Variable> bits = parseVariables(varbitText);
			List<Variable> players = parseVariables(varpText);
			List<List<ItemChoice>> items = parseItems(itemText);
			if (skills.isEmpty() && quests.isEmpty() && bits.isEmpty() && players.isEmpty() && items.isEmpty())
			{
				return Optional.of(NONE);
			}
			return Optional.of(new RouteRequirements(skills, quests, bits, players, items));
		}
		catch (IllegalArgumentException exception)
		{
			return Optional.empty();
		}
	}

	public Set<Quest> getRequiredQuests()
	{
		return quests;
	}

	public Set<Integer> getRequiredVarbits()
	{
		return requiredVarbits;
	}

	public Set<Integer> getRequiredVarps()
	{
		return requiredVarps;
	}

	public boolean isSatisfied(AccountRouteState state)
	{
		if (state == null) return false;
		for (Map.Entry<Skill, Integer> skill : skills.entrySet())
		{
			// The Prayer Guild checks the level, not remaining prayer points.
			// Other physical skill gates use the currently effective level.
			int level = skill.getKey() == Skill.PRAYER
				? state.realLevel(skill.getKey()) : state.boostedLevel(skill.getKey());
			if (level < skill.getValue()) return false;
		}
		for (Quest quest : quests)
		{
			if (state.questState(quest) != QuestState.FINISHED) return false;
		}
		for (Variable variable : varbits)
		{
			if (!variable.satisfied(state.varbit(variable.id))) return false;
		}
		for (Variable variable : varps)
		{
			if (!variable.satisfied(state.varp(variable.id))) return false;
		}
		for (List<ItemChoice> alternatives : items)
		{
			boolean matched = false;
			for (ItemChoice alternative : alternatives)
			{
				if (alternative.satisfied(state))
				{
					matched = true;
					break;
				}
			}
			if (!matched) return false;
		}
		return true;
	}

	private static String[] terms(String text)
	{
		if (text == null || text.trim().isEmpty()) return new String[0];
		String[] terms = text.trim().split(";");
		if (terms.length == 0) throw new IllegalArgumentException("Empty requirement");
		for (int i = 0; i < terms.length; i++)
		{
			terms[i] = terms[i].trim();
			if (terms[i].isEmpty()) throw new IllegalArgumentException("Empty requirement term");
		}
		return terms;
	}

	private static List<Variable> parseVariables(String text)
	{
		List<Variable> variables = new ArrayList<>();
		for (String term : terms(text))
		{
			Matcher matcher = VARIABLE.matcher(term);
			if (!matcher.matches()) throw new IllegalArgumentException("Unknown variable requirement");
			variables.add(new Variable(Integer.parseInt(matcher.group(1)), matcher.group(2).charAt(0),
				Integer.parseInt(matcher.group(3))));
		}
		return variables;
	}

	private static List<List<ItemChoice>> parseItems(String text)
	{
		if (text == null || text.trim().isEmpty()) return Collections.emptyList();
		String normalized = text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
			.replace("&&", "&").replace("||", "|");
		List<List<ItemChoice>> groups = new ArrayList<>();
		for (String conjunction : normalized.split("&", -1))
		{
			List<ItemChoice> choices = new ArrayList<>();
			for (String choice : conjunction.split("\\|", -1))
			{
				Matcher matcher = ITEM.matcher(choice);
				if (!matcher.matches()) throw new IllegalArgumentException("Unknown item requirement");
				String name = matcher.group(1);
				int quantity = Integer.parseInt(matcher.group(2));
				if (name.startsWith("UNLOCK_"))
				{
					if (quantity < 1 || !("UNLOCK_KARAMJA_ELITE".equals(name) || "UNLOCK_MOR_UL_REK".equals(name)))
					{
						throw new IllegalArgumentException("Unknown unlock");
					}
					choices.add(new ItemChoice(Collections.emptySet(), quantity, name, false));
					continue;
				}
				Set<Integer> ids = ITEM_ALIASES.get(name);
				if (ids == null)
				{
					int id = Integer.parseInt(name);
					if (id <= 0) throw new IllegalArgumentException("Invalid item ID");
					ids = Set.of(id);
				}
				// Having a high-tier crossbow in a backpack does not establish
				// that this account can equip it. Require ready grapple gear.
				choices.add(new ItemChoice(ids, quantity, null, quantity > 0 && "CROSSBOW".equals(name)));
			}
			groups.add(List.copyOf(choices));
		}
		return groups;
	}

	private static Map<String, Set<Integer>> loadAliases()
	{
		Map<String, Set<Integer>> aliases = new HashMap<>();
		try (InputStream stream = RouteRequirements.class.getResourceAsStream("/routes/item-variations.tsv"))
		{
			if (stream == null) return Collections.emptyMap();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.isBlank() || line.startsWith("#")) continue;
					String[] columns = line.split("\\t", -1);
					if (columns.length < 2) continue;
					Set<Integer> ids = new LinkedHashSet<>();
					for (String id : columns[1].split(",")) ids.add(Integer.parseInt(id));
					aliases.put(columns[0], Set.copyOf(ids));
				}
			}
		}
		catch (IOException | NumberFormatException exception)
		{
			// Missing or malformed aliases never turn into no-requirement rows.
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(aliases);
	}

	private static final class ItemChoice
	{
		private final Set<Integer> ids;
		private final int quantity;
		private final String unlock;
		private final boolean equippedOnly;

		private ItemChoice(Set<Integer> ids, int quantity, String unlock, boolean equippedOnly)
		{
			this.ids = ids;
			this.quantity = quantity;
			this.unlock = unlock;
			this.equippedOnly = equippedOnly;
		}

		private boolean satisfied(AccountRouteState state)
		{
			if (unlock != null)
			{
				return "UNLOCK_KARAMJA_ELITE".equals(unlock)
					&& Integer.valueOf(1).equals(state.varbit(VarbitID.KARAMJA_ELITE_REWARD));
			}
			return state.hasItems(ids, quantity, equippedOnly);
		}
	}

	private static final class Variable
	{
		private final int id;
		private final char operator;
		private final int value;

		private Variable(int id, char operator, int value)
		{
			this.id = id;
			this.operator = operator;
			this.value = value;
		}

		private boolean satisfied(Integer current)
		{
			if (current == null) return false;
			switch (operator)
			{
				case '=': return current == value;
				case '>': return current > value;
				case '<': return current < value;
				case '&': return (current & value) != 0;
				default: return false;
			}
		}
	}
}
