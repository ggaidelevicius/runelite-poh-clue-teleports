package com.clueteleports;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.FontMetrics;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.components.TitleComponent;

public final class ClueTeleportsOverlay extends OverlayPanel
{
	private static final Color LEARNED_COLOR = new Color(126, 215, 137);
	private static final Color NOT_LEARNED_COLOR = new Color(255, 196, 93);
	private final ClueTeleportsPlugin plugin;

	@Inject
	ClueTeleportsOverlay(ClueTeleportsPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		setResizable(false);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Forget learned sources", "POH Clue Teleports",
			entry -> plugin.forgetAvailability());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Display display = plugin.getDisplay();
		if (display == null)
		{
			return null;
		}
		panelComponent.getChildren().clear();
		setPreferredSize(null);
		panelComponent.setPreferredSize(new Dimension(contentWidth(graphics.getFontMetrics(), display), 0));
		if (display.isLearning())
		{
			panelComponent.getChildren().add(TitleComponent.builder().text(display.first).color(Color.CYAN).build());
			panelComponent.getChildren().add(LineComponent.builder().left(display.second).leftColor(Color.LIGHT_GRAY).build());
			for (TeleportSource source : display.getRequiredSources())
			{
				boolean learned = display.getLearnedSources().contains(source);
				panelComponent.getChildren().add(LineComponent.builder()
					.left(source.getDisplayName())
					.right(learned ? "Learned" : "Not learned")
					.rightColor(learned ? LEARNED_COLOR : NOT_LEARNED_COLOR)
					.build());
			}
		}
		else
		{
			panelComponent.getChildren().add(TitleComponent.builder().text("POH clue teleport").color(Color.CYAN).build());
			panelComponent.getChildren().add(LineComponent.builder().left(display.first).build());
			panelComponent.getChildren().add(LineComponent.builder().left(display.second).build());
			if (display.third != null)
			{
				panelComponent.getChildren().add(LineComponent.builder().left(display.third).leftColor(Color.LIGHT_GRAY).build());
			}
		}
		// PanelComponent sizes its background from its previous layout. Measure
		// without painting first so content changes resize correctly this frame.
		Graphics2D measurement = (Graphics2D) graphics.create();
		try
		{
			measurement.setClip(0, 0, 0, 0);
			panelComponent.render(measurement);
		}
		finally { measurement.dispose(); }
		return super.render(graphics);
	}


	private static int contentWidth(FontMetrics metrics, Display display)
	{
		int width = textWidth(metrics, display.isLearning() ? display.first : "POH clue teleport");
		width = Math.max(width, textWidth(metrics, display.first));
		width = Math.max(width, textWidth(metrics, display.second));
		width = Math.max(width, textWidth(metrics, display.third));
		for (TeleportSource source : display.getRequiredSources())
		{
			String status = display.getLearnedSources().contains(source) ? "Learned" : "Not learned";
			width = Math.max(width, textWidth(metrics, source.getDisplayName()) + 12 + textWidth(metrics, status));
		}
		return Math.max(100, Math.min(300, width + 2 * ComponentConstants.STANDARD_BORDER));
	}

	private static int textWidth(FontMetrics metrics, String text)
	{
		return text == null ? 0 : metrics.stringWidth(Text.removeTags(text));
	}

	static final class Display
	{
		final String first;
		final String second;
		final String third;
		private final Set<TeleportSource> learnedSources;
		private final Set<TeleportSource> requiredSources;

		Display(String first, String second, String third)
		{
			this(first, second, third, null, Collections.emptySet());
		}

		private Display(String first, String second, String third, Set<TeleportSource> learnedSources, Set<TeleportSource> requiredSources)
		{
			this.first = first;
			this.second = second;
			this.third = third;
			this.learnedSources = learnedSources;
			this.requiredSources = requiredSources;
		}

		static Display learning(Map<TeleportSource, Set<String>> snapshots, Set<TeleportSource> required, String hint)
		{
			Objects.requireNonNull(snapshots, "snapshots");
			Set<TeleportSource> learned = EnumSet.noneOf(TeleportSource.class);
			learned.addAll(snapshots.keySet());
			learned.retainAll(required);
			Set<TeleportSource> copied = EnumSet.noneOf(TeleportSource.class);
			copied.addAll(required);
			return new Display("Learn POH teleports (" + learned.size() + "/" + required.size() + ")",
				Objects.requireNonNull(hint, "hint"), null, Collections.unmodifiableSet(learned), Collections.unmodifiableSet(copied));
		}

		Set<TeleportSource> getRequiredSources() { return requiredSources; }

		boolean isLearning()
		{
			return learnedSources != null;
		}

		Set<TeleportSource> getLearnedSources()
		{
			return learnedSources == null ? Collections.emptySet() : learnedSources;
		}
	}
}
