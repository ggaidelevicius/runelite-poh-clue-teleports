package com.clueteleports;

import com.clueteleports.TeleportRouting.Plan;
import com.clueteleports.routing.RoutingData;
import com.clueteleports.routing.WalkingRouteSearch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.VarbitComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AsyncRoutePlannerTest
{
	private static final String HEADER = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\tItems\tQuests\tVarbits\tVarPlayers\tDuration\n";
	private final ManualExecutor worker = new ManualExecutor();
	private final List<AsyncRoutePlanner> planners = new ArrayList<>();
	private final AtomicInteger liveReads = new AtomicInteger();
	private boolean clientThread = true;
	private int agility = 99;
	private int questStatus = 2;
	private int varbit = 1;
	private Item[] inventoryItems = new Item[0];
	private Item[] equipmentItems = new Item[0];
	private final ItemContainer inventory = proxy(ItemContainer.class, (method, args) ->
	{
		if ("getItems".equals(method)) return inventoryItems;
		throw new UnsupportedOperationException(method);
	});
	private final ItemContainer equipment = proxy(ItemContainer.class, (method, args) ->
	{
		if ("getItems".equals(method)) return equipmentItems;
		throw new UnsupportedOperationException(method);
	});
	private final VarbitComposition varbitDefinition = proxy(VarbitComposition.class, (method, args) -> 0);
	private final Client client = proxy(Client.class, (method, args) ->
	{
		assertFalse("Live Client state must never be read by a queued worker task", worker.running);
		liveReads.incrementAndGet();
		switch (method)
		{
			case "isClientThread": return clientThread;
			case "getRealSkillLevel":
			case "getBoostedSkillLevel": return args[0] == Skill.AGILITY ? agility : 99;
			case "getItemContainer": return args[0].equals(InventoryID.INV) ? inventory : equipment;
			case "getVarps": return new int[64];
			case "getVarbit": return varbitDefinition;
			case "getVarbitValue": return varbit;
			case "runScript": return null;
			case "getIntStack": return new int[]{questStatus};
			default: throw new UnsupportedOperationException(method);
		}
	});

	@After
	public void closePlanners()
	{
		for (AsyncRoutePlanner planner : planners) planner.close();
	}

	@Test
	public void preparationLoadsOnTheWorkerBeforeAnyClientSnapshotIsRequested() throws Exception
	{
		AsyncRoutePlanner planner = planner(HEADER);
		assertEquals(1, worker.tasks.size());
		assertEquals(Plan.Status.PREPARING, update(planner, point(12, 12), arrivals()).status);
		assertEquals(0, liveReads.get());

		worker.run(0);

		assertEquals(Plan.Status.SEARCHING, update(planner, point(12, 12), arrivals()).status);
		assertTrue(liveReads.get() > 0);
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void loadingFailureStaysUnavailableWithoutReadingTheClient()
	{
		AsyncRoutePlanner planner = new AsyncRoutePlanner(worker,
			() -> { throw new IOException("Synthetic load failure"); }, new WalkingRouteSearch());
		planners.add(planner);
		worker.run(0);

		assertEquals(Plan.Status.UNAVAILABLE, update(planner, point(12, 12), arrivals()).status);
		assertEquals(0, liveReads.get());
		assertEquals(1, worker.tasks.size());
	}

	@Test
	public void identicalRequestsReuseTheFinishedPlanWithoutSubmittingAnotherSearch() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		assertEquals(Plan.Status.SEARCHING, update(planner, point(12, 12), arrivals).status);
		int captured = liveReads.get();

		worker.run(1);

		assertEquals(captured, liveReads.get());
		Plan completed = update(planner, point(12, 12), arrivals);
		assertEquals(Plan.Status.FOUND, completed.status);
		assertEquals(2, worker.tasks.size());
		assertSame(completed, update(planner, point(12, 12), arrivals));
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void inputOrderingDoesNotInvalidateAnEquivalentSnapshot() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		List<WorldPoint> goals = List.of(point(14, 14), point(15, 15));
		planner.update(client, "profile", goals, arrivals, 0);
		worker.run(1);
		Plan result = planner.update(client, "profile", goals, arrivals, 0);

		assertSame(result, planner.update(client, "profile", List.of(point(15, 15), point(14, 14)),
			List.of(arrivals.get(1), arrivals.get(0)), 0));
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void theSubmittedRequestCopiesTargetsAndArrivalsBeforeTheCallerChangesThem() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> source = new ArrayList<>(arrivals());
		List<TeleportDestination> original = new ArrayList<>(source);
		List<WorldPoint> goals = new ArrayList<>(List.of(point(12, 12)));
		planner.update(client, "profile", goals, source, 0);
		source.clear();
		goals.clear();

		worker.run(1);

		Plan result = planner.update(client, "profile", List.of(point(12, 12)), original, 0);
		assertEquals(Plan.Status.FOUND, result.status);
		assertSame(original.get(0), result.destination);
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void aProfileSwitchCancelsAndRejectsAnOlderTaskEvenIfItCompletesLate() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		planner.update(client, "first-profile", List.of(point(12, 12)), arrivals, 0);
		ManualTask old = worker.tasks.get(1);
		planner.update(client, "second-profile", List.of(point(12, 12)), arrivals, 0);
		assertTrue(old.cancelled);
		worker.run(2);
		Plan latest = planner.update(client, "second-profile", List.of(point(12, 12)), arrivals, 0);
		assertEquals(Plan.Status.FOUND, latest.status);

		worker.runIgnoringCancellation(1);

		assertSame(latest, planner.update(client, "second-profile", List.of(point(12, 12)), arrivals, 0));
	}

	@Test
	public void changingTheClueCancelsAndRejectsAnOldCompletion() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		planner.update(client, "profile", List.of(point(12, 12)), arrivals, 0);
		planner.update(client, "profile", List.of(point(39, 39)), arrivals, 0);
		assertTrue(worker.tasks.get(1).cancelled);
		worker.run(2);
		Plan latest = planner.update(client, "profile", List.of(point(39, 39)), arrivals, 0);
		assertSame(arrivals.get(1), latest.destination);

		worker.runIgnoringCancellation(1);

		assertSame(latest, planner.update(client, "profile", List.of(point(39, 39)), arrivals, 0));
	}

	@Test
	public void removingAnArrivalInvalidatesTheWinner() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		update(planner, point(12, 12), arrivals);
		worker.run(1);
		assertSame(arrivals.get(0), update(planner, point(12, 12), arrivals).destination);

		assertEquals(Plan.Status.SEARCHING, update(planner, point(12, 12),
			List.of(arrivals.get(1))).status);
		worker.run(2);
		assertSame(arrivals.get(1), update(planner, point(12, 12), List.of(arrivals.get(1))).destination);
	}

	@Test
	public void interactionRadiusParticipatesInTheCacheKey() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = List.of(arrival("next-to", point(10, 10)));
		List<WorldPoint> targets = List.of(point(11, 10));
		planner.update(client, "profile", targets, arrivals, 0);
		worker.run(1);
		assertEquals(1, planner.update(client, "profile", targets, arrivals, 0).walkingTiles);

		assertEquals(Plan.Status.SEARCHING, planner.update(client, "profile", targets, arrivals, 1).status);
		worker.run(2);
		assertEquals(0, planner.update(client, "profile", targets, arrivals, 1).walkingTiles);
	}

	@Test
	public void crossingAnAgilityThresholdInvalidatesTheCacheButQueuedWorkUsesItsCapturedSnapshot() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("70 Agility", "", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		agility = 69;
		update(planner, point(50, 50), arrivals);
		int captured = liveReads.get();
		agility = 99;
		worker.run(1);
		assertEquals(captured, liveReads.get());
		agility = 69;
		assertSame(arrivals.get(1), update(planner, point(50, 50), arrivals).destination);

		agility = 99;
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		assertSame(arrivals.get(0), update(planner, point(50, 50), arrivals).destination);
	}

	@Test
	public void accountChangesOnlyInvalidateWhenTheyChangeTheEligiblePassages() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("", "995=10", "Tree Gnome Village", "9=1"));
		List<TeleportDestination> arrivals = shortcutArrivals();
		inventoryItems = new Item[]{new Item(995, 10)};
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		assertSame(arrivals.get(0), update(planner, point(50, 50), arrivals).destination);

		inventoryItems = new Item[0];
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		Plan blocked = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(1), blocked.destination);

		// Different unmet requirements still leave the same passage unavailable.
		inventoryItems = new Item[]{new Item(995, 10)};
		questStatus = 1;
		assertSame(blocked, update(planner, point(50, 50), arrivals));
		questStatus = 2;
		varbit = 0;
		assertSame(blocked, update(planner, point(50, 50), arrivals));
		assertEquals(3, worker.tasks.size());

		varbit = 1;
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(3);
		assertSame(arrivals.get(0), update(planner, point(50, 50), arrivals).destination);
		assertEquals(4, worker.tasks.size());
	}

	@Test
	public void depositingAndWithdrawingUnrelatedItemsReuseTheFinishedPlan() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("", "ROPE=1", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		inventoryItems = new Item[]{new Item(954, 1), new Item(995, 500)};
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		Plan completed = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(0), completed.destination);

		inventoryItems = new Item[]{new Item(954, 1)};
		assertSame(completed, update(planner, point(50, 50), arrivals));
		inventoryItems = new Item[]{new Item(954, 1), new Item(1511, 20), new Item(995, 1000)};
		assertSame(completed, update(planner, point(50, 50), arrivals));
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void itemQuantityChangesOnlyRerouteWhenTheyCrossTheRequiredThreshold() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("", "COINS=10", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		inventoryItems = new Item[]{new Item(995, 10)};
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		Plan available = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(0), available.destination);

		inventoryItems = new Item[]{new Item(995, 100)};
		assertSame(available, update(planner, point(50, 50), arrivals));
		assertEquals(2, worker.tasks.size());

		inventoryItems = new Item[]{new Item(995, 9)};
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		Plan blocked = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(1), blocked.destination);
		inventoryItems = new Item[]{new Item(995, 1)};
		assertSame(blocked, update(planner, point(50, 50), arrivals));
		assertEquals(3, worker.tasks.size());
	}

	@Test
	public void equivalentItemAlternativesReuseTheSameEligibleGraph() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("", "SHANTAY_PASS=1|COINS=5", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		inventoryItems = new Item[]{new Item(1854, 1)};
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		Plan completed = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(0), completed.destination);

		inventoryItems = new Item[]{new Item(995, 5)};
		assertSame(completed, update(planner, point(50, 50), arrivals));
		assertEquals(2, worker.tasks.size());
		inventoryItems = new Item[0];
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		assertSame(arrivals.get(1), update(planner, point(50, 50), arrivals).destination);
	}

	@Test
	public void agilityBoostChangesOnTheSameSideOfTheThresholdReuseThePlan() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("70 Agility", "", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		agility = 70;
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		Plan available = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(0), available.destination);

		agility = 99;
		assertSame(available, update(planner, point(50, 50), arrivals));
		assertEquals(2, worker.tasks.size());
		agility = 69;
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		Plan blocked = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(1), blocked.destination);
		agility = 68;
		assertSame(blocked, update(planner, point(50, 50), arrivals));
		assertEquals(3, worker.tasks.size());
	}

	@Test
	public void equippingTheCrossbowChangesReadinessButAnEquivalentEquippedCrossbowDoesNot() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("", "CROSSBOW=1&MITH_GRAPPLE=1", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		inventoryItems = new Item[]{new Item(837, 1), new Item(9419, 1)};
		update(planner, point(50, 50), arrivals);
		worker.run(1);
		assertSame(arrivals.get(1), update(planner, point(50, 50), arrivals).destination);

		inventoryItems = new Item[]{new Item(9419, 1)};
		equipmentItems = new Item[]{new Item(837, 1)};
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(2);
		Plan ready = update(planner, point(50, 50), arrivals);
		assertSame(arrivals.get(0), ready.destination);
		equipmentItems = new Item[]{new Item(767, 1)};
		assertSame(ready, update(planner, point(50, 50), arrivals));
		assertEquals(3, worker.tasks.size());

		equipmentItems = new Item[0];
		inventoryItems = new Item[]{new Item(767, 1), new Item(9419, 1)};
		assertEquals(Plan.Status.SEARCHING, update(planner, point(50, 50), arrivals).status);
		worker.run(3);
		assertSame(arrivals.get(1), update(planner, point(50, 50), arrivals).destination);
	}

	@Test
	public void irrelevantAccountChangesDoNotCancelThePendingSearch() throws Exception
	{
		AsyncRoutePlanner planner = ready(table("70 Agility", "COINS=10", "", ""));
		List<TeleportDestination> arrivals = shortcutArrivals();
		agility = 70;
		inventoryItems = new Item[]{new Item(995, 10)};
		Plan searching = update(planner, point(50, 50), arrivals);
		assertEquals(Plan.Status.SEARCHING, searching.status);
		ManualTask pending = worker.tasks.get(1);

		agility = 75;
		inventoryItems = new Item[]{new Item(995, 100), new Item(1511, 20)};
		assertSame(searching, update(planner, point(50, 50), arrivals));
		assertFalse(pending.cancelled);
		assertEquals(2, worker.tasks.size());
		int captured = liveReads.get();
		worker.run(1);
		assertEquals(captured, liveReads.get());
		assertSame(arrivals.get(0), update(planner, point(50, 50), arrivals).destination);
		assertEquals(2, worker.tasks.size());
	}

	@Test
	public void explicitInvalidationCancelsPendingWorkAndForcesAFreshSearch() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		List<TeleportDestination> arrivals = arrivals();
		update(planner, point(12, 12), arrivals);
		ManualTask old = worker.tasks.get(1);

		planner.invalidate();

		assertTrue(old.cancelled);
		assertEquals(Plan.Status.SEARCHING, update(planner, point(12, 12), arrivals).status);
		assertEquals(3, worker.tasks.size());
		worker.run(2);
		Plan latest = update(planner, point(12, 12), arrivals);
		worker.runIgnoringCancellation(1);
		assertSame(latest, update(planner, point(12, 12), arrivals));
	}

	@Test
	public void closeCancelsLoadingAndPendingWorkAndShutsDownTheOwnedExecutor() throws Exception
	{
		AsyncRoutePlanner planner = planner(HEADER);
		planner.close();

		assertTrue(worker.tasks.get(0).cancelled);
		assertTrue(worker.shutdown);
		assertEquals(Plan.Status.UNAVAILABLE, update(planner, point(12, 12), arrivals()).status);
		assertEquals(0, liveReads.get());
		worker.runIgnoringCancellation(0);
		assertEquals(Plan.Status.UNAVAILABLE, update(planner, point(12, 12), arrivals()).status);
	}

	@Test
	public void closeAfterSubmissionRejectsAnyLateResult() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		update(planner, point(12, 12), arrivals());
		planner.close();
		assertTrue(worker.tasks.get(1).cancelled);

		worker.runIgnoringCancellation(1);

		assertEquals(Plan.Status.UNAVAILABLE, update(planner, point(12, 12), arrivals()).status);
		assertTrue(worker.isTerminated());
	}

	@Test(expected = IllegalStateException.class)
	public void liveSnapshotCaptureRefusesAnOffClientThreadCaller() throws Exception
	{
		AsyncRoutePlanner planner = ready(HEADER);
		clientThread = false;
		update(planner, point(12, 12), arrivals());
	}

	private AsyncRoutePlanner planner(String table) throws IOException
	{
		RoutingData data = data(table);
		AsyncRoutePlanner planner = new AsyncRoutePlanner(worker, () -> data, new WalkingRouteSearch(100_000, 5_000));
		planners.add(planner);
		return planner;
	}

	private AsyncRoutePlanner ready(String table) throws IOException
	{
		AsyncRoutePlanner planner = planner(table);
		worker.run(0);
		return planner;
	}

	private Plan update(AsyncRoutePlanner planner, WorldPoint target, List<TeleportDestination> arrivals)
	{
		return planner.update(client, "profile", List.of(target), arrivals, 0);
	}

	private static List<TeleportDestination> arrivals()
	{
		return List.of(arrival("first", point(10, 10)), arrival("second", point(40, 40)));
	}

	private static List<TeleportDestination> shortcutArrivals()
	{
		return List.of(arrival("shortcut", point(4, 4)), arrival("ordinary", point(54, 50)));
	}

	private static TeleportDestination arrival(String id, WorldPoint point)
	{
		return new TeleportDestination(id, TeleportSource.FAIRY_RING, id, point);
	}

	private static WorldPoint point(int x, int y) { return new WorldPoint(x, y, 0); }

	private static String table(String skills, String items, String quests, String bits)
	{
		return HEADER + "4 4 0\t50 50 0\tCross shortcut\t" + skills + "\t" + items + "\t" + quests
			+ "\t" + bits + "\t\t1\n";
	}

	private static RoutingData data(String table) throws IOException
	{
		byte[] flags = new byte[2048];
		Arrays.fill(flags, (byte) 0x33);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes))
		{
			zip.putNextEntry(new ZipEntry("0_0"));
			zip.write(flags);
			zip.closeEntry();
		}
		return RoutingData.load(new ByteArrayInputStream(bytes.toByteArray()), stream(table), stream(HEADER));
	}

	private static ByteArrayInputStream stream(String value)
	{
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
	}

	private interface Handler { Object invoke(String method, Object[] args); }

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Handler handler)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
			(instance, method, args) -> handler.invoke(method.getName(), args));
	}

	/** No threads are created. Tasks can deliberately finish after cancellation. */
	private static final class ManualExecutor extends AbstractExecutorService
	{
		private final List<ManualTask> tasks = new ArrayList<>();
		private boolean running;
		private boolean shutdown;

		@Override public Future<?> submit(Runnable runnable)
		{
			if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
			ManualTask task = new ManualTask(this, runnable);
			tasks.add(task);
			return task;
		}

		@Override public void execute(Runnable runnable) { submit(runnable); }
		private void run(int index) { tasks.get(index).run(false); }
		private void runIgnoringCancellation(int index) { tasks.get(index).run(true); }
		@Override public void shutdown() { shutdown = true; }
		@Override public List<Runnable> shutdownNow()
		{
			shutdown = true;
			for (ManualTask task : tasks) task.cancel(true);
			return Collections.emptyList();
		}
		@Override public boolean isShutdown() { return shutdown; }
		@Override public boolean isTerminated()
		{
			return shutdown && tasks.stream().allMatch(task -> task.done || task.cancelled);
		}
		@Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
	}

	private static final class ManualTask implements Future<Object>
	{
		private final ManualExecutor owner;
		private final Runnable runnable;
		private boolean cancelled;
		private boolean done;
		private Throwable failure;

		private ManualTask(ManualExecutor owner, Runnable runnable)
		{
			this.owner = owner;
			this.runnable = runnable;
		}

		private void run(boolean ignoreCancellation)
		{
			if (done || cancelled && !ignoreCancellation) return;
			owner.running = true;
			try
			{
				runnable.run();
			}
			catch (RuntimeException | Error error)
			{
				failure = error;
				throw error;
			}
			finally
			{
				owner.running = false;
				done = true;
			}
		}

		@Override public boolean cancel(boolean mayInterruptIfRunning)
		{
			if (done) return false;
			cancelled = true;
			return true;
		}
		@Override public boolean isCancelled() { return cancelled; }
		@Override public boolean isDone() { return done || cancelled; }
		@Override public Object get() throws ExecutionException
		{
			if (cancelled) throw new CancellationException();
			if (failure != null) throw new ExecutionException(failure);
			if (!done) throw new IllegalStateException("Run the queued task before reading it");
			return null;
		}
		@Override public Object get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException { return get(); }
	}
}
