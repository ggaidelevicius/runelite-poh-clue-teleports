package com.clueteleports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Read-only original labels for one native generic travel-menu initialization.
 * Cosmetic plugins may rename, recolour, or hide the rendered native widgets.
 * Native script inputs retain destination identities and disabled-row markup.
 *
 * MENU (217) receives two strings and one integer; MENU_NEW (9142) receives
 * two strings and four integers. The first string is the title, and the second
 * is the pipe-delimited list. Native menu_createentry (218) consumes each list
 * entry. menu_new_createentry (9143) reuses an empty entry's index: only a final
 * empty entry leaves one hidden blank placeholder. These rules follow
 * runelite/cs2-scripts at 1d418ffb6bce96c2d0712eb2652712d28e01b115.
 *
 * A successful PostFired binds exact current widget identities once. This
 * object never runs callbacks/scripts or changes widgets or game variables.
 * Its owner clears it on account, game-state, and own-house transitions.
 */
public final class NativeTeleportMenus
{
	private static final int LEGACY_MENU = 217;
	private static final int MODERN_MENU = 9142;
	private Pending pending;
	private volatile Capture capture;

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event == null || !isMenuScript(event.getScriptId()))
		{
			return;
		}
		// A malformed, nested, unrelated, or different generic menu must never
		// retain an earlier menu's originals merely because row counts match.
		clear();
		ScriptEvent root = event.getScriptEvent();
		Object[] arguments = root == null ? null : root.getArguments();
		int scriptId = event.getScriptId();
		int expected = scriptId == LEGACY_MENU ? 4 : 7;
		if (arguments == null || arguments.length != expected || !(arguments[0] instanceof Integer)
			|| ((Integer) arguments[0]) != scriptId)
		{
			return;
		}
		List<String> strings = new ArrayList<>(2);
		for (int index = 1; index < arguments.length; index++)
		{
			Object argument = arguments[index];
			if (argument instanceof String)
			{
				strings.add((String) argument);
			}
			else if (!(argument instanceof Integer))
			{
				return;
			}
		}
		if (strings.size() != 2)
		{
			return;
		}
		String[] split = strings.get(1).split("\\|", -1);
		List<String> labels = new ArrayList<>(split.length);
		for (String label : split)
		{
			if (scriptId == LEGACY_MENU || !label.isEmpty())
			{
				labels.add(label);
			}
		}
		if (scriptId == MODERN_MENU && split[split.length - 1].isEmpty())
		{
			labels.add("");
		}
		pending = new Pending(scriptId, Collections.unmodifiableList(labels));
	}

	public void onScriptPostFired(Client client, ScriptPostFired event)
	{
		Pending original = pending;
		if (event == null || original == null || event.getScriptId() != original.scriptId)
		{
			return;
		}
		pending = null;
		if (!client.isClientThread())
		{
			throw new IllegalStateException("Native menu observations require the client thread");
		}
		int component = original.scriptId == LEGACY_MENU ? InterfaceID.Menu.LJ_LAYER1 : InterfaceID.MenuNew.TEXT;
		Widget container = client.getWidget(component);
		if (container == null || container.getId() != component)
		{
			return;
		}
		Widget[] rows = container.getDynamicChildren();
		if (rows == null || rows.length != original.labels.size())
		{
			return;
		}
		Map<Widget, String> mapped = new IdentityHashMap<>();
		for (int index = 0; index < rows.length; index++)
		{
			Widget row = rows[index];
			if (row == null || row.getId() != component || row.getIndex() != index)
			{
				return;
			}
			String label = original.labels.get(index);
			String visible = row.getText();
			if (label.isEmpty() ? !row.isHidden() || visible == null || !visible.isBlank()
				: visible == null || visible.isBlank())
			{
				return;
			}
			mapped.put(row, label);
		}
		capture = new Capture(container, Collections.unmodifiableMap(mapped));
	}

	public String text(Widget row)
	{
		if (row == null)
		{
			return null;
		}
		Capture current = validatedCapture();
		String original = current == null ? null : current.labels.get(row);
		return original == null ? row.getText() : original;
	}

	public boolean isCaptured(Widget row)
	{
		Capture current = validatedCapture();
		return current != null && current.labels.containsKey(row);
	}

	public boolean hasCapturedMenu(Widget container)
	{
		Capture current = validatedCapture();
		return current != null && current.container == container;
	}

	private Capture validatedCapture()
	{
		Capture current = capture;
		if (current == null)
		{
			return null;
		}
		Widget[] rows = current.container.getDynamicChildren();
		boolean valid = rows != null && rows.length == current.labels.size();
		if (valid)
		{
			Map<Widget, Boolean> seen = new IdentityHashMap<>();
			for (Widget row : rows)
			{
				if (row == null || !current.labels.containsKey(row) || seen.put(row, Boolean.TRUE) != null)
				{
					valid = false;
					break;
				}
			}
		}
		if (!valid)
		{
			// Never mix an old row's originals with a partly rebuilt menu. Once
			// invalidated, only a new native initialization may establish them.
			if (capture == current) capture = null;
			return null;
		}
		return current;
	}

	public void onWidgetLoaded(int groupId)
	{
		if (isMenuGroup(groupId)) clear();
	}

	public void onWidgetClosed(int groupId)
	{
		if (isMenuGroup(groupId)) clear();
	}

	public void clear()
	{
		pending = null;
		capture = null;
	}

	static boolean isMenuScript(int scriptId)
	{
		return scriptId == LEGACY_MENU || scriptId == MODERN_MENU;
	}

	private static boolean isMenuGroup(int groupId)
	{
		return groupId == InterfaceID.MENU || groupId == InterfaceID.MENU_NEW;
	}

	private static final class Pending
	{
		private final int scriptId;
		private final List<String> labels;

		private Pending(int scriptId, List<String> labels)
		{
			this.scriptId = scriptId;
			this.labels = labels;
		}
	}

	private static final class Capture
	{
		private final Widget container;
		private final Map<Widget, String> labels;

		private Capture(Widget container, Map<Widget, String> labels)
		{
			this.container = container;
			this.labels = labels;
		}
	}
}
