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

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.function.Predicate.not;

/**
 * This class is the core part of the unified resource consumption model of
 * DISSECT-CF.
 * <p>
 * The main purpose of this class is to create and manage influence groups from
 * resource spreaders that are connected with resource consumptions. Also, the
 * class is also expected to coordinate the processing of the resource
 * consumptions within the entire influence group. Finally, the class is
 * responsible to deliver the completion or failure events for the resource
 * consumptions deregistered from the influence group's resource spreaders.
 * <p>
 * The name FreqSyncer comes from the class's primary goal, identify the
 * earliest time there is a change in the scheduling (e.g., because a new
 * resource consumption is added to the group or because one of the consumptions
 * complete), and then make sure that all spreaders in the influence group
 * receive timing events at the same time instance.
 *
 * @author "Gabor Kecskemeti, Distributed and Parallel Systems Group, University
 * of Innsbruck (c) 2013" "Gabor Kecskemeti, Laboratory of Parallel and
 * Distributed Systems, MTA SZTAKI (c) 2015"
 */
public class FreqSyncer extends Timed {
    public enum DepKind {
        CONSUMER, PROVIDER
    }

    /**
     * The influence group managed by this freqsyncer object.
     * <p>
     * myDepGroup is always kept in order: first all the providers are listed, then
     * all the consumers, when dealing with this data member please keep in mind
     * this expected behavior. At the end of the array we have a padding with null
     * items.
     * <p>
     * Contents:
     * <ul>
     * <li>[0,firstConsumerId[ providers
     * <li>[firstConsumerId,depgrouplen[ consumers
     * <li>[depgrouplen,myDepGroup.length[ padding with null items
     * </ul>
     */
    final EnumMap<DepKind, Set<ResourceSpreader>> myDepGroup;
    /**
     * those resource spreaders that need to be added to the influence group at the
     * particular time instance
     */
    private final HashSet<ResourceSpreader> depGroupExtension = new HashSet<>();
    /**
     * if there are some external activities that could lead to influence group
     * changes this field will be turned to true
     * <p>
     * the tick function then ensures it to return to false once it has completed
     * its management operations on the influence groups
     */
    private boolean nudged = false;
    /**
     * if the freqsyncer identifies the need to fire events with 0 frequency then it
     * turns this mode off. This allows ResourceSpreader.doProcessing to happen
     * multiple times in a single time instance.
     */
    private boolean regularFreqMode = true;

    /**
     * Constructor of a freqsyncer to be used when neither the provider nor the
     * consumer of a resource consumption belongs to an already existing influence
     * group.
     *
     * @param provider the provider to be added to the initial influence group
     * @param consumer the consumer to be added to the initial influence group
     */
    FreqSyncer(final ResourceSpreader provider, final ResourceSpreader consumer) {
        myDepGroup=new EnumMap<>(DepKind.class);
        initDGMap(myDepGroup);
        myDepGroup.get(DepKind.PROVIDER).add(provider);
        myDepGroup.get(DepKind.CONSUMER).add(consumer);
        provider.setSyncer(this);
        consumer.setSyncer(this);
        setBackPreference(true);
    }

    private static void initDGMap(EnumMap<DepKind, Set<ResourceSpreader>> toInit) {
        toInit.put(DepKind.PROVIDER, new HashSet<>());
        toInit.put(DepKind.CONSUMER, new HashSet<>());
    }

    Stream<ResourceSpreader> getProviderStream() {
        return getDepGroupStream(DepKind.PROVIDER);
    }

    /**
     * @return Returns a new stream of all the members of the influence group, it is guaranteed to offer all providers first in the sequential stream
     */
    Stream<ResourceSpreader> getCompleteDGStream() {
        return Stream.concat(getProviderStream(),getDepGroupStream(DepKind.CONSUMER));
    }

    Stream<ResourceSpreader> getDepGroupStream(DepKind streamKind) {
        return myDepGroup.get(streamKind).stream();
    }

    /**
     * The constructor to be used when a new influence group needs to be created
     * because the original group got fragmented.
     *
     * @param predefinedDepGroup the group members to take part in the new influence group
     */
    private FreqSyncer(EnumMap<DepKind,Set<ResourceSpreader>> predefinedDepGroup) {
        myDepGroup=predefinedDepGroup;
        getCompleteDGStream().forEach(rs -> rs.setSyncer(this));
        setBackPreference(true);
    }

    /**
     * This function copies the contents of the depGroupExtension list to the array
     * representing the influence group and ensures that all newly added members of
     * the influence group know their group membership.
     */
    private void addToGroup() {
        depGroupExtension.forEach(rs -> {
            myDepGroup.get(rs.spreaderType()).add(rs);
            rs.setSyncer(this);
        });
    }

    /**
     * Determines if the spreader in question is part of the current influence group
     * or not
     *
     * @param lookfor the spreader in question
     * @return <i>true</i> if the group is part of the current influence group
     */
    private boolean isInDepGroup(final ResourceSpreader lookfor) {
        return getDepGroupStream(lookfor.spreaderType()).anyMatch( d -> d==lookfor);
    }

    /**
     * To be used to initiate out of order frequency updates. This is useful when
     * one or more resource spreaders in the influence group have gone through
     * frequency altering changes (i.e., dropping or receiving a new resource
     * consumption) and the rest of the group needs to be rescheduled.
     */
    void nudge() {
        if (!nudged) updateFrequency(0);
        nudged = true;
    }

    /**
     * provides a textual overview of the influence group with all its members.
     * useful for debugging and tracing.
     */
    @Override
    public String toString() {
        return "FreqSyncer(" + super.toString() + " depGroup: " + myDepGroup + ")";
    }

    /**
     * Goes through the entire influence group and for each member it initiates its
     * doProcessing function.
     * <p>
     * This is the actual function that does influence group wise resource
     * consumption processing.
     *
     * @param currentTime the time instance for which the processing should be done
     */
    protected final void outOfOrderProcessing(final long currentTime) {
        getCompleteDGStream().forEach(rs -> rs.doProcessing(currentTime));
    }

    /**
     * Implementation of Algorithm 1 from "DISSECT-CF: a simulator to foster
     * energy-aware scheduling in infrastructure clouds"
     * <p>
     * Manages the influence group's growth and decomposition.
     * <p>
     * Sends out the notifications for completed or failed resource consumptions.
     * <p>
     * This is executed with the frequency identified by the low level scheduler.
     * The execution of this tick function is run at the very end of the event loop
     * in Timed (with the help of backpreference that is set up in the constructors
     * of this class). The backpreference ensures that frequency setup is only done
     * after all other events are handled by Timed (i.e., it is not expected that
     * new resource consumption objects and similar will occur because of other
     * activities). This actually ensures that the nudging of the freqsyncer will
     * not cause too frequent repetitions of this heavyweight algorithm.
     */
    @Override
    public void tick(final long fires) {
        if (identifyGroupMembers(fires)) {
            groupSeparation();
        } else {
            // No separation was needed we just update our freq
            updateMyFreqNow();
        }
    }

    /**
     * Phase I. Identifying new influence group members, sending out consumption notification events
     *
     * @param fires time at which we are working on this
     * @return if we need to do the group separation phase
     */
    private boolean identifyGroupMembers(long fires) {
        var didRemovals = false;
        boolean didExtension;
        do {
            addToGroup();
            outOfOrderProcessing(fires);
            depGroupExtension.clear();
            nudged = false;
            var res = getCompleteDGStream()
                    .map(rs -> Pair.of(rs.handleRemovals(), rs.handleAdditions(fires)))
                    .reduce((in1, in2) -> Pair.of(in1.getLeft() | in2.getLeft(), in1.getRight() | in2.getRight()))
                    .orElse(Pair.of(false, false));
            didRemovals |= res.getLeft();
            didExtension = res.getRight();
        } while (didExtension || nudged);
        return didRemovals;
    }

