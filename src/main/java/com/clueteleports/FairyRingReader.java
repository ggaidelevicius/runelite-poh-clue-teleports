package com.clueteleports;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Captures known fairy-ring access when the player opens the code selector.
 *
 * The travel log records visits, not the full set of available destinations.
 * Ordinary unvisited rings are therefore included, and restricted rings use
 * quest and region access state. Prior successful travel is only a positive
 * fallback for partial-quest or island access that cannot be fully established
 * from a published client-side requirement.
 *
 * Network permission: runelite/cs2-scripts [proc,helper_agility_requirements].
 * Travel-log semantics: [proc,script7866] and [proc,script7317].
 * Current ring requirements: https://oldschool.runescape.wiki/w/Fairy_rings .
 * BLQ's LOTG stage >= 50 also appears in shortest-path/fairy_rings.tsv at
 * 50f30435f37d1d5c5e2af790925fa7aa70b7840d.
 *
 * BJR and AKP can unlock before quest completion; without a previous successful
 * visit they are conservatively omitted until their quest is complete. CJQ can
 * unlock before Troubled Tortugans; a previous successful visit is accepted.
 * This class never treats a missing travel-log entry as a general access denial.
 * Invoke on the client thread; the same read-only status script as Quest.getState is used.
 */
public final class FairyRingReader
{
	private static final Quest[] ACCESS_QUESTS = {
		Quest.DEATH_TO_THE_DORGESHUUN,
		Quest.REGICIDE,
		Quest.THE_FREMENNIK_TRIALS,
		Quest.MONKEY_MADNESS_I,
		Quest.IN_SEARCH_OF_THE_MYREQUE,
		Quest.HOLY_GRAIL,
		Quest.BENEATH_CURSED_SANDS,
		Quest.TROUBLED_TORTUGANS
	};

	private FairyRingReader()
	{
	}

	/**
	 * Empty means the selector is closed or not ready; it must not replace an
	 * existing character snapshot. A result contains only positively known access.
	 */
	public static Optional<Set<String>> readOpenMenu(Client client)
	{
		Widget confirm = client.getWidget(InterfaceID.Fairyrings.CONFIRM);
		Widget log = client.getWidget(InterfaceID.FairyringsLog.CONTENTS);
		if (confirm == null || confirm.isHidden() || log == null || log.isHidden()
			|| confirm.getText() == null || confirm.getText().isBlank())
		{
			return Optional.empty();
		}
		if (client.getVarbitValue(VarbitID.FAIRYRING_USE) < 1
			|| client.getVarbitValue(VarbitID.FAIRYRING_PERMISSION) < 2)
		{
			return Optional.empty();
		}

		Map<Quest, QuestState> quests = new EnumMap<>(Quest.class);
		for (Quest quest : ACCESS_QUESTS)
		{
			client.runScript(ScriptID.QUEST_STATUS_GET, quest.getId());
			int[] stack = client.getIntStack();
			if (stack == null || stack.length == 0 || stack[0] < 0 || stack[0] > 2)
			{
				return Optional.empty();
			}
			quests.put(quest, stack[0] == 2 ? QuestState.FINISHED
				: stack[0] == 1 ? QuestState.NOT_STARTED : QuestState.IN_PROGRESS);
		}

		Set<String> available = new LinkedHashSet<>();
		for (TeleportDestination destination : TeleportCatalog.forSource(TeleportSource.FAIRY_RING))
		{
			if (accessible(client, quests, destination.getId()))
			{
				available.add(destination.getId());
			}
		}
		return Optional.of(Collections.unmodifiableSet(available));
	}

	private static boolean accessible(Client client, Map<Quest, QuestState> quests, String id)
	{
		switch (id)
		{
			case "fairy-ais":
			case "fairy-ajp":
			case "fairy-ckq":
				return client.getVarbitValue(VarbitID.VARLAMORE_VISITED) == 1;
			case "fairy-akr":
			case "fairy-bls":
			case "fairy-cir":
			case "fairy-cis":
			case "fairy-djr":
				return client.getVarbitValue(VarbitID.ZEAH_PLAYERHASVISITED) == 1;
			case "fairy-ajq":
				return finished(quests, Quest.DEATH_TO_THE_DORGESHUUN);
			case "fairy-bjs":
				return finished(quests, Quest.REGICIDE);
			case "fairy-cip":
				return finished(quests, Quest.THE_FREMENNIK_TRIALS);
			case "fairy-clr":
				return finished(quests, Quest.MONKEY_MADNESS_I);
			case "fairy-dls":
				return finished(quests, Quest.IN_SEARCH_OF_THE_MYREQUE);
			case "fairy-bjr":
				return finished(quests, Quest.HOLY_GRAIL)
					|| client.getVarbitValue(VarbitID.FAIRYRINGS_LOG_BJR) == 1;
			case "fairy-akp":
				return finished(quests, Quest.BENEATH_CURSED_SANDS)
					|| client.getVarbitValue(VarbitID.FAIRYRINGS_LOG_AKP) == 1;
			case "fairy-blq":
				return client.getVarbitValue(VarbitID.LOTG) >= 50;
			case "fairy-cjq":
				return finished(quests, Quest.TROUBLED_TORTUGANS)
					|| client.getVarbitValue(VarbitID.FAIRYRINGS_LOG_CJQ) == 1;
			case "fairy-dlp":
				return client.getVarbitValue(VarbitID.GRIMSTONE_VISITED) == 1;
			default:
				return true;
		}
	}

	private static boolean finished(Map<Quest, QuestState> quests, Quest quest)
	{
		return quests.get(quest) == QuestState.FINISHED;
	}
}
