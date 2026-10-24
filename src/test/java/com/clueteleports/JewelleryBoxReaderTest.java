package com.clueteleports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.api.ScriptEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JewelleryBoxReaderTest
{
	private final MenuReaderFixture fixture = new MenuReaderFixture();
	private static final int[] COMPONENTS = {InterfaceID.PohJewelleryBox.DUELING, InterfaceID.PohJewelleryBox.GAMING,
		InterfaceID.PohJewelleryBox.COMBAT, InterfaceID.PohJewelleryBox.SKILLS,
		InterfaceID.PohJewelleryBox.WEALTH, InterfaceID.PohJewelleryBox.GLORY};
	private static final String[] HEADERS = {"Ring of Dueling", "Games Necklace", "Combat bracelet", "Skills necklace", "Ring of Wealth", "Amulet of Glory"};
	private static final String[][] NAMES = {
		{"Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum"},
		{"Burthorpe", "Barbarian Outpost", "Corporeal Beast", "Tears of Guthix", "Wintertodt Camp"},
		{"Warriors' Guild", "Champions' Guild", "Monastery", "Ranging Guild"},
		{"Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild", "Woodcutting Guild", "Farming Guild"},
		{"Miscellania", "Grand Exchange", "Falador Park", "Dondakan's Rock"},
		{"Edgeville", "Karamja", "Draynor Village", "Al Kharid"}
	};

	@Test
	public void unopenedOrHiddenMenuIsNotAnObservation()
	{
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		menu(3, Collections.emptySet());
		fixture.widgets.put(InterfaceID.PohJewelleryBox.UNIVERSE,
			MenuReaderFixture.widget("", 0, true, placeholders()));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void basicBoxIncludesOnlyDuelingAndGamesDestinations()
	{
		menu(1, Collections.emptySet());
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(9, available.size());
		assertTrue(available.contains("jewellery-chasm-of-tears"));
		assertFalse(available.contains("jewellery-warriors-guild"));
		assertFalse(available.contains("jewellery-edgeville"));
	}

	@Test
	public void fancyBoxExcludesWealthAndGlory()
	{
		menu(2, Collections.emptySet());
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(19, available.size());
		assertTrue(available.contains("jewellery-farming-guild"));
		assertFalse(available.contains("jewellery-miscellania"));
	}

	@Test
	public void ornateBoxIncludesAllBuiltOptions()
	{
		menu(3, Collections.emptySet());
		assertEquals(27, JewelleryBoxReader.readOpenMenu(fixture.client).get().size());
	}

	@Test
	public void struckQuestAndRegionalUnlocksAreExcluded()
	{
		menu(3, Set.of("Fortis Colosseum", "Tears of Guthix", "Wintertodt Camp", "Woodcutting Guild",
			"Farming Guild", "Miscellania", "Dondakan's Rock"));
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(20, available.size());
		assertFalse(available.contains("jewellery-fortis-colosseum"));
		assertFalse(available.contains("jewellery-chasm-of-tears"));
		assertFalse(available.contains("jewellery-farming-guild"));
		assertFalse(available.contains("jewellery-dondakans-rock"));
		assertTrue(available.contains("jewellery-castle-wars"));
	}

	@Test
	public void wrapperKeysWithColonOutsideTheColorTagAreAccepted()
	{
		menu(1, Collections.emptySet());
		fixture.widgets.put(COMPONENTS[0], MenuReaderFixture.container(
			MenuReaderFixture.text(HEADERS[0]),
			MenuReaderFixture.text("<col=ccccff>1</col>: Emir's Arena"),
			MenuReaderFixture.text("<col=ccccff>2</col>: Castle Wars"),
			MenuReaderFixture.text("<col=ccccff>3</col>: Ferox Enclave"),
			MenuReaderFixture.text("<col=ccccff>4</col>: <str>Fortis Colosseum</str>")));
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(8, available.size());
		assertFalse(available.contains("jewellery-fortis-colosseum"));
	}

	@Test
	public void customChordsAndUnboundOptionsPreserveDestinationLocks()
	{
		menu(1, Collections.emptySet());
		fixture.widgets.put(COMPONENTS[0], MenuReaderFixture.container(
			MenuReaderFixture.text(HEADERS[0]),
			MenuReaderFixture.text("<col=ccccff>Ctrl+G:</col> Emir's Arena"),
			MenuReaderFixture.text("Castle Wars"),
			MenuReaderFixture.text("Shift+F: <col=5f5f5f>Ferox Enclave</col>"),
			MenuReaderFixture.text("<str>Fortis Colosseum</str>")));
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(7, available.size());
		assertTrue(available.contains("jewellery-emirs-arena"));
		assertTrue(available.contains("jewellery-castle-wars"));
		assertFalse(available.contains("jewellery-ferox-enclave"));
		assertFalse(available.contains("jewellery-fortis-colosseum"));
	}

	@Test
	public void aLowerTierProducesAReplacementWithoutPreviouslyBuiltOptions()
	{
		menu(3, Collections.emptySet());
		assertTrue(JewelleryBoxReader.readOpenMenu(fixture.client).get().contains("jewellery-edgeville"));
		menu(1, Collections.emptySet());
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).get().contains("jewellery-edgeville"));
	}

	@Test
	public void incompleteUniverseOrSectionIsNotAuthoritative()
	{
		menu(3, Collections.emptySet());
		Widget[] partial = placeholders();
		partial[26] = null;
		fixture.widgets.put(InterfaceID.PohJewelleryBox.UNIVERSE, MenuReaderFixture.container(partial));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		menu(3, Collections.emptySet());
		fixture.widgets.remove(InterfaceID.PohJewelleryBox.GLORY);
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		menu(3, Collections.emptySet());
		fixture.widgets.put(InterfaceID.PohJewelleryBox.GLORY,
			MenuReaderFixture.container(MenuReaderFixture.text("Amulet of Glory")));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void aMissingDestinationOrLoadingGapDoesNotEraseTheCache()
	{
		menu(3, Collections.emptySet());
		fixture.widgets.put(InterfaceID.PohJewelleryBox.GLORY, MenuReaderFixture.container(
			MenuReaderFixture.text("Amulet of Glory"), MenuReaderFixture.text("<col=ccccff>a:</col> Edgeville")));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		fixture.widgets.put(InterfaceID.PohJewelleryBox.GLORY,
			MenuReaderFixture.container(MenuReaderFixture.text("Amulet of Glory"), null));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void inconsistentBoxTiersAreNotAuthoritative()
	{
		menu(1, Collections.emptySet());
		fixture.widgets.put(InterfaceID.PohJewelleryBox.GLORY, section(5, true, Collections.emptySet()));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
	}

	@Test
	public void aFullyLockedMenuCanAuthoritativelyReturnAnEmptySet()
	{
		Set<String> locked = new java.util.HashSet<>();
		for (String[] section : NAMES)
		{
			Collections.addAll(locked, section);
		}
		menu(3, locked);
		assertTrue(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		assertTrue(JewelleryBoxReader.readOpenMenu(fixture.client).get().isEmpty());
	}

	@Test
	public void nativeOperationsIdentifyArbitraryNamesAndColorsAcrossEverySection()
	{
		menu(3, Collections.emptySet());
		int destination = 0;
		for (int section = 0; section < COMPONENTS.length; section++)
		{
			List<Widget> rows = new ArrayList<>();
			rows.add(MenuReaderFixture.text(HEADERS[section]));
			for (int index = 0; index < NAMES[section].length; index++)
			{
				rows.add(nativeRow(section, index, destination++, "<col=808080>" + HEADERS[section] + "</col>",
					NAMES[section][index]));
			}
			// Visual order and duplicate replacement names are not identity.
			Collections.reverse(rows);
			fixture.widgets.put(COMPONENTS[section], MenuReaderFixture.container(rows.toArray(new Widget[0])));
		}
		assertEquals(27, JewelleryBoxReader.readOpenMenu(fixture.client).get().size());
	}

	@Test
	public void nativeLocksSurviveCosmeticReplacementsThatRemoveTheirMarkup()
	{
		menu(1, Collections.emptySet());
		fixture.widgets.put(COMPONENTS[0], MenuReaderFixture.container(
			MenuReaderFixture.text(HEADERS[0]),
			nativeRow(0, 0, 0, "Arena", "Emir's Arena"),
			nativeRow(0, 1, 1, "Bank", "Castle Wars"),
			nativeRow(0, 2, 2, "Restore", "Ferox Enclave"),
			nativeRow(0, 3, 3, "Colosseum", "<str>Fortis Colosseum</str>")));
		Set<String> available = JewelleryBoxReader.readOpenMenu(fixture.client).get();
		assertEquals(8, available.size());
		assertFalse(available.contains("jewellery-fortis-colosseum"));
		assertFalse(available.contains("jewellery-edgeville"));
	}

	@Test
	public void malformedOrMismatchedNativeOperationsCannotAuthorizeAReplacement()
	{
		Object[][] invalid = {
			{1691, ScriptEvent.MENU_OP, 0, COMPONENTS[0], 0, "Emir's Arena", "1", ""},
			{1690, 1, 0, COMPONENTS[0], 0, "Emir's Arena", "1", ""},
			{1690, ScriptEvent.MENU_OP, 27, COMPONENTS[0], 0, "Emir's Arena", "1", ""},
			{1690, ScriptEvent.MENU_OP, 0, COMPONENTS[1], 0, "Emir's Arena", "1", ""},
			{1690, ScriptEvent.MENU_OP, 0, COMPONENTS[0], 99, "Emir's Arena", "1", ""},
			{1690, ScriptEvent.MENU_OP, 0, COMPONENTS[0], 0, "Castle Wars", "1", ""},
			{1690, ScriptEvent.MENU_OP, 0, COMPONENTS[0], 0, "Emir's Arena", null, ""},
			{1690, ScriptEvent.MENU_OP}
		};
		for (Object[] listener : invalid)
		{
			menu(1, Collections.emptySet());
			Widget invalidRow = MenuReaderFixture.widget("Emir's Arena", 0xffffff, false,
				new Widget[0], COMPONENTS[0], 0, listener);
			fixture.widgets.put(COMPONENTS[0], MenuReaderFixture.container(
				MenuReaderFixture.text(HEADERS[0]), invalidRow,
				MenuReaderFixture.text("Castle Wars"), MenuReaderFixture.text("Ferox Enclave"),
				MenuReaderFixture.text("Fortis Colosseum")));
			assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
		}
	}

	@Test
	public void duplicateNativeDestinationsDoNotSatisfyACompleteSection()
	{
		menu(1, Collections.emptySet());
		fixture.widgets.put(COMPONENTS[0], MenuReaderFixture.container(
			MenuReaderFixture.text(HEADERS[0]),
			nativeRow(0, 0, 0, "First", "Emir's Arena"),
			nativeRow(0, 1, 1, "Second", "Castle Wars"),
			nativeRow(0, 2, 1, "Third", "Castle Wars"),
			nativeRow(0, 3, 3, "Fourth", "Fortis Colosseum")));
		assertFalse(JewelleryBoxReader.readOpenMenu(fixture.client).isPresent());
	}

	private Widget nativeRow(int section, int child, int destination, String display, String original)
	{
		Object[] listener = {1690, ScriptEvent.MENU_OP, destination, COMPONENTS[section], child,
			"<col=ccccff>1:</col> " + original, "1", ""};
		return MenuReaderFixture.widget(display, 0x808080, false, new Widget[0], COMPONENTS[section], child, listener);
	}

	@Test(expected = UnsupportedOperationException.class)
	public void snapshotsCannotBeMutated()
	{
		menu(3, Collections.emptySet());
		JewelleryBoxReader.readOpenMenu(fixture.client).get().clear();
	}

	private void menu(int tier, Set<String> locked)
	{
		fixture.widgets.put(InterfaceID.PohJewelleryBox.UNIVERSE, MenuReaderFixture.container(placeholders()));
		for (int index = 0; index < COMPONENTS.length; index++)
		{
			fixture.widgets.put(COMPONENTS[index], section(index, index / 2 + 1 <= tier, locked));
		}
	}

	private Widget section(int index, boolean built, Set<String> locked)
	{
		List<Widget> children = new ArrayList<>();
		children.add(MenuReaderFixture.text("")); // Background and border graphics have no text.
		children.add(MenuReaderFixture.widget(HEADERS[index], built ? 0xffffff : 0x7f7f7f, false, new Widget[0]));
		if (built)
		{
			int key = 0;
			for (String name : NAMES[index])
			{
				String label = locked.contains(name) ? "<str>" + name + "</str>" : name;
				children.add(MenuReaderFixture.text("<col=ccccff>" + (char) ('a' + key++) + ":</col> " + label));
			}
		}
		return MenuReaderFixture.container(children.toArray(new Widget[0]));
	}

	private Widget[] placeholders()
	{
		Widget[] result = new Widget[27];
		for (int index = 0; index < result.length; index++)
		{
			result[index] = MenuReaderFixture.text("");
		}
		return result;
	}
}