    /**
     * Phase II. managing separation of influence groups
     */
    private void groupSeparation() {
        cleanDepGroupFromUnusedSpreaders();
        if(!isEmptyDG()) {
            // Marking all current members of the depgroup as non-members
            getCompleteDGStream().forEach(rs -> rs.stillInDepGroup = false);
            boolean needsFurtherSplitChecks = true;
            do {
                buildDepGroup(getFirstProvider());
                if (getCompleteDGStream().anyMatch(not(rs -> rs.stillInDepGroup))) {
                    // a split is needed, we have identified an influence group which does not belong to the group of the first member
                    EnumMap<DepKind, Set<ResourceSpreader>> newInfluenceGroup = new EnumMap<>(DepKind.class);
                    initDGMap(newInfluenceGroup);
                    var notClassified = getCompleteDGStream();
                    // Separate the newly identified subgroup into its own
                    notClassified.filter(rs -> rs.stillInDepGroup).peek(rs -> newInfluenceGroup.get(rs.spreaderType()).add(rs)).collect(Collectors.toList()).forEach(rs ->
                            myDepGroup.get(rs.spreaderType()).remove(rs) // remove the new sub group's members from the original
                    );
                    // by this time we have a smaller influence group in the current freq syncer as the new group has some of the old members
                    var newFreqSyncer = new FreqSyncer(newInfluenceGroup);
                    newFreqSyncer.updateMyFreqNow();
                } else {
                    // no split is needed, we have got the final group, it is just a bit smaller than before
                    needsFurtherSplitChecks = false;
                }
            } while (needsFurtherSplitChecks);
        }
        if(isEmptyDG()) {
            // We have not been left to work with anything, no need to keep our subscription
            unsubscribe();
        } else {
            updateMyFreqNow();
        }
    }

    private void cleanDepGroupFromUnusedSpreaders() {
        for(DepKind dgType:DepKind.values()) cleanDepGroupFromUnusedSpreaders(dgType);
    }

    private void cleanDepGroupFromUnusedSpreaders(DepKind dgType) {
        // Removing all past members which are no longer processing
        // Clearing out previous freq syncer references to members no longer needing one
        myDepGroup.get(dgType).removeIf(ResourceSpreader::cleanSyncerWhenNotProcessing);
    }

    private boolean isEmptyDG() {
        return getCompleteDGStream().findFirst().isEmpty();
    }

    /**
     * Calls out to the low level scheduler of the group to assign processing limits
     * for each consumption in the group and to identify the completion time of the
     * earliest terminating consumption. Then propagates this time to be the next
     * time Timed fires up our tick function. This technique reduces the number of
     * times influence group management has to be executed. Unless someone asks for
     * it explicitly, this process also reduces the frequency with which the
     * ResourceSpreader.doProcessing is called.
     */
    private void updateMyFreqNow() {
        var newFreq = getFirstProvider().singleGroupwiseFreqUpdater();
        regularFreqMode = newFreq != 0;
        updateFrequency(newFreq);
    }

    private ResourceSpreader getFirstProvider() {
        return getProviderStream().findFirst().orElseThrow();
    }

    /**
     * Determines if the influence group is processing 0 ticks long consumptions.
     *
     * @return <i>true</i> if the influence group is not processing 0 tick long
     * consumptions - this is expected to be the usual case.
     */
    public boolean isRegularFreqMode() {
        return regularFreqMode;
    }

    /**
     * Marks all resource spreaders in the currently decomposing influence group
     * that the starting spreader has connections with. Before calling this function
     * it is expected that all past members of the influence group are marked not in
     * the group anymore. This function will then change these flags back only on
     * the relevant members from the point of view of the starting item.
     *
     * @param startingItem the starting item from which point the influence group
     *                     should be constructured.
     */
    private void buildDepGroup(final ResourceSpreader startingItem) {
        if (!startingItem.toProcess.isEmpty() && !startingItem.stillInDepGroup) {
            startingItem.stillInDepGroup = true;
            startingItem.toProcess.stream().map(startingItem::getCounterPart).forEach(this::buildDepGroup);
        }
    }

    boolean ensureDepGroupHasCounterPart(ResourceSpreader cp) {
        // Check if counterpart is in the dependency group
        if (!isInDepGroup(cp)) {
            var cpSyncer=cp.getSyncer();
            // No it is not, we need an extension
            if (cpSyncer == null || cpSyncer == this) {
                // Just this single item is missing
                depGroupExtension.add(cp);
            } else {
                // There are further items missing
                cpSyncer.unsubscribe(); // we will remove its old syncer
                cpSyncer.getCompleteDGStream().forEach(depGroupExtension::add);
                // Make sure, that if we encounter this cp
                // next time we will not try to add all its
                // dep group
                cp.setSyncer(null);
            }
            return true;
        } else {
            return false;
        }
    }
}
