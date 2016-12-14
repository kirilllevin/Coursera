import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.
public class BlockChain {
    public static final int CUT_OFF_AGE = 10;

    // Mapping from hashes to their corresponding blocks.
    private Map<ByteArrayWrapper, BlockInfo> blocks = new HashMap<>();

    // Mapping from block heights to hashes of the corresponding blocks.
    private Map<Integer, ArrayList<ByteArrayWrapper>> heightsToBlocks = new HashMap<>();

    // The global transaction pool for this block chain.
    private TransactionPool transactionPool = new TransactionPool();

    // The current maximum block height.
    private int maxHeight = 0;
    private BlockInfo maxHeightBlockInfo;

    private class BlockInfo {
        public Block block;
        public int height;
        public UTXOPool utxoPool;

        public BlockInfo(Block block, int height, UTXOPool utxoPool) {
            this.block = block;
            this.height = height;
            this.utxoPool = utxoPool;
        }
    }

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block.
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool utxoPool = new UTXOPool();
        
        for (Transaction tx : genesisBlock.getTransactions()) {
            // Add new UTXOs for the produced outputs.
            addTransactionToUTXOPool(utxoPool, tx);
        }
         // Now do this for the coinbase.
        addTransactionToUTXOPool(utxoPool, genesisBlock.getCoinbase());

        storeBlock(genesisBlock, 1, utxoPool);
    }

    private void addTransactionToUTXOPool(UTXOPool utxoPool, Transaction tx) {
        byte[] hash = tx.getHash();
        for (int i = 0; i < tx.numOutputs(); i++) {
            Transaction.Output output = tx.getOutput(i);
            UTXO utxo = new UTXO(hash, i);
            // Disallow transactions from overwriting contents of the UTXO pool.
            if (!utxoPool.contains(utxo)) {
                utxoPool.addUTXO(utxo, output);    
            }
        }
    }

    private void storeBlock(Block block, int height, UTXOPool utxoPool) {
        BlockInfo blockInfo = new BlockInfo(block, height, utxoPool);
        ByteArrayWrapper hash = new ByteArrayWrapper(block.getHash());
        blocks.put(hash, blockInfo);

        List<ByteArrayWrapper> blocksAtHeight = heightsToBlocks.get(height);
        if (blocksAtHeight == null) {
            blocksAtHeight = new ArrayList<>();
        }
        blocksAtHeight.add(hash);

        if (height > maxHeight) {
            maxHeight = height;
            maxHeightBlockInfo = blockInfo;
            for (int i = 0; i < maxHeight - CUT_OFF_AGE; i++) {
                List<ByteArrayWrapper> blockHashes = heightsToBlocks.get(i);
                if (blockHashes != null) {
                    heightsToBlocks.remove(i);
                    for (ByteArrayWrapper h : blockHashes) {
                        blocks.remove(h);
                    }
                }
            }
        }
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return maxHeightBlockInfo.block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return new UTXOPool(maxHeightBlockInfo.utxoPool);
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return new TransactionPool(transactionPool);
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        byte[] prevBlockHash = block.getPrevBlockHash();
        // Every block added via this function must have a parent.
        if (prevBlockHash == null) {
            return false;
        }

        BlockInfo prevBlockInfo = blocks.get(new ByteArrayWrapper(prevBlockHash));
        // If we don't have the parent in our map, either the parent is too old (height too low),
        // or this is an invalid block. In either case, we reject it.
        if (prevBlockInfo == null) {
            return false;
        }
        int blockHeight = prevBlockInfo.height + 1;

        // If the block is too low, reject it.
        if (blockHeight <= maxHeight - CUT_OFF_AGE) {
            return false;
        }

        // Attempt to process the transactions.
        //
        // First, add all the new outputs being added in this block's list of transactions to the
        // UTXO pool.
        UTXOPool utxoPool = new UTXOPool(prevBlockInfo.utxoPool);
        for (Transaction tx : block.getTransactions()) {
            addTransactionToUTXOPool(utxoPool, tx);
        }

        // Now go through all the inputs in the transactions and remove the associated UTXOs.
        // If a UTXO is not found for some input, that means the transaction is invalid and we
        // should reject the block.
        for (Transaction tx : block.getTransactions()) {
            // Every transaction other than the coinbase must have some inputs.
            if (tx.numInputs() == 0) {
                return false;
            }
            for (Transaction.Input input : tx.getInputs()) {
                UTXO inputUtxo = new UTXO(input.prevTxHash, input.outputIndex);
                if (!utxoPool.contains(inputUtxo)) {
                    return false;
                }

                // Remove the UTXO, it has been consumed.
                utxoPool.removeUTXO(inputUtxo);
            }
        }

        // Add the coinbase transaction to the pool, since it should be spendable by children of
        // this block.
        addTransactionToUTXOPool(utxoPool, block.getCoinbase());

        // Update the transaction pool to remove the transactions in this block.
        for (Transaction tx : block.getTransactions()) {
            transactionPool.removeTransaction(tx.getHash());
        }

        // Store the block.
        storeBlock(block, blockHeight, utxoPool);

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        transactionPool.addTransaction(tx);
    }
}