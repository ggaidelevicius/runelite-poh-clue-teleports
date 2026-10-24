package com.clueteleports;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.util.Text;

/**
 * Read-only guidance for the Hub spirit-tree-map plugin's existing map buttons.
 * Source: MJHylkema/spirit-tree-map at
 * 25382eeebed8d5b88abfb9710d4040f986687caf, AdventureLogComposite,
 * SpiritTreeMap, XericsMap, WildernessObeliskMap, and ui/UIComponent.
 *
 * The pinned implementation builds maps into legacy MENU:0 or MENU_NEW:0,
 * retains the native entry widgets, and names Travel wrappers from their
 * current displayed labels. Obelisk wrappers use canonical wilderness names.
 * UIComponent installs one JavaScriptCallback and stores actions on wrappers.
 * Callback contents are opaque and are never inspected or executed here.
 */
final class TeleportMapsGuidance
{
	private static final int[] ROOTS = {InterfaceID.Menu.LJ_LAYER2, InterfaceID.MenuNew.INFINITE};
	private static final int[] LISTS = {InterfaceID.Menu.LJ_LAYER1, InterfaceID.MenuNew.TEXT};

	private TeleportMapsGuidance()
	{
	}

	static View read(Client client, NativeTeleportMenus menus)
	{
		if (client == null || menus == null) return null;
		View result = null;
		for (int index = 0; index < ROOTS.length; index++)
		{
			Widget root = client.getWidget(ROOTS[index]);
			Widget nativeList = client.getWidget(LISTS[index]);
			Rectangle rootBounds = visibleBounds(root);
			if (rootBounds == null || root.getId() != ROOTS[index] || nativeList == null
				|| nativeList.getId() != LISTS[index] || !menus.hasCapturedMenu(nativeList)) continue;
			Widget[] children = root.getDynamicChildren();
			if (children == null) continue;
			Widget background = null;
			TeleportSource source = null;
			boolean valid = true;
			for (int childIndex = 0; childIndex < children.length; childIndex++)
			{
				Widget child = children[childIndex];
				if (!directChild(child, ROOTS[index], childIndex))
				{
					valid = false;
					break;
				}
				if (child.getType() != WidgetType.GRAPHIC || visibleBounds(child) == null) continue;
				TeleportSource identity = source(child.getSpriteId());
				if (identity == null) continue;
				if (background != null)
				{
					valid = false;
					break;
				}
				background = child;
				source = identity;
			}
			if (!valid || background == null || !nativeSource(menus, nativeList, source)) continue;
			Rectangle clip = rootBounds.intersection(background.getBounds());
			if (clip.isEmpty() || buttons(children, rootBounds, source) == null) continue;
			if (result != null) return null;
			result = new View(source, clip, root, nativeList, background, menus);
		}
		return result;
	}

	static final class View
	{
		final TeleportSource source;
		final Rectangle bounds;
		private final Widget root;
		private final Widget nativeList;
		private final Widget background;
		private final NativeTeleportMenus menus;

		private View(TeleportSource source, Rectangle bounds, Widget root, Widget nativeList,
			Widget background, NativeTeleportMenus menus)
		{
			this.source = source;
			this.bounds = new Rectangle(bounds);
			this.root = root;
			this.nativeList = nativeList;
			this.background = background;
			this.menus = menus;
		}

		Widget destination(TeleportDestination selected)
		{
			Rectangle rootBounds = visibleBounds(root);
			if (selected == null || selected.getSource() != source || rootBounds == null
				|| visibleBounds(background) == null || !menus.hasCapturedMenu(nativeList)) return null;
			Widget[] children = root.getDynamicChildren();
			if (children == null) return null;
			boolean retainedBackground = false;
			for (int index = 0; index < children.length; index++)
			{
				if (!directChild(children[index], root.getId(), index)) return null;
				if (children[index] == background) retainedBackground = true;
			}
			if (!retainedBackground) return null;
			List<Button> buttons = buttons(children, rootBounds, source);
			if (buttons == null) return null;
			Widget original = null;
			Widget[] rows = nativeList.getDynamicChildren();
			for (Widget row : rows)
			{
				if (!menus.isCaptured(row)) return null;
				String raw = menus.text(row);
				if (matches(selected, HouseLearningOverlay.stripKey(raw)))
				{
					if (original != null || !enabled(raw)) return null;
					original = row;
				}
			}
			if (original == null) return null;
			String displayed = normalize(HouseLearningOverlay.stripKey(original.getText()));
			Widget result = null;
			for (Button button : buttons)
			{
				if (!button.enabled || !(button.name.equals(displayed) || matches(selected, button.name))) continue;
				// A canonical alias may now be another native destination's custom
				// display name. It must never redirect guidance to that row's button.
				for (Widget row : rows)
				{
					if (row != original && !menus.text(row).isBlank()
						&& button.name.equals(normalize(HouseLearningOverlay.stripKey(row.getText())))) return null;
				}
				if (result != null) return null;
				result = button.widget;
			}
			return result;
		}
	}

