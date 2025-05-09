package io.distributedLedger;

import io.Adrestus.config.Directory;
import io.Adrestus.crypto.elliptic.mapper.BigDecimalSerializer;
import io.Adrestus.crypto.elliptic.mapper.BigIntegerSerializer;
import io.Adrestus.util.SerializationUtil;
import io.distributedLedger.exception.*;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SerializationException;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static io.distributedLedger.Constants.LevelDBConstants.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;


public class LevelDBConnectionManager<K, V> implements IDatabase<K, V> {

    private static org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LevelDBConnectionManager.class);
    private String CONNECTION_NAME = "TransactionDatabase";

    private static volatile LevelDBConnectionManager instance;
    private final SerializationUtil<V> valueMapper;
    private final SerializationUtil<K> keyMapper;
    private final Class<K> keyClass;
    private final ReentrantReadWriteLock rwl;
    private final Lock r;
    private final Lock w;
    private final String path;

    private Class<V> valueClass;
    private File dbFile;
    private Options options;
    private DB level_db;


    private LevelDBConnectionManager(Class<K> keyClass, Class<V> valueClass) {
        this.rwl = new ReentrantReadWriteLock();
        this.r = rwl.readLock();
        this.w = rwl.writeLock();
        this.path = Directory.getConfigPathPlusPathName(CONNECTION_NAME);
        this.dbFile = new File(this.path);
        this.valueClass = valueClass;
        this.keyClass = keyClass;
        this.keyMapper = new SerializationUtil<>(this.keyClass);
        this.valueMapper = new SerializationUtil<>(this.valueClass);
        setupOptions();
        load_connection();
    }

    private LevelDBConnectionManager(Class<K> keyClass, Type fluentType) {
        List<SerializationUtil.Mapping> list = new ArrayList<>();
        list.add(new SerializationUtil.Mapping(BigDecimal.class, ctx -> new BigDecimalSerializer()));
        list.add(new SerializationUtil.Mapping(BigInteger.class, ctx -> new BigIntegerSerializer()));
        this.rwl = new ReentrantReadWriteLock();
        this.r = rwl.readLock();
        this.w = rwl.writeLock();
        if (fluentType.getTypeName().contains("Receipt")) {
            this.CONNECTION_NAME = "ReceiptDatabase";
        } else {
            this.CONNECTION_NAME = "TransactionDatabase";
        }
        this.path = Directory.getConfigPathPlusPathName(CONNECTION_NAME);
        this.dbFile = new File(path);
        this.keyClass = keyClass;
        this.keyMapper = new SerializationUtil<>(this.keyClass);
        this.valueMapper = new SerializationUtil<>(fluentType, list);
        setupOptions();
        load_connection();
    }

    public static <K, V> LevelDBConnectionManager getInstance(Class<K> keyClass, Class<V> valueClass) {
        var result = instance;
        if (result == null) {
            synchronized (LevelDBConnectionManager.class) {
                result = instance;
                if (result == null) {
                    result = new LevelDBConnectionManager(keyClass, valueClass);
                    instance = result;
                }
            }
        }
        return result;
    }

    public static <K, V> LevelDBConnectionManager getInstance(Class<K> keyClass, Type fluentType) {
        var result = instance;
        if (result == null) {
            synchronized (LevelDBConnectionManager.class) {
                result = instance;
                if (result == null) {
                    result = new LevelDBConnectionManager(keyClass, fluentType);
                    instance = result;
                }
            }
        }
        return result;
    }

    @Override
    public void setupOptions() {
        options = new Options();

        options.createIfMissing(true);
        options.compressionType(CompressionType.NONE);
        options.blockSize(BLOCK_SIZE);
        options.writeBufferSize(WRITE_BUFFER_SIZE); // (levelDb default: 8mb)
        options.cacheSize(CACHE_SIZE);
        options.paranoidChecks(true);
        options.verifyChecksums(true);
        options.maxOpenFiles(MAX_OPEN_FILES);
    }

    @SneakyThrows
    @Override
    public void load_connection() {
        w.lock();
        String pathstring = Paths.get(Directory.getConfigPath(), CONNECTION_NAME).toString();
        this.dbFile = new File(pathstring);
        Path path = Files.createDirectories(Paths.get(dbFile.getAbsolutePath()));
        try {
            dbFile.createNewFile();
            if (CONNECTION_NAME.contains("ReceiptDatabase")) {
                level_db = DatabaseRawReceiptInstance.getInstance(options, path.toAbsolutePath().toString()).getDB();
            } else {
                level_db = DatabaseRawTransactionInstance.getInstance(options, path.toAbsolutePath().toString()).getDB();
            }
        } catch (IOException e) {
            LOGGER.error("Path to create file is incorrect. {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.error("LevelDB exception caught. {}", e.getMessage());
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
            if (value instanceof String) {
                byte[] serializedkey = keyMapper.encode(key);
                byte[] serializedValue = valueMapper.encode((V) value);
                level_db.put(serializedkey, serializedValue);
            } else if (CONNECTION_NAME.contains("ReceiptDatabase")) {
                final Optional<V> obj = findByKey(key);
                final String str_key = (String) key;
                final LevelDBReceiptWrapper<Object> wrapper;
                if (obj == null) {
                    wrapper = new LevelDBReceiptWrapper();
                    wrapper.addTo(value);
                } else if (obj.isEmpty()) {
                    wrapper = new LevelDBReceiptWrapper();
                    wrapper.addTo(value);
                } else {
                    wrapper = (LevelDBReceiptWrapper) obj.get();
                    wrapper.addTo(value);
                }
                byte[] serializedkey = keyMapper.encode((key));
                byte[] serializedValue = valueMapper.encode((V) wrapper);
                level_db.put(serializedkey, serializedValue);
                return;
            } else {
                final Optional<V> obj = findByKey(key);
                final String str_key = (String) key;
                final LevelDBTransactionWrapper<Object> wrapper;
                Method m1 = value.getClass().getDeclaredMethod("getFrom", value.getClass().getClasses());
                if (((String) m1.invoke(value)).equals(str_key)) {
                    if (obj.isEmpty()) {
                        wrapper = new LevelDBTransactionWrapper<Object>();
                        wrapper.addFrom(value);
                    } else {
                        wrapper = (LevelDBTransactionWrapper) obj.get();
                        wrapper.addFrom(value);
                    }
                } else {
                    if (obj == null) {
                        wrapper = new LevelDBTransactionWrapper();
                        wrapper.addTo(value);
                    } else if (obj.isEmpty()) {
                        wrapper = new LevelDBTransactionWrapper();
                        wrapper.addTo(value);
                    } else {
                        wrapper = (LevelDBTransactionWrapper) obj.get();
                        wrapper.addTo(value);
                    }
                }
                byte[] serializedkey = keyMapper.encode(key);
                byte[] serializedValue = valueMapper.encode((V) wrapper);
                level_db.put(serializedkey, serializedValue);
                return;
            }

        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during save operation. {}", exception.getMessage());
            throw exception;
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during save operation. {}", exception.getMessage());
            throw new SaveFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
        }
    }

    @SneakyThrows
    @Override
    public void save(K key, Object value, int length) {
        w.lock();
        try {
            if (value instanceof String) {
                byte[] serializedkey = keyMapper.encode((key));
                byte[] serializedValue = valueMapper.encode((V) value);
                level_db.put(serializedkey, serializedValue);
            } else if (CONNECTION_NAME.contains("ReceiptDatabase")) {
                final Optional<V> obj = findByKey(key);
                final String str_key = (String) key;
                final LevelDBReceiptWrapper<Object> wrapper;
                if (obj == null) {
                    wrapper = new LevelDBReceiptWrapper();
                    wrapper.addTo(value);
                } else if (obj.isEmpty()) {
                    wrapper = new LevelDBReceiptWrapper();
                    wrapper.addTo(value);
                } else {
                    wrapper = (LevelDBReceiptWrapper) obj.get();
                    wrapper.addTo(value);
                }
                byte[] serializedkey = keyMapper.encode(key);
                byte[] serializedValue = valueMapper.encode((V) wrapper);
                level_db.put(serializedkey, serializedValue);
                return;
            } else {
                final Optional<V> obj = findByKey(key);
                final String str_key = (String) key;
                final LevelDBTransactionWrapper<Object> wrapper;
                Method m1 = value.getClass().getDeclaredMethod("getFrom", value.getClass().getClasses());
                if (((String) m1.invoke(value)).equals(str_key)) {
                    if (obj.isEmpty()) {
                        wrapper = new LevelDBTransactionWrapper<Object>();
                        wrapper.addFrom(value);
                    } else {
                        wrapper = (LevelDBTransactionWrapper) obj.get();
                        wrapper.addFrom(value);
                    }
                } else {
                    if (obj == null) {
                        wrapper = new LevelDBTransactionWrapper();
                        wrapper.addTo(value);
                    } else if (obj.isEmpty()) {
                        wrapper = new LevelDBTransactionWrapper();
                        wrapper.addTo(value);
                    } else {
                        wrapper = (LevelDBTransactionWrapper) obj.get();
                        wrapper.addTo(value);
                    }
                }
                byte[] serializedkey = keyMapper.encode(key);
                byte[] serializedValue = valueMapper.encode((V) wrapper);
                level_db.put(serializedkey, serializedValue);
                return;
            }

        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during save operation. {}", exception.getMessage());
            throw exception;
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during save operation. {}", exception.getMessage());
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
            if (!map.isEmpty()) {
                K[] keys = (K[]) map.keySet().toArray();
                V[] values = (V[]) map.values().toArray();

                for (int i = 0; i < keys.length; i++) {
                    byte[] serializedkey = keyMapper.encode(keys[i]);
                    byte[] serializedValue = valueMapper.encode(values[i]);
                    level_db.put(serializedkey, serializedValue);
                }
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during save operation. {}", exception.getMessage());
            throw exception;
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during save operation. {}", exception.getMessage());
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
            final byte[] bytes = level_db.get(serializedKey);
            return (Optional<V>) Optional.ofNullable(valueMapper.decode(bytes));
        } catch (final NullPointerException exception) {
            // LOGGER.info("Key value not exists in Database return empty");
            return Optional.empty();
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during findByKey operation. {}", exception.getMessage());
            throw new FindFailedException(exception.getMessage(), exception);
        } finally {
            r.unlock();
        }
        return Optional.empty();
    }

    @Override
    public TreeSet<K> retrieveAllKeys() throws FindFailedException {
        r.lock();
        TreeSet<K> hashSet = new TreeSet<>();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seekToFirst();

            while (iterator.hasNext()) {
                byte[] serializedKey = iterator.peekNext().getKey();
                hashSet.add((K) keyMapper.decode(serializedKey));
                iterator.next();
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        } finally {
            r.unlock();
        }
        return hashSet;
    }

    @SneakyThrows
    @Override
    public List<V> findByListKey(List<K> key) {
        r.lock();
        try {
            List<Optional<Object>> list = new ArrayList<>();
            for (int i = 0; i < key.size(); i++) {
                final byte[] serializedKey = keyMapper.encode(key.get(i));
                final byte[] bytes = level_db.get(serializedKey);
                list.add(Optional.ofNullable((V) valueMapper.decode(bytes)));
            }
        } catch (final NullPointerException exception) {
            LOGGER.info("Key value not exists in Database return empty");
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during findByKey operation. {}", exception.getMessage());
            throw new FindFailedException(exception.getMessage(), exception);
        } finally {
            r.unlock();
        }
        return new ArrayList<V>();
    }

    @SneakyThrows
    @Override
    public void deleteByKey(K key) {
        w.lock();
        try {
            final byte[] serializedKey = keyMapper.encode(key);
            level_db.delete(serializedKey);
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
            throw exception;
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during deleteByKey operation. {}", exception.getMessage());
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
            final DBIterator iterator = level_db.iterator();

            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                level_db.delete(iterator.peekNext().getKey());
            }
            level_db.close();
            factory.destroy(dbFile.getParentFile(), options);
            level_db = null;
            instance = null;
        } catch (NullPointerException exception) {
            LOGGER.error("Exception occurred during delete_db operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during deleteAll operation. {}", exception.getMessage());
            throw new DeleteAllFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
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
            final DBIterator iterator = level_db.iterator();

            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                level_db.delete(iterator.peekNext().getKey());
            }

            if (CONNECTION_NAME.contains("ReceiptDatabase")) {
                DatabaseRawReceiptInstance.getInstance(options, dbFile.getParentFile().getAbsolutePath()).close(options);
            } else {
                DatabaseRawTransactionInstance.getInstance(options, dbFile.getParentFile().getAbsolutePath()).close(options);
            }
            level_db.close();
            factory.destroy(dbFile.getParentFile(), options);
            level_db = null;
        } catch (NullPointerException exception) {
            LOGGER.error("Exception occurred during delete_db operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during deleteAll operation. {}", exception.getMessage());
            throw new DeleteAllFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
        }
        dbFile.delete();
        dbFile.getParentFile().delete();
        instance = null;
        return dbFile.delete();
    }

    @Override
    public boolean erase_db() {
        w.lock();
        try {
            final DBIterator iterator = level_db.iterator();

            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                level_db.delete(iterator.peekNext().getKey());
            }

            return true;
        } catch (NullPointerException exception) {
            LOGGER.error("Exception occurred during delete_db operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during deleteAll operation. {}", exception.getMessage());
            throw new DeleteAllFailedException(exception.getMessage(), exception);
        } finally {
            w.unlock();
            return true;
        }
    }


    @SneakyThrows
    @Override
    public Map<K, V> findBetweenRange(K key) {
        r.lock();
        Map<Object, Object> hashmap = new HashMap<>();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seek(keyMapper.encode(key));

            while (iterator.hasNext()) {
                byte[] serializedKey = iterator.peekNext().getKey();
                byte[] serializedValue = iterator.peekNext().getValue();
                hashmap.put(keyMapper.decode(serializedKey), valueMapper.decode(serializedValue));
                iterator.next();
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return (Map<K, V>) hashmap;
    }

    @Override
    public Map<K, V> seekFromStart() {
        r.lock();
        Map<Object, Object> hashmap = new HashMap<>();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seekToFirst();

            while (iterator.hasNext()) {
                byte[] serializedKey = iterator.peekNext().getKey();
                byte[] serializedValue = iterator.peekNext().getValue();
                hashmap.put(keyMapper.decode(serializedKey), valueMapper.decode(serializedValue));
                iterator.next();
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
        } finally {
            r.unlock();
        }
        return (Map<K, V>) hashmap;
    }

    @Override
    public Map<K, V> seekBetweenRange(int start, int finish) {
        r.lock();
        Map<Object, Object> hashmap = new HashMap<>();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seekToFirst();
            int counter = 1;
            while (iterator.hasNext() && start <= finish) {
                if (counter < start) {
                    iterator.next();
                } else {
                    byte[] serializedKey = iterator.peekNext().getKey();
                    byte[] serializedValue = iterator.peekNext().getValue();
                    hashmap.put(keyMapper.decode(serializedKey), valueMapper.decode(serializedValue));
                    iterator.next();
                    start++;
                }
                counter++;
            }
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return (Map<K, V>) hashmap;
    }


    @Override
    public Optional<V> seekLast() {
        r.lock();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seekToLast();
            byte[] serializedValue = iterator.peekNext().getValue();
            return (Optional<V>) Optional.of(valueMapper.decode(serializedValue));
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return Optional.empty();
    }

    @Override
    public Optional<V> seekFirst() {
        r.lock();
        try {
            final DBIterator iterator = level_db.iterator();
            iterator.seekToFirst();
            byte[] serializedValue = iterator.peekNext().getValue();
            return (Optional<V>) Optional.of(valueMapper.decode(serializedValue));
        } catch (final SerializationException exception) {
            LOGGER.error("Serialization exception occurred during findByKey operation. {}", exception.getMessage());
        } finally {
            r.unlock();
        }
        return Optional.empty();
    }

    @SneakyThrows
    @Override
    public int findDBsize() {
        r.lock();
        try {
            final DBIterator start_iterator = level_db.iterator();

            start_iterator.seekToFirst();
            int entries = 0;

            while (start_iterator.hasNext()) {
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

            try (DBIterator itr = level_db.iterator()) {
                itr.seekToFirst();

                // check if there is at least one valid item
                return !itr.hasNext();
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
                level_db.compactRange(new byte[]{(byte) 0x00}, new byte[]{(byte) 0xff});
            } catch (Exception e) {
                LOGGER.error("Cannot compact data.");
                e.printStackTrace();
            }
        } finally {
            w.unlock();
        }
    }

    @Override
    public void chooseDB(File dbFile) {

    }

    @Override
    public void closeNoDelete() {
        w.lock();
        try {
            level_db.close();
            instance = null;
        } catch (NullPointerException exception) {
            LOGGER.error("Exception occurred during delete_db operation. {}", exception.getMessage());
        } catch (final Exception exception) {
            LOGGER.error("Exception occurred during deleteAll operation. {}", exception.getMessage());
        } finally {
            w.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        r.lock();
        try {
            return level_db != null;
        } finally {
            r.unlock();
        }
    }


}
