package com.clueteleports;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Reads a complete spirit tree travel menu, on the client thread. */
public final class SpiritTreeReader
{
	private static final Pattern KEY_PREFIX = Pattern.compile("^[^:]+:\\s*");
	private static final Pattern COLORED_KEY_PREFIX = Pattern.compile(
		"^<col=[0-9a-fA-F]+>[^<]*(?::</col>|</col>\\s*:)\\s*");
	private static final Set<String> BASELINE = Set.of("tree-tree-gnome-village", "tree-gnome-stronghold",
		"tree-battlefield-of-khazard", "tree-grand-exchange");

	private SpiritTreeReader()
	{
	}

	/**
	 * Empty Optional means no complete observation; a present set replaces the
	 * cached network, so trees omitted or disabled by the game are removed.
	 *
	 * RuneLite cs2-scripts [clientscript,menu], [proc,menu_createentry], and
	 * [proc,menu_new_createentry] populate these dynamic text containers.
	 * Gray availability markup is also documented by Shortest Path's own parser:
	 * https://github.com/Skretzo/shortest-path/blob/master/src/main/java/shortestpath/spirittree/SpiritTreeService.java
	 * Exact option labels, including the four baseline trees, match:
	 * https://github.com/trs/runelite-teleport-maps/blob/e006270494500ab8e4826903b377bb945ca9fc96/src/main/resources/SpiritTreeMap/TreeDefinitions.json
	 * Unlike that service's partial-patch merge, this reader requires the full
	 * baseline and every visible row to parse before replacing a cached set.
	 */
	public static Optional<Set<String>> readOpenMenu(Client client)
	{
		Optional<Set<String>> current = read(client.getWidget(InterfaceID.MenuNew.TEXT));
		return current.isPresent() ? current : read(client.getWidget(InterfaceID.Menu.LJ_LAYER1));
	}

	private static Optional<Set<String>> read(Widget container)
	{
		if (container == null || container.isHidden())
		{
			return Optional.empty();
		}
		Widget[] rows = container.getDynamicChildren();
		if (rows == null || rows.length < BASELINE.size())
		{
			return Optional.empty();
		}
		Map<String, String> names = new LinkedHashMap<>();
		for (TeleportDestination destination : TeleportCatalog.forSource(TeleportSource.SPIRIT_TREE))
		{
			names.put(destination.getName().toLowerCase(Locale.ROOT), destination.getId());
		}
		names.put("tree gnome stronghold", "tree-gnome-stronghold");
		Set<String> listed = new LinkedHashSet<>();
		Set<String> available = new LinkedHashSet<>();
		boolean firstLabelSeen = false;
		for (Widget row : rows)
		{
			if (row == null)
			{
				return Optional.empty();
			}
			String raw = row.getText();
			if (raw == null || raw.trim().isEmpty())
			{
				// MENU_NEW creates hidden empty framework entries. A visible
				// blank row still indicates an incomplete menu.
				if (row.isHidden())
				{
					continue;
				}
				return Optional.empty();
			}
			// Better Teleport Menu can replace one-character keys with
			// chords, remove them, or keep the colon inside the color tag.
			String label = COLORED_KEY_PREFIX.matcher(raw.trim()).replaceFirst("");
			String name = KEY_PREFIX.matcher(Text.removeTags(label).trim()).replaceFirst("")
				.toLowerCase(Locale.ROOT);
			if (!firstLabelSeen && !"tree gnome village".equals(name))
			{
				return Optional.empty();
			}
			firstLabelSeen = true;
			// The game can append the house's location in parentheses.
			if ("your house".equals(name) || (name.startsWith("your house (") && name.endsWith(")"))
				|| "cancel".equals(name) || "close".equals(name))
			{
				continue;
			}
			String id = names.get(name);
			if (id == null || !listed.add(id))
			{
				return Optional.empty();
			}
			// Disabled options carry gray markup. Treat other explicit status
			// colors or strike markup conservatively as unavailable, too.
			String markup = label.toLowerCase(Locale.ROOT);
			if (!row.isHidden() && !markup.contains("<col=") && !markup.contains("<str"))
			{
				available.add(id);
			}
		}
		return listed.containsAll(BASELINE)
			? Optional.of(Collections.unmodifiableSet(available)) : Optional.empty();
	}
}
