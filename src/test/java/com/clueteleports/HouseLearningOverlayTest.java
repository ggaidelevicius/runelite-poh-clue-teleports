package com.clueteleports;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HouseLearningOverlayTest
{
	private final Map<Integer, Widget> widgets = new HashMap<>();
	private final AtomicInteger widgetReads = new AtomicInteger();
	private final Client client = proxy(Client.class, (method, args) ->
	{
		switch (method)
		{
			case "isClientThread": return true;
			case "getWidget":
				widgetReads.incrementAndGet();
				return widgets.get(args[0]);
			case "getViewportXOffset":
			case "getViewportYOffset": return 0;
			case "getViewportWidth":
			case "getCanvasWidth": return 765;
			case "getViewportHeight":
			case "getCanvasHeight": return 503;
			default: throw new UnsupportedOperationException(method);
		}
	});
	private final HouseLearningOverlay overlay = new HouseLearningOverlay(client, () -> null);

	@Test
	public void guestAndAbsentGuidanceNeverReadOrMarkHouseControls()
	{
		assertTrue(overlay.marks(null).isEmpty());
		assertTrue(overlay.marks(new HouseLearningOverlay.Guidance(false,
			Set.of(TeleportSource.FAIRY_RING), null, null)).isEmpty());
		assertEquals(0, widgetReads.get());
	}

	@Test
	public void openPendingJewelleryMenuGetsAQuietLearningBorder()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(pending(TeleportSource.JEWELLERY_BOX));

		assertEquals(1, marks.size());
		assertEquals(new Rectangle(100, 100, 400, 220), marks.get(0).shape.getBounds());
		assertEquals(HouseLearningOverlay.LEARNING, marks.get(0).color);
		assertEquals("Learning destinations", marks.get(0).label);
		assertFalse(marks.get(0).label.contains("..."));
	}

	@Test
	public void nativeJewelleryKeyMarkupFindsOnlyTheRecommendedChoice()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
			row("<col=ff981f>A:</col> Emir's Arena", new Rectangle(110, 130, 160, 20)),
			row("<col=ff981f>B:</col> Castle Wars", new Rectangle(110, 155, 160, 20)));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(TeleportSource.JEWELLERY_BOX,
			"jewellery-castle-wars"));

		assertEquals(1, marks.size());
		assertEquals(new Rectangle(110, 155, 160, 20), marks.get(0).shape.getBounds());
		assertEquals(HouseLearningOverlay.RECOMMENDED, marks.get(0).color);
	}

	@Test
	public void disabledAndHiddenRowsAreNeverOfferedAsActions()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
			row("<col=ff981f>B:</col> <str>Castle Wars</str>", new Rectangle(110, 155, 160, 20)),
			widget(0, "Castle Wars", new Rectangle(110, 180, 160, 20), true, 0xffffff, new Widget[0]),
			widget(0, "Castle Wars", new Rectangle(110, 205, 160, 20), false, 0x7f7f7f, new Widget[0]));

		assertTrue(overlay.marks(selected(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars")).isEmpty());
	}

	@Test
	public void nativeJewelleryLabelOverridesAnArbitraryDisplayNameAndCosmeticGray()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		Widget custom = nativeJewelleryRow("<col=00ff00>My favourite place</col>",
			"<col=ccccff>B:</col> Castle Wars", false);
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100), custom);

		List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(TeleportSource.JEWELLERY_BOX,
			"jewellery-castle-wars"));

		assertEquals(1, marks.size());
		assertEquals(custom.getBounds(), marks.get(0).shape.getBounds());
	}

	@Test
	public void originalJewelleryLocksAndActualVisibilitySurviveCosmeticRewrites()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		for (String original : new String[]{"B: <str>Castle Wars</str>", "B: <col=808080>Castle Wars</col>"})
		{
			putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
				nativeJewelleryRow("Castle Wars", original, false));
			assertTrue(overlay.marks(selected(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars")).isEmpty());
		}
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
			nativeJewelleryRow("Castle Wars", "B: Castle Wars", true));
		assertTrue(overlay.marks(selected(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars")).isEmpty());
	}

	@Test
	public void malformedJewelleryListenersDoNotFallBackToAnApparentlyMatchingDisplayName()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		Object[][] invalid = {
			{1690, ScriptEvent.MENU_OP, 0, InterfaceID.PohJewelleryBox.DUELING, 2, "B: Castle Wars", "B", ""},
			{999, ScriptEvent.MENU_OP, 1, InterfaceID.PohJewelleryBox.DUELING, 2, "B: Castle Wars", "B", ""},
			{1690, ScriptEvent.MENU_OP, 1, InterfaceID.PohJewelleryBox.GAMING, 2, "B: Castle Wars", "B", ""}};
		for (Object[] listener : invalid)
		{
			putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
				widget(InterfaceID.PohJewelleryBox.DUELING, 2, "Castle Wars", new Rectangle(110, 155, 160, 20),
					false, 0xffffff, new Widget[0], listener));
			assertTrue(overlay.marks(selected(TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars")).isEmpty());
		}
	}

	@Test
	public void recommendationDoesNotHideAnotherSourcesLearningBorder()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
			row("B: Castle Wars", new Rectangle(110, 155, 160, 20)));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(new HouseLearningOverlay.Guidance(true,
			Set.of(TeleportSource.JEWELLERY_BOX), TeleportSource.JEWELLERY_BOX, "jewellery-castle-wars"));

		assertEquals(2, marks.size());
		assertEquals(HouseLearningOverlay.LEARNING, marks.get(0).color);
		assertEquals(HouseLearningOverlay.RECOMMENDED, marks.get(1).color);
	}

	@Test
	public void nexusRowsAreMatchedAndClippedToTheActualScrollViewport()
	{
		put(InterfaceID.TelenexusTeleport.UNIVERSE, new Rectangle(100, 100, 400, 250));
		put(InterfaceID.TelenexusTeleport.SCROLLING1, new Rectangle(200, 130, 250, 60));
		putRows(InterfaceID.TelenexusTeleport.TEXT1, new Rectangle(200, 130, 250, 500),
			row("<col=ffffff>1</col> :  Barrows", new Rectangle(200, 180, 250, 24)),
			row("2: Barrows", new Rectangle(200, 230, 250, 24)));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-barrows"));

		assertEquals(1, marks.size());
		assertEquals(new Rectangle(200, 180, 250, 10), marks.get(0).shape.getBounds());
	}

	@Test
	public void nexusAlternateDestinationsUseTheNativeExtrasRows()
	{
		put(InterfaceID.TelenexusTeleport.UNIVERSE, new Rectangle(100, 100, 400, 250));
		putRows(InterfaceID.TelenexusTeleport.EXTRAS, new Rectangle(200, 130, 250, 200),
			row("A: Grand Exchange", new Rectangle(200, 150, 250, 24)));

		assertEquals(1, overlay.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-grand-exchange")).size());
	}

	@Test
	public void bothModernAndLegacySpiritTreeInterfacesAreSupported()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			putRows(component, new Rectangle(100, 100, 300, 150),
				row("1: Tree Gnome Village", new Rectangle(110, 130, 270, 20)),
				row("2: Tree Gnome Stronghold", new Rectangle(110, 155, 270, 20)));

			List<HouseLearningOverlay.Mark> marks = overlay.marks(new HouseLearningOverlay.Guidance(true,
				Set.of(TeleportSource.SPIRIT_TREE), TeleportSource.SPIRIT_TREE, "tree-gnome-stronghold"));

			assertEquals(2, marks.size());
			assertEquals("Learning destinations", marks.get(0).label);
			assertEquals(new Rectangle(110, 155, 270, 20), marks.get(1).shape.getBounds());
		}
	}

	@Test
	public void retainedHiddenFirstSpiritTreeIdentifiesTheMenuWithoutBeingHighlighted()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			putRows(component, new Rectangle(100, 100, 300, 150),
				widget(0, "1: <str>Tree Gnome Village</str>", new Rectangle(110, 130, 270, 20),
					true, 0xffffff, new Widget[0]),
				row("2: Gnome Stronghold", new Rectangle(110, 155, 270, 20)));

			List<HouseLearningOverlay.Mark> marks = overlay.marks(new HouseLearningOverlay.Guidance(true,
				Set.of(TeleportSource.SPIRIT_TREE), TeleportSource.SPIRIT_TREE, "tree-gnome-stronghold"));

			assertEquals(2, marks.size());
			assertEquals("Learning destinations", marks.get(0).label);
			assertEquals(new Rectangle(110, 155, 270, 20), marks.get(1).shape.getBounds());
			assertTrue(overlay.marks(selected(TeleportSource.SPIRIT_TREE, "tree-tree-gnome-village")).isEmpty());
		}
	}

	@Test
	public void capturedGenericLabelsIdentifyRewrittenSpiritTreesAndIgnoreCosmeticColors()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			NativeTeleportMenus menus = captureSpiritMenu(component, "Battlefield of Khazard", false);
			HouseLearningOverlay captured = new HouseLearningOverlay(client, () -> null, menus);

			List<HouseLearningOverlay.Mark> marks = captured.marks(selected(TeleportSource.SPIRIT_TREE,
				"tree-battlefield-of-khazard"));

			assertEquals(1, marks.size());
			assertEquals(new Rectangle(110, 180, 270, 20), marks.get(0).shape.getBounds());
			assertTrue(captured.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-grand-exchange")).isEmpty());
		}
	}

	@Test
	public void capturedGenericLocksAndHiddenRowsAreNeverHighlighted()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			NativeTeleportMenus menus = captureSpiritMenu(component, "<str>Battlefield of Khazard</str>", false);
			HouseLearningOverlay captured = new HouseLearningOverlay(client, () -> null, menus);
			assertTrue(captured.marks(selected(TeleportSource.SPIRIT_TREE, "tree-battlefield-of-khazard")).isEmpty());
			menus = captureSpiritMenu(component, "Battlefield of Khazard", true);
			captured = new HouseLearningOverlay(client, () -> null, menus);
			assertTrue(captured.marks(selected(TeleportSource.SPIRIT_TREE, "tree-battlefield-of-khazard")).isEmpty());
		}
	}

	@Test
	public void mountedMenusHighlightOnlyTheRecommendedDestinationWithNativeKeyMarkup()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			putRows(component, new Rectangle(100, 100, 300, 150),
				row("<col=735a28>1</col>: Digsite", new Rectangle(110, 130, 270, 20)),
				row("<col=735a28>2</col>: Fossil Island", new Rectangle(110, 155, 270, 20)),
				row("<col=735a28>3</col>: Lithkren", new Rectangle(110, 180, 270, 20)),
				row("<col=735a28>4</col>: Nowhere", new Rectangle(110, 205, 270, 20)));

			List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(
				TeleportSource.MOUNTED_DIGSITE_PENDANT, "digsite-fossil-island"));

			assertEquals(1, marks.size());
			assertEquals(new Rectangle(110, 155, 270, 20), marks.get(0).shape.getBounds());
			assertEquals(HouseLearningOverlay.RECOMMENDED, marks.get(0).color);
		}
	}

	@Test
	public void regionalXericMenusHighlightEachCanonicalDestination()
	{
		String[] labels = {"Shayzien", "Hosidius", "Lovakengj", "Kourend Castle", "Chambers of Xeric"};
		String[] ids = {"xeric-lookout", "xeric-glade", "xeric-inferno", "xeric-heart", "xeric-honour"};
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			Widget[] rows = new Widget[6];
			for (int index = 0; index < labels.length; index++)
			{
				rows[index] = row("<col=735a28>" + (index + 1) + "</col>: " + labels[index],
					new Rectangle(110, 130 + index * 25, 270, 20));
			}
			rows[5] = row("6: Nowhere", new Rectangle(110, 255, 270, 20));
			putRows(component, new Rectangle(100, 100, 300, 200), rows);
			for (int index = 0; index < ids.length; index++)
			{
				List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(
					TeleportSource.MOUNTED_XERICS_TALISMAN, ids[index]));
				assertEquals(1, marks.size());
				assertEquals(new Rectangle(110, 130 + index * 25, 270, 20), marks.get(0).shape.getBounds());
				assertEquals(HouseLearningOverlay.RECOMMENDED, marks.get(0).color);
			}
		}
	}

	@Test
	public void regionalXericMenusReceiveTheLearningBorder()
	{
		for (int component : new int[]{InterfaceID.MenuNew.TEXT, InterfaceID.Menu.LJ_LAYER1})
		{
			widgets.clear();
			putRows(component, new Rectangle(100, 100, 300, 200),
				row("1: Shayzien", new Rectangle(110, 130, 270, 20)),
				row("2: Hosidius", new Rectangle(110, 155, 270, 20)));

			List<HouseLearningOverlay.Mark> marks = overlay.marks(pending(TeleportSource.MOUNTED_XERICS_TALISMAN));

			assertEquals(1, marks.size());
			assertEquals(HouseLearningOverlay.LEARNING, marks.get(0).color);
			assertEquals("Learning destinations", marks.get(0).label);
		}
	}

	@Test
	public void disabledRegionalXericChoicesAreNotHighlighted()
	{
		putRows(InterfaceID.Menu.LJ_LAYER1, new Rectangle(100, 100, 300, 200),
			row("1: Shayzien", new Rectangle(110, 130, 270, 20)),
			row("2: <str>Hosidius</str>", new Rectangle(110, 155, 270, 20)),
			widget(0, "3: Lovakengj", new Rectangle(110, 180, 270, 20), false, 0x7f7f7f, new Widget[0]),
			widget(0, "4: Kourend Castle", new Rectangle(110, 205, 270, 20), true, 0xffffff, new Widget[0]));

		for (String id : new String[]{"xeric-glade", "xeric-inferno", "xeric-heart"})
		{
			assertTrue(overlay.marks(selected(TeleportSource.MOUNTED_XERICS_TALISMAN, id)).isEmpty());
		}
	}

	@Test
	public void xericRegionalNamesDoNotRedirectANexusRecommendation()
	{
		putRows(InterfaceID.Menu.LJ_LAYER1, new Rectangle(100, 100, 300, 200),
			row("1: Shayzien", new Rectangle(110, 130, 270, 20)),
			row("4: Kourend Castle", new Rectangle(110, 205, 270, 20)));

		assertTrue(overlay.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-kourend")).isEmpty());
		assertEquals(1, overlay.marks(selected(TeleportSource.MOUNTED_XERICS_TALISMAN, "xeric-heart")).size());
	}

	@Test
	public void matchingNamesInAnotherSourcesMenuAreNotHighlighted()
	{
		putRows(InterfaceID.Menu.LJ_LAYER1, new Rectangle(100, 100, 300, 150),
			row("1: Tree Gnome Village", new Rectangle(110, 130, 270, 20)),
			row("2: Grand Exchange", new Rectangle(110, 155, 270, 20)));

		assertTrue(overlay.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-grand-exchange")).isEmpty());
		assertTrue(overlay.marks(selected(TeleportSource.JEWELLERY_BOX, "jewellery-grand-exchange")).isEmpty());
		assertEquals(1, overlay.marks(selected(TeleportSource.SPIRIT_TREE, "tree-grand-exchange")).size());
	}

	@Test
	public void missingTravelLogEntryDoesNotOutlineAnyDialOrTeleportControl()
	{
		fairyControls();
		put(InterfaceID.FairyringsLog.CONTENTS, new Rectangle(400, 100, 250, 300));

		assertTrue(overlay.marks(selected(TeleportSource.FAIRY_RING, "fairy-bkr")).isEmpty());
	}

	@Test
	public void fairyLearningBorderSurroundsTheSelectorRatherThanOnlyItsTravelLog()
	{
		fairyControls();
		put(InterfaceID.FairyringsLog.CONTENTS, new Rectangle(400, 100, 250, 300));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(pending(TeleportSource.FAIRY_RING));

		assertEquals(1, marks.size());
		assertTrue(marks.get(0).shape.getBounds().contains(widgets.get(InterfaceID.Fairyrings.CONFIRM).getBounds()));
		assertTrue(marks.get(0).shape.getBounds().contains(widgets.get(InterfaceID.Fairyrings._1_CLOCKWISE).getBounds()));
		assertFalse(marks.get(0).shape.getBounds().intersects(new Rectangle(400, 100, 250, 300)));
	}

	@Test
	public void hiddenFairyHistoryDoesNotProduceFallbackGuidance()
	{
		fairyControls();
		put(InterfaceID.FairyringsLog.CONTENTS, new Rectangle(400, 100, 250, 300));
		int bkr = InterfaceID.FairyringsLog.BKR;
		widgets.put(bkr, widget(bkr, "B K R", new Rectangle(400, 200, 150, 20), true, 0xffffff, new Widget[0]));

		assertTrue(overlay.marks(selected(TeleportSource.FAIRY_RING, "fairy-bkr")).isEmpty());
	}

	@Test
	public void readableFairyTravelLogHighlightsOnlyTheExistingAction()
	{
		fairyControls();
		put(InterfaceID.FairyringsLog.CONTENTS, new Rectangle(400, 100, 250, 300));
		widgets.put(InterfaceID.FairyringsLog.BKR, widget(InterfaceID.FairyringsLog.BKR,
			"Mort Myre Swamp", new Rectangle(430, 200, 180, 20), false, 0xffffff, new Widget[0]));

		List<HouseLearningOverlay.Mark> marks = overlay.marks(selected(TeleportSource.FAIRY_RING, "fairy-bkr"));

		assertEquals(1, marks.size());
		assertEquals("Use BKR", marks.get(0).label);
		assertEquals(new Rectangle(430, 200, 180, 20), marks.get(0).shape.getBounds());
	}

	@Test
	public void aTravelLogEntryOutsideTheManualScrollViewportDoesNotRestoreDialBoxes()
	{
		fairyControls();
		put(InterfaceID.FairyringsLog.CONTENTS, new Rectangle(400, 100, 250, 100));
		widgets.put(InterfaceID.FairyringsLog.BKR, widget(InterfaceID.FairyringsLog.BKR,
			"Mort Myre Swamp", new Rectangle(430, 350, 180, 20), false, 0xffffff, new Widget[0]));

		assertTrue(overlay.marks(selected(TeleportSource.FAIRY_RING, "fairy-bkr")).isEmpty());
	}


	@Test
	public void knownGenericMenuAliasesMatchActualDestinationLabels()
	{
		assertTrue(HouseLearningOverlay.matches(TeleportCatalog.byId("cape-rimmington").get(), "Rimmington"));
		assertTrue(HouseLearningOverlay.matches(TeleportCatalog.byId("obelisk-13").get(), "Level 13"));
		assertTrue(HouseLearningOverlay.matches(TeleportCatalog.byId("cape-diary-jarr").get(), "Desert - Jarr"));
		assertTrue(HouseLearningOverlay.matches(TeleportCatalog.byId("jewellery-chasm-of-tears").get(), "Tears of Guthix"));
		assertFalse(HouseLearningOverlay.matches(TeleportCatalog.byId("cape-diary-jarr").get(), "Jarr's unrelated object"));
	}

	@Test
	public void sourceAndDestinationMustAgreeBeforeHighlightingAnyChoice()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		putRows(InterfaceID.PohJewelleryBox.DUELING, new Rectangle(100, 100, 180, 100),
			row("Castle Wars", new Rectangle(110, 155, 160, 20)));

		assertTrue(overlay.marks(selected(TeleportSource.SPIRIT_TREE, "jewellery-castle-wars")).isEmpty());
	}

	@Test
	public void teleportMapsHighlightsTheExistingTreeXericAndObeliskButtonsInBothInterfaces()
	{
		TeleportSource[] sources = {TeleportSource.SPIRIT_TREE, TeleportSource.MOUNTED_XERICS_TALISMAN,
			TeleportSource.WILDERNESS_OBELISK};
		String[] ids = {"tree-gnome-stronghold", "xeric-glade", "obelisk-35"};
		String[] names = {"Gnome Stronghold", "Xeric's Glade", "Level 35 Wilderness"};
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			for (int index = 0; index < sources.length; index++)
			{
				widgets.clear();
				HouseLearningOverlay mapped = mappedOverlay(component, sources[index], names[index]);
				List<HouseLearningOverlay.Mark> marks = mapped.marks(selected(sources[index], ids[index]));
				assertEquals(1, marks.size());
				assertEquals(new Rectangle(260, 180, 30, 35), marks.get(0).shape.getBounds());
				assertEquals(HouseLearningOverlay.RECOMMENDED, marks.get(0).color);
			}
		}
	}

	@Test
	public void teleportMapsLearningBorderUsesTheVisibleMapInsteadOfTheHiddenList()
	{
		for (int component : new int[]{InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT})
		{
			widgets.clear();
			HouseLearningOverlay mapped = mappedOverlay(component, TeleportSource.SPIRIT_TREE, "Gnome Stronghold");
			List<HouseLearningOverlay.Mark> marks = mapped.marks(pending(TeleportSource.SPIRIT_TREE));
			assertEquals(1, marks.size());
			assertEquals(new Rectangle(100, 100, 512, 334), marks.get(0).shape.getBounds());
			assertEquals(HouseLearningOverlay.LEARNING, marks.get(0).color);
		}
	}

	@Test
	public void teleportMapsRetainedNativeIdentityHandlesCustomXericNamesContainingAColon()
	{
		HouseLearningOverlay mapped = mappedOverlay(InterfaceID.Menu.LJ_LAYER1,
			TeleportSource.MOUNTED_XERICS_TALISMAN, "Raid: west");
		assertEquals("Raid: west", HouseLearningOverlay.stripKey("<col=735a28>2</col>: Raid: west"));
		List<HouseLearningOverlay.Mark> marks = mapped.marks(selected(
			TeleportSource.MOUNTED_XERICS_TALISMAN, "xeric-glade"));
		assertEquals(1, marks.size());
		assertEquals(new Rectangle(260, 180, 30, 35), marks.get(0).shape.getBounds());
		assertTrue(mapped.marks(selected(TeleportSource.PORTAL_NEXUS, "nexus-kourend-castle")).isEmpty());
		assertTrue(mapped.marks(new HouseLearningOverlay.Guidance(false, Collections.emptySet(),
			TeleportSource.MOUNTED_XERICS_TALISMAN, "xeric-glade")).isEmpty());
	}

	private HouseLearningOverlay mappedOverlay(int component, TeleportSource source, String buttonName)
	{
		String[] originals;
		int sprite;
		int renamedIndex;
		if (source == TeleportSource.SPIRIT_TREE)
		{
			originals = new String[]{"Tree Gnome Village", "Gnome Stronghold", "Battlefield of Khazard", "Grand Exchange"};
			sprite = -19000;
			renamedIndex = 1;
		}
		else if (source == TeleportSource.MOUNTED_XERICS_TALISMAN)
		{
			originals = new String[]{"Xeric's Lookout", "Xeric's Glade", "Xeric's Inferno", "Xeric's Heart", "Xeric's Honour"};
			sprite = -19400;
			renamedIndex = 1;
		}
		else
		{
			originals = new String[]{"Level 13 Wilderness", "Level 19 Wilderness", "Level 27 Wilderness",
				"Level 35 Wilderness", "Level 44 Wilderness", "Level 50 Wilderness"};
			sprite = -19600;
			renamedIndex = 3;
		}
		Widget[] nativeRows = new Widget[originals.length];
		for (int index = 0; index < nativeRows.length; index++)
		{
			String current = index == renamedIndex ? buttonName : originals[index];
			nativeRows[index] = widget(component, index, "<col=735a28>" + (index + 1) + "</col>: " + current,
				new Rectangle(100, 100 + index * 20, 300, 20), true, 0xffffff, new Widget[0], null);
		}
		widgets.put(component, widget(component, "", new Rectangle(100, 100, 300, 200), true, 0, nativeRows));
		NativeTeleportMenus menus = new NativeTeleportMenus();
		int script = component == InterfaceID.Menu.LJ_LAYER1 ? 217 : 9142;
		Object[] arguments = script == 217
			? new Object[]{script, "Travel", String.join("|", originals), 1}
			: new Object[]{script, "Travel", String.join("|", originals), 1, 0, 0, 0};
		ScriptPreFired pre = new ScriptPreFired(script);
		pre.setScriptEvent(proxy(ScriptEvent.class, (method, args) ->
		{
			if ("getArguments".equals(method)) return arguments;
			throw new UnsupportedOperationException(method);
		}));
		menus.onScriptPreFired(pre);
		menus.onScriptPostFired(client, new ScriptPostFired(script));
		int root = component == InterfaceID.Menu.LJ_LAYER1 ? InterfaceID.Menu.LJ_LAYER2 : InterfaceID.MenuNew.INFINITE;
		Rectangle map = new Rectangle(100, 100, 512, 334);
		Widget background = mapGraphic(root, 0, sprite, "", map, null);
		Widget button = mapGraphic(root, 1, -1, buttonName, new Rectangle(260, 180, 30, 35),
			source == TeleportSource.WILDERNESS_OBELISK ? "Set" : "Travel");
		putRows(root, map, background, button);
		return new HouseLearningOverlay(client, () -> null, menus);
	}

	private static Widget mapGraphic(int root, int index, int sprite, String name, Rectangle bounds, String action)
	{
		JavaScriptCallback callback = event -> { throw new AssertionError("Guidance must never execute a map action"); };
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
				case "getParentId": return root;
				case "getIndex": return index;
				case "getType": return WidgetType.GRAPHIC;
				case "getSpriteId": return sprite;
				case "getName": return "<col=ff9040>" + name + "</col>";
				case "getText": return "";
				case "getBounds": return bounds;
				case "isHidden": return false;
				case "getActions": return action == null ? null : new String[]{action};
				case "getOnOpListener": return action == null ? null : new Object[]{callback};
				case "getDynamicChildren": return new Widget[0];
				default: throw new UnsupportedOperationException(method);
			}
		});
	}

	@Test
	public void closedMenusDoNotReadTheSceneOrHighlightPendingFixtures()
	{
		for (TeleportSource source : TeleportSource.menuSources())
		{
			assertTrue(overlay.marks(pending(source)).isEmpty());
		}
	}

	@Test
	public void closedMenusDoNotReadTheSceneOrHighlightRecommendedFixtures()
	{
		for (TeleportDestination destination : TeleportCatalog.all())
		{
			assertTrue(overlay.marks(selected(destination.getSource(), destination.getId())).isEmpty());
		}
	}

	@Test
	public void guidanceCopiesItsPendingSourcesBeforePublication()
	{
		Set<TeleportSource> pending = new HashSet<>(Set.of(TeleportSource.FAIRY_RING));
		HouseLearningOverlay.Guidance guidance = new HouseLearningOverlay.Guidance(true, pending,
			TeleportSource.FAIRY_RING, "fairy-bkr");
		pending.clear();

		assertEquals(Set.of(TeleportSource.FAIRY_RING), guidance.getPendingSources());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void publishedGuidanceCannotBeMutated()
	{
		pending(TeleportSource.FAIRY_RING).getPendingSources().clear();
	}

	@Test
	public void renderingTheGuidancePaintsItsNativeBoundsWithoutMutatingWidgets()
	{
		put(InterfaceID.PohJewelleryBox.UNIVERSE, new Rectangle(100, 100, 400, 220));
		HouseLearningOverlay rendered = new HouseLearningOverlay(client,
			() -> pending(TeleportSource.JEWELLERY_BOX));
		BufferedImage image = new BufferedImage(765, 503, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			rendered.render(graphics);
			assertEquals(HouseLearningOverlay.LEARNING.getRGB(), image.getRGB(100, 150));
		}
		finally
		{
			graphics.dispose();
		}
	}

	private HouseLearningOverlay.Guidance pending(TeleportSource source)
	{
		return new HouseLearningOverlay.Guidance(true, Set.of(source), null, null);
	}

	private HouseLearningOverlay.Guidance selected(TeleportSource source, String id)
	{
		return new HouseLearningOverlay.Guidance(true, Collections.emptySet(), source, id);
	}

	private static Widget nativeJewelleryRow(String displayed, String original, boolean hidden)
	{
		int section = InterfaceID.PohJewelleryBox.DUELING;
		return widget(section, 2, displayed, new Rectangle(110, 155, 160, 20), hidden, 0x7f7f7f,
			new Widget[0], new Object[]{1690, ScriptEvent.MENU_OP, 1, section, 2, original, "B", ""});
	}

	private NativeTeleportMenus captureSpiritMenu(int component, String battlefield, boolean hidden)
	{
		Widget[] rows = {
			widget(component, 0, "Village", new Rectangle(110, 130, 270, 20), false, 0xffffff, new Widget[0], null),
			widget(component, 1, "Stronghold", new Rectangle(110, 155, 270, 20), false, 0xffffff, new Widget[0], null),
			widget(component, 2, "<col=ff0000>B'field shortcut</col>", new Rectangle(110, 180, 270, 20),
				hidden, 0x7f7f7f, new Widget[0], null),
			widget(component, 3, "GE", new Rectangle(110, 205, 270, 20), false, 0xffffff, new Widget[0], null)};
		putRows(component, new Rectangle(100, 100, 300, 200), rows);
		String options = "Tree Gnome Village|Gnome Stronghold|" + battlefield + "|Grand Exchange";
		int script = component == InterfaceID.MenuNew.TEXT ? 9142 : 217;
		Object[] arguments = script == 217 ? new Object[]{217, "Spirit Tree Locations", options, 0}
			: new Object[]{9142, "Spirit Tree Locations", options, 0, 0, 0, 0};
		ScriptPreFired pre = new ScriptPreFired(script);
		pre.setScriptEvent(proxy(ScriptEvent.class, (method, args) ->
		{
			if ("getArguments".equals(method)) return arguments;
			throw new UnsupportedOperationException(method);
		}));
		NativeTeleportMenus menus = new NativeTeleportMenus();
		menus.onScriptPreFired(pre);
		menus.onScriptPostFired(client, new ScriptPostFired(script));
		assertTrue(menus.isCaptured(rows[2]));
		return menus;
	}

	private void fairyControls()
	{
		put(InterfaceID.Fairyrings.CONFIRM, new Rectangle(110, 250, 200, 25));
		int[] controls = {InterfaceID.Fairyrings._1_CLOCKWISE, InterfaceID.Fairyrings._1_ANTICLOCKWISE,
			InterfaceID.Fairyrings._2_CLOCKWISE, InterfaceID.Fairyrings._2_ANTICLOCKWISE,
			InterfaceID.Fairyrings._3_CLOCKWISE, InterfaceID.Fairyrings._3_ANTICLOCKWISE};
		for (int index = 0; index < controls.length; index++)
		{
			put(controls[index], new Rectangle(100 + index / 2 * 70, 120 + index % 2 * 55, 30, 20));
		}
	}

	private void put(int component, Rectangle bounds)
	{
		widgets.put(component, widget(component, "", bounds, false, 0xffffff, new Widget[0]));
	}

	private void putRows(int component, Rectangle bounds, Widget... rows)
	{
		widgets.put(component, widget(component, "", bounds, false, 0xffffff, rows));
	}

	private static Widget row(String text, Rectangle bounds)
	{
		return widget(0, text, bounds, false, 0xffffff, new Widget[0]);
	}

	private static Widget widget(int id, String text, Rectangle bounds, boolean hidden, int color, Widget[] children)
	{
		return widget(id, -1, text, bounds, hidden, color, children, null);
	}

	private static Widget widget(int id, int index, String text, Rectangle bounds, boolean hidden, int color,
		Widget[] children, Object[] listener)
	{
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "getId":
				case "getParentId": return id;
				case "getIndex": return index;
				case "getText": return text;
				case "getBounds": return bounds;
				case "getTextColor": return color;
				case "isHidden": return hidden;
				case "getDynamicChildren": return children;
				case "getOnOpListener": return listener;
				case "getActions": return new String[]{"Use code"};
				default: throw new UnsupportedOperationException(method);
			}
		});
	}

	private interface Handler
	{
		Object invoke(String method, Object[] args);
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> handler.invoke(method.getName(), args));
	}
}