	private static List<Button> buttons(Widget[] children, Rectangle rootBounds, TeleportSource source)
	{
		List<Button> result = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (Widget child : children)
		{
			Rectangle bounds = visibleBounds(child);
			if (child.getType() != WidgetType.GRAPHIC || bounds == null || !rootBounds.contains(bounds)) continue;
			String name = normalize(child.getName());
			if (name.isEmpty()) continue;
			if (!names.add(name)) return null;
			Object[] callback = child.getOnOpListener();
			String[] actions = child.getActions();
			boolean enabled = callback != null && callback.length == 1 && callback[0] instanceof JavaScriptCallback
				&& actions != null && actions.length > 0
				&& (source == TeleportSource.WILDERNESS_OBELISK ? "Set" : "Travel").equals(actions[0]);
			result.add(new Button(child, name, enabled));
		}
		return result;
	}

	private static boolean nativeSource(NativeTeleportMenus menus, Widget container, TeleportSource source)
	{
		Widget[] rows = container.getDynamicChildren();
		if (rows == null) return false;
		for (Widget row : rows)
		{
			if (!menus.isCaptured(row)) return false;
			String original = normalize(HouseLearningOverlay.stripKey(menus.text(row)));
			if (original.isEmpty()) continue;
			switch (source)
			{
				case SPIRIT_TREE: return "tree gnome village".equals(original);
				case MOUNTED_XERICS_TALISMAN: return "xeric-lookout".equals(HouseTransportReader.xericDestinationId(original));
				case WILDERNESS_OBELISK: return original.matches("level (13|19|27|35|44|50)( wilderness)?");
				default: return false;
			}
		}
		return false;
	}

	private static boolean matches(TeleportDestination selected, String label)
	{
		if (HouseLearningOverlay.matches(selected, label)) return true;
		return selected.getSource() == TeleportSource.WILDERNESS_OBELISK
			&& normalize(label).equals("level " + selected.getId().substring("obelisk-".length()) + " wilderness");
	}

	private static boolean enabled(String raw)
	{
		String label = HouseLearningOverlay.stripKey(raw);
		String markup = label.toLowerCase(Locale.ROOT);
		return !normalize(label).isEmpty() && !markup.contains("<str") && !markup.contains("<col=");
	}

	private static TeleportSource source(int sprite)
	{
		if (sprite == -19000) return TeleportSource.SPIRIT_TREE;
		if (sprite == -19400) return TeleportSource.MOUNTED_XERICS_TALISMAN;
		if (sprite == -19600) return TeleportSource.WILDERNESS_OBELISK;
		return null;
	}

	private static boolean directChild(Widget widget, int parent, int index)
	{
		return widget != null && widget.getId() == parent && widget.getParentId() == parent && widget.getIndex() == index;
	}

	private static Rectangle visibleBounds(Widget widget)
	{
		if (widget == null || widget.isHidden()) return null;
		Rectangle bounds = widget.getBounds();
		return bounds != null && bounds.width > 0 && bounds.height > 0 ? bounds : null;
	}

	private static String normalize(String text)
	{
		return text == null ? "" : Text.removeTags(text).trim().replace('\u2019', '\'')
			.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	private static final class Button
	{
		private final Widget widget;
		private final String name;
		private final boolean enabled;

		private Button(Widget widget, String name, boolean enabled)
		{
			this.widget = widget;
			this.name = name;
			this.enabled = enabled;
		}
	}
}
