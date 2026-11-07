package com.clueteleports;

import com.clueteleports.routing.AccountRouteState;
import com.clueteleports.routing.RouteGoals;
import com.clueteleports.routing.RouteRequirements;
import com.clueteleports.routing.RouteTransport;
import com.clueteleports.routing.RoutingData;
import com.clueteleports.routing.WalkingRouteResult;
import com.clueteleports.routing.WalkingRouteSearch;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One cancellable background search, cached by clue and usable physical graph. */
final class AsyncRoutePlanner implements TeleportRouting
{
	interface Loader { RoutingData load() throws IOException; }
	private static final Logger log = LoggerFactory.getLogger(AsyncRoutePlanner.class);
	private final ExecutorService worker;
	private final WalkingRouteSearch search;
	private final AtomicLong generation = new AtomicLong();
	private volatile RoutingData data;
	private volatile List<RouteRequirements> requirements = Collections.emptyList();
	private volatile boolean loadFailed;
	private volatile boolean closed;
	private final Future<?> loading;
	private Future<?> pending;
	private Request request;
	private Plan plan = Plan.status(Plan.Status.PREPARING);

	AsyncRoutePlanner()
	{
		this(Executors.newSingleThreadExecutor(task ->
		{
			Thread thread = new Thread(task, "poh-clue-routes");
			thread.setDaemon(true);
			return thread;
		}), RoutingData::load, new WalkingRouteSearch());
	}

	AsyncRoutePlanner(ExecutorService worker, Loader loader, WalkingRouteSearch search)
	{
		this.worker = Objects.requireNonNull(worker);
		this.search = Objects.requireNonNull(search);
		loading = worker.submit(() ->
		{
			try
			{
				RoutingData loaded = loader.load();
				requirements = Collections.unmodifiableList(loaded.getTransports().stream()
					.map(RouteTransport::getRequirements).collect(Collectors.toList()));
				data = loaded;
			}
			catch (IOException | RuntimeException error)
			{
				loadFailed = true;
				log.warn("Unable to load bundled POH walking-route data", error);
			}
		});
	}

	@Override
	public Plan update(Client client, String profile, List<WorldPoint> targets,
		Collection<TeleportDestination> arrivals, int interactionRadius)
	{
		RoutingData loaded = data;
		if (closed || loadFailed) return Plan.status(Plan.Status.UNAVAILABLE);
		if (loaded == null) return Plan.status(Plan.Status.PREPARING);
		// All live Client reads finish here, on the caller's client thread.
		AccountRouteState state = AccountRouteState.capture(client, requirements);
		// Raw item quantities and skill boosts may change without opening or
		// closing a passage. Cache by the resulting graph to avoid bank churn.
		List<RouteTransport> eligible = loaded.getTransports().stream()
			.filter(edge -> edge.getRequirements().isSatisfied(state)).collect(Collectors.toList());
		Request next = new Request(profile, targets, arrivals, interactionRadius, eligible);
		synchronized (this)
		{
			if (closed) return Plan.status(Plan.Status.UNAVAILABLE);
			if (next.equals(request)) return plan;
			long version = generation.incrementAndGet();
			if (pending != null) pending.cancel(true);
			request = next;
			plan = Plan.status(Plan.Status.SEARCHING);
			pending = worker.submit(() ->
			{
				Plan answer;
				try
				{
					List<WorldPoint> goals = RouteGoals.qualify(loaded.getCollisionMap(), next.targets, next.interactionRadius);
					WalkingRouteResult result = search.search(loaded.getCollisionMap(), next.eligible, goals, next.arrivals,
						() -> closed || generation.get() != version || Thread.currentThread().isInterrupted());
					answer = Plan.from(result);
				}
				catch (RuntimeException error)
				{
					answer = Plan.status(Plan.Status.UNAVAILABLE);
					log.warn("Unable to calculate a POH walking route", error);
				}
				finish(version, answer);
			});
			return plan;
		}
	}

	private synchronized void finish(long version, Plan answer)
	{
		if (!closed && generation.get() == version) plan = answer;
	}

	@Override
	public synchronized void invalidate()
	{
		generation.incrementAndGet();
		if (pending != null) pending.cancel(true);
		pending = null;
		request = null;
		plan = Plan.status(data == null ? Plan.Status.PREPARING : Plan.Status.SEARCHING);
	}

	@Override
	public synchronized void close()
	{
		closed = true;
		invalidate();
		loading.cancel(true);
		worker.shutdownNow();
	}

	private static final class Request
	{
		final String profile;
		final List<WorldPoint> targets;
		final List<TeleportDestination> arrivals;
		final int interactionRadius;
		final List<RouteTransport> eligible;

		Request(String profile, List<WorldPoint> targets, Collection<TeleportDestination> arrivals, int radius, List<RouteTransport> eligible)
		{
			this.profile = profile;
			List<WorldPoint> copiedTargets = new ArrayList<>(targets);
			copiedTargets.sort(Comparator.comparingInt(WorldPoint::getPlane).thenComparingInt(WorldPoint::getX).thenComparingInt(WorldPoint::getY));
			this.targets = Collections.unmodifiableList(copiedTargets);
			List<TeleportDestination> copiedArrivals = new ArrayList<>(arrivals);
			copiedArrivals.sort(Comparator.comparing(TeleportDestination::getId));
			this.arrivals = Collections.unmodifiableList(copiedArrivals);
			this.interactionRadius = radius;
			this.eligible = List.copyOf(eligible);
		}

		@Override public boolean equals(Object other)
		{
			if (!(other instanceof Request)) return false;
			Request that = (Request) other;
			return interactionRadius == that.interactionRadius && Objects.equals(profile, that.profile)
				&& targets.equals(that.targets) && arrivals.equals(that.arrivals) && eligible.equals(that.eligible);
		}
		@Override public int hashCode() { return Objects.hash(profile, targets, arrivals, interactionRadius, eligible); }
	}
}
