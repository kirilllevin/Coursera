import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private class NodeModel {
        // The probability that this node is malicious.
        public double p_malicious;

        public boolean trusted = false;

        // The set of transactions we've seen from this node.
        public Set<Transaction> seenTransactions = new HashSet<>();

        public NodeModel(double p_malicious) {
            this.p_malicious = p_malicious;
        }
    }

    private double p_graph;
    private double p_malicious;
    private double p_txDistribution;
    private int numRounds;

    private int currentRound = 0;
    private int numFollowees;
    private Map<Integer, NodeModel> nodeModels = new HashMap<>();

    private Set<Transaction> acceptedTransactions = new HashSet<>();

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution,
                         int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        // Instantiate a node model for each followee, with the prior probability that it is
        // malicious being given by the global prior.
        for (int i = 0; i < followees.length; i++) {
            if (followees[i]) {
                nodeModels.put(i, new NodeModel(p_malicious));
            }
        }
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        acceptedTransactions = pendingTransactions;
    }

    public Set<Transaction> sendToFollowers() {
        return acceptedTransactions;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        //System.out.println("========");
        Map<Integer, Set<Transaction>> transactionsPerNode = new HashMap<>();
        
        Map<Transaction, Double> weightedVotesTx = new HashMap<>();

        Map<Transaction, Double> probTx = new HashMap<>();


        Map<Transaction, Set<Integer>> transactionToFollowee = new HashMap<>();
        Map<Integer, Set<Transaction>> followeeToTransaction = new HashMap<>();

        Map<Integer, Set<Transaction>> newTransactionPerFollowee = new HashMap<>();


        double baseRate = 0;
        for (NodeModel model : nodeModels.values()) {
            baseRate += (1 - model.p_malicious);
        }

        // Preprocess the candidates.
        for (Candidate candidate : candidates) {
            int followee = candidate.sender;
            Transaction tx = candidate.tx;
            
            if (!followeeToTransaction.containsKey(followee)) {
                followeeToTransaction.put(followee, new HashSet<Transaction>());
            }
            if (!transactionToFollowee.containsKey(tx)) {
                transactionToFollowee.put(tx, new HashSet<Integer>());
            }

            followeeToTransaction.get(followee).add(tx);
            transactionToFollowee.get(tx).add(followee);

            // If we trust this node already, automatically accept the transaction.
            NodeModel model = nodeModels.get(followee);
            if (model.trusted) {
                acceptedTransactions.add(tx);
            }
        }

        Map<Transaction, Integer> votesPerTransaction = new HashMap<>();
        for (Integer followee : followeeToTransaction.keySet()) {
            Set<Transaction> transactions = followeeToTransaction.get(followee);
            for (Transaction tx : transactions) {
                if (!acceptedTransactions.contains(tx)) {
                    int prevVotes = votesPerTransaction.containsKey(tx) ? votesPerTransaction.get(tx) : 0;
                    votesPerTransaction.put(tx, prevVotes + 1);
                }
            }
        }
 
        // Accept all transactions that appear frequently enough.
        for (Transaction tx : votesPerTransaction.keySet()) {
            // If more followees voted for this transaction than we'd expect to be malicious, accept it.
            // In earlier rounds, we want to be more permissive, so dampen this based on the round.
            // currentRound / numRounds
            if (votesPerTransaction.get(tx) > 0) { //followeeToTransaction.size() * p_malicious * p_graph) {
                acceptedTransactions.add(tx);
            }
        }

        // Figure out which nodes to accept as trusted in the future.
        Set<Integer> accept = new HashSet<>();
        for (Integer followee : followeeToTransaction.keySet()) {
            NodeModel model = nodeModels.get(followee);
            // If we trust this node already, automatically accept it.
            if (model.trusted) {
                accept.add(followee);
                continue;
            }

            int numAlreadyAccepted = 0;
            int numNew = 0;
            Set<Transaction> transactions = followeeToTransaction.get(followee);
            for (Transaction tx : transactions) {
                if (acceptedTransactions.contains(tx)) {
                    numAlreadyAccepted++;
                } else {
                    numNew++;
                }
            }

            // If we've already accepted most of the transactions from this node, accept it.
            if (numAlreadyAccepted > numNew) {
                accept.add(followee);
            }
        }

        // Mark all the relevant nodes as trusted, and accept all of their transactions.
        for (Integer followee : accept) {
            NodeModel model = nodeModels.get(followee);
            model.trusted = true;
            for (Transaction tx : followeeToTransaction.get(followee)) {
                acceptedTransactions.add(tx);
            }
        }


        // int followees = nodeModels.size();
        // Set<Transaction> accepted = new HashSet<>();
        // for (Map.Entry<Transaction, Double> entry : weightedVotesTx.entrySet()) {
        //     if (entry.getValue() > followees * p_graph) {
        //         accepted.add(entry.getKey());
        //         txProbability.put(entry.getKey(), 1.0);
        //     }
        // }

        // Map<Integer, Double> unnormalizedProbs = new HashMap<>();
        // double totalSum = 0;

        // // For each followee, compute how many of its transactions we've accepted.
        // for (Map.Entry<Integer, Set<Transaction>> entry : transactionsPerNode.entrySet()) {
        //     NodeModel model = nodeModels.get(entry.getKey());
        //     double numAccepted = 0.0;
        //     double total = (double) entry.getValue().size();
        //     for (Transaction tx : entry.getValue()) {
        //         if (accepted.contains(tx)) {
        //             numAccepted += 1;
        //         }
        //     }

        //     double unnormalized = numAccepted / total + (total - numAccepted) / total * model.p_malicious;
        //     totalSum += unnormalized;
        //     unnormalizedProbs.put(entry.getKey(), unnormalized);
        // }

        // for (Integer index : unnormalizedProbs.keySet()) {
        //     NodeModel model = nodeModels.get(index);
        //     double before = model.p_malicious;
        //     model.p_malicious = 1 - (unnormalizedProbs.get(index)); // / totalSum * p_malicious);
        //     // if (before != model.p_malicious) {
        //     //     System.out.println("Node " + index + " | round = " + currentRound + " | before = " + before + " | after = " + model.p_malicious);
        //     // }
        // }

        currentRound++;
    }
}
