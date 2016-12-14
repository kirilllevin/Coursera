import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MaxFeeTxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public MaxFeeTxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        
        Set<UTXO> claimedUtxo = new HashSet<UTXO>();
        double sumInputs = 0;
        double sumOutputs = 0;

        for (int i = 0; i < tx.numInputs(); i++) {
            Transaction.Input input = tx.getInput(i);

            // Grab the output that is claimed by this input and ensure that it exists.
            UTXO inputUtxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output output = utxoPool.getTxOutput(inputUtxo);
            if (output == null) {
                return false;
            }

            // Ensure that the claimed UTXO hasn't been claimed already.
            if (claimedUtxo.contains(inputUtxo)) {
                return false;
            }

            // Validate that the signature of the input is valid for the output.
            if (!Crypto.verifySignature(output.address, tx.getRawDataToSign(i), input.signature)) {
                return false;
            }

            // Mark the UTXO as claimed.
            claimedUtxo.add(inputUtxo);

            // Add the value of the claimed output to the total sum.
            sumInputs += output.value;
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            // Ensure that the value of the output is non-negative.
            double outputValue = tx.getOutput(i).value;
            if (outputValue < 0) {
                return false;
            }

            // Add the value of this output to the total sum.
            sumOutputs += outputValue;
        }

        // Ensure that the total sum of outputs is no greater than the total sum of inputs.
        if (sumOutputs > sumInputs) {
            return false;
        }

        return true;
    }

    /**
     * Compute the transcation fee (i.e. sum of input values - sum of output values) for the given
     * transaction.
     */
    private double getFee(Transaction tx) {
        double sumInputs = 0;
        double sumOutputs = 0;

        for (Transaction.Input input : tx.getInputs()) {
            // Grab the output that is claimed by this input.
            UTXO inputUtxo = new UTXO(input.prevTxHash, input.outputIndex);
            Transaction.Output output = utxoPool.getTxOutput(inputUtxo);

            // Add the value of the claimed output to the total sum.
            sumInputs += output.value;
        }

        for (int i = 0; i < tx.numOutputs(); i++) {
            // Add the value of this output to the total sum.
            sumOutputs += tx.getOutput(i).value;
        }

        return sumInputs - sumOutputs;
    }

    private Set<UTXO> getClaimedUTXOs(Transaction tx) {
        Set<UTXO> claimedUTXOs = new HashSet<UTXO>();
        for (Transaction.Input input : tx.getInputs()) {
             claimedUTXOs.add(new UTXO(input.prevTxHash, input.outputIndex));
        }
        return claimedUTXOs;  
    } 

    // A simple cache for storing the results of computeValidTransactionCombos().
    private class ComboCache {
        private Map<Set<Integer>, Map<Set<UTXO>, List<Set<Integer>>>> cache;

        public ComboCache() {
            cache = new HashMap<>();
        }

        public void store(Set<Integer> processedIndices, Set<UTXO> availableUTXOs,
                          List<Set<Integer>> combos) {
            if (!cache.containsKey(processedIndices)) {
                Map<Set<UTXO>, List<Set<Integer>>> map = new HashMap<>();
                cache.put(processedIndices, map);
            }
            cache.get(processedIndices).put(availableUTXOs, combos);
        }

        public List<Set<Integer>> getCombos(
            Set<Integer> processedIndices, Set<UTXO> availableUTXOs) {
            if (cache.containsKey(processedIndices)) {
                return cache.get(processedIndices).get(availableUTXOs);
            }
            return null;
        }
    }

    /**
     * Compute all of the valid transaction combinations, as sets of integers (indices into the
     * transactions array).
     */
    private List<Set<Integer>> computeValidTransactionCombos(
        Transaction[] transactions, List<Set<UTXO>> claimedUTXOs,
        Set<Integer> processedIndices, Set<UTXO> availableUTXOs, ComboCache cache) {
        
        // If the cache already contains a result, return it.
        List<Set<Integer>> validCombos = cache.getCombos(processedIndices, availableUTXOs);
        if (validCombos == null) {
            validCombos = new ArrayList<Set<Integer>>();
        } else {
            return validCombos;
        }

        for (int i = 0; i < claimedUTXOs.size(); i++) {
            // This index was already processed, so skip it.
            if (processedIndices.contains(i)) {
                continue;
            }

            Set<UTXO> claimedUTXOsAtIndex = claimedUTXOs.get(i);
            
            // Skip invalid transactions.
            if (claimedUTXOsAtIndex.isEmpty()) {
                continue;
            }

            // If there is at least one UTXO being claimed that isn't actually availble, skip this
            // index.
            boolean missing = false;
            for (UTXO utxo : claimedUTXOsAtIndex) {
                if (!availableUTXOs.contains(utxo)) {
                    missing = true;
                    break;
                }
            }
            if (missing) {
                continue;
            }

            // Choosing this index is valid.
            // Update the set of available UTXOs by removing the ones being claimed in this
            // transaction and adding the ones that are being introduced by it. Also add this index
            // to the list of processed indices. Then recurse.
            // Note that if all indices have been processed, the recursive call will return an
            // empty list.
            Set<UTXO> updatedAvailableUTXOs = new HashSet<UTXO>(availableUTXOs);
            updatedAvailableUTXOs.removeAll(claimedUTXOsAtIndex);

            Transaction tx = transactions[i];
            for (int j = 0; j < tx.numOutputs(); j++) {
                updatedAvailableUTXOs.add(new UTXO(tx.getHash(), j));
            }

            Set<Integer> updatedProcessedIndices = new HashSet<Integer>(processedIndices);
            updatedProcessedIndices.add(i);

            List<Set<Integer>> subcombos =
                computeValidTransactionCombos(transactions, claimedUTXOs,
                                              updatedProcessedIndices, updatedAvailableUTXOs,
                                              cache);

            // For each result, add the current index to the beginning of the list and add that to
            // the full list of valid combos.
            for (Set<Integer> subcombo : subcombos) {
                subcombo.add(i);
                validCombos.add(subcombo);
            }

            // Base case: add the list that contains just this integer.
            validCombos.add(new HashSet<Integer>(Arrays.asList(i)));
        }

        // Store the resut in the cache.
        cache.store(processedIndices, availableUTXOs, validCombos);

        return validCombos;
     }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // Store the original UTXO pool, since we need to mutate it for validation purposes.
        UTXOPool originalUtxoPool = new UTXOPool(utxoPool);

        // Add all the possible new UTXOs from this batch of transactions to the pool, so that we
        // can validate each transaction independently.
        for (Transaction tx : possibleTxs) {
            byte[] hash = tx.getHash();
            for (int j = 0; j < tx.numOutputs(); j++) {
                Transaction.Output output = tx.getOutput(j);
                utxoPool.addUTXO(new UTXO(hash, j), output);
            }
        }

        // Preprocess the transactions.
        // These lists will help us find the maximal set of transactions. Their indices are the
        // same as the input list of possible transactions.
        // Invalid transactions are marked with invalid values in the lists, to preserve indexing.
        double[] fees = new double[possibleTxs.length];
        List<Set<UTXO>> claimedUTXOs = new ArrayList<Set<UTXO>>();
        for (int i = 0; i < possibleTxs.length; i++) {
            Transaction tx = possibleTxs[i];
            if (isValidTx(tx)) {
                fees[i] = getFee(tx);
                claimedUTXOs.add(getClaimedUTXOs(tx));
            } else {
                fees[i] = -1;
                claimedUTXOs.add(new HashSet<UTXO>());
            }
        }

        // Reset the UTXO pool to the original one.
        utxoPool = originalUtxoPool;

        // Figure out all the possible valid combinations of transactions.
        List<Set<Integer>> validCombos =
            computeValidTransactionCombos(possibleTxs, claimedUTXOs,
                                          new HashSet<Integer>(),
                                          new HashSet<UTXO>(utxoPool.getAllUTXO()),
                                          new ComboCache());

        // Now go through each combination and figure out which one has the highest fees.
        Set<Integer> bestCombo = new HashSet<Integer>();
        double bestFees = 0;
        for (Set<Integer> combo : validCombos) {
            double comboFees = 0;
            for (Integer i : combo) {
                comboFees += fees[i];
            }
            if (comboFees > bestFees) {
                bestFees = comboFees;
                bestCombo = combo;
            }
        }

        // Process the best combo.
        List<Transaction> accepted = new ArrayList<Transaction>();
        for (Integer i : bestCombo) {
            Transaction tx = possibleTxs[i];
            
            // Remove the UTXOs claimed by the inputs.
            for (Transaction.Input input : tx.getInputs()) {
                utxoPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
            }

            // Add new UTXOs for the produced outputs.
            byte[] hash = tx.getHash();
            for (int j = 0; j < tx.numOutputs(); j++) {
                Transaction.Output output = tx.getOutput(j);
                utxoPool.addUTXO(new UTXO(hash, j), output);
            }

            // Add the transaction to the list of accepted transactions.
            accepted.add(tx);
        }

        return accepted.toArray(new Transaction[0]);
    }
}
