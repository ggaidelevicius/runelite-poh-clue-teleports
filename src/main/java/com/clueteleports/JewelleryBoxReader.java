package com.clueteleports;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Reads built box tiers and the game's individual destination locks. */
public final class JewelleryBoxReader
{
	private static final Pattern KEY_PREFIX = Pattern.compile("^[^:]+:\\s*");
	private static final Pattern COLORED_KEY_PREFIX = Pattern.compile(
		"^<col=[0-9a-fA-F]+>[^<]*(?::</col>|</col>\\s*:)\\s*");
	private static final Section[] SECTIONS = {
		new Section(InterfaceID.PohJewelleryBox.DUELING, "Ring of Dueling", "Emir's Arena", "Castle Wars", "Ferox Enclave", "Fortis Colosseum"),
		new Section(InterfaceID.PohJewelleryBox.GAMING, "Games Necklace", "Burthorpe", "Barbarian Outpost", "Corporeal Beast", "Tears of Guthix", "Wintertodt Camp"),
		new Section(InterfaceID.PohJewelleryBox.COMBAT, "Combat bracelet", "Warriors' Guild", "Champions' Guild", "Monastery", "Ranging Guild"),
		new Section(InterfaceID.PohJewelleryBox.SKILLS, "Skills necklace", "Fishing Guild", "Mining Guild", "Crafting Guild", "Cooking Guild", "Woodcutting Guild", "Farming Guild"),
		new Section(InterfaceID.PohJewelleryBox.WEALTH, "Ring of Wealth", "Miscellania", "Grand Exchange", "Falador Park", "Dondakan's Rock"),
		new Section(InterfaceID.PohJewelleryBox.GLORY, "Amulet of Glory", "Edgeville", "Karamja", "Draynor Village", "Al Kharid")
	};

	private JewelleryBoxReader()
	{
	}

	/**
	 * Invoke on the client thread. Only a completely initialized menu yields a
	 * replacement snapshot, including removals after downgrades or new locks.
	 *
	 * RuneLite cs2-scripts [clientscript,poh_jewellery_box_init] (1685) creates
	 * 27 universe placeholders, then six sections. [proc,poh_jewellery_box_prepbox]
	 * (1686) uses white/gray headers for built/unbuilt tiers, and
	 * [proc,poh_jewellery_box_addbutton] (1688) omits buttons above the built tier.
	 * Individual locks, including quest and league locks, are marked <str>.
	 * https://github.com/runelite/cs2-scripts/blob/1d418ffb6bce96c2d0712eb2652712d28e01b115/scripts/%5Bclientscript,poh_jewellery_box_init%5D.cs2
	 */
	public static Optional<Set<String>> readOpenMenu(Client client)
	{
		Widget universe = client.getWidget(InterfaceID.PohJewelleryBox.UNIVERSE);
		if (universe == null || universe.isHidden())
		{
			return Optional.empty();
		}
		Widget[] placeholders = universe.getDynamicChildren();
		if (placeholders == null || placeholders.length != 27 || Arrays.stream(placeholders).anyMatch(widget -> widget == null))
		{
			return Optional.empty();
		}
		Set<String> available = new LinkedHashSet<>();
		boolean[] built = new boolean[SECTIONS.length];
		for (int index = 0; index < SECTIONS.length; index++)
		{
			Section section = SECTIONS[index];
			Widget container = client.getWidget(section.component);
			if (container == null || container.isHidden())
			{
				return Optional.empty();
			}
			Widget[] children = container.getDynamicChildren();
			if (children == null || children.length == 0)
			{
				return Optional.empty();
			}
			boolean headerSeen = false;
			Set<String> listed = new LinkedHashSet<>();
			for (Widget child : children)
			{
				if (child == null || child.isHidden())
				{
					return Optional.empty();
				}
				String raw = child.getText();
				if (raw == null || raw.trim().isEmpty())
				{
					continue;
				}
				String original = destinationLabel(child);
				if (original == null)
				{
					return Optional.empty();
				}
				String plain = Text.removeTags(original).trim();
				if (section.header.equals(plain))
				{
					if (headerSeen || (child.getTextColor() != 0xffffff && child.getTextColor() != 0x7f7f7f))
					{
						return Optional.empty();
					}
					headerSeen = true;
					built[index] = child.getTextColor() == 0xffffff;
					continue;
				}
				// Native keys put the colon inside the color tag; wrappers may
				// put it outside or replace the key with a chord. Remove only
				// key markup, preserving the destination's strike/color locks.
				String label = COLORED_KEY_PREFIX.matcher(original.trim()).replaceFirst("");
				String name = KEY_PREFIX.matcher(Text.removeTags(label).trim()).replaceFirst("");
				if (!section.destinations.contains(name) || !listed.add(name))
				{
					return Optional.empty();
				}
				Optional<TeleportDestination> destination = TeleportCatalog.forSource(TeleportSource.JEWELLERY_BOX).stream()
					.filter(option -> option.getName().equals(name)
						|| ("Tears of Guthix".equals(name) && "jewellery-chasm-of-tears".equals(option.getId())))
					.findFirst();
				if (!destination.isPresent())
				{
					return Optional.empty();
				}
				String markup = label.toLowerCase(Locale.ROOT);
				if (!markup.contains("<str") && !markup.contains("<col="))
				{
					available.add(destination.get().getId());
				}
			}
			if (!headerSeen || (built[index] ? !listed.equals(section.destinations) : !listed.isEmpty()))
			{
				return Optional.empty();
			}
		}
		if (!built[0] || !built[1] || built[2] != built[3] || built[4] != built[5] || (built[4] && !built[2]))
		{
			return Optional.empty();
		}
		return Optional.of(Collections.unmodifiableSet(available));
	}

