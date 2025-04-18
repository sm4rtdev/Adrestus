package io.Adrestus.crypto;


//import io.Adrestus.util.RLP;

import com.google.common.base.Suppliers;
import com.google.common.hash.Hashing;
import lombok.SneakyThrows;
import net.openhft.hashing.LongHashFunction;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.RIPEMD160Digest;
import org.spongycastle.util.encoders.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Random;
import java.util.function.Supplier;

import static io.Adrestus.crypto.ByteUtil.EMPTY_BYTE_ARRAY;
import static java.util.Arrays.copyOfRange;


public class HashUtil {

    private static final Logger LOG = LoggerFactory.getLogger(HashUtil.class);

    public static byte[] EMPTY_DATA_HASH;
    public static byte[] EMPTY_LIST_HASH;
    public static byte[] EMPTY_TRIE_HASH;

    private static Provider CRYPTO_PROVIDER;
    public static final String KECCAK256_ALG = "KECCAK-256";
    private static final Supplier<MessageDigest> KECCAK256_SUPPLIER =
            Suppliers.memoize(() -> messageDigest(KECCAK256_ALG));
    private static final String HASH_256_ALGORITHM_NAME = "SHA-256";
    private static final String HASH_MD5_ALGORITHM_NAME = "MD5";
    private static final String SHA3_HASH_256_ALGORITHM_NAME = "ETH-KECCAK-256";
    private static final String HASH_512_ALGORITHM_NAME = "ETH-KECCAK-512";
    private static final String HASH_256_HMAC = "HmacSHA256";
    private static SecureRandom random = new SecureRandom();

