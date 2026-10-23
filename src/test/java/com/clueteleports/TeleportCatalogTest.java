package com.clueteleports;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeleportCatalogTest
{
	@Test
	public void catalogIncludesEverySupportedTransportFamily()
	{
		assertEquals(54, TeleportCatalog.forSource(TeleportSource.FAIRY_RING).size());
		assertEquals(13, TeleportCatalog.forSource(TeleportSource.SPIRIT_TREE).size());
		assertEquals(42, TeleportCatalog.forSource(TeleportSource.PORTAL_NEXUS).size());
		assertEquals(27, TeleportCatalog.forSource(TeleportSource.JEWELLERY_BOX).size());
		assertEquals(42, TeleportCatalog.forSource(TeleportSource.PORTAL_CHAMBER).size());
		assertEquals(6, TeleportCatalog.forSource(TeleportSource.WILDERNESS_OBELISK).size());
		assertEquals(4, TeleportCatalog.forSource(TeleportSource.MOUNTED_GLORY).size());
		assertEquals(3, TeleportCatalog.forSource(TeleportSource.MOUNTED_DIGSITE_PENDANT).size());
		assertEquals(5, TeleportCatalog.forSource(TeleportSource.MOUNTED_XERICS_TALISMAN).size());
		assertEquals(1, TeleportCatalog.forSource(TeleportSource.MOUNTED_MYTHICAL_CAPE).size());
		assertEquals(39, TeleportCatalog.forSource(TeleportSource.CAPE_HANGER).size());
		assertEquals(9, TeleportCatalog.forSource(TeleportSource.HOUSE_EXIT).size());
		for (TeleportSource source : TeleportSource.values())
		{
			assertFalse(source.name(), TeleportCatalog.forSource(source).isEmpty());
		}
	}

	@Test
	public void idsAreUniqueAndRoundTrip()
	{
		Set<String> ids = new HashSet<>();
		for (TeleportDestination destination : TeleportCatalog.all())
		{
			assertTrue(destination.getId(), ids.add(destination.getId()));
			assertEquals(destination, TeleportCatalog.byId(destination.getId()).get());
			assertFalse(destination.getName().isEmpty());
		}
	}

	@Test
	public void onlyJewelleryBoxItemsAreInTheJewelleryCatalog()
	{
		assertFalse(TeleportCatalog.byId("jewellery-digsite").isPresent());
		assertFalse(TeleportCatalog.byId("jewellery-myths-guild").isPresent());
		assertFalse(TeleportCatalog.byId("jewellery-xerics-heart").isPresent());
		assertEquals(1, TeleportCatalog.jewelleryTier(TeleportCatalog.byId("jewellery-castle-wars").get()));
		assertEquals(2, TeleportCatalog.jewelleryTier(TeleportCatalog.byId("jewellery-farming-guild").get()));
		assertEquals(3, TeleportCatalog.jewelleryTier(TeleportCatalog.byId("jewellery-edgeville").get()));
		assertEquals(0, TeleportCatalog.jewelleryTier(TeleportCatalog.byId("nexus-varrock").get()));
	}

	@Test
	public void pohReturnAndMovingDestinationsHaveNoInventedFixedLanding()
	{
		assertFalse(TeleportCatalog.byId("fairy-diq").isPresent());
		assertFalse(TeleportCatalog.byId("tree-your-house").isPresent());
		assertFalse(TeleportCatalog.nexusDestination("Teleport to House").isPresent());
		assertFalse(TeleportCatalog.nexusDestination("Teleport to Target").isPresent());
		assertFalse(TeleportCatalog.nexusDestination("Respawn").isPresent());
		assertFalse(TeleportCatalog.nexusDestination("Boat").isPresent());
	}

	@Test
	public void includesFebruary2026NexusAdditionsAndCorrectDungeonPlanes()
	{
		assertEquals("nexus-trollheim", TeleportCatalog.nexusDestination("Trollheim").get().getId());
		assertEquals("nexus-ourania", TeleportCatalog.nexusDestination("Ourania Cave").get().getId());
		assertEquals(new WorldPoint(2771, 9102, 0),
			TeleportCatalog.nexusDestination("Ape Atoll Dungeon").get().getLocation());
		assertEquals(1, TeleportCatalog.nexusDestination("Marim").get().getLocation().getPlane());
		assertEquals(2, TeleportCatalog.byId("jewellery-chasm-of-tears").get().getLocation().getPlane());
		assertEquals(2, TeleportCatalog.byId("jewellery-corporeal-beast").get().getLocation().getPlane());
	}

	@Test
	public void nexusNamesSupportAlternateMenuNamesAndCustomHotkeyPrefixes()
	{
		assertEquals("nexus-grand-exchange", TeleportCatalog.nexusDestination("<col=735a28>A</col>: Grand Exchange").get().getId());
		assertEquals("nexus-kourend", TeleportCatalog.nexusDestination("Great Kourend").get().getId());
		assertEquals("nexus-kharyrll", TeleportCatalog.nexusDestination("Canifis (Kharyrll)").get().getId());
		assertEquals("nexus-yanille", TeleportCatalog.nexusDestination("Yanille").get().getId());
		assertEquals("nexus-watchtower", TeleportCatalog.nexusDestination("Watchtower").get().getId());
	}

	@Test
	public void allFortyFiveInstalledSlotsAreDistinctAndCannotBeMutatedByCallers()
	{
		int[] slots = TeleportCatalog.nexusSlotVarbits();
		Set<Integer> distinct = new HashSet<>();
		for (int slot : slots)
		{
			assertTrue(distinct.add(slot));
		}
		assertEquals(45, slots.length);
		assertTrue(distinct.contains(20120));
		slots[0] = -1;
		assertEquals(6654, TeleportCatalog.nexusSlotVarbits()[0]);
	}

	@Test
	public void chamberPortalsKeepTheirOwnSourceAndTheSameFixedNexusLanding()
	{
		for (TeleportDestination nexus : TeleportCatalog.forSource(TeleportSource.PORTAL_NEXUS))
		{
			TeleportDestination portal = TeleportCatalog.portalDestination(nexus.getName()).get();
			assertEquals(TeleportSource.PORTAL_CHAMBER, portal.getSource());
			assertEquals("portal-" + nexus.getId().substring("nexus-".length()), portal.getId());
			assertEquals(nexus.getLocation(), portal.getLocation());
		}
		assertEquals("portal-grand-exchange",
			TeleportCatalog.portalDestination("<col=ff0000>Grand Exchange Portal</col>").get().getId());
		assertEquals("portal-watchtower", TeleportCatalog.portalDestination("Yanille Watchtower Portal").get().getId());
		assertEquals("portal-ourania", TeleportCatalog.portalDestination("Ourania Cave Portal").get().getId());
		assertFalse(TeleportCatalog.portalDestination("Respawn Portal").isPresent());
		assertFalse(TeleportCatalog.portalDestination("Boat Portal").isPresent());
		assertFalse(TeleportCatalog.portalDestination(null).isPresent());
	}

	@Test
	public void obelisksCoverAllSixWildernessLandingCentres()
	{
		assertLanding("obelisk-13", TeleportSource.WILDERNESS_OBELISK, 3156, 3620, 0);
		assertLanding("obelisk-19", TeleportSource.WILDERNESS_OBELISK, 3227, 3667, 0);
		assertLanding("obelisk-27", TeleportSource.WILDERNESS_OBELISK, 3035, 3732, 0);
		assertLanding("obelisk-35", TeleportSource.WILDERNESS_OBELISK, 3106, 3794, 0);
		assertLanding("obelisk-44", TeleportSource.WILDERNESS_OBELISK, 2980, 3866, 0);
		assertLanding("obelisk-50", TeleportSource.WILDERNESS_OBELISK, 3307, 3916, 0);
	}

	@Test
	public void mountedGlorySharesOnlyTheFourGloryJewelleryArrivals()
	{
		for (String suffix : new String[]{"edgeville", "karamja", "draynor-village", "al-kharid"})
		{
			TeleportDestination glory = TeleportCatalog.byId("glory-" + suffix).get();
			assertEquals(TeleportSource.MOUNTED_GLORY, glory.getSource());
			assertEquals(TeleportCatalog.byId("jewellery-" + suffix).get().getLocation(), glory.getLocation());
		}
	}

	@Test
	public void mountedPendantAndXericDestinationsRetainDistinctLandingPointsAndPlanes()
	{
		assertLanding("digsite-digsite", TeleportSource.MOUNTED_DIGSITE_PENDANT, 3341, 3445, 0);
		assertLanding("digsite-fossil-island", TeleportSource.MOUNTED_DIGSITE_PENDANT, 3763, 3869, 1);
		assertLanding("digsite-lithkren", TeleportSource.MOUNTED_DIGSITE_PENDANT, 3549, 10456, 0);
		assertLanding("xeric-lookout", TeleportSource.MOUNTED_XERICS_TALISMAN, 1579, 3530, 0);
		assertLanding("xeric-glade", TeleportSource.MOUNTED_XERICS_TALISMAN, 1752, 3566, 0);
		assertLanding("xeric-inferno", TeleportSource.MOUNTED_XERICS_TALISMAN, 1504, 3815, 0);
		assertLanding("xeric-heart", TeleportSource.MOUNTED_XERICS_TALISMAN, 1644, 3673, 0);
		assertLanding("xeric-honour", TeleportSource.MOUNTED_XERICS_TALISMAN, 1254, 3560, 0);
	}

	@Test
	public void mythicalTrophyAndCapeHangerKeepSeparateAvailabilitySources()
	{
		assertLanding("mythical-myths-guild", TeleportSource.MOUNTED_MYTHICAL_CAPE, 2457, 2850, 0);
		assertLanding("cape-myths-guild", TeleportSource.CAPE_HANGER, 2457, 2850, 0);
		assertFalse(TeleportCatalog.byId("mounted-legends-guild").isPresent());
		assertFalse(TeleportCatalog.byId("cape-boat").isPresent());
	}

	@Test
	public void capeHangerIncludesEachFixedTeleportPerkAndAllSixteenDiaryDestinations()
	{
		assertLanding("cape-crafting-guild", TeleportSource.CAPE_HANGER, 2931, 3286, 0);
		assertLanding("cape-farming-guild", TeleportSource.CAPE_HANGER, 1248, 3725, 0);
		assertLanding("cape-fishing-guild", TeleportSource.CAPE_HANGER, 2604, 3401, 0);
		assertLanding("cape-ottos-grotto", TeleportSource.CAPE_HANGER, 2504, 3484, 0);
		assertLanding("cape-warriors-guild", TeleportSource.CAPE_HANGER, 2865, 3546, 0);
		assertLanding("cape-legends-guild", TeleportSource.CAPE_HANGER, 2729, 3348, 0);
		assertLanding("cape-falo-the-bard", TeleportSource.CAPE_HANGER, 2689, 3547, 0);
		assertLanding("cape-hunter-guild", TeleportSource.CAPE_HANGER, 1558, 3046, 0);
		assertLanding("cape-red-chinchompas", TeleportSource.CAPE_HANGER, 2556, 2916, 0);
		assertLanding("cape-black-chinchompas", TeleportSource.CAPE_HANGER, 3144, 3772, 0);
		assertLanding("cape-pandemonium", TeleportSource.CAPE_HANGER, 3048, 2972, 0);
		assertLanding("cape-ardougne-monastery", TeleportSource.CAPE_HANGER, 2606, 3222, 0);
		assertLanding("cape-ardougne-farming-patch", TeleportSource.CAPE_HANGER, 2673, 3375, 0);
		assertLanding("cape-diary-kaleb-paramaya", TeleportSource.CAPE_HANGER, 2861, 2995, 1);
		assertLanding("cape-diary-twiggy-okorn", TeleportSource.CAPE_HANGER, 3097, 3226, 0);
		assertEquals(16, TeleportCatalog.forSource(TeleportSource.CAPE_HANGER).stream()
			.filter(destination -> destination.getId().startsWith("cape-diary-")).count());
	}

	@Test
	public void houseExitsIncludeEveryFixedCurrentHouseLocation()
	{
		for (String location : new String[]{"rimmington", "taverley", "pollnivneach", "hosidius",
			"rellekka", "aldarin", "brimhaven", "yanille", "prifddinas"})
		{
			TeleportDestination exit = TeleportCatalog.byId("exit-" + location).get();
			assertEquals(TeleportSource.HOUSE_EXIT, exit.getSource());
			assertEquals(0, exit.getLocation().getPlane());
			assertTrue(TeleportCatalog.byId("cape-" + location).isPresent());
		}
		assertLanding("exit-aldarin", TeleportSource.HOUSE_EXIT, 1422, 2963, 0);
		assertFalse(TeleportCatalog.byId("exit-yew-tree").isPresent());
	}

	private static void assertLanding(String id, TeleportSource source, int x, int y, int plane)
	{
		TeleportDestination destination = TeleportCatalog.byId(id).get();
		assertEquals(source, destination.getSource());
		assertEquals(new WorldPoint(x, y, plane), destination.getLocation());
	}
}
