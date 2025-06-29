/*
 *  ========================================================================
 *  DIScrete event baSed Energy Consumption simulaTor 
 *    					             for Clouds and Federations (DISSECT-CF)
 *  ========================================================================
 *  
 *  This file is part of DISSECT-CF.
 *  
 *  DISSECT-CF is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or (at
 *  your option) any later version.
 *  
 *  DISSECT-CF is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 *  General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with DISSECT-CF.  If not, see <http://www.gnu.org/licenses/>.
 *  
 *  (C) Copyright 2014, Gabor Kecskemeti (gkecskem@dps.uibk.ac.at,
 *   									  kecskemeti.gabor@sztaki.mta.hu)
 */

package hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.statenotifications.PowerStateChangeNotificationHandler;
import hu.mta.sztaki.lpds.cloud.simulator.notifications.StateDependentEventHandler;
import hu.mta.sztaki.lpds.cloud.simulator.util.ArrayHandler;

/**
 * This class is part of the unified resource consumption model of DISSECT-CF.
 * 
 * This class provides a foundation for ensuring equal access to resource
 * limited devices (such as network interfaces, cpus or disks) for all ongoing
 * resource consumptions.
 * 
 * Resource processing is actually handled by the processSingleConsumption
 * function which must be implemented externally for performance. This allows
 * the resource consumption simulation code to run with less conditional
 * statements in its core. While it also allows to efficiently add new features
 * later on.
 * 
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 *         of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 *         Distributed Systems, MTA SZTAKI (c) 2012"
 * 
 */
public abstract class ResourceSpreader {

	// These final variables define the base behavior of the class:
	/**
	 * Maximum amount of resources to be shared among the consumption objects during
	 * a single tick.
	 */
	protected double perTickProcessingPower;
	/**
	 * For floating point operations using the perTickProcessingPower this defines
	 * the precision, i.e. the amount of processing to be considered 0. It is defined
	 * to be 1 billionth of the perTickProcessingPower.
	 */
	protected double negligibleProcessing;
	/**
	 * The array of consumption objects that will share the processing power of this
	 * spreader. The order is not guaranteed!
	 */
	final ArrayList<ResourceConsumption> toProcess = new ArrayList<>();
	/**
	 * The unalterable array of resource consumption objects.
	 */
	public final List<ResourceConsumption> underProcessing = Collections.unmodifiableList(toProcess);
	/**
	 * The influence group in which this resource spreader belongs.
	 */
	private FreqSyncer mySyncer = null;
	/**
	 * The resource consumptions that got registered to this spreader in the last
	 * tick
	 */
	final ArrayList<ResourceConsumption> underAddition = new ArrayList<>();
	/**
	 * The resource consumptions that got deregistered from this spreader in the
	 * last tick
	 */
	final ArrayList<ResourceConsumption> underRemoval = new ArrayList<>();
	/**
	 * Public, unmodifiable list of just registered resource consumptions
	 */
	public final List<ResourceConsumption> toBeRemoved = Collections.unmodifiableList(underRemoval);
	/**
	 * Public, unmodifiable list of just deregistered resource consumptions
	 */
	public final List<ResourceConsumption> toBeAdded = Collections.unmodifiableList(underAddition);

	/**
	 * The power behavior that currently models the resource spreader's energy
	 * characteristics.
	 */
	private PowerState currentPowerBehavior;
	/**
	 * This is the notification handler object that belongs to this particular
	 * resource spreader. Resource spreaders use this object to handle power state
	 * related events.
	 */
	private StateDependentEventHandler<PowerBehaviorChangeListener, Pair<ResourceSpreader, PowerState>> powerBehaviorListenerManager;

	/**
	 * The last time there were some processing operations done by this object. The
	 * notification time is used to stop infinite loops in the doProcessing
	 * function.
	 */
	protected long lastNotifTime = 0;
	/**
	 * Shows how much processing this spreader done in its lifetime.
	 */
	private double totalProcessed = 0;
	/**
	 * A helper field that allows the rapid discovery of influence groups by the
	 * group's freq syncer object
	 */
	boolean stillInDepGroup;

	/**
	 * This constructor just saves the processing power that can be spread in every
	 * tick by the newly instantiated spreader.
	 * 
	 * @param initialProcessingPower Maximum usable bandwidth in a during a single
	 *                               timing event
	 */
	public ResourceSpreader(final double initialProcessingPower) {
		setPerTickProcessingPower(initialProcessingPower);
	}

	/**
	 * Determines the influence group this resource spreader is participating in. If
	 * null, then this spreader is not having any resource consumptions registered.
	 * 
	 * @return the object representing this spreader's influence group.
	 */
	public final FreqSyncer getSyncer() {
		return mySyncer;
	}

