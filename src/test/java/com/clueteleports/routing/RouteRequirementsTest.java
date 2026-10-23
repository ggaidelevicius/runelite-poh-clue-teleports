package com.clueteleports.routing;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteRequirementsTest
{
	private final State state = new State();

	@Test
	public void emptyRequirementsAcceptAnOtherwiseUnknownSnapshot()
	{
		assertTrue(RouteRequirements.none().isSatisfied(state.build()));
		assertTrue(RouteRequirements.parse(null, "", " ", null, "").orElseThrow().isSatisfied(state.build()));
		assertFalse(RouteRequirements.none().isSatisfied(null));
	}

	@Test
	public void agilityUsesCurrentBoostedLevelAndReactsToDrain()
	{
		RouteRequirements requirement = required("70 Agility", "", "", "", "");
		state.real.put(Skill.AGILITY, 65);
		state.boosted.put(Skill.AGILITY, 70);
		assertTrue(requirement.isSatisfied(state.build()));
		state.real.put(Skill.AGILITY, 75);
		state.boosted.put(Skill.AGILITY, 69);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void prayerGuildChecksLevelRatherThanRemainingPrayerPoints()
	{
		RouteRequirements requirement = required("31 Prayer", "", "", "", "");
		state.real.put(Skill.PRAYER, 31);
		state.boosted.put(Skill.PRAYER, 0);
		assertTrue(requirement.isSatisfied(state.build()));
		state.real.put(Skill.PRAYER, 30);
		state.boosted.put(Skill.PRAYER, 31);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void everyDeclaredSkillMustBeMetAndUnknownSkillsCannotPass()
	{
		RouteRequirements requirement = required("8 Agility;37 Ranged;19 Strength", "", "", "", "");
		state.boosted.put(Skill.AGILITY, 8);
		state.boosted.put(Skill.RANGED, 37);
		assertFalse(requirement.isSatisfied(state.build()));
		state.boosted.put(Skill.STRENGTH, 19);
		assertTrue(requirement.isSatisfied(state.build()));
		assertFalse(RouteRequirements.parse("70 Unknown", "", "", "", "").isPresent());
		assertFalse(RouteRequirements.parse("Agility 70", "", "", "", "").isPresent());
	}

	@Test
	public void questsMustBeKnownAndFinished()
	{
		RouteRequirements requirement = required("", "", "Tree Gnome Village", "", "");
		assertFalse(requirement.isSatisfied(state.build()));
		state.quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.IN_PROGRESS);
		assertFalse(requirement.isSatisfied(state.build()));
		state.quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		assertTrue(requirement.isSatisfied(state.build()));
		assertFalse(RouteRequirements.parse("", "", "Unknown quest", "", "").isPresent());
	}

	@Test
	public void allQuestTermsAreRequired()
	{
		RouteRequirements requirement = required("", "", "Tree Gnome Village;The Grand Tree", "", "");
		state.quests.put(Quest.TREE_GNOME_VILLAGE, QuestState.FINISHED);
		assertFalse(requirement.isSatisfied(state.build()));
		state.quests.put(Quest.THE_GRAND_TREE, QuestState.FINISHED);
		assertTrue(requirement.isSatisfied(state.build()));
	}

	@Test
	public void varbitAndVarpGatesKeepStrictComparisonsAndMaskSemantics()
	{
		RouteRequirements requirement = required("", "", "", "10=1;11>3;12<5;13&4", "20>2");
		state.bits.put(10, 1);
		state.bits.put(11, 4);
		state.bits.put(12, 4);
		state.bits.put(13, 12);
		state.players.put(20, 3);
		assertTrue(requirement.isSatisfied(state.build()));
		state.bits.put(11, 3);
		assertFalse(requirement.isSatisfied(state.build()));
		state.bits.put(11, 4);
		state.bits.put(13, 2);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void unknownNativeVariableCannotSatisfyZeroOrLessThanGate()
	{
		assertFalse(required("", "", "", "10=0", "").isSatisfied(state.build()));
		assertFalse(required("", "", "", "", "20<10").isSatisfied(state.build()));
		state.bits.put(10, 0);
		assertTrue(required("", "", "", "10=0", "").isSatisfied(state.build()));
	}

	@Test
	public void malformedVariableNeverDropsTheRestOfItsRequirement()
	{
		for (String value : new String[]{"10>=1", "10!=1", "10@5", "10=1;unknown", "-1=0", "10=1;;11=0"})
		{
			assertFalse(value, RouteRequirements.parse("", "", "", value, "").isPresent());
		}
		assertTrue(RouteRequirements.parse("", "", "", "10=1;", "").isPresent());
	}

	@Test
	public void itemAlternativesKeepTheirOwnQuantitiesAndAndGroups()
	{
		RouteRequirements requirement = required("", "SHANTAY_PASS=1|COINS=5&ROPE=1", "", "", "");
		state.items.put(995, 4);
		state.items.put(954, 1);
		assertFalse(requirement.isSatisfied(state.build()));
		state.items.put(995, 5);
		assertTrue(requirement.isSatisfied(state.build()));
		state.items.remove(995);
		state.items.put(1854, 1);
		assertTrue(requirement.isSatisfied(state.build()));
		state.items.remove(954);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void aliasesAcceptPortableToolsFromInventoryOrEquipment()
	{
		RouteRequirements requirement = required("", "AXE=1&MACHETE=1", "", "", "");
		state.items.put(1351, 1);
		state.worn.put(975, 1);
		assertTrue(requirement.isSatisfied(state.build()));
		state.worn.clear();
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void grappleRequiresEquippedCrossbowAndCarriedGrapple()
	{
		RouteRequirements requirement = required("", "CROSSBOW=1&MITH_GRAPPLE=1", "", "", "");
		state.items.put(837, 1);
		state.items.put(9419, 1);
		assertFalse(requirement.isSatisfied(state.build()));
		state.worn.put(837, 1);
		assertTrue(requirement.isSatisfied(state.build()));
		state.items.remove(9419);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void quantityZeroRequiresAbsenceAndKnownItemContainers()
	{
		RouteRequirements requirement = required("", "SKAVID_MAP=0", "", "", "");
		assertTrue(requirement.isSatisfied(state.build()));
		state.items.put(2376, 1);
		assertFalse(requirement.isSatisfied(state.build()));
		state.items.clear();
		state.knownItems = false;
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void zeroQuantityAliasDoesNotPassBecauseAnotherVariantIsMissing()
	{
		RouteRequirements requirement = required("", "AXE=0", "", "", "");
		state.items.put(1351, 1);
		assertFalse(requirement.isSatisfied(state.build()));
	}

	@Test
	public void knownDiaryUnlockUsesCollectedNativeReward()
	{
		RouteRequirements requirement = required("", "UNLOCK_KARAMJA_ELITE=1||6306=100", "", "", "");
		assertTrue(requirement.getRequiredVarbits().contains(VarbitID.KARAMJA_ELITE_REWARD));
		assertFalse(requirement.isSatisfied(state.build()));
		state.bits.put(VarbitID.KARAMJA_ELITE_REWARD, 1);
		assertTrue(requirement.isSatisfied(state.build()));
		state.bits.clear();
		state.items.put(6306, 100);
		assertTrue(requirement.isSatisfied(state.build()));
	}

	@Test
	public void serverOnlyUnlockFailsClosedWhileExplicitCarriedAlternativeStillWorks()
	{
		RouteRequirements requirement = required("", "6570=1|UNLOCK_MOR_UL_REK=1", "", "", "");
		assertFalse(requirement.isSatisfied(state.build()));
		state.items.put(6570, 1);
		assertTrue(requirement.isSatisfied(state.build()));
		assertFalse(RouteRequirements.parse("", "UNLOCK_UNKNOWN=1", "", "", "").isPresent());
	}

	@Test
	public void malformedOrUnknownItemRequirementsRejectWholeTransport()
	{
		for (String value : new String[]{"UNKNOWN_TOOL=1", "ROPE", "ROPE=-1", "0=1", "995=1&", "995=1|", "995=999999999999"})
		{
			assertFalse(value, RouteRequirements.parse("", value, "", "", "").isPresent());
		}
		state.items.put(954, 1);
		state.items.put(995, 5);
		assertTrue(required("", "rope=1 && COINS=5 || 1854=1", "", "", "").isSatisfied(state.build()));
	}

	@Test
	public void requestedNativeIdsAreImmutableAndContainEveryConjunct()
	{
		RouteRequirements requirement = required("", "UNLOCK_KARAMJA_ELITE=1", "Tree Gnome Village", "10=1;11>2", "20=0");
		assertEquals(3, requirement.getRequiredVarbits().size());
		assertEquals(1, requirement.getRequiredVarps().size());
		try
		{
			requirement.getRequiredQuests().clear();
			throw new AssertionError("Expected immutable requirements");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	@Test
	public void everyPinnedPhysicalDataRequirementHasSupportedGrammar() throws Exception
	{
		List<String> rejected = new ArrayList<>();
		int rows = 0;
		for (String resource : new String[]{"transports.tsv", "agility_shortcuts.tsv"})
		{
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				RouteRequirements.class.getResourceAsStream("/routes/" + resource), StandardCharsets.UTF_8)))
			{
				String[] header = reader.readLine().split("\\t", -1);
				Map<String, Integer> columns = new HashMap<>();
				for (int i = 0; i < header.length; i++) columns.put(header[i], i);
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.isBlank() || line.startsWith("#")) continue;
					String[] values = line.split("\\t", -1);
					rows++;
					if (!RouteRequirements.parse(cell(values, columns.get("Skills")), cell(values, columns.get("Items")),
						cell(values, columns.get("Quests")), cell(values, columns.get("Varbits")),
						cell(values, columns.get("VarPlayers"))).isPresent()) rejected.add(resource + ":" + line);
				}
			}
		}
		assertTrue(rows > 5000);
		assertTrue(rejected.toString(), rejected.isEmpty());
	}

	private static String cell(String[] values, int column)
	{
		return column < values.length ? values[column] : "";
	}

	private static RouteRequirements required(String skills, String items, String quests, String bits, String players)
	{
		return RouteRequirements.parse(skills, items, quests, bits, players).orElseThrow();
	}

	private static final class State
	{
		private final Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		private final Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		private final Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		private final Map<Integer, Integer> bits = new HashMap<>();
		private final Map<Integer, Integer> players = new HashMap<>();
		private final Map<Integer, Integer> items = new HashMap<>();
		private final Map<Integer, Integer> worn = new HashMap<>();
		private boolean knownItems = true;

		private AccountRouteState build()
		{
			Map<Integer, Integer> combined = new HashMap<>(items);
			worn.forEach((id, count) -> combined.merge(id, count, Integer::sum));
			return new AccountRouteState(real, boosted, quests, bits, players, combined, worn, knownItems);
		}
	}
}
