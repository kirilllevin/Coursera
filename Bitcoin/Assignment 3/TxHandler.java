import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TxHandler {

    private UTXOPool utxoPool;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
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
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        List<Transaction> accepted = new ArrayList<Transaction>();
        for (Transaction t : possibleTxs) {
            if (isValidTx(t)) {
                // Remove the UTXOs claimed by the inputs.
                for (Transaction.Input input : t.getInputs()) {
                    utxoPool.removeUTXO(new UTXO(input.prevTxHash, input.outputIndex));
                }

                // Add new UTXOs for the produced outputs.
                byte[] hash = t.getHash();
                for (int i = 0; i < t.numOutputs(); i++) {
                    Transaction.Output output = t.getOutput(i);
                    utxoPool.addUTXO(new UTXO(hash, i), output);
                }

                // Add this transaction to the list of accepted transactions.
                accepted.add(t);
            }
        }
        return accepted.toArray(new Transaction[0]);
    }

    public UTXOPool getUTXOPool() {
        return utxoPool;
    }
}
