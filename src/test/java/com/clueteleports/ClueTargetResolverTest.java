package com.clueteleports;

import java.awt.Graphics2D;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.FaloTheBardClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.LocationsClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClueTargetResolverTest
{
	private static final WorldPoint VARROCK = new WorldPoint(3212, 3424, 0);
	private static final WorldPoint FALADOR = new WorldPoint(2965, 3379, 0);
	private final ClueTargetResolver resolver = new ClueTargetResolver();

	@Test
	public void noActiveClueHasNoTarget()
	{
		assertEquals(ClueTargetResolver.State.NO_CLUE, resolver.resolve((ClueScrollPlugin) null).getState());
		assertTrue(resolver.resolve(null, null).getLocations().isEmpty());
	}

	@Test
	public void readsTheActiveClueFromTheCorePlugin()
	{
		ClueScrollPlugin plugin = new ClueScrollPlugin()
		{
			@Override
			public ClueScroll getClue()
			{
				return new SingleLocationClue(VARROCK);
			}
		};
		assertEquals(Collections.singletonList(VARROCK), resolver.resolve(plugin).getLocations());
	}

	@Test
	public void unknownClueDoesNotInventALocation()
	{
		ClueTargetResolver.Resolution result = resolver.resolve(new TestClue(), null);
		assertEquals(ClueTargetResolver.State.NO_LOCATION, result.getState());
		assertTrue(result.getLocations().isEmpty());
	}

	@Test
	public void resolvesFaloItemRiddlesWithoutALocationInterface()
	{
		FaloTheBardClue clue = FaloTheBardClue.forText("A mark used to increase one's grace, found atop a seer's place.");
		assertNotNull(clue);
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(new WorldPoint(2689, 3550, 0)), result.getLocations());
	}

	@Test
	public void completedSherlockChallengeRoutesBackToSherlock()
	{
		SkillChallengeClue clue = SkillChallengeClue.forText("craft a nature rune.", "<str>craft a nature rune.</str>");
		assertNotNull(clue);
		assertTrue(clue.isChallengeCompleted());
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(new WorldPoint(2733, 3415, 0)), result.getLocations());
	}

	@Test
	public void unfinishedSherlockChallengeRoutesToItsVerifiedTaskSite()
	{
		SkillChallengeClue clue = SkillChallengeClue.forText("craft a nature rune.", "craft a nature rune.");
		assertNotNull(clue);
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(new WorldPoint(2869, 3019, 0)), result.getLocations());
		assertFalse(result.hasAlternativeTaskLocations());
	}

	@Test
	public void charlieItemRequestsRouteBackToCharlie()
	{
		String text = "i need to give charlie a cooked pike.";
		SkillChallengeClue clue = SkillChallengeClue.forText(text, text);
		assertNotNull(clue);
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(new WorldPoint(3209, 3390, 0)), result.getLocations());
	}

	@Test
	public void supportsTheDefaultSingleLocationInterface()
	{
		ClueTargetResolver.Resolution result = resolver.resolve(new SingleLocationClue(VARROCK), null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(VARROCK), result.getLocations());
	}

	@Test
	public void preservesEveryLocationFromTheMultiLocationInterface()
	{
		ClueTargetResolver.Resolution result = resolver.resolve(new MultipleLocationClue(VARROCK, FALADOR), null);
		assertEquals(ClueTargetResolver.State.MULTIPLE_LOCATIONS, result.getState());
		assertEquals(Arrays.asList(VARROCK, FALADOR), result.getLocations());
		assertFalse(result.hasAlternativeTaskLocations());
	}

	@Test
	public void honorsOverriddenLocationsForCluesWithBothInterfaces()
	{
		ClueTargetResolver.Resolution result = resolver.resolve(new BothLocationClue(VARROCK, FALADOR), null);
		assertEquals(ClueTargetResolver.State.MULTIPLE_LOCATIONS, result.getState());
		assertEquals(Arrays.asList(VARROCK, FALADOR), result.getLocations());
		assertFalse(result.hasAlternativeTaskLocations());
	}

	@Test
	public void removesNullsAndDuplicateDestinations()
	{
		ClueTargetResolver.Resolution result = resolver.resolve(new MultipleLocationClue(null, VARROCK, VARROCK, null), null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(VARROCK), result.getLocations());
	}

	@Test
	public void handlesMissingLocations()
	{
		assertEquals(ClueTargetResolver.State.NO_LOCATION, resolver.resolve(new SingleLocationClue(null), null).getState());
		assertEquals(ClueTargetResolver.State.NO_LOCATION, resolver.resolve(new MultipleLocationClue((WorldPoint[]) null), null).getState());
		assertEquals(ClueTargetResolver.State.NO_LOCATION, resolver.resolve(new MultipleLocationClue(null, null), null).getState());
	}

	@Test
	public void rereadsMutableClueStateAndKeepsPreviousSnapshots()
	{
		MultipleLocationClue clue = new MultipleLocationClue(VARROCK, FALADOR);
		ClueTargetResolver.Resolution previous = resolver.resolve(clue, null);
		clue.locations[0] = null;
		ClueTargetResolver.Resolution current = resolver.resolve(clue, null);
		assertEquals(Arrays.asList(VARROCK, FALADOR), previous.getLocations());
		assertEquals(Collections.singletonList(FALADOR), current.getLocations());
		assertEquals(ClueTargetResolver.State.RESOLVED, current.getState());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void resultsCannotBeMutatedByTheCaller()
	{
		resolver.resolve(new SingleLocationClue(VARROCK), null).getLocations().clear();
	}

	@Test
	public void bloodChallengeResolvesWithoutAClientOrQuestAccess()
	{
		String text = "bind a blood rune at the blood altar.";
		SkillChallengeClue clue = SkillChallengeClue.forText(text, text);
		assertNotNull(clue);
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.RESOLVED, result.getState());
		assertEquals(Collections.singletonList(new WorldPoint(1716, 3827, 0)), result.getLocations());
		assertFalse(result.hasAlternativeTaskLocations());
	}

	@Test
	public void unlockedBloodAltarsAreValidTaskAlternativesInsteadOfUnresolvedClueCandidates()
	{
		String text = "bind a blood rune at the blood altar.";
		SkillChallengeClue clue = SkillChallengeClue.forText(text, text);
		assertNotNull(clue);
		Client client = ChallengeLocationsTest.questClient(2);
		ClueScrollPlugin plugin = new ClueScrollPlugin()
		{
			@Override
			public Client getClient()
			{
				return client;
			}
		};
		ClueTargetResolver.Resolution result = resolver.resolve(clue, plugin);
		assertEquals(ClueTargetResolver.State.MULTIPLE_LOCATIONS, result.getState());
		assertTrue(result.hasAlternativeTaskLocations());
		assertEquals(Arrays.asList(new WorldPoint(1716, 3827, 0), new WorldPoint(3625, 9717, 0)),
			result.getLocations());
	}

	@Test
	public void completingBloodChallengeReplacesItsAltarWithSherlock()
	{
		String text = "bind a blood rune at the blood altar.";
		SkillChallengeClue clue = SkillChallengeClue.forText(text, text);
		ClueTargetResolver.Resolution unfinished = resolver.resolve(clue, null);
		SkillChallengeClue.forText(text, "<str>" + text + "</str>");
		ClueTargetResolver.Resolution completed = resolver.resolve(clue, null);
		assertEquals(Collections.singletonList(new WorldPoint(2733, 3415, 0)), completed.getLocations());
		assertEquals(ClueTargetResolver.State.RESOLVED, completed.getState());
		assertFalse(completed.hasAlternativeTaskLocations());
		assertEquals(Collections.singletonList(new WorldPoint(1716, 3827, 0)), unfinished.getLocations());
	}

	@Test
	public void flexibleSkillTasksStillHaveNoInventedDestination()
	{
		String text = "burn a magic log.";
		SkillChallengeClue clue = SkillChallengeClue.forText(text, text);
		assertNotNull(clue);
		ClueTargetResolver.Resolution result = resolver.resolve(clue, null);
		assertEquals(ClueTargetResolver.State.NO_LOCATION, result.getState());
		assertTrue(result.getLocations().isEmpty());
		assertFalse(result.hasAlternativeTaskLocations());
	}

	private static class TestClue extends ClueScroll
	{
		@Override
		public void makeOverlayHint(PanelComponent panelComponent, ClueScrollPlugin plugin)
		{
		}

		@Override
		public void makeWorldOverlayHint(Graphics2D graphics, ClueScrollPlugin plugin)
		{
		}
	}

	private static final class SingleLocationClue extends TestClue implements LocationClueScroll
	{
		private final WorldPoint location;

		private SingleLocationClue(WorldPoint location)
		{
			this.location = location;
		}

		@Override
		public WorldPoint getLocation(ClueScrollPlugin plugin)
		{
			return location;
		}
	}

	private static class MultipleLocationClue extends TestClue implements LocationsClueScroll
	{
		private final WorldPoint[] locations;

		private MultipleLocationClue(WorldPoint... locations)
		{
			this.locations = locations;
		}

		@Override
		public WorldPoint[] getLocations(ClueScrollPlugin plugin)
		{
			return locations;
		}

		@Override
		public void reset()
		{
		}
	}

	private static final class BothLocationClue extends MultipleLocationClue implements LocationClueScroll
	{
		private BothLocationClue(WorldPoint... locations)
		{
			super(locations);
		}

		@Override
		public WorldPoint getLocation(ClueScrollPlugin plugin)
		{
			return null;
		}
	}
}
