package com.clueteleports;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Read-only visual guidance over native POH controls and recognized Teleport Maps buttons.
 *
 * Native geometry uses Widget.getBounds. Menu containers follow the same cs2
 * scripts as the availability readers:
 * menu_createentry, menu_new_createentry, poh_jewellery_box_addbutton, and
 * telenexus_teleport_init / script2675. Nexus text rows are TEXT1 and EXTRAS.
 * Fairy-ring guidance marks only an existing clickable travel-log entry.
 * Missing or hidden entries do not produce dial or teleport-control guidance.
 *
 * https://github.com/runelite/cs2-scripts/tree/1d418ffb6bce96c2d0712eb2652712d28e01b115/scripts
 */
public final class HouseLearningOverlay extends Overlay
{
	static final Color LEARNING = new Color(255, 196, 93);
	static final Color RECOMMENDED = new Color(70, 220, 235);
	private static final Color BACKGROUND = new Color(20, 24, 27, 235);
	private static final Pattern KEY_PREFIX = Pattern.compile("^[^:]+:\\s*");
	private static final Pattern COLORED_KEY_PREFIX = Pattern.compile(
		"^<col=[0-9a-fA-F]+>[^<]*(?::</col>|</col>\\s*:)\\s*");
	private static final int[][] DIAL_BUTTONS = {
		{InterfaceID.Fairyrings._1_CLOCKWISE, InterfaceID.Fairyrings._1_ANTICLOCKWISE},
		{InterfaceID.Fairyrings._2_CLOCKWISE, InterfaceID.Fairyrings._2_ANTICLOCKWISE},
		{InterfaceID.Fairyrings._3_CLOCKWISE, InterfaceID.Fairyrings._3_ANTICLOCKWISE}
	};
	private static final int[] BOX_SECTIONS = {InterfaceID.PohJewelleryBox.DUELING, InterfaceID.PohJewelleryBox.GAMING,
		InterfaceID.PohJewelleryBox.COMBAT, InterfaceID.PohJewelleryBox.SKILLS,
		InterfaceID.PohJewelleryBox.WEALTH, InterfaceID.PohJewelleryBox.GLORY};

	private final Client client;
	private final Supplier<Guidance> guidance;
	private final NativeTeleportMenus menus;

	public HouseLearningOverlay(Client client, Supplier<Guidance> guidance)
	{
		this(client, guidance, new NativeTeleportMenus());
	}

	public HouseLearningOverlay(Client client, Supplier<Guidance> guidance, NativeTeleportMenus menus)
	{
		this.client = Objects.requireNonNull(client);
		this.guidance = Objects.requireNonNull(guidance);
		this.menus = Objects.requireNonNull(menus);
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		for (Mark mark : marks(guidance.get()))
		{
			Graphics2D copy = (Graphics2D) graphics.create();
			try
			{
				if (mark.clip != null)
				{
					copy.clip(mark.clip);
				}
				copy.setColor(new Color(mark.color.getRed(), mark.color.getGreen(), mark.color.getBlue(), 22));
				copy.fill(mark.shape);
				copy.setColor(mark.color);
				copy.setStroke(new BasicStroke(2));
				copy.draw(mark.shape);
			}
			finally
			{
				copy.dispose();
			}
			if (mark.label != null)
			{
				drawLabel(graphics, mark);
			}
		}
		return null;
	}

