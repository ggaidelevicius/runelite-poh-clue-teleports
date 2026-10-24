package com.clueteleports;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/** Small client/widget proxies; these readers must only inspect menu state. */
final class MenuReaderFixture
{
	final Map<Integer, Widget> widgets = new HashMap<>();
	final Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(),
		new Class<?>[]{Client.class}, (proxy, method, args) ->
		{
			if ("getWidget".equals(method.getName()) && args.length == 1)
			{
				return widgets.get(args[0]);
			}
			throw new UnsupportedOperationException(method.getName());
		});

	static Widget container(Widget... children)
	{
		return widget("", 0, false, children);
	}

	static Widget text(String text)
	{
		return widget(text, 0xffffff, false, new Widget[0]);
	}

	static Widget widget(String text, int color, boolean hidden, Widget[] children)
	{
		return widget(text, color, hidden, children, 0, 0, null);
	}

	static Widget widget(String text, int color, boolean hidden, Widget[] children, int component, int index, Object[] listener)
	{
		return (Widget) Proxy.newProxyInstance(Widget.class.getClassLoader(), new Class<?>[]{Widget.class},
			(proxy, method, args) ->
			{
				switch (method.getName())
				{
					case "getText": return text;
					case "getTextColor": return color;
					case "getOnOpListener": return listener;
					case "getId": return component;
					case "getIndex": return index;
					case "isHidden": return hidden;
					case "getDynamicChildren": return children;
					default: throw new UnsupportedOperationException(method.getName());
				}
			});
	}
}
