package com.clueteleports;

import java.util.Arrays;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpiritTreeReaderTest
{
	private final MenuReaderFixture fixture = new MenuReaderFixture();
	private static final String[] BASELINE = {"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange"};

	@Test
	public void unopenedOrHiddenMenuIsNotAnObservation()
	{
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1,
			MenuReaderFixture.widget("", 0, true, rows(BASELINE, "735a28")));
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void readsTheOriginalMenuAndExcludesGrayTrees()
	{
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "<col=5f5f5f>Port Sarim</col>", "Farming Guild", "Your house");
		Set<String> available = SpiritTreeReader.readOpenMenu(fixture.client).get();
		assertTrue(available.contains("tree-farming-guild"));
		assertFalse(available.contains("tree-port-sarim"));
		assertEquals(5, available.size());
	}

	@Test
	public void readsTheNewMenu()
	{
		putMenu(InterfaceID.MenuNew.TEXT, "ffffff", BASELINE);
		assertEquals(4, SpiritTreeReader.readOpenMenu(fixture.client).get().size());
	}

	@Test
	public void readsEveryCurrentServerLabelAndHouseLocationSuffix()
	{
		// Labels verified against Teleport Maps' game-facing TreeDefinitions.json.
		putMenu(InterfaceID.MenuNew.TEXT, "ffffff", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Feldip Hills", "Prifddinas", "Port Sarim",
			"Etceteria", "Brimhaven", "Hosidius", "Farming Guild", "Your house (Rimmington)",
			"Poison Waste", "Laguna Aurorae");
		Set<String> available = SpiritTreeReader.readOpenMenu(fixture.client).get();
		assertEquals(13, available.size());
		assertTrue(available.contains("tree-laguna-aurorae"));
		assertTrue(available.contains("tree-battlefield-of-khazard"));
	}

	@Test
	public void supportsMobileRowsWithoutHotkeys()
	{
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			Arrays.stream(BASELINE).map(MenuReaderFixture::text).toArray(Widget[]::new)));
		assertEquals(4, SpiritTreeReader.readOpenMenu(fixture.client).get().size());
	}

	@Test
	public void omittedQuestLockedDestinationsAreUnavailableInACompleteMenu()
	{
		putMenu(InterfaceID.MenuNew.TEXT, "ffffff", BASELINE);
		Set<String> available = SpiritTreeReader.readOpenMenu(fixture.client).get();
		assertFalse(available.contains("tree-prifddinas"));
		assertFalse(available.contains("tree-laguna-aurorae"));
		assertTrue(available.contains("tree-grand-exchange"));
	}

	@Test
	public void newlyDisabledTreeIsRemovedFromTheNextSnapshot()
	{
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Port Sarim");
		assertTrue(SpiritTreeReader.readOpenMenu(fixture.client).get().contains("tree-port-sarim"));
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "<col=5f5f5f>Port Sarim</col>");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).get().contains("tree-port-sarim"));
	}

	@Test
	public void hiddenRowsWithoutOriginalInputAreNotAuthoritative()
	{
		Widget[] rows = rows(new String[]{"Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Port Sarim"}, "735a28");
		rows[4] = MenuReaderFixture.widget("<col=735a28>5</col>: <str>Port Sarim</str>",
			0, true, new Widget[0]);
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(rows));
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void unexplainedHiddenTreeDoesNotReplaceTheCachedNetwork()
	{
		Widget[] rows = rows(new String[]{"Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Port Sarim"}, "735a28");
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(rows));
		assertTrue(SpiritTreeReader.readOpenMenu(fixture.client).get().contains("tree-port-sarim"));
		rows[4] = MenuReaderFixture.widget("<col=735a28>5</col>: Port Sarim", 0, true, new Widget[0]);
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void hiddenEmptyFrameworkRowsAreSkippedBeforeAndAfterTheMenu()
	{
		Widget[] baseline = rows(BASELINE, "ffffff");
		Widget[] withFramework = new Widget[baseline.length + 2];
		withFramework[0] = MenuReaderFixture.widget("", 0, true, new Widget[0]);
		System.arraycopy(baseline, 0, withFramework, 1, baseline.length);
		withFramework[withFramework.length - 1] = MenuReaderFixture.widget(null, 0, true, new Widget[0]);
		fixture.widgets.put(InterfaceID.MenuNew.TEXT, MenuReaderFixture.container(withFramework));
		assertEquals(4, SpiritTreeReader.readOpenMenu(fixture.client).get().size());
		withFramework[withFramework.length - 1] = MenuReaderFixture.text("");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void customChordsAlternateColonPositionsAndUnsetKeysAreAccepted()
	{
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(
			MenuReaderFixture.text("<col=735a28>Ctrl+T</col>: Tree Gnome Village"),
			MenuReaderFixture.text("<col=735a28>Shift+G:</col> Gnome Stronghold"),
			MenuReaderFixture.text("Alt+B: Battlefield of Khazard"),
			MenuReaderFixture.text("Grand Exchange"),
			MenuReaderFixture.text("<col=735a28>Ctrl+S</col>: <col=5f5f5f>Port Sarim</col>"),
			MenuReaderFixture.text("<col=735a28>Ctrl+F:</col> <str>Farming Guild</str>")));
		Set<String> available = SpiritTreeReader.readOpenMenu(fixture.client).get();
		assertEquals(4, available.size());
		assertTrue(available.contains("tree-gnome-stronghold"));
		assertFalse(available.contains("tree-port-sarim"));
		assertFalse(available.contains("tree-farming-guild"));
	}

	@Test
	public void unrelatedMenuOrUnknownRowsAreNotAuthoritative()
	{
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Another menu", "Tree Gnome Village",
			"Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Unknown new tree");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void loadingGapsAndMissingBaselineAreNotAuthoritative()
	{
		Widget[] partial = rows(BASELINE, "735a28");
		partial[2] = null;
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(partial));
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold", "Grand Exchange", "Port Sarim");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
		partial = rows(BASELINE, "735a28");
		partial[2] = MenuReaderFixture.text("");
		fixture.widgets.put(InterfaceID.Menu.LJ_LAYER1, MenuReaderFixture.container(partial));
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void duplicateRowsAreNotACompleteNetwork()
	{
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", "Tree Gnome Village", "Gnome Stronghold",
			"Battlefield of Khazard", "Grand Exchange", "Grand Exchange");
		assertFalse(SpiritTreeReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void snapshotsCannotBeMutated()
	{
		putMenu(InterfaceID.Menu.LJ_LAYER1, "735a28", BASELINE);
		SpiritTreeReader.readOpenMenu(fixture.client).get().clear();
	}

	private void putMenu(int component, String prefixColor, String... names)
	{
		fixture.widgets.put(component, MenuReaderFixture.container(rows(names, prefixColor)));
	}

	private Widget[] rows(String[] names, String prefixColor)
	{
		Widget[] rows = new Widget[names.length];
		for (int index = 0; index < names.length; index++)
		{
			String key = index < 9 ? Integer.toString(index + 1) : Character.toString((char) ('a' + index - 9));
			rows[index] = MenuReaderFixture.text("<col=" + prefixColor + ">" + key + "</col>: " + names[index]);
		}
		return rows;
	}
}