	/**
	 * Easy Teleports changes the displayed text, including arbitrary names and
	 * colors, but leaves the native operation listener intact. Read its original
	 * label (and strike locks), validating the operation's destination, section,
	 * and child identity instead of guessing from a replacement label or position.
	 * The listener layout is poh_jewellery_box_addbutton / poh_jewellery_box_op:
	 * https://github.com/runelite/cs2-scripts/blob/1d418ffb6bce96c2d0712eb2652712d28e01b115/scripts/%5Bproc,poh_jewellery_box_addbutton%5D.cs2
	 * Cosmetic replacements:
	 * https://github.com/DapperMickie/easy-teleports/blob/2b18c2bfa57494185fdffbc93c9d91042a3b9cf5/src/main/java/com/duckblade/osrs/easyteleports/EasyTeleportsPlugin.java
	 */
	static String destinationLabel(Widget row)
	{
		Object[] listener = row.getOnOpListener();
		return listener == null ? row.getText() : nativeDestinationLabel(row, listener);
	}

	static boolean hasNativeDestination(Widget row)
	{
		Object[] listener = row.getOnOpListener();
		return listener != null && nativeDestinationLabel(row, listener) != null;
	}

	private static String nativeDestinationLabel(Widget row, Object[] listener)
	{
		if (listener.length != 8 || !Integer.valueOf(1690).equals(listener[0])
			|| !Integer.valueOf(ScriptEvent.MENU_OP).equals(listener[1])
			|| !(listener[2] instanceof Integer) || !(listener[3] instanceof Integer)
			|| !(listener[4] instanceof Integer) || !(listener[5] instanceof String)
			|| !(listener[6] instanceof String) || !(listener[7] instanceof String)
			|| !Integer.valueOf(row.getId()).equals(listener[3])
			|| !Integer.valueOf(row.getIndex()).equals(listener[4]))
		{
			return null;
		}
		int destinationIndex = (Integer) listener[2];
		if (destinationIndex < 0)
		{
			return null;
		}
		String original = (String) listener[5];
		String label = COLORED_KEY_PREFIX.matcher(original.trim()).replaceFirst("");
		String name = KEY_PREFIX.matcher(Text.removeTags(label).trim()).replaceFirst("");
		for (Section section : SECTIONS)
		{
			for (String expected : section.destinations)
			{
				if (destinationIndex-- == 0)
				{
					return section.component == row.getId() && expected.equals(name) ? original : null;
				}
			}
		}
		return null;
	}

	private static final class Section
	{
		private final int component;
		private final String header;
		private final Set<String> destinations;

		private Section(int component, String header, String... destinations)
		{
			this.component = component;
			this.header = header;
			this.destinations = new LinkedHashSet<>(Arrays.asList(destinations));
		}
	}
}