	/** Package-visible geometry permits regression tests without a live client. */
	List<Mark> marks(Guidance state)
	{
		if (state == null || !state.ownHouse)
		{
			return Collections.emptyList();
		}
		List<Mark> result = new ArrayList<>();
		Map<TeleportSource, Rectangle> open = new EnumMap<>(TeleportSource.class);
		Widget confirm = visible(InterfaceID.Fairyrings.CONFIRM);
		if (confirm != null)
		{
			open.put(TeleportSource.FAIRY_RING, fairySelector(confirm));
		}
		putOpen(open, TeleportSource.PORTAL_NEXUS, InterfaceID.TelenexusTeleport.UNIVERSE);
		putOpen(open, TeleportSource.JEWELLERY_BOX, InterfaceID.PohJewelleryBox.UNIVERSE);
		Widget generic = genericMenu();
		if (generic != null)
		{
			String first = firstLabel(generic);
			if ("tree gnome village".equals(first))
			{
				open.put(TeleportSource.SPIRIT_TREE, genericClip(generic));
			}
			else if ("digsite".equals(first))
			{
				open.put(TeleportSource.MOUNTED_DIGSITE_PENDANT, genericClip(generic));
			}
			else if ("xeric-lookout".equals(HouseTransportReader.xericDestinationId(first)))
			{
				open.put(TeleportSource.MOUNTED_XERICS_TALISMAN, genericClip(generic));
			}
		}
		TeleportMapsGuidance.View map = TeleportMapsGuidance.read(client, menus);
		if (map != null)
		{
			open.put(map.source, map.bounds);
		}
		for (TeleportSource pending : state.pendingSources)
		{
			Rectangle bounds = open.get(pending);
			if (bounds != null)
			{
				result.add(new Mark(bounds, null, LEARNING, "Learning destinations"));
			}
		}

		TeleportDestination selected = TeleportCatalog.byId(state.destinationId)
			.filter(destination -> destination.getSource() == state.source).orElse(null);
		if (selected != null)
		{
			if (selected.getSource() == TeleportSource.FAIRY_RING && confirm != null)
			{
				fairyMarks(result, selected);
			}
			else if (selected.getSource() == TeleportSource.PORTAL_NEXUS
				&& open.containsKey(TeleportSource.PORTAL_NEXUS))
			{
				Rectangle clip = bounds(visible(InterfaceID.TelenexusTeleport.SCROLLING1));
				addRows(result, client.getWidget(InterfaceID.TelenexusTeleport.TEXT1), clip, selected);
				addRows(result, client.getWidget(InterfaceID.TelenexusTeleport.EXTRAS), clip, selected);
			}
			else if (selected.getSource() == TeleportSource.JEWELLERY_BOX
				&& open.containsKey(TeleportSource.JEWELLERY_BOX))
			{
				for (int component : BOX_SECTIONS)
				{
					addRows(result, client.getWidget(component), open.get(TeleportSource.JEWELLERY_BOX), selected);
				}
			}
			else if (map != null && map.source == selected.getSource())
			{
				Widget button = map.destination(selected);
				if (button != null)
				{
					addRectangle(result, button.getBounds(), map.bounds, RECOMMENDED, null);
				}
			}
			else if (generic != null && (open.containsKey(selected.getSource())
				|| !TeleportSource.menuSources().contains(selected.getSource()) && open.isEmpty()))
			{
				// A shared destination name must not redirect a recommendation to
				// a different source. Other fixture menus have no learning interface.
				addRows(result, generic, genericClip(generic), selected);
			}
		}

		return Collections.unmodifiableList(result);
	}

	private void fairyMarks(List<Mark> marks, TeleportDestination selected)
	{
		Widget entry = FairyRingTravelLog.readableEntry(client, selected.getId());
		Rectangle logClip = bounds(visible(InterfaceID.FairyringsLog.CONTENTS));
		if (entry != null && logClip != null)
		{
			String code = selected.getId().substring("fairy-".length()).toUpperCase(Locale.ROOT);
			addRectangle(marks, entry.getBounds(), logClip, RECOMMENDED, "Use " + code);
		}
	}

	private Rectangle fairySelector(Widget confirm)
	{
		Rectangle selector = new Rectangle(confirm.getBounds());
		for (int[] controls : DIAL_BUTTONS)
		{
			for (int component : controls)
			{
				Rectangle control = bounds(visible(component));
				if (control != null)
				{
					selector = selector.union(control);
				}
			}
		}
		selector.grow(4, 4);
		return selector;
	}