	/**
	 * The entry to the lowest level schedulers in DISSECT-CF. This function is
	 * expected to calculate for each resource consumption of this spreader the
	 * amount of resource share it could receive.
	 * 
	 * @return the duration (in ticks) one has to wait before any of the resource
	 *         consumptions in this spreader complete
	 */
	protected abstract long singleGroupwiseFreqUpdater();

	/**
	 * Allows the management of the underRemoval list. When some objects are removed
	 * the influence groups are reevaluated.
	 * 
	 * @param conList the resource consumptions that must be dropped (either because
	 *                they complete or because they are cancelled)
	 */
	protected final void removeTheseConsumptions(final Stream<ResourceConsumption> conList) {
		var removalCount = conList.filter(rem -> {
			if (!underRemoval.contains(rem)) {
				underRemoval.add(rem);
			}
			ArrayHandler.removeAndReplaceWithLast(underAddition, rem);
			return true;
		}).count();
		if (removalCount > 0 && mySyncer != null) {
			mySyncer.nudge();
		}
	}

	/**
	 * When a new consumption is initiated it must be registered to the
	 * corresponding spreader with this function.
	 * 
	 * The consumption object is added to the array of current consumptions. This
	 * function also makes sure that the timing events arrive if this is the first
	 * object in the array.
	 * 
	 * WARNING: This function should not be called by anyone else but the
	 * registration function of the resource consumption! (Otherwise duplicate
	 * registrations could happen!)
	 * 
	 * @param con The consumption object to be registered
	 * @return
	 *         <ul>
	 *         <li><i>true</i> if the registration was successful
	 *         <li><i>false</i> if the consumption was already registered or if the
	 *         consumption is not acceptable by its set provider or consumer.
	 *         </ul>
	 */
	static boolean registerConsumption(final ResourceConsumption con) {
		var provider = con.getProvider();
		var consumer = con.getConsumer();
		if (con.isRegistered() || !(provider.isAcceptableConsumption(con) && consumer.isAcceptableConsumption(con))) {
			return false;
		}
		// ResourceConsumption synchronization
		Stream.of(provider,consumer).forEach(rs -> {
			ArrayHandler.removeAndReplaceWithLast(rs.underRemoval, con);
			rs.underAddition.add(con);
		});
		nudgeSyncers(provider,consumer);
		return true;
	}

	private static void nudgeSyncers(ResourceSpreader provider, ResourceSpreader consumer) {
		var nudgedCount = Stream.of(provider, consumer).filter(rs -> rs.mySyncer != null).mapToInt(rs -> {
			rs.mySyncer.nudge();
			return 1;
		}).sum();
		if (nudgedCount == 0) {
			// We just form our new influence group
			new FreqSyncer(provider, consumer).nudge();
		}
	}

	/**
	 * Allows the rejection of the registration of some resource consumptions. By default,
	 * this function only checks whether the registration is actually happening for the
	 * resource spreader that the consumption refers to. Also, the default function stops
	 * registrations if the processing power of this spreader is 0.
	 * 
	 * <i>NOTE:</i> by further implementing this function one can block
	 * registrations in even more cases (e.g. no registration of CPU utilization is
	 * possible for DESTROYED virtual machines).
	 * 
	 * <i>WARNING:</i> when overriding this method, always combine the original's
	 * decision
	 * 
	 * @param con the consumption that is asked to be registered
	 * @return <i>true</i> if the consumption can be registered in this spreader's
	 *         underprocessing list
	 */
	protected boolean isAcceptableConsumption(final ResourceConsumption con) {
		return getSamePart(con).equals(this) && perTickProcessingPower > 0 && con.getHardLimit() > 0;
	}

	/**
	 * Organizes the coordinated removal of this consumption from the
	 * underProcessing list.
	 * 
	 * @param con the consumption to be removed from its processing
	 *            consumer/provider pair.
	 */
	static void cancelConsumption(final ResourceConsumption con) {
		Stream.of(con.getProvider(), con.getConsumer()).forEach(rs -> rs.removeTheseConsumptions(Stream.of(con)));
	}

	/**
	 * The main resource processing loop. This loop is responsible for actually
	 * offering resources to consumers in case of providers or in case of consumers
	 * it is responsible to actually utilize the offered resources. The loop does
	 * not make decisions on how many resources to use/offer for a particular
	 * resource consumption, it is assumed that by the time this function is called
	 * all resource consumption objects are allocated their share of the complete
	 * available resource set.
	 * 
	 * The loop also uses the lastNotiftime field to determine how much time passed
	 * since it last shared the resources to all its consumption objects.
	 * 
	 * @param currentFireCount the time at which this processing task must take
	 *                         place.
	 */
	void doProcessing(final long currentFireCount) {
		if (currentFireCount == lastNotifTime && mySyncer.isRegularFreqMode()) {
			return;
		}
		var ticksPassed = currentFireCount - lastNotifTime;
		removeTheseConsumptions(toProcess.stream().filter(con -> {
					final double processed = processSingleConsumption(con, ticksPassed);
					totalProcessed += Math.abs(processed);
					return processed < 0;
				}
		));
		lastNotifTime = currentFireCount;
	}

