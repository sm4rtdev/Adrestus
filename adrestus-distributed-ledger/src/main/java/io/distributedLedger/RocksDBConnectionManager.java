package io.distributedLedger;

import io.Adrestus.config.Directory;
import io.Adrestus.crypto.bls.BLS381.ECP;
import io.Adrestus.crypto.bls.BLS381.ECP2;
import io.Adrestus.crypto.bls.mapper.ECP2mapper;
import io.Adrestus.crypto.bls.mapper.ECPmapper;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.crypto.elliptic.mapper.CustomSerializerTreeMap;
import io.Adrestus.util.SerializationUtil;
import io.distributedLedger.exception.*;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationException;
import org.rocksdb.*;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static io.distributedLedger.Constants.RocksDBConstants.*;
import static java.lang.Math.max;

public class RocksDBConnectionManager<K, V> implements IDriver<RocksDBConnectionManager>, IDatabase<K, V> {

    private static org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RocksDBConnectionManager.class);
    private static final String CONNECTION_NAME = "\\Blockchain_rocks-db";
    private static volatile RocksDBConnectionManager instance;
    private static final boolean enableDbCompression = false;


    private final SerializationUtil valueMapper;
    private final SerializationUtil keyMapper;
    private final Class<V> keyClass;
    private final Class<V> valueClass;
    private final ReentrantReadWriteLock rwl;
    private final Lock r;
    private final Lock w;


    private File dbFile;
    private Options options;
    private RocksDB rocksDB;

    private RocksDBConnectionManager(Class<V> keyClass, Class<V> valueClass) {
        if (instance != null) {
            throw new IllegalStateException("Already initialized.");
        }
        this.rwl = new ReentrantReadWriteLock();
        this.r = rwl.readLock();
        this.w = rwl.writeLock();
        this.dbFile = new File(Directory.getConfigPath() + CONNECTION_NAME);
        this.valueClass = valueClass;
        this.keyClass = keyClass;
        List<SerializationUtil.Mapping> list = new ArrayList<>();
        list.add(new SerializationUtil.Mapping(ECP.class, ctx -> new ECPmapper()));
        list.add(new SerializationUtil.Mapping(ECP2.class, ctx -> new ECP2mapper()));
        list.add(new SerializationUtil.Mapping(BigInteger.class, ctx->new BigIntegerSerializer()));
        list.add(new SerializationUtil.Mapping(TreeMap.class,ctx->new CustomSerializerTreeMap()));
        this.keyMapper = new SerializationUtil<>(this.keyClass);
        this.valueMapper = new SerializationUtil<>(this.valueClass,list);
        setupOptions();
        load_connection();
    }


    public static synchronized RocksDBConnectionManager getInstance(Class keyClass, Class valueClass) {
        if (instance == null) {
            synchronized (RocksDBConnectionManager.class) {
                if (instance == null) {
                    instance = new RocksDBConnectionManager(keyClass, valueClass);
                }
            }
        }
        return instance;
    }


    @Override
    public RocksDBConnectionManager get() {
        return instance;
    }


    @Override
    public void setupOptions() {
        options = new Options();

        options.setCreateIfMissing(true);
        options.setUseFsync(false);
        options.setCompressionType(
                enableDbCompression
                        ? CompressionType.LZ4_COMPRESSION
                        : CompressionType.NO_COMPRESSION);

        options.setBottommostCompressionType(CompressionType.ZLIB_COMPRESSION);
        options.setMinWriteBufferNumberToMerge(MIN_WRITE_BUFFER_NUMBER_TOMERGE);
        options.setLevel0StopWritesTrigger(LEVEL0_STOP_WRITES_TRIGGER);
        options.setLevel0SlowdownWritesTrigger(LEVEL0_SLOWDOWN_WRITES_TRIGGER);
        options.setAtomicFlush(true);
        options.setWriteBufferSize(WRITE_BUFFER_SIZE);
        options.setRandomAccessMaxBufferSize(READ_BUFFER_SIZE);
        options.setParanoidChecks(true);
        options.setMaxOpenFiles(MAX_OPEN_FILES);
        options.setTableFormatConfig(setupBlockBasedTableConfig());
        options.setDisableAutoCompactions(false);
        options.setIncreaseParallelism(max(1, Runtime.getRuntime().availableProcessors() / 2));

        options.setLevelCompactionDynamicLevelBytes(true);
        options.setMaxBackgroundCompactions(MAX_BACKGROUND_COMPACTIONS);
        options.setMaxBackgroundFlushes(MAX_BACKGROUND_FLUSHES);
        options.setBytesPerSync(BYTES_PER_SYNC);
        options.setCompactionPriority(CompactionPriority.MinOverlappingRatio);
        options.optimizeLevelStyleCompaction(OPTIMIZE_LEVEL_STYLE_COMPACTION);
    }

    private BlockBasedTableConfig setupBlockBasedTableConfig() {
        BlockBasedTableConfig bbtc = new BlockBasedTableConfig();
        bbtc.setBlockSize(BLOCK_SIZE);
        bbtc.setCacheIndexAndFilterBlocks(true);
        bbtc.setPinL0FilterAndIndexBlocksInCache(true);
        bbtc.setFilterPolicy(new BloomFilter(BLOOMFILTER_BITS_PER_KEY, false));
        return bbtc;
    }

    @SneakyThrows
    @Override
    public void load_connection() {
        w.lock();
        dbFile = new File(Directory.getConfigPath(), CONNECTION_NAME);
        try {
            dbFile.createNewFile();
            rocksDB = RocksDB.open(options, Directory.getConfigPath());
        } catch (IOException e) {
            LOGGER.error("Path to create file is incorrect. {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("RocksDB exception caught. {}", e.getMessage());
            throw new EmptyFailedException(e.getMessage());
        } finally {
            w.unlock();
        }
    }

    @SneakyThrows
    @Override
    public void save(K key, Object value) {
        w.lock();
        try {

            byte[] serializedkey = keyMapper.encode(key);
            byte[] serializedValue = valueMapper.encode(value);
            rocksDB.put(serializedkey, serializedValue);
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during save operation. {}", exception.getMessage());
            throw exception;
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during save operation. {}", exception.getMessage());
            throw new SaveFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
        }
    }

    @SneakyThrows
    @Override
    public void saveAll(Map<K, V> map) {
        w.lock();
        try {

            K[] keys = (K[]) map.keySet().toArray();
            V[] values = (V[]) map.values().toArray();

            for (int i = 0; i < keys.length; i++) {
                byte[] serializedkey = keyMapper.encode(keys[i]);
                byte[] serializedValue = valueMapper.encode(values[i]);
                rocksDB.put(serializedkey, serializedValue);
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during save operation. {}", exception.getMessage());
            throw exception;
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during save operation. {}", exception.getMessage());
            throw new SaveFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
        }

    }


    @SneakyThrows
    @Override
    public Optional<V> findByKey(K key) {
        r.lock();
        try {
            final byte[] serializedKey = keyMapper.encode(key);
            final byte[] bytes = rocksDB.get(serializedKey);
            return (Optional<V>) Optional.ofNullable(valueMapper.decode(bytes));
        } catch (final NullPointerException exception) {
            LOGGER.info("Key value not exists in Database return empty");
            return Optional.empty();
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during findByKey operation. {}", exception.getMessage());
            throw new FindFailedException(exception.getMessage(), exception);
        } finally {
            r.unlock();
        }
        return Optional.empty();
    }

    @SneakyThrows
    @Override
    public void deleteByKey(K key) {
        w.lock();
        try {
            final byte[] serializedKey = keyMapper.encode(key);
            rocksDB.delete(serializedKey);
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
            throw exception;
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during deleteByKey operation. {}", exception.getMessage());
            throw new DeleteFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
        }
    }

    @SneakyThrows
    @Override
    public void deleteAll() {
        w.lock();
        try {
            final RocksIterator iterator = rocksDB.newIterator();

            iterator.seekToFirst();
            final byte[] firstKey = getKey(iterator);

            iterator.seekToLast();
            final byte[] lastKey = getKey(iterator);

            if (firstKey != null || lastKey != null) {
                rocksDB.deleteRange(firstKey, lastKey);
                rocksDB.delete(lastKey);
            }
            rocksDB.close();

            RocksDB.destroyDB(Directory.getConfigPath(), options);
        } catch (NullPointerException exception) {
            LOGGER.error("RocksDBException occurred during delete_db operation. {}", exception.getMessage());
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during deleteAll operation. {}", exception.getMessage());
            throw new DeleteAllFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
            instance = null;
        }
    }

    @Override
    public boolean isDBexists() {
        r.lock();
        try {
            return dbFile.exists();
        } finally {
            r.unlock();
        }
    }

    @SneakyThrows
    @Override
    public boolean delete_db() {
        w.lock();
        try {
            final RocksIterator iterator = rocksDB.newIterator();

            iterator.seekToFirst();
            final byte[] firstKey = getKey(iterator);

            iterator.seekToLast();
            final byte[] lastKey = getKey(iterator);


            if (firstKey != null || lastKey != null) {
                rocksDB.deleteRange(firstKey, lastKey);
                rocksDB.delete(lastKey);
            }
            rocksDB.close();

            RocksDB.destroyDB(Directory.getConfigPath(), options);
            return dbFile.delete();
        } catch (NullPointerException exception) {
            LOGGER.error("RocksDBException occurred during delete_db operation. {}", exception.getMessage());
        } catch (final RocksDBException exception) {
            LOGGER.error("RocksDBException occurred during deleteAll operation. {}", exception.getMessage());
            throw new DeleteAllFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
            instance = null;
        }
        return true;
    }

    private byte[] getKey(final RocksIterator iterator) {
        if (!iterator.isValid()) {
            return null;
        }
        return iterator.key();
    }

    @SneakyThrows
    @Override
    public Map<K, V> findBetweenRange(K key) {
        r.lock();
        Map<Object, Object> hashmap = new LinkedHashMap<>();
        try {
            final RocksIterator iterator = rocksDB.newIterator();
            iterator.seek(keyMapper.encode(key));
            do {
                byte[] serializedKey = iterator.key();
                byte[] serializedValue = iterator.value();
                hashmap.put(keyMapper.decode(serializedKey), valueMapper.decode(serializedValue));
                iterator.next();
            } while (iterator.isValid());
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return (Map<K, V>) hashmap;
    }

    @Override
    public Map<K, V> seekBetweenRange(int start, int finish) {
        r.lock();
        Map<Object, Object> hashmap = new LinkedHashMap<>();
        try {
            final RocksIterator iterator = rocksDB.newIterator();
            iterator.seekToFirst();
            while (iterator.isValid() && start <= finish) {
                byte[] serializedKey = iterator.key();
                byte[] serializedValue = iterator.value();
                hashmap.put(keyMapper.decode(serializedKey), valueMapper.decode(serializedValue));
                iterator.next();
                start++;
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return (Map<K, V>) hashmap;
    }

    @SneakyThrows
    @Override
    public int findDBsize() {
        r.lock();
        try {
            final RocksIterator start_iterator = rocksDB.newIterator();
            start_iterator.seekToFirst();

            int entries = 0;

            while (start_iterator.isValid()) {
                entries++;
                start_iterator.next();
            }
            return entries;
        } finally {
            r.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        r.lock();
        try {

            try (RocksIterator itr = rocksDB.newIterator()) {
                itr.seekToFirst();

                return !itr.isValid();
            } catch (Exception e) {
                LOGGER.error("Unable to extract information from database " + this.toString() + ".", e);
            }
        } finally {
            r.unlock();
        }

        return true;
    }

    @Override
    public void compact() {
        w.lock();
        try {
            try {
                rocksDB.compactRange(new byte[]{(byte) 0x00}, new byte[]{(byte) 0xff});
            } catch (RocksDBException e) {
                LOGGER.error("Cannot compact data.");
                e.printStackTrace();
            }
        } finally {
            w.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        r.lock();
        try {
            return rocksDB != null;
        } finally {
            r.unlock();
        }
    }

}
