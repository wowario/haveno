/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.core.offer.availability;

import com.google.common.annotations.VisibleForTesting;
import haveno.common.util.Tuple2;
import haveno.core.support.dispute.agent.DisputeAgent;
import haveno.core.support.dispute.agent.DisputeAgentManager;
import haveno.core.trade.statistics.TradeStatistics3;
import haveno.core.trade.statistics.TradeStatisticsManager;
import haveno.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class DisputeAgentSelection {
    public static final int LOOK_BACK_RANGE = 100;

    public static <T extends DisputeAgent> T getLeastUsedArbitrator(TradeStatisticsManager tradeStatisticsManager,
                                                                  DisputeAgentManager<T> disputeAgentManager) {
        return getLeastUsedDisputeAgent(tradeStatisticsManager,
                disputeAgentManager,
                null);
    }

    public static <T extends DisputeAgent> T getLeastUsedArbitrator(TradeStatisticsManager tradeStatisticsManager,
                                                                  DisputeAgentManager<T> disputeAgentManager,
                                                                  Set<NodeAddress> excludedArbitrator) {
        return getLeastUsedDisputeAgent(tradeStatisticsManager,
                disputeAgentManager,
                excludedArbitrator);
    }

    private static <T extends DisputeAgent> T getLeastUsedDisputeAgent(TradeStatisticsManager tradeStatisticsManager,
                                                                       DisputeAgentManager<T> disputeAgentManager,
                                                                       Set<NodeAddress> excludedDisputeAgents) {
        // We take last 100 entries from trade statistics
        List<TradeStatistics3> list = new ArrayList<>(tradeStatisticsManager.getObservableTradeStatisticsSet());
        list.sort(Comparator.comparing(TradeStatistics3::getDateAsLong));
        Collections.reverse(list);
        if (!list.isEmpty()) {
            int max = Math.min(list.size(), LOOK_BACK_RANGE);
            list = list.subList(0, max);
        }

        // We stored only first 4 chars of disputeAgents onion address
        List<String> lastAddressesUsedInTrades = list.stream()
                .map(tradeStatistics3 -> tradeStatistics3.getArbitrator())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Set<String> disputeAgents = disputeAgentManager.getObservableMap().values().stream()
                .map(disputeAgent -> disputeAgent.getNodeAddress().getFullAddress())
                .collect(Collectors.toSet());

        if (excludedDisputeAgents != null) disputeAgents.removeAll(excludedDisputeAgents.stream().map(NodeAddress::getFullAddress).collect(Collectors.toList()));
        if (disputeAgents.isEmpty()) return null;

        String result = getLeastUsedDisputeAgent(lastAddressesUsedInTrades, disputeAgents);

        Optional<T> optionalDisputeAgent = disputeAgentManager.getObservableMap().values().stream()
                .filter(e -> e.getNodeAddress().getFullAddress().equals(result))
                .findAny();
        checkArgument(optionalDisputeAgent.isPresent(), "optionalDisputeAgent has to be present");
        return optionalDisputeAgent.get();
    }

    @VisibleForTesting
    static String getLeastUsedDisputeAgent(List<String> lastAddressesUsedInTrades, Set<String> disputeAgents) {
        checkArgument(!disputeAgents.isEmpty(), "disputeAgents must not be empty");
        List<Tuple2<String, AtomicInteger>> disputeAgentTuples = disputeAgents.stream()
                .map(e -> new Tuple2<>(e, new AtomicInteger(0)))
                .collect(Collectors.toList());
        disputeAgentTuples.forEach(tuple -> {
            int count = (int) lastAddressesUsedInTrades.stream()
                    .filter(tuple.first::startsWith) // we use only first 4 chars for comparing
                    .mapToInt(e -> 1)
                    .count();
            tuple.second.set(count);
        });

        disputeAgentTuples.sort(Comparator.comparing(e -> e.first));
        disputeAgentTuples.sort(Comparator.comparingInt(e -> e.second.get()));
        return disputeAgentTuples.get(0).first;
    }

    public static <T extends DisputeAgent> T getRandomArbitrator(DisputeAgentManager<T> disputeAgentManager) {
        return getRandomArbitrator(disputeAgentManager, null);
    }

    public static <T extends DisputeAgent> T getRandomArbitrator(DisputeAgentManager<T> disputeAgentManager,
                    Set<NodeAddress> excludedArbitrator) {
        return getRandomDisputeAgent(disputeAgentManager, excludedArbitrator);
    }

    private static <T extends DisputeAgent> T getRandomDisputeAgent(DisputeAgentManager<T> disputeAgentManager,
                    Set<NodeAddress> excludedDisputeAgents) {

        // get all dispute agents
        Set<String> disputeAgents = disputeAgentManager.getObservableMap().values().stream()
                        .map(disputeAgent -> disputeAgent.getNodeAddress().getFullAddress())
                        .collect(Collectors.toSet());

        // remove excluded dispute agents
        if (excludedDisputeAgents != null) disputeAgents.removeAll(excludedDisputeAgents.stream().map(NodeAddress::getFullAddress).collect(Collectors.toList()));
        if (disputeAgents.isEmpty()) return null;

        // get random dispute agent
        String result = getRandomDisputeAgent(disputeAgents);
        Optional<T> optionalDisputeAgent = disputeAgentManager.getObservableMap().values().stream()
                        .filter(e -> e.getNodeAddress().getFullAddress().equals(result))
                        .findAny();
        checkArgument(optionalDisputeAgent.isPresent(), "optionalDisputeAgent has to be present");
        return optionalDisputeAgent.get();
    }

    private static String getRandomDisputeAgent(Set<String> disputeAgents) {
        int randomIndex = new Random().nextInt(disputeAgents.size());
        List<String> elements = new ArrayList<String>(disputeAgents);
        return elements.get(randomIndex);
    }
}
