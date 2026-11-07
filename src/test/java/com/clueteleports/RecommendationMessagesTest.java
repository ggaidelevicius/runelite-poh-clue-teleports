package com.clueteleports;

import java.awt.Graphics2D;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.HotColdClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.MusicClue;
import net.runelite.client.plugins.cluescrolls.clues.SkillChallengeClue;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class RecommendationMessagesTest
{
	private static final WorldPoint VARROCK = new WorldPoint(3212, 3424, 0);
	private static final WorldPoint FALADOR = new WorldPoint(2965, 3379, 0);
	private final RecommendationMessages messages = new RecommendationMessages();
	private final ClueTargetResolver resolver = new ClueTargetResolver();
	private final TestClue clue = new TestClue(VARROCK, 1);
	private final TeleportDestination destination = new TeleportDestination("nexus-varrock",
		TeleportSource.PORTAL_NEXUS, "Varrock", VARROCK);

	@Test
	public void announcesTheFacilityAndDestinationOnce()
	{
		assertEquals("[POH Clue Teleports] Portal nexus: Varrock", update(clue, destination));
		assertNull(update(clue, destination));
		assertNull(update(clue, destination));
	}

	@Test
	public void temporarySearchesAndUnavailableTargetsDoNotRepeatTheAnnouncement()
	{
		assertNotNull(update(clue, destination));
		assertNull(update(clue, null));
		assertNull(messages.update("one", clue, resolver.resolve(new TestClue(null, 1), null), null, true));
		assertNull(update(clue, destination));
	}

	@Test
	public void eachDifferentClueStepAnnouncesEvenWhenTheDestinationIsUnchanged()
	{
		assertNotNull(update(clue, destination));
		assertNotNull(update(new TestClue(VARROCK, 2), destination));
		assertNull(update(new TestClue(VARROCK, 2), destination));
	}

	@Test
	public void rereadingAnEquivalentClueDoesNotAnnounceAgain()
	{
		assertNotNull(update(clue, destination));
		assertNull(update(new TestClue(VARROCK, 1), destination));
	}

	@Test
	public void changingTheDestinationIdAnnouncesEvenWhenItsNameIsUnchanged()
	{
		assertNotNull(update(clue, destination));
		TeleportDestination changed = new TeleportDestination("other-varrock",
			TeleportSource.PORTAL_NEXUS, "Varrock", VARROCK);
		assertNotNull(update(clue, changed));
		assertNull(update(clue, changed));
	}

	@Test
	public void changingTheSourceAnnouncesEvenWhenTheDestinationIdIsUnchanged()
	{
		assertNotNull(update(clue, destination));
		TeleportDestination changed = new TeleportDestination("nexus-varrock",
			TeleportSource.PORTAL_CHAMBER, "Varrock", VARROCK);
		assertEquals("[POH Clue Teleports] Portal chamber: Varrock", update(clue, changed));
		assertNull(update(clue, changed));
	}

	@Test
	public void clearingTheClueAllowsTheSameStepToAnnounceAgain()
	{
		assertNotNull(update(clue, destination));
		assertNull(messages.update("one", null, resolver.resolve(null, null), null, true));
		assertNotNull(update(clue, destination));
	}

	@Test
	public void changingTheResolvedTargetAnnouncesTheNewTask()
	{
		assertNotNull(update(clue, destination));
		assertNotNull(update(new TestClue(FALADOR, 1), destination));
		assertNull(update(new TestClue(FALADOR, 1), destination));
	}

	@Test
	public void differentProfilesDoNotShareAnnouncementState()
	{
		assertNotNull(update(clue, destination));
		assertNotNull(messages.update("two", clue, resolver.resolve(clue, null), destination, true));
		assertNull(messages.update("two", clue, resolver.resolve(clue, null), destination, true));
	}

	@Test
	public void enablingChatAnnouncesTheCurrentRecommendation()
	{
		assertNull(messages.update("one", clue, resolver.resolve(clue, null), destination, false));
		assertNotNull(update(clue, destination));
		assertNull(messages.update("one", clue, resolver.resolve(clue, null), destination, false));
		assertNotNull(update(clue, destination));
		assertNull(update(clue, destination));
	}

	@Test
	public void resettingAllowsARelearnedRecommendationToAnnounce()
	{
		assertNotNull(update(clue, destination));
		messages.reset();
		assertNotNull(update(clue, destination));
	}

	@Test
	public void theAnnouncementCopiesMutableClueKeys()
	{
		assertNotNull(update(clue, destination));
		clue.keys[0] = 2;
		assertNotNull(update(clue, destination));
		assertNull(update(clue, destination));
	}

	@Test
	public void unkeyedCluesUseTheirTypeAndResolvedLocations()
	{
		assertNotNull(update(new TestClue(VARROCK, (int[]) null), destination));
		assertNull(update(new TestClue(VARROCK, (int[]) null), destination));
		assertNotNull(update(new TestClue(FALADOR, (int[]) null), destination));
	}

	@Test
	public void differentMusicRequestsAnnounceDespiteSharingCeciliaAndTheSameTeleport()
	{
		MusicClue vision = MusicClue.forSong("Vision");
		MusicClue tiptoe = MusicClue.forSong("Tiptoe");
		assertNotNull(vision);
		assertNotNull(tiptoe);
		assertNull(vision.getConfigKeys());
		assertNull(tiptoe.getConfigKeys());
		assertEquals(resolver.resolve(vision, null).getLocations(), resolver.resolve(tiptoe, null).getLocations());
		assertNotNull(update(vision, destination));
		assertNotNull(update(tiptoe, destination));
		assertNull(update(tiptoe, destination));
	}

	@Test
	public void rereadingAnUnlistedSongDoesNotAnnounceForEachNewMusicClueInstance()
	{
		MusicClue firstRead = MusicClue.forText("<col=ffffff>Unlisted Test Song</col>");
		MusicClue secondRead = MusicClue.forText("<col=ffffff>Unlisted Test Song</col>");
		assertNotNull(firstRead);
		assertNotNull(secondRead);
		assertNotNull(update(firstRead, destination));
		assertNull(update(secondRead, destination));
	}

	@Test
	public void beginnerAndMasterHotColdRequestsAtTheSameNpcRemainDifferentSteps()
	{
		String text = "Buried beneath the ground, who knows where it's found. The League Tutor may have a strange device lying around.";
		HotColdClue beginner = HotColdClue.forText(text, 23182);
		HotColdClue master = HotColdClue.forText(text, 19835);
		assertNotNull(beginner);
		assertNotNull(master);
		assertNull(beginner.getConfigKeys());
		assertNull(master.getConfigKeys());
		assertEquals(resolver.resolve(beginner, null).getLocations(), resolver.resolve(master, null).getLocations());
		assertNotNull(update(beginner, destination));
		assertNotNull(update(master, destination));
		assertNull(update(master, destination));
	}

	@Test
	public void unresolvedCluesNeverAnnounceAPassedDestination()
	{
		assertNull(update(new TestClue(null, 1), destination));
		assertNull(messages.update(null, clue, resolver.resolve(clue, null), destination, true));
	}

	@Test
	public void completingASkillChallengeAnnouncesItsReturnRecommendation()
	{
		String text = "craft a nature rune.";
		SkillChallengeClue challenge = SkillChallengeClue.forText(text, text);
		assertNotNull(challenge);
		assertNotNull(update(challenge, destination));
		challenge.setChallengeCompleted(true);
		try
		{
			assertNotNull(update(challenge, destination));
			assertNull(update(challenge, destination));
		}
		finally
		{
			challenge.setChallengeCompleted(false);
		}
	}

	private String update(ClueScroll current, TeleportDestination arrival)
	{
		return messages.update("one", current, resolver.resolve(current, null), arrival, true);
	}

	private static final class TestClue extends ClueScroll implements LocationClueScroll
	{
		private final WorldPoint location;
		private final int[] keys;

		private TestClue(WorldPoint location, int... keys)
		{
			this.location = location;
			this.keys = keys;
		}

		@Override
		public int[] getConfigKeys() { return keys; }

		@Override
		public WorldPoint getLocation(ClueScrollPlugin plugin) { return location; }

		@Override
		public void makeOverlayHint(PanelComponent panelComponent, ClueScrollPlugin plugin) { }

		@Override
		public void makeWorldOverlayHint(Graphics2D graphics, ClueScrollPlugin plugin) { }
	}
}