	private void addRows(List<Mark> marks, Widget container, Rectangle clip, TeleportDestination selected)
	{
		if (container == null || container.isHidden())
		{
			return;
		}
		Widget[] rows = container.getDynamicChildren();
		if (rows == null)
		{
			return;
		}
		for (Widget row : rows)
		{
			if (matchesMenuRow(selected, row))
			{
				addRectangle(marks, row.getBounds(), clip, RECOMMENDED, null);
			}
		}
	}

	private boolean matchesMenuRow(TeleportDestination selected, Widget row)
	{
		if (row == null) return false;
		if (selected.getSource() == TeleportSource.JEWELLERY_BOX)
		{
			String label = JewelleryBoxReader.destinationLabel(row);
			return label != null && (JewelleryBoxReader.hasNativeDestination(row)
				? matchesAvailableRow(selected, row, label) : matchesAvailableRow(selected, row));
		}
		if (selected.getSource() != TeleportSource.PORTAL_NEXUS && menus.isCaptured(row))
		{
			return matchesAvailableRow(selected, row, menus.text(row));
		}
		return matchesAvailableRow(selected, row);
	}

	/** Scrolling and highlighting must agree on the existing enabled menu entry. */
	static boolean matchesAvailableRow(TeleportDestination selected, Widget row)
	{
		return row != null && row.getTextColor() != 0x7f7f7f && row.getTextColor() != 0x808080
			&& matchesAvailableRow(selected, row, row.getText());
	}

	/** Native metadata retains availability locks when display names/colors are customized. */
	static boolean matchesAvailableRow(TeleportDestination selected, Widget row, String authoritativeLabel)
	{
		if (selected == null || row == null || row.isHidden() || authoritativeLabel == null)
		{
			return false;
		}
		String label = stripKey(authoritativeLabel);
		String markup = label.toLowerCase(Locale.ROOT);
		return !markup.contains("<str") && !markup.contains("<col=") && matches(selected, label);
	}

	static boolean matches(TeleportDestination selected, String row)
	{
		String label = normalize(row);
		if (label.equals(normalize(selected.getName())))
		{
			return true;
		}
		if (selected.getSource() == TeleportSource.MOUNTED_XERICS_TALISMAN)
		{
			return selected.getId().equals(HouseTransportReader.xericDestinationId(label));
		}
		if (selected.getSource() == TeleportSource.PORTAL_NEXUS)
		{
			return TeleportCatalog.nexusDestination(Text.removeTags(row).trim())
				.map(destination -> destination.getId().equals(selected.getId())).orElse(false);
		}
		if (selected.getSource() == TeleportSource.CAPE_HANGER)
		{
			String name = normalize(selected.getName());
			if (name.startsWith("poh portal - "))
			{
				return label.equals(name.substring("poh portal - ".length()));
			}
			if (selected.getId().startsWith("cape-diary-"))
			{
				return label.endsWith(" - " + name) || label.startsWith(name + " (");
			}
		}
		if (selected.getSource() == TeleportSource.WILDERNESS_OBELISK)
		{
			return label.equals("level " + selected.getId().substring("obelisk-".length()));
		}
		return "jewellery-chasm-of-tears".equals(selected.getId()) && "tears of guthix".equals(label)
			|| "tree-gnome-stronghold".equals(selected.getId()) && "tree gnome stronghold".equals(label);
	}

	private Widget genericMenu()
	{
		Widget modern = visible(InterfaceID.MenuNew.TEXT);
		return modern != null ? modern : visible(InterfaceID.Menu.LJ_LAYER1);
	}

	private Rectangle genericClip(Widget container)
	{
		int component = container.getId() == InterfaceID.MenuNew.TEXT
			? InterfaceID.MenuNew.CONTENT_SCROLL : InterfaceID.Menu.LJ_LAYER2;
		Rectangle clip = bounds(visible(component));
		return clip == null ? container.getBounds() : clip;
	}