    private static MessageDigest messageDigest(final String algorithm) {
        try {
            return MessageDigestFactory.create(algorithm);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        CRYPTO_PROVIDER = Security.getProvider("BC");
        CRYPTO_PROVIDER.put("MessageDigest.ETH-KECCAK-256", "org.ethereum.crypto.cryptohash.Keccak256");
        CRYPTO_PROVIDER.put("MessageDigest.ETH-KECCAK-512", "org.ethereum.crypto.cryptohash.Keccak512");
        if (EMPTY_BYTE_ARRAY == null) {
            EMPTY_DATA_HASH = sha3(EMPTY_BYTE_ARRAY);
            EMPTY_LIST_HASH = sha3(EncodeUtil.encodeList());
            EMPTY_TRIE_HASH = sha3(EncodeUtil.encodeElement(EMPTY_BYTE_ARRAY));
        }
    }

    /**
     * @param input - data for hashing
     * @return - sha256 hash of the data
     */
    public static String sha256(String input) {
        // return input;
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME);
            String result = Hex.toHexString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
            return result;
        } catch (NoSuchAlgorithmException ex) {
            LOG.error("Can't find such algorithm", ex);
            throw new RuntimeException(ex);
        }
    }

    /**
     * @param data - data for hashing
     * @return - sha1 hash of the data
     */
    public static long sha1ToLong(byte[] data) {
        return Hashing.sha1().hashBytes(data).asLong();
    }

    /**
     * @param data - data for hashing
     * @return - sha1 hash of the data
     */
    public static byte[] sha1ToBytes(byte[] data) {
        return Hashing.sha1().hashBytes(data).asBytes();
    }

    public static long md5ToLong(byte[] data) {
        return Hashing.md5().hashBytes(data).asLong();
    }

    public static byte[] sha256(byte[] input) {
        try {
            MessageDigest sha256digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME);
            return sha256digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    public static String convertIPtoHex(String ip, int bits) {
        try {
            MessageDigest md5digest = MessageDigest.getInstance(HASH_MD5_ALGORITHM_NAME);
            byte[] hased_data = md5digest.digest(ip.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(convertStringToHex(Hex.toHexString(hased_data)));
            return hex.substring(0, bits);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    public static String convertStringToHex(String str) {

        StringBuffer hex = new StringBuffer();

        // loop chars one by one
        for (char temp : str.toCharArray()) {

            // convert char to int, for char `a` decimal 97
            int decimal = (int) temp;

            // convert int to hex, for decimal 97 hex 61
            hex.append(Integer.toHexString(decimal));
        }

        return hex.toString();

    }

    public static String sha256_bytetoString(byte[] input) {
        try {
            MessageDigest sha256digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME);
            String result = Hex.toHexString(sha256digest.digest(input));
            return result;
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha256omit(byte[] input) {
        try {
            byte[] res = DigestUtils.sha256(input);
            return res;
        } catch (Exception e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Digest using keccak-256.
     *
     * @param input The input bytes to produce the digest for.
     * @return A digest.
     */
    public static Bytes32 keccak256(final Bytes input) {
        return Bytes32.wrap(digestUsingAlgorithm(input, KECCAK256_SUPPLIER));
    }

    private static byte[] digestUsingAlgorithm(
            final Bytes input, final Supplier<MessageDigest> digestSupplier) {
        try {
            final MessageDigest digest = (MessageDigest) digestSupplier.get().clone();
            input.update(digest);
            return digest.digest();
        } catch (final CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha256(byte[] input, Provider provider) {
        try {
            MessageDigest sha256digest = MessageDigest.getInstance(HASH_256_ALGORITHM_NAME, provider);
            return sha256digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha3(byte[] input) {
        Keccak.Digest256 digest;
        try {
            digest = new Keccak.Digest256();
            byte[] hash = digest.digest(input);
            return hash;
        } catch (Exception e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }

    }

    public static byte[] sha3(byte[] input1, byte[] input2) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA3_HASH_256_ALGORITHM_NAME);
            digest.update(input1, 0, input1.length);
            digest.update(input2, 0, input2.length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public static String XXH3(byte[] data) {
        return String.valueOf(LongHashFunction.xx3().hashBytes(data));
    }

    @SneakyThrows
    public static String XXH3(String data) {
        return String.valueOf(LongHashFunction.xx3().hashBytes(data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * hashing chunk of the data
     *
     * @param input  - data for hash
     * @param start  - start of hashing chunk
     * @param length - length of hashing chunk
     * @return - keccak hash of the chunk
     */
    public static byte[] sha3(byte[] input, int start, int length) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(SHA3_HASH_256_ALGORITHM_NAME, CRYPTO_PROVIDER);
            digest.update(input, start, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    public static byte[] sha512(byte[] input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_512_ALGORITHM_NAME, CRYPTO_PROVIDER);
            digest.update(input);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @param data - message to hash
     * @return - reipmd160 hash of the message
     */
    public static byte[] ripemd160(byte[] data) {
        Digest digest = new RIPEMD160Digest();
        if (data != null) {
            byte[] resBuf = new byte[digest.getDigestSize()];
            digest.update(data, 0, data.length);
            digest.doFinal(resBuf, 0);
            return resBuf;
        }
        throw new NullPointerException("Can't hash a NULL value");
    }

    public static byte[] mac(byte[] data, byte[] key) throws Exception {

        Mac sha256HMAC = Mac.getInstance(HASH_256_HMAC);
        SecretKeySpec secretKey = new SecretKeySpec(key, HASH_256_HMAC);
        sha256HMAC.init(secretKey);
        return sha256HMAC.doFinal(data);
    }

    public static byte[] Shake256(byte[] inp) {
        Shake256 shake256 = new Shake256();
        shake256.getAbsorbStream().write(inp);
        byte[] out = new byte[48];
        shake256.getSqueezeStream().read(out);
        return out;
    }

    public static byte[] sha3omit12(byte[] input) {
        byte[] hash = sha3(input);
        return copyOfRange(hash, 12, hash.length);
    }

    /**
     * The way to calculate new address inside ethereum
     *
     * @param addr  - creating address
     * @param nonce - nonce of creating address
     * @return new address
     */
    public static byte[] calcNewAddr(byte[] addr, byte[] nonce) {

        byte[] encSender = EncodeUtil.encodeElement(addr);
        byte[] encNonce = EncodeUtil.encodeBigInteger(new BigInteger(1, nonce));

        return sha3omit12(EncodeUtil.encodeList(encSender, encNonce));
    }


    public static byte[] calcSaltAddr(byte[] senderAddr, byte[] initCode, byte[] salt) {
        // 1 - 0xff length, 32 bytes - keccak-256
        byte[] data = new byte[1 + senderAddr.length + salt.length + 32];
        data[0] = (byte) 0xff;
        int currentOffset = 1;
        System.arraycopy(senderAddr, 0, data, currentOffset, senderAddr.length);
        currentOffset += senderAddr.length;
        System.arraycopy(salt, 0, data, currentOffset, salt.length);
        currentOffset += salt.length;
        byte[] sha3InitCode = sha3(initCode);
        System.arraycopy(sha3InitCode, 0, data, currentOffset, sha3InitCode.length);

        return sha3omit12(data);
    }

    /**
     * @param input -
     * @return -
     * @see #doubleDigest(byte[], int, int)
     */
    public static byte[] doubleDigest(byte[] input) {
        return doubleDigest(input, 0, input.length);
    }

    /**
     * Calculates the SHA-256 hash of the given byte range, and then hashes the
     * resulting hash again. This is standard procedure in Bitcoin. The
     * resulting hash is in big endian form.
     *
     * @param input  -
     * @param offset -
     * @param length -
     * @return -
     */
    public static byte[] doubleDigest(byte[] input, int offset, int length) {
        try {
            MessageDigest sha256digest = MessageDigest.getInstance("SHA-256");
            sha256digest.reset();
            sha256digest.update(input, offset, length);
            byte[] first = sha256digest.digest();
            return sha256digest.digest(first);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Can't find such algorithm", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @return generates random peer id for the HelloMessage
     */
    public static byte[] randomPeerId() {

        byte[] peerIdBytes = new BigInteger(512, random).toByteArray();

        final String peerId;
        if (peerIdBytes.length > 64)
            peerId = Hex.toHexString(peerIdBytes, 1, 64);
        else
            peerId = Hex.toHexString(peerIdBytes);

        return Hex.decode(peerId);
    }

    /**
     * @return - generate random 32 byte hash
     */
    public static byte[] randomHash() {

        byte[] randomHash = new byte[32];
        Random random = new Random();
        random.nextBytes(randomHash);
        return randomHash;
    }

    public static String shortHash(byte[] hash) {
        return Hex.toHexString(hash).substring(0, 6);
    }
}

