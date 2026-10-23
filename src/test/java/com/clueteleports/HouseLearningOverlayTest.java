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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
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
		return proxy(Widget.class, (method, args) ->
		{
			switch (method)
			{
				case "getId": return id;
				case "getText": return text;
				case "getBounds": return bounds;
				case "getTextColor": return color;
				case "isHidden": return hidden;
				case "getDynamicChildren": return children;
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