	private String firstLabel(Widget container)
	{
		Widget[] rows = container.getDynamicChildren();
		if (rows != null)
		{
			for (Widget row : rows)
			{
				String label = row == null ? null : menus.text(row);
				// Hidden disabled entries still identify a retained native menu;
				// visibility is checked separately before drawing a destination.
				if (label != null && !label.isBlank())
				{
					return normalize(stripKey(label));
				}
			}
		}
		return "";
	}

	private void putOpen(Map<TeleportSource, Rectangle> open, TeleportSource source, int component)
	{
		Rectangle bounds = bounds(visible(component));
		if (bounds != null)
		{
			open.put(source, bounds);
		}
	}

	private Widget visible(int component)
	{
		Widget widget = client.getWidget(component);
		return widget == null || widget.isHidden() || bounds(widget) == null ? null : widget;
	}

	private static Rectangle bounds(Widget widget)
	{
		if (widget == null)
		{
			return null;
		}
		Rectangle bounds = widget.getBounds();
		return bounds == null || bounds.width <= 0 || bounds.height <= 0 ? null : bounds;
	}

	private static void addRectangle(List<Mark> marks, Rectangle rectangle, Rectangle clip, Color color, String label)
	{
		if (rectangle == null)
		{
			return;
		}
		Rectangle visible = clip == null ? new Rectangle(rectangle) : rectangle.intersection(clip);
		if (visible.width > 0 && visible.height > 0)
		{
			marks.add(new Mark(visible, clip, color, label));
		}
	}

	static String stripKey(String text)
	{
		if (text == null)
		{
			return "";
		}
		String raw = text.trim();
		String withoutColoredKey = COLORED_KEY_PREFIX.matcher(raw).replaceFirst("");
		// A destination may itself contain a colon. Strip one key prefix only.
		return withoutColoredKey.equals(raw) ? KEY_PREFIX.matcher(raw).replaceFirst("") : withoutColoredKey;
	}

	private static String normalize(String text)
	{
		return Text.removeTags(text).trim().replace('\u2019', '\'').replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private void drawLabel(Graphics2D graphics, Mark mark)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		Rectangle bounds = mark.shape.getBounds();
		int width = metrics.stringWidth(mark.label) + 10;
		int height = metrics.getHeight() + 4;
		int x = Math.max(2, Math.min(bounds.x, client.getCanvasWidth() - width - 2));
		int y = Math.max(2, bounds.y - height - 4);
		graphics.setColor(BACKGROUND);
		graphics.fillRoundRect(x, y, width, height, 5, 5);
		graphics.setColor(mark.color);
		graphics.drawString(mark.label, x + 5, y + 2 + metrics.getAscent());
	}

	static final class Mark
	{
		final Shape shape;
		final Rectangle clip;
		final Color color;
		final String label;

		private Mark(Shape shape, Rectangle clip, Color color, String label)
		{
			this.shape = shape;
			this.clip = clip;
			this.color = color;
			this.label = label;
		}
	}

	/** Immutable state supplied by the plugin; null guidance hides all marks. */
	public static final class Guidance
	{
		private final boolean ownHouse;
		private final Set<TeleportSource> pendingSources;
		private final TeleportSource source;
		private final String destinationId;

		public Guidance(boolean ownHouse, Set<TeleportSource> pendingSources, TeleportSource source, String destinationId)
		{
			this.ownHouse = ownHouse;
			Set<TeleportSource> pending = EnumSet.noneOf(TeleportSource.class);
			pending.addAll(Objects.requireNonNull(pendingSources));
			this.pendingSources = Collections.unmodifiableSet(pending);
			this.source = source;
			this.destinationId = destinationId;
		}

		public boolean isOwnHouse()
		{
			return ownHouse;
		}

		public Set<TeleportSource> getPendingSources()
		{
			return pendingSources;
		}

		public TeleportSource getSource()
		{
			return source;
		}

		public String getDestinationId()
		{
			return destinationId;
		}
	}
}