	/**
	 * This function is expected to realign the underProcessing and toBeProcessed
	 * fields of the ResourceConsumption object it receives. It is expected to do
	 * the realignment depending on whether this resource spreader is a consumer or
	 * a provider.
	 * 
	 * All subclasses that embody consumer/provider roles must implement this method
	 * so the doProcessing function above could uniformly process all resource
	 * consumptions independently of being a provider/consumer.
	 * 
	 * @param con         the resource consumption to be realigned
	 * @param ticksPassed the time passed since the last processing request
	 * @return Its absolute value represents the amount of actually processed
	 *         resources. If negative then the 'con' has completed its processing.
	 */
	protected abstract double processSingleConsumption(final ResourceConsumption con, final long ticksPassed);

	/**
	 * If it is unknown whether we are a provider or a consumer (this is usually the
	 * case in the generic resource spreader class or anyone outside the actual
	 * provider/consumer implementations) then it is useful to figure out the
	 * counterpart who also participates in the same resource consumption
	 * processing operation.
	 * 
	 * @param con The consumption object for which we would like to know the other
	 *            party that participates in the processing with us.
	 * @return the other resource spreader that does the processing with us
	 */
	protected abstract ResourceSpreader getCounterPart(final ResourceConsumption con);

	/**
	 * The function gets that particular resource spreader from the given resource
	 * consumption object that is the same kind (i.e., provider/consumer) as the
	 * resource spreader that calls for this function.
	 * 
	 * This function is useful to collect the same kind of resource spreaders
	 * identified through a set of resource consumption objects. Or just to know if
	 * a particular resource spreader is the one that is set in the expected role in
	 * a resource consumption object (e.g., am I really registered as a consumer as
	 * I expected?).
	 * 
	 * @param con The consumption object on which this call will operate
	 * 
	 * @return the resource spreader set to be in the same role (provider/consumer)
	 *         as this resource spreader object (i.e., the spreader on which the
	 *         function was calleD)
	 */
	protected abstract ResourceSpreader getSamePart(final ResourceConsumption con);

	/**
	 * Determines if a particular resource spreader is acting as a consumer or not.
	 * 
	 * @return <i>true</i> if this resource spreader is a consumer, <i>false</i>
	 *         otherwise
	 */
	protected abstract boolean isConsumer();

	protected abstract void manageRemoval(final ResourceConsumption con);

	/**
	 * Returns the total amount of resources processed (i.e., via all past and
	 * present resource consumption objects) by this resource spreader object at the
	 * time instance this call is made.
	 * 
	 * <i>WARNING:</i> this operation could be rather expensive to call as it first
	 * has to ensure all consumption processing is done before the query to the
	 * totalProcessed field can be actually accomplished.
	 * 
	 * @return the amount of processing done so far. The unit of the processed value
	 *         is application specific here it is not relevant. For example if this
	 *         function is used on a PhysicalMachine then it will report the amount
	 *         of instructions executed so far by the PM.
	 */
	public double getTotalProcessed() {
		if (mySyncer != null) {
			var currTime = Timed.getFireCount();
			if (isConsumer()) {
				// We first have to make sure the providers provide the
				// stuff that this consumer might need
				mySyncer.getCompleteDGStream().forEach(c -> c.doProcessing(currTime));
			}
			doProcessing(currTime);
		}
		return totalProcessed;
	}

	/**
	 * Determines the current processing power of this resource spreader
	 * 
	 * @return the processing power of this resource spreader. The returned value
	 *         has no unit, the unit depends on the user of the spreader (e.g. in
	 *         networking it could be bytes/tick)
	 */
	public double getPerTickProcessingPower() {
		return perTickProcessingPower;
	}

	/**
	 * Allows to set the current processing power of this resource spreader
	 * 
	 * <i>WARNING:</i> this is not intended to be used for altering the performance
	 * of the spreader while it participates in resource consumption processing
	 * 
	 * @param perTickProcessingPower the new processing power of this resource
	 *                               spreader. The new value has no unit, the unit
	 *                               depends on the user of the spreader (e.g. in
	 *                               networking it could be bytes/tick)
	 */
	protected void setPerTickProcessingPower(double perTickProcessingPower) {
		// if (isSubscribed()) {
		// // TODO: this case might be interesting to support.
		// throw new IllegalStateException(
		// "It is not possible to change the processing power of a spreader
		// while it is subscribed!");
		// }
		this.perTickProcessingPower = perTickProcessingPower;
		this.negligibleProcessing = this.perTickProcessingPower / 1000000000;
	}

