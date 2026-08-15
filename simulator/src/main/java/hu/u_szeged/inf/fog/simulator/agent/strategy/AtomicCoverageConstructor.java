package hu.u_szeged.inf.fog.simulator.agent.strategy;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication;
import hu.u_szeged.inf.fog.simulator.agent.AgentApplication.Component;
import hu.u_szeged.inf.fog.simulator.agent.ResourceAgent;
import hu.u_szeged.inf.fog.simulator.agent.offer.AtomicCoverageState;
import hu.u_szeged.inf.fog.simulator.agent.offer.LocalOffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Random;

public class AtomicCoverageConstructor {

    private final Random random;

    public AtomicCoverageConstructor() {
        this.random = SeedSyncer.centralRnd;
    }

    public AtomicCoverageState constructCoverage(AgentApplication application, List<LocalOffer> availableOffers, int maxRestarts) {
        return completeCoverage(application, availableOffers, List.of(), maxRestarts);
    }

    public AtomicCoverageState repairCoverage(AgentApplication application, List<LocalOffer> availableOffers, List<LocalOffer> retainedOffers, int maxRestarts) {
        return completeCoverage( application, availableOffers, retainedOffers, maxRestarts);
    }

    private AtomicCoverageState completeCoverage(AgentApplication application, List<LocalOffer> availableOffers, List<LocalOffer> retainedOffers, int maxRestarts) {
        if (availableOffers.isEmpty()) {
            return null;
        }

        for (int restart = 0; restart < maxRestarts; restart++) {
            List<LocalOffer> selectedOffers = new ArrayList<>(retainedOffers);
            Set<Component> coveredComponents = new LinkedHashSet<>();
            Set<ResourceAgent> selectedAgents = new LinkedHashSet<>();

            boolean retainedOffersAreValid = true;

            for (LocalOffer retainedOffer : retainedOffers) {
                if (!selectedAgents.add(retainedOffer.agent)) {
                    retainedOffersAreValid = false;
                    break;
                }

                for (LocalOffer.ComponentPlacement placement : retainedOffer.placements) {
                    if (!application.components.contains(placement.component)) {
                        retainedOffersAreValid = false;
                        break;
                    }

                    if (!coveredComponents.add(placement.component)) {
                        retainedOffersAreValid = false;
                        break;
                    }
                }

                if (!retainedOffersAreValid) {
                    break;
                }
            }

            if (!retainedOffersAreValid) {
                return null;
            }

            while (coveredComponents.size() < application.components.size()) {
                List<Component> missingComponents = new ArrayList<>();

                for (Component component : application.components) {
                    if (!coveredComponents.contains(component)) {
                        missingComponents.add(component);
                    }
                }

                Collections.shuffle(missingComponents, random);

                List<LocalOffer> selectedCandidates = null;
                int smallestCandidateCount = Integer.MAX_VALUE;

                for (Component missingComponent : missingComponents) {
                    List<LocalOffer> compatibleOffers = new ArrayList<>();

                    for (LocalOffer offer : availableOffers) {
                        if (selectedAgents.contains(offer.agent)) {
                            continue;
                        }

                        boolean coversMissingComponent = false;
                        boolean overlapsWithCurrentCoverage = false;
                        boolean containsForeignComponent = false;

                        for (LocalOffer.ComponentPlacement placement : offer.placements) {
                            if (!application.components.contains(placement.component)) {
                                containsForeignComponent = true;
                                break;
                            }

                            if (placement.component == missingComponent) {
                                coversMissingComponent = true;
                            }

                            if (coveredComponents.contains(placement.component)) {
                                overlapsWithCurrentCoverage = true;
                                break;
                            }
                        }

                        if (coversMissingComponent && !overlapsWithCurrentCoverage && !containsForeignComponent) {
                            compatibleOffers.add(offer);
                        }
                    }

                    if (compatibleOffers.size() < smallestCandidateCount) {
                        selectedCandidates = compatibleOffers;
                        smallestCandidateCount = compatibleOffers.size();
                    }
                }

                if (selectedCandidates == null || selectedCandidates.isEmpty()) {
                    break;
                }

                LocalOffer selectedOffer = selectedCandidates.get(random.nextInt(selectedCandidates.size()));

                selectedOffers.add(selectedOffer);
                selectedAgents.add(selectedOffer.agent);

                for (LocalOffer.ComponentPlacement placement : selectedOffer.placements) {
                    coveredComponents.add(placement.component);
                }
            }

            AtomicCoverageState completedState =new AtomicCoverageState(application.components,selectedOffers);

            if (completedState.isStructurallyValid()) {
                return completedState;
            }
        }

        return null;
    }
}