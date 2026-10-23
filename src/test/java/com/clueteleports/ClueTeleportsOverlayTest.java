package com.clueteleports;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ClueTeleportsOverlayTest
{
	private static final Set<TeleportSource> FOUR = EnumSet.of(TeleportSource.FAIRY_RING, TeleportSource.SPIRIT_TREE, TeleportSource.PORTAL_NEXUS, TeleportSource.JEWELLERY_BOX);
	private static final String HINT = "Open each travel menu in your house.";

	@Test
	public void hiddenStateDoesNotDrawAnOverlay()
	{
		ClueTeleportsOverlay overlay = new ClueTeleportsOverlay(new ClueTeleportsPlugin());
		Graphics2D graphics = new BufferedImage(320, 160, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			assertNull(overlay.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void fourDetectedFacilitiesShowTheirUnlearnedMenus() throws Exception
	{
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(Collections.emptyMap(), FOUR, HINT);
		assertTrue(display.isLearning());
		assertEquals("Learn POH teleports (0/4)", display.first);
		List<LineComponent> rows = renderChecklist(display);
		for (LineComponent row : rows)
		{
			assertEquals("Not learned", field(row, "right"));
		}
	}

	@Test
	public void partialLearningUsesSourcePresenceIncludingKnownEmptySources() throws Exception
	{
		Map<TeleportSource, Set<String>> snapshots = new EnumMap<>(TeleportSource.class);
		snapshots.put(TeleportSource.FAIRY_RING, Collections.emptySet());
		snapshots.put(TeleportSource.JEWELLERY_BOX, Set.of("jewellery-castle-wars"));
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(snapshots, FOUR, HINT);
		assertEquals("Learn POH teleports (2/4)", display.first);
		assertEquals(Set.of(TeleportSource.FAIRY_RING, TeleportSource.JEWELLERY_BOX), display.getLearnedSources());
		List<LineComponent> rows = renderChecklist(display);
		assertEquals("Learned", field(rows.get(0), "right"));
		assertEquals("Not learned", field(rows.get(1), "right"));
		assertEquals("Not learned", field(rows.get(2), "right"));
		assertEquals("Learned", field(rows.get(3), "right"));
		assertEquals(field(rows.get(0), "rightColor"), field(rows.get(3), "rightColor"));
		assertFalse(field(rows.get(0), "rightColor").equals(field(rows.get(1), "rightColor")));
	}

	@Test
	public void fourKnownEmptySnapshotsStillCountAsLearned() throws Exception
	{
		Map<TeleportSource, Set<String>> snapshots = new EnumMap<>(TeleportSource.class);
		for (TeleportSource source : TeleportSource.values())
		{
			snapshots.put(source, Collections.emptySet());
		}
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(snapshots, FOUR, HINT);
		assertEquals("Learn POH teleports (4/4)", display.first);
		for (LineComponent row : renderChecklist(display))
		{
			assertEquals("Learned", field(row, "right"));
		}
	}


	@Test
	public void optionalFixturesDoNotCountAsRequiredTravelMenus() throws Exception
	{
		Map<TeleportSource, Set<String>> snapshots = new EnumMap<>(TeleportSource.class);
		snapshots.put(TeleportSource.FAIRY_RING, Collections.emptySet());
		snapshots.put(TeleportSource.WILDERNESS_OBELISK, Set.of("obelisk-13"));
		snapshots.put(TeleportSource.CAPE_HANGER, Collections.emptySet());
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(snapshots, FOUR, HINT);
		assertEquals("Learn POH teleports (1/4)", display.first);
		assertEquals(Set.of(TeleportSource.FAIRY_RING), display.getLearnedSources());
		assertEquals(4, renderChecklist(display).size());
	}

	@Test
	public void learningSnapshotIsCopiedBeforeItIsPublished()
	{
		Map<TeleportSource, Set<String>> snapshots = new EnumMap<>(TeleportSource.class);
		snapshots.put(TeleportSource.FAIRY_RING, Collections.emptySet());
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(snapshots, FOUR, HINT);
		snapshots.clear();
		assertEquals(Set.of(TeleportSource.FAIRY_RING), display.getLearnedSources());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void learningSnapshotCannotBeMutated()
	{
		ClueTeleportsOverlay.Display.learning(Collections.emptyMap(), FOUR, HINT).getLearnedSources().add(TeleportSource.FAIRY_RING);
	}

	@Test
	public void repeatedLearningFramesRenderARealChecklistPreview() throws Exception
	{
		Map<TeleportSource, Set<String>> snapshots = new EnumMap<>(TeleportSource.class);
		snapshots.put(TeleportSource.FAIRY_RING, Collections.emptySet());
		snapshots.put(TeleportSource.JEWELLERY_BOX, Set.of("jewellery-castle-wars"));
		ClueTeleportsOverlay overlay = overlayFor(ClueTeleportsOverlay.Display.learning(snapshots, FOUR, HINT));
		BufferedImage image = new BufferedImage(300, 240, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setFont(FontManager.getRunescapeFont());
			overlay.render(graphics);
			clear(graphics, image);
			Dimension size = overlay.render(graphics);
			assertNotNull(size);
			assertTrue(size.width < 235);
			assertTrue(size.height >= 100);
			assertTrue(size.height <= image.getHeight());
			assertTrue(overlay.getPanelComponent().getChildren().isEmpty());
			assertTrue("Learned text must be visible in green", containsColor(image, new Color(126, 215, 137)));
			assertTrue("Not learned text must be visible in amber", containsColor(image, new Color(255, 196, 93)));
			Files.createDirectories(Path.of("build"));
			assertTrue(ImageIO.write(image.getSubimage(0, 0, size.width, size.height), "png", new File("build/learning-preview.png")));
			assertEquals(size, overlay.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	public void repeatedFramesKeepOneRecommendationAndWrapLongNames() throws Exception
	{
		ClueTeleportsOverlay.Display display = new ClueTeleportsOverlay.Display("Fairy ring",
			"BKR - Mort Myre Swamp, south of Canifis", "~13s running, 42 walking tiles");
		assertFalse(display.isLearning());
		assertTrue(display.getLearnedSources().isEmpty());
		ClueTeleportsOverlay overlay = overlayFor(display);
		BufferedImage image = new BufferedImage(300, 160, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setFont(FontManager.getRunescapeFont());
			overlay.render(graphics);
			clear(graphics, image);
			Dimension first = overlay.render(graphics);
			assertTrue(first.height > 50);
			assertTrue(first.height <= image.getHeight());
			assertTrue(overlay.getPanelComponent().getChildren().isEmpty());
			Files.createDirectories(Path.of("build"));
			assertTrue(ImageIO.write(image.getSubimage(0, 0, first.width, first.height), "png", new File("build/overlay-preview.png")));
			assertEquals(first, overlay.render(graphics));
		}
		finally
		{
			graphics.dispose();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void forgettingLearnedSourcesIsAvailableFromTheOverlay() throws Exception
	{
		AtomicInteger resets = new AtomicInteger();
		ClueTeleportsPlugin plugin = new ClueTeleportsPlugin()
		{
			@Override
			void forgetAvailability()
			{
				resets.incrementAndGet();
			}
		};
		ClueTeleportsOverlay overlay = new ClueTeleportsOverlay(plugin);
		assertSame(plugin, overlay.getPlugin());
		OverlayMenuEntry reset = overlay.getMenuEntries().stream()
			.filter(entry -> "Forget learned sources".equals(entry.getOption())).findFirst().orElseThrow(AssertionError::new);
		assertEquals(MenuAction.RUNELITE_OVERLAY, reset.getMenuAction());
		assertEquals("POH Clue Teleports", reset.getTarget());
		((Consumer<MenuEntry>) field(reset, "callback")).accept(null);
		assertEquals(1, resets.get());
	}


	@Test
	public void checklistContainsOnlyInstalledSources() throws Exception
	{
		Set<TeleportSource> installed = Set.of(TeleportSource.FAIRY_RING, TeleportSource.JEWELLERY_BOX);
		ClueTeleportsOverlay.Display display = ClueTeleportsOverlay.Display.learning(
			Map.of(TeleportSource.FAIRY_RING, Collections.emptySet()), installed, HINT);
		assertEquals("Learn POH teleports (1/2)", display.first);
		assertEquals(installed, display.getRequiredSources());
		assertEquals(2, renderChecklist(display).size());
	}

	@Test
	public void contentWidthGrowsAndShrinksInTheSameFrame() throws Exception
	{
		ClueTeleportsPlugin plugin = new ClueTeleportsPlugin();
		Field displayField = ClueTeleportsPlugin.class.getDeclaredField("display");
		displayField.setAccessible(true);
		ClueTeleportsOverlay overlay = new ClueTeleportsOverlay(plugin);
		BufferedImage canvas = new BufferedImage(320, 220, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		try
		{
			graphics.setFont(FontManager.getRunescapeFont());
			displayField.set(plugin, new ClueTeleportsOverlay.Display("Fairy ring", "CIS", "42 tiles"));
			Dimension small = overlay.render(graphics);
			assertTrue(small.width < 180);
			displayField.set(plugin, new ClueTeleportsOverlay.Display("Mounted Xeric's talisman",
				"A sufficiently long destination name to require a bounded and wrapped overlay", "~13s running, 42 walking tiles"));
			Dimension wide = overlay.render(graphics);
			assertEquals(300, wide.width);
			assertTrue(wide.width > small.width);
			displayField.set(plugin, new ClueTeleportsOverlay.Display("Fairy ring", "CIS", "42 tiles"));
			assertEquals(small, overlay.render(graphics));
			clear(graphics, canvas);
			Dimension preview = overlay.render(graphics);
			assertTrue(ImageIO.write(canvas.getSubimage(0, 0, preview.width, preview.height), "png", new File("build/compact-preview.png")));
		}
		finally { graphics.dispose(); }
	}

	private List<LineComponent> renderChecklist(ClueTeleportsOverlay.Display display) throws Exception
	{
		ClueTeleportsOverlay overlay = overlayFor(display);
		overlay.setClearChildren(false);
		Graphics2D graphics = new BufferedImage(300, 240, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			graphics.setFont(FontManager.getRunescapeFont());
			overlay.render(graphics);
			List<LayoutableRenderableEntity> children = overlay.getPanelComponent().getChildren();
			assertEquals("Title, hint, and one row per installed menu", display.getRequiredSources().size() + 2, children.size());
			assertTrue(children.get(0) instanceof TitleComponent);
			assertEquals(display.first, field(children.get(0), "text"));
			assertEquals(HINT, field(children.get(1), "left"));
			List<LineComponent> rows = new ArrayList<>();
			TeleportSource[] menuSources = display.getRequiredSources().toArray(new TeleportSource[0]);
			for (int index = 0; index < menuSources.length; index++)
			{
				LineComponent row = (LineComponent) children.get(index + 2);
				assertEquals(menuSources[index].getDisplayName(), field(row, "left"));
				rows.add(row);
			}
			return rows;
		}
		finally
		{
			graphics.dispose();
		}
	}

	private ClueTeleportsOverlay overlayFor(ClueTeleportsOverlay.Display display)
	{
		return new ClueTeleportsOverlay(new ClueTeleportsPlugin()
		{
			@Override
			ClueTeleportsOverlay.Display getDisplay()
			{
				return display;
			}
		});
	}

	private static Object field(Object instance, String name) throws Exception
	{
		Field field = instance.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	private static void clear(Graphics2D graphics, BufferedImage image)
	{
		graphics.setComposite(AlphaComposite.Clear);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setComposite(AlphaComposite.SrcOver);
	}

	private static boolean containsColor(BufferedImage image, Color color)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == color.getRGB())
				{
					return true;
				}
			}
		}
		return false;
	}
}
