package com.clueteleports;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FairyRingReaderTest
{
	@Test
	public void closedHiddenAndPartiallyInitializedSelectorsCannotOverwriteACache()
	{
		Fixture fixture = new Fixture();
		fixture.open = false;
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		fixture.open = true;
		fixture.hidden = true;
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		fixture.hidden = false;
		fixture.logLoaded = false;
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		fixture.logLoaded = true;
		fixture.confirmText = "";
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		assertEquals(0, fixture.questReads);
	}

	@Test
	public void networkPermissionMustBePresentBeforeAnySnapshot()
	{
		Fixture fixture = new Fixture();
		fixture.varbits.put(VarbitID.FAIRYRING_PERMISSION, 1);
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		assertEquals(0, fixture.questReads);
	}

	@Test
	public void ordinaryUnvisitedRingsAreAvailableWithoutAnyTravelLogEntry()
	{
		Set<String> available = FairyRingReader.readOpenMenu(new Fixture().client).get();
		assertTrue(available.contains("fairy-aiq"));
		assertTrue(available.contains("fairy-dkr"));
		assertTrue(available.contains("fairy-ckp"));
		assertTrue(available.size() > 20);
		assertFalse(available.contains("fairy-cip"));
		assertFalse(available.contains("fairy-dlp"));
	}

	@Test
	public void regionAccessUnlocksUnvisitedRingsAndCisNeedsNoObsoletePayment()
	{
		Fixture fixture = new Fixture();
		fixture.varbits.put(VarbitID.ZEAH_PLAYERHASVISITED, 1);
		fixture.varbits.put(VarbitID.VARLAMORE_VISITED, 1);
		fixture.varbits.put(VarbitID.GRIMSTONE_VISITED, 1);
		Set<String> available = FairyRingReader.readOpenMenu(fixture.client).get();
		assertTrue(available.contains("fairy-cis"));
		assertTrue(available.contains("fairy-cir"));
		assertTrue(available.contains("fairy-akr"));
		assertTrue(available.contains("fairy-djr"));
		assertTrue(available.contains("fairy-ais"));
		assertTrue(available.contains("fairy-ckq"));
		assertTrue(available.contains("fairy-dlp"));
	}

	@Test
	public void finishedQuestsUnlockTheirDestinationsWithoutRequiringPriorRingUse()
	{
		Fixture fixture = new Fixture();
		fixture.finish(Quest.DEATH_TO_THE_DORGESHUUN, Quest.REGICIDE,
			Quest.THE_FREMENNIK_TRIALS, Quest.MONKEY_MADNESS_I, Quest.IN_SEARCH_OF_THE_MYREQUE);
		Set<String> available = FairyRingReader.readOpenMenu(fixture.client).get();
		assertTrue(available.contains("fairy-ajq"));
		assertTrue(available.contains("fairy-bjs"));
		assertTrue(available.contains("fairy-cip"));
		assertTrue(available.contains("fairy-clr"));
		assertTrue(available.contains("fairy-dls"));
	}

	@Test
	public void partialQuestDestinationsNeedPositiveEvidenceRatherThanAStartedQuest()
	{
		Fixture fixture = new Fixture();
		fixture.questStatuses.put(Quest.HOLY_GRAIL.getId(), 0);
		fixture.questStatuses.put(Quest.BENEATH_CURSED_SANDS.getId(), 0);
		Set<String> before = FairyRingReader.readOpenMenu(fixture.client).get();
		assertFalse(before.contains("fairy-bjr"));
		assertFalse(before.contains("fairy-akp"));

		fixture.varbits.put(VarbitID.FAIRYRINGS_LOG_BJR, 1);
		fixture.varbits.put(VarbitID.FAIRYRINGS_LOG_AKP, 1);
		fixture.varbits.put(VarbitID.FAIRYRINGS_LOG_CJQ, 1);
		Set<String> after = FairyRingReader.readOpenMenu(fixture.client).get();
		assertTrue(after.contains("fairy-bjr"));
		assertTrue(after.contains("fairy-akp"));
		assertTrue(after.contains("fairy-cjq"));
	}

	@Test
	public void yubiuskUsesThePartialQuestUnlockRatherThanRequiringCompletion()
	{
		Fixture fixture = new Fixture();
		fixture.varbits.put(VarbitID.LOTG, 49);
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).get().contains("fairy-blq"));
		fixture.varbits.put(VarbitID.LOTG, 50);
		assertTrue(FairyRingReader.readOpenMenu(fixture.client).get().contains("fairy-blq"));
	}

	@Test
	public void malformedQuestStatusAbortsTheEntireSnapshot()
	{
		Fixture fixture = new Fixture();
		fixture.questStatuses.put(Quest.REGICIDE.getId(), -1);
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
		fixture.nullQuestStack = true;
		assertFalse(FairyRingReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void fullyUnlockedCharacterHasEveryFixedCatalogRing()
	{
		Fixture fixture = new Fixture();
		fixture.finish(Quest.DEATH_TO_THE_DORGESHUUN, Quest.REGICIDE,
			Quest.THE_FREMENNIK_TRIALS, Quest.MONKEY_MADNESS_I, Quest.IN_SEARCH_OF_THE_MYREQUE,
			Quest.HOLY_GRAIL, Quest.BENEATH_CURSED_SANDS, Quest.TROUBLED_TORTUGANS);
		fixture.varbits.put(VarbitID.ZEAH_PLAYERHASVISITED, 1);
		fixture.varbits.put(VarbitID.VARLAMORE_VISITED, 1);
		fixture.varbits.put(VarbitID.GRIMSTONE_VISITED, 1);
		fixture.varbits.put(VarbitID.LOTG, 54);
		assertEquals(54, FairyRingReader.readOpenMenu(fixture.client).get().size());
	}

	private static final class Fixture
	{
		private final Map<Integer, Integer> varbits = new HashMap<>();
		private final Map<Integer, Integer> questStatuses = new HashMap<>();
		private boolean open = true;
		private boolean hidden;
		private boolean logLoaded = true;
		private boolean nullQuestStack;
		private String confirmText = "Teleport";
		private int[] stack = {1};
		private int questReads;
		private final Widget widget = proxy(Widget.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "isHidden":
					return hidden;
				case "getText":
					return confirmText;
				default:
					throw new AssertionError("Unexpected widget call: " + method.getName());
			}
		});
		private final Client client = proxy(Client.class, (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "getWidget":
					int id = (Integer) args[0];
					return !open || (id == InterfaceID.FairyringsLog.CONTENTS && !logLoaded) ? null : widget;
				case "getVarbitValue":
					return varbits.getOrDefault((Integer) args[0], 0);
				case "runScript":
					Object[] scriptArgs = (Object[]) args[0];
					assertEquals(ScriptID.QUEST_STATUS_GET, scriptArgs[0]);
					stack = new int[]{questStatuses.getOrDefault((Integer) scriptArgs[1], 1)};
					questReads++;
					return null;
				case "getIntStack":
					return nullQuestStack ? null : stack;
				default:
					throw new AssertionError("Unexpected client call: " + method.getName());
			}
		});

		private Fixture()
		{
			varbits.put(VarbitID.FAIRYRING_USE, 1);
			varbits.put(VarbitID.FAIRYRING_PERMISSION, 2);
		}

		private void finish(Quest... quests)
		{
			for (Quest quest : quests)
			{
				questStatuses.put(quest.getId(), 2);
			}
		}
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