	/**
	 * Queries the current power behavior object
	 * 
	 * @return the current power behavior
	 */
	public PowerState getCurrentPowerBehavior() {
		return currentPowerBehavior;
	}

	/**
	 * Allows to change the power behavior of the resource spreader. Once the change
	 * is done it notifies the listeners interested in power behavior changes.
	 * 
	 * @param newPowerBehavior the new power behavior to be set. Null values are not
	 *                         allowed!
	 */
	public void setCurrentPowerBehavior(final PowerState newPowerBehavior) {
		// FIXME: this might be protected later on.
		if (newPowerBehavior == null) {
			throw new IllegalStateException("Trying to set an unknown power behavior");
		}
		if (currentPowerBehavior == null) {
			powerBehaviorListenerManager = PowerStateChangeNotificationHandler.getHandlerInstance();
		}
		if (currentPowerBehavior != newPowerBehavior) {
			currentPowerBehavior = newPowerBehavior;
			powerBehaviorListenerManager.notifyListeners(Pair.of(this, newPowerBehavior));
		}
	}

	/**
	 * allows interested parties to receive power behavior change events by
	 * subscribing with a listener object.
	 * 
	 * <i>Note:</i> Internally the state dependent event handling framework is used
	 * for this operation.
	 * 
	 * @param pbcl the new listener object
	 */
	public void subscribePowerBehaviorChangeEvents(final PowerBehaviorChangeListener pbcl) {
		powerBehaviorListenerManager.subscribeToEvents(pbcl);
	}

	/**
	 * allows parties that got uninterested to cancel the reception of new power
	 * behavior change events by unsubscribing with a listener object.
	 * 
	 * <i>Note:</i> Internally the state dependent event handling framework is used
	 * for this operation.
	 * 
	 * @param pbcl the old listener object
	 */
	public void unsubscribePowerBehaviorChangeEvents(final PowerBehaviorChangeListener pbcl) {
		powerBehaviorListenerManager.unsubscribeFromEvents(pbcl);
	}

	/**
	 * Provides a nice formatted single line representation of the spreader. It
	 * lists the currently processed resource consumptions and the power behavior as
	 * well. Useful for debugging and tracing purposes.
	 */
	@Override
	public String toString() {
		return "RS(processing: " + toProcess + " in power state: "
				+ (currentPowerBehavior == null ? "-" : currentPowerBehavior.toString()) + ")";
	}

	/**
	 * A continuously increasing simple hash value to be used by the next resource
	 * spreader object created
	 */
	static int hashCounter = 0;
	/**
	 * The hashcode of the actual resource spreader to be used in java's built-in
	 * hashCode function
	 */
	private final int myHashCode = getHashandIncCounter();

	/**
	 * Manages the increment of the hashCounter and offers the latest hash code for
	 * new objects
	 * 
	 * <i>WARNING:</i> as this function does not check if a hash value is already
	 * given or not there might be hash collisions if there are so many resource
	 * spreaders created that the hashcounter overflows.
	 * 
	 * @return the hash code to be used by the newest object
	 */
	static int getHashandIncCounter() {
		// FIXME
		// WARNING: some possible hash collisions!
		return hashCounter++;
	}

	/**
	 * Returns the constant hashcode that was generated for this object during its
	 * instantiation.
	 */
	@Override
	public final int hashCode() {
		return myHashCode;
	}

	void setSyncer(FreqSyncer syncer) {
		mySyncer=syncer;
	}

	/**
	 * Making sure we send out the necessary notifications on removing the consumptions from the spreader's
	 * responsibility
	 * @return if there were any removals actually done
	 */
	boolean handleRemovals() {
		// managing removals
		underRemoval.forEach(con -> {
			ArrayHandler.removeAndReplaceWithLast(toProcess, con);
			manageRemoval(con);
		});
		var didRemovals=underRemoval.size()!=0;
		underRemoval.clear();
		return didRemovals;
	}

	boolean handleAdditions(long fires) {
		if (toProcess.size() == 0) {
			lastNotifTime = fires;
		}
		var added = underAddition.stream().filter(con -> getSyncer().ensureDepGroupHasCounterPart(getCounterPart(con))).count();
		toProcess.addAll(underAddition);
		underAddition.clear();
		return added != 0;
	}

	public boolean isProcessing() {
		return !toProcess.isEmpty();
	}

	public boolean cleanSyncerWhenNotProcessing() {
		if(toProcess.isEmpty()) {
			setSyncer(null);
			return true;
		} else {
			return false;
		}
	}

	public abstract FreqSyncer.DepKind spreaderType();
}
