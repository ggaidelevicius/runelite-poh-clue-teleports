package com.clueteleports;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HouseInventoryTest
{
	private final HouseInventory inventory = new HouseInventory();

	@Test
	public void aNewProfileHasNoCompletedSurveyOrRequiredMenus()
	{
		assertFalse(inventory.isSurveyed());
		assertTrue(inventory.installedSources().isEmpty());
		assertTrue(inventory.requiredMenus().isEmpty());
	}

	@Test
	public void observesOnlyInstalledInterfacesIncludingBothMountedAmulets()
	{
		Set<TeleportSource> all = EnumSet.allOf(TeleportSource.class);
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1840:7040", all));

		assertTrue(inventory.isSurveyed());
		assertEquals(all, inventory.installedSources());
		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE,
			TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX,
			TeleportSource.MOUNTED_DIGSITE_PENDANT, TeleportSource.MOUNTED_XERICS_TALISMAN),
			inventory.requiredMenus());
	}

	@Test
	public void facilitiesWithNoDestinationMenuNeverAddLearningRequirements()
	{
		Set<TeleportSource> fixtures = EnumSet.of(TeleportSource.HOUSE_EXIT,
			TeleportSource.WILDERNESS_OBELISK, TeleportSource.PORTAL_CHAMBER,
			TeleportSource.MOUNTED_GLORY, TeleportSource.MOUNTED_MYTHICAL_CAPE, TeleportSource.CAPE_HANGER);
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1840:7040", fixtures));

		assertEquals(fixtures, inventory.installedSources());
		assertTrue(inventory.requiredMenus().isEmpty());
	}

	@Test
	public void aCombinedObjectRequiresBothOfItsInterfaces()
	{
		Set<TeleportSource> combined = EnumSet.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE);
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1840:7040", combined));

		assertEquals(combined, inventory.requiredMenus());
	}

	@Test
	public void discoveringAnotherLoadedRoomExpandsRequirementsAndKeepsUnloadedRoom()
	{
		observe("0:1840:7040", TeleportSource.FAIRY_RING);
		observe("0:1900:7100", TeleportSource.PORTAL_NEXUS);

		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.PORTAL_NEXUS), inventory.requiredMenus());
		assertTrue(inventory.encode().contains("0:1840:7040=FAIRY_RING"));
		assertTrue(inventory.encode().contains("0:1900:7100=PORTAL_NEXUS"));
	}

	@Test
	public void completelyLoadedEmptyPositionRemovesItsOldFacility()
	{
		observe("0:1840:7040", TeleportSource.JEWELLERY_BOX);
		observe("0:1900:7100", TeleportSource.PORTAL_NEXUS);
		inventory.observe(Set.of("0:1840:7040"), Collections.emptyMap());

		assertEquals(Set.of(TeleportSource.PORTAL_NEXUS), inventory.installedSources());
		assertEquals(Set.of(TeleportSource.PORTAL_NEXUS), inventory.requiredMenus());
	}

	@Test
	public void removingLastInterfaceLeavesACompletedSurveyWithNoMenuRequirements()
	{
		observe("0:1840:7040", TeleportSource.JEWELLERY_BOX);
		inventory.observe(Set.of("0:1840:7040"), Collections.emptyMap());

		assertTrue(inventory.isSurveyed());
		assertTrue(inventory.installedSources().isEmpty());
		assertTrue(inventory.requiredMenus().isEmpty());
		assertEquals("", inventory.encode());
	}

	@Test
	public void replacingLoadedFixtureDropsItsPreviousSourceAtThatPosition()
	{
		observe("0:1840:7040", TeleportSource.FAIRY_RING);
		observe("0:1840:7040", TeleportSource.SPIRIT_TREE);

		assertEquals(Set.of(TeleportSource.SPIRIT_TREE), inventory.requiredMenus());
		assertEquals("0:1840:7040=SPIRIT_TREE", inventory.encode());
	}

	@Test
	public void removingOneRepeatedFixtureKeepsItsOtherUnloadedCopy()
	{
		observe("0:1840:7040", TeleportSource.PORTAL_NEXUS);
		observe("1:1840:7040", TeleportSource.PORTAL_NEXUS);
		inventory.observe(Set.of("0:1840:7040"), Collections.emptyMap());

		assertEquals(Set.of(TeleportSource.PORTAL_NEXUS), inventory.requiredMenus());
		assertEquals("1:1840:7040=PORTAL_NEXUS", inventory.encode());
	}

	@Test
	public void incompleteTileCannotOverwriteItsPreviouslyLearnedInventory()
	{
		observe("0:1840:7040", TeleportSource.FAIRY_RING);
		inventory.observe(Set.of("0:1841:7040"), Map.of("0:1840:7040", Set.of(TeleportSource.PORTAL_NEXUS)));

		assertEquals(Set.of(TeleportSource.FAIRY_RING), inventory.requiredMenus());
		assertEquals("0:1840:7040=FAIRY_RING", inventory.encode());
	}

	@Test
	public void unknownOrUncoveredRoomsDoNotCreateNewRequiredMenus()
	{
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1900:7100", Set.of(TeleportSource.JEWELLERY_BOX)));

		assertTrue(inventory.installedSources().isEmpty());
		assertTrue(inventory.requiredMenus().isEmpty());
	}

	@Test
	public void observationCopiesCallerOwnedMapsAndSourceSets()
	{
		Set<TeleportSource> sources = EnumSet.of(TeleportSource.FAIRY_RING);
		Map<String, Set<TeleportSource>> observed = new HashMap<>();
		observed.put("0:1840:7040", sources);
		inventory.observe(Set.of("0:1840:7040"), observed);
		sources.clear();
		observed.clear();

		assertEquals(Set.of(TeleportSource.FAIRY_RING), inventory.requiredMenus());
	}

	@Test
	public void returnedSetsAreImmutableSnapshotsAcrossLaterChanges()
	{
		observe("0:1840:7040", TeleportSource.FAIRY_RING);
		Set<TeleportSource> installed = inventory.installedSources();
		Set<TeleportSource> required = inventory.requiredMenus();
		observe("0:1840:7040", TeleportSource.JEWELLERY_BOX);

		assertEquals(Set.of(TeleportSource.FAIRY_RING), installed);
		assertEquals(Set.of(TeleportSource.FAIRY_RING), required);
		assertImmutable(installed);
		assertImmutable(required);
	}

	@Test
	public void persistenceRoundTripKeepsMultiplePlanesAndCombinedFixtures()
	{
		inventory.observe(Set.of("1:1900:7100", "0:1840:7040"), Map.of(
			"1:1900:7100", Set.of(TeleportSource.MOUNTED_XERICS_TALISMAN),
			"0:1840:7040", Set.of(TeleportSource.SPIRIT_TREE, TeleportSource.FAIRY_RING)));
		String saved = inventory.encode();
		HouseInventory restored = new HouseInventory();
		restored.restore(saved);

		assertTrue(restored.isSurveyed());
		assertEquals(inventory.installedSources(), restored.installedSources());
		assertEquals(inventory.requiredMenus(), restored.requiredMenus());
		assertEquals(saved, restored.encode());
		assertEquals("0:1840:7040=FAIRY_RING,SPIRIT_TREE;1:1900:7100=MOUNTED_XERICS_TALISMAN", saved);
	}

	@Test
	public void restoringDifferentProfileDropsAllPreviousPositions()
	{
		observe("0:1840:7040", TeleportSource.FAIRY_RING);
		inventory.restore("1:1900:7100=JEWELLERY_BOX");

		assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), inventory.requiredMenus());
		assertEquals("1:1900:7100=JEWELLERY_BOX", inventory.encode());
		inventory.restore(null);
		assertFalse(inventory.isSurveyed());
		assertTrue(inventory.installedSources().isEmpty());
	}

	@Test
	public void persistedEmptySurveyIsDistinctFromNoSurvey()
	{
		inventory.restore("");
		assertTrue(inventory.isSurveyed());
		assertTrue(inventory.requiredMenus().isEmpty());
		inventory.restore(null);
		assertFalse(inventory.isSurveyed());
	}

	@Test
	public void malformedEntriesAndUnknownFutureSourcesDoNotInventMenuRequirements()
	{
		inventory.restore("garbage;4:1:1=JEWELLERY_BOX;-1:1:1=PORTAL_NEXUS;"
			+ "0:x:1=JEWELLERY_BOX;0:1840:7040=FUTURE_SOURCE,SPIRIT_TREE;1:1900:7100=FUTURE_SOURCE");

		assertEquals(Set.of(TeleportSource.SPIRIT_TREE), inventory.requiredMenus());
		assertEquals("0:1840:7040=SPIRIT_TREE", inventory.encode());
	}

	@Test
	public void fixtureVersionsPersistAlongsideLegacySourceOnlyEntries()
	{
		inventory.restore("0:1840:7040=FAIRY_RING;1:1900:7100=JEWELLERY_BOX#37546,37520");

		assertEquals("", inventory.capabilitySignature(TeleportSource.FAIRY_RING));
		assertEquals("29156", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("0:1840:7040=FAIRY_RING;1:1900:7100=JEWELLERY_BOX#37520,37546", inventory.encode());
		HouseInventory restored = new HouseInventory();
		restored.restore(inventory.encode());
		assertEquals(inventory.requiredMenus(), restored.requiredMenus());
		assertEquals(inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX),
			restored.capabilitySignature(TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void aSourceWithoutNativeVersionEvidenceHasAnEmptySignature()
	{
		observe("0:1840:7040", TeleportSource.JEWELLERY_BOX);

		assertEquals("", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("", inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		assertEquals("0:1840:7040=JEWELLERY_BOX", inventory.encode());
	}

	@Test
	public void movingSameFixtureDoesNotChangeCapabilitySignature()
	{
		observeVersion("0:1840:7040", TeleportSource.PORTAL_NEXUS, ObjectID.POH_TELENEXUS_3);
		String previous = inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS);
		inventory.observe(Set.of("0:1840:7040", "1:1900:7100"),
			Map.of("1:1900:7100", Set.of(TeleportSource.PORTAL_NEXUS)),
			Map.of("1:1900:7100", Set.of(ObjectID.POH_TELENEXUS_3)));

		assertEquals(previous, inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		assertEquals("1:1900:7100=PORTAL_NEXUS#33351", inventory.encode());
	}

	@Test
	public void replacingOrRemovingLoadedFixtureUpdatesOnlyItsKnownVersion()
	{
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_3_BASE);
		observeVersion("1:1900:7100", TeleportSource.PORTAL_NEXUS, ObjectID.POH_TELENEXUS_3);
		String oldBox = inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX);
		String oldNexus = inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS);
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_1_BASE);

		assertFalse(oldBox.equals(inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX)));
		assertEquals(oldNexus, inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		inventory.observe(Set.of("0:1840:7040"), Collections.emptyMap(), Collections.emptyMap());
		assertEquals("", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals(oldNexus, inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
	}

	@Test
	public void everyJewelleryDefaultAndColosseumVariantNormalizesToItsTier()
	{
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_1, "29154");
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_1_COLOSSEUM, "29154");
		for (int id = ObjectID.POH_JEWELLERY_BOX_1_BASE; id <= ObjectID.POH_JEWELLERY_BOX_1_WINTERTODT; id++)
		{
			assertTierSignature(id, "29154");
		}
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_2, "29155");
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_2_COLOSSEUM, "29155");
		for (int id = ObjectID.POH_JEWELLERY_BOX_2_BASE; id <= ObjectID.POH_JEWELLERY_BOX_2_FARMING_GUILD; id++)
		{
			assertTierSignature(id, "29155");
		}
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_3, "29156");
		assertTierSignature(ObjectID.POH_JEWELLERY_BOX_3_COLOSSEUM, "29156");
		for (int id = ObjectID.POH_JEWELLERY_BOX_3_BASE; id <= ObjectID.POH_JEWELLERY_BOX_3_KHARID; id++)
		{
			assertTierSignature(id, "29156");
		}
	}

	@Test
	public void repeatedIdenticalFixturesAndDefaultChangesDoNotAlterCapabilityUnion()
	{
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_3_BASE);
		String previous = inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX);
		observeVersion("1:1900:7100", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_3_EDGEVILLE);
		assertEquals(previous, inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		inventory.observe(Set.of("0:1840:7040"), Collections.emptyMap(), Collections.emptyMap());
		assertEquals(previous, inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
	}

	@Test
	public void signaturesAreNumericSortedUnionsAcrossAllKnownPositions()
	{
		inventory.observe(Set.of("0:1840:7040", "1:1900:7100"), Map.of(
			"0:1840:7040", Set.of(TeleportSource.PORTAL_NEXUS),
			"1:1900:7100", Set.of(TeleportSource.PORTAL_NEXUS)), Map.of(
			"0:1840:7040", Set.of(ObjectID.POH_NEXUS_PORTAL_LEAGUE_5, ObjectID.POH_TELENEXUS_1),
			"1:1900:7100", Set.of(ObjectID.POH_TELENEXUS_1, ObjectID.POH_TELENEXUS_3)));

		assertEquals("33346,33351,56075", inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
	}

	@Test
	public void partialOrUncoveredVersionsCannotErasePreviousNativeEvidence()
	{
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_3_BASE);
		inventory.observe(Set.of("1:1900:7100"), Map.of("0:1840:7040", Set.of(TeleportSource.JEWELLERY_BOX)),
			Map.of("0:1840:7040", Set.of(ObjectID.POH_JEWELLERY_BOX_1_BASE)));

		assertEquals("29156", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("0:1840:7040=JEWELLERY_BOX#37520", inventory.encode());
	}

	@Test
	public void mutableObservedVersionSetsAreCopied()
	{
		Set<Integer> ids = new HashSet<>(Set.of(ObjectID.POH_JEWELLERY_BOX_3_BASE));
		Map<String, Set<Integer>> versions = new HashMap<>();
		versions.put("0:1840:7040", ids);
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1840:7040", Set.of(TeleportSource.JEWELLERY_BOX)), versions);
		ids.clear();
		versions.clear();

		assertEquals("29156", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("0:1840:7040=JEWELLERY_BOX#37520", inventory.encode());
	}

	@Test
	public void malformedVersionTokensCannotProduceNativeCapabilities()
	{
		inventory.restore("0:1840:7040=JEWELLERY_BOX#garbage,-1,0,999999999999,37546,37546;"
			+ "1:1900:7100=FUTURE_SOURCE#33351;0:16384:7040=PORTAL_NEXUS#33351");

		assertEquals(Set.of(TeleportSource.JEWELLERY_BOX), inventory.requiredMenus());
		assertEquals("29156", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("", inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		assertEquals("0:1840:7040=JEWELLERY_BOX#37546", inventory.encode());
	}

	@Test
	public void restoringAnotherProfileClearsOldNativeVersions()
	{
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, ObjectID.POH_JEWELLERY_BOX_3_BASE);
		inventory.restore("1:1900:7100=PORTAL_NEXUS#33351");

		assertEquals("", inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
		assertEquals("33351", inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		inventory.restore(null);
		assertEquals("", inventory.capabilitySignature(TeleportSource.PORTAL_NEXUS));
		assertFalse(inventory.isSurveyed());
	}

	@Test
	public void movingMenuFixtureAwayFromColocatedDecorationKeepsItsVersion()
	{
		inventory.observe(Set.of("0:1840:7040"), Map.of("0:1840:7040",
			Set.of(TeleportSource.JEWELLERY_BOX, TeleportSource.MOUNTED_GLORY)), Map.of("0:1840:7040",
			Set.of(ObjectID.POH_JEWELLERY_BOX_3_BASE, ObjectID.POH_TROPHY_AMULETOFGLORY_4)));
		String previous = inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX);
		assertEquals("29156", previous);
		inventory.observe(Set.of("0:1840:7040", "1:1900:7100"), Map.of(
			"0:1840:7040", Set.of(TeleportSource.MOUNTED_GLORY),
			"1:1900:7100", Set.of(TeleportSource.JEWELLERY_BOX)), Map.of(
			"0:1840:7040", Set.of(ObjectID.POH_TROPHY_AMULETOFGLORY_4),
			"1:1900:7100", Set.of(ObjectID.POH_JEWELLERY_BOX_3_BASE)));

		assertEquals(previous, inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
	}

	private void observeVersion(String position, TeleportSource source, int objectId)
	{
		inventory.observe(Set.of(position), Map.of(position, Set.of(source)), Map.of(position, Set.of(objectId)));
	}

	private void assertTierSignature(int objectId, String expected)
	{
		observeVersion("0:1840:7040", TeleportSource.JEWELLERY_BOX, objectId);
		assertEquals("native box " + objectId, expected, inventory.capabilitySignature(TeleportSource.JEWELLERY_BOX));
	}

	private void observe(String position, TeleportSource source)
	{
		inventory.observe(Set.of(position), Map.of(position, Set.of(source)));
	}

	private static void assertImmutable(Set<TeleportSource> sources)
	{
		try
		{
			sources.clear();
			throw new AssertionError("Expected an immutable inventory snapshot");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}
}
