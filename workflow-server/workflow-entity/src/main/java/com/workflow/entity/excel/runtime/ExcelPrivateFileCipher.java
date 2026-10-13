package com.workflow.entity.excel.runtime;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * Excel 私有任务文件的有界内存加密格式。每个 64 KiB 块独立使用 AES-GCM 认证，
 * 避免单条 GCM 消息在解密时缓存整个导出文件。实例可并发复用，不保存某个文件的流状态。
 *
 * <p>WEX2 文件头为 magic(4) + 块大小(4) + 随机 salt(32)，后续每帧为明文长度(4)
 * + 密文 + GCM 标签(16)。长度为零的认证帧必须恰好位于文件结尾，不能以普通 EOF 代替。
 * salt 派生独立文件密钥；块序号既进入 nonce 又进入 AAD，文件头和块长度也进入 AAD。
 * 因此修改内容、跨文件拼接、重排、删除块及截断终止帧均不能被当作完整文件读取。
 */
public final class ExcelPrivateFileCipher {
    public static final int CHUNK_SIZE = 64 * 1024;
    private static final byte[] MAGIC = {'W', 'E', 'X', '2'};
    private static final int SALT_SIZE = 32;
    private static final int HEADER_SIZE = MAGIC.length + Integer.BYTES + SALT_SIZE;
    private static final int TAG_SIZE = 16;
    private static final byte[] KEY_DOMAIN = "entity-excel-private-file:chunk-key:v2\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] masterKey;

    /**
     * 接受文件存储层已经完成业务域隔离的 AES 密钥；每个文件还会派生不同子密钥。
     * 密钥需要可导出为 128/192/256 位原始 AES 密钥，构造后不受调用方数组修改影响。
     */
    public ExcelPrivateFileCipher(SecretKey key) {
        Objects.requireNonNull(key, "key");
        byte[] encoded = key.getEncoded();
        if (!"AES".equalsIgnoreCase(key.getAlgorithm()) || encoded == null
                || (encoded.length != 16 && encoded.length != 24 && encoded.length != 32)) {
            throw new IllegalArgumentException("Excel任务文件需要可导出的AES密钥");
        }
        masterKey = encoded.clone();
    }

    /**
     * 按块读取明文并写出密文及认证终止帧；内存占用与文件长度无关。
     * 不关闭或刷新调用方流。任一读写/加密失败均抛出 IOException，调用方应删除不完整输出。
     */
    public void encrypt(InputStream plaintext, OutputStream ciphertext) throws IOException {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(ciphertext, "ciphertext");
        byte[] salt = new byte[SALT_SIZE];
        RANDOM.nextBytes(salt);
        byte[] header = ByteBuffer.allocate(HEADER_SIZE).put(MAGIC).putInt(CHUNK_SIZE).put(salt).array();
        SecretKey fileKey = deriveFileKey(salt);
        ciphertext.write(header);
        byte[] block = new byte[CHUNK_SIZE];
        try {
            long sequence = 0;
            int count;
            while ((count = readBlock(plaintext, block)) > 0) {
                writeFrame(ciphertext, header, fileKey, sequence, block, count);
                sequence = nextSequence(sequence);
            }
            // 即使明文为空也必须认证文件结束；普通 EOF 无法区分合法空文件和被截断文件。
            writeFrame(ciphertext, header, fileKey, sequence, block, 0);
        } finally {
            Arrays.fill(block, (byte) 0);
        }
    }

    /**
     * 返回逐块认证后才释放明文的输入流；关闭返回流会关闭密文流。
     * 此方法只读取固定文件头，后续格式及认证错误在 read/skip 时抛出 IOException。
     * 调用方必须读到 EOF 才能认定文件完整；提前 close 不会隐式下载和验证剩余文件。
     * 创建流失败时仍由调用方关闭传入流。
     */
    public InputStream decryptStream(InputStream ciphertext) throws IOException {
        Objects.requireNonNull(ciphertext, "ciphertext");
        byte[] header = readExact(ciphertext, HEADER_SIZE);
        if (!Arrays.equals(MAGIC, Arrays.copyOf(header, MAGIC.length))
                || ByteBuffer.wrap(header, MAGIC.length, Integer.BYTES).getInt() != CHUNK_SIZE) {
            throw new IOException("Excel任务文件格式无效");
        }
        return new DecryptingInputStream(ciphertext, header,
                deriveFileKey(Arrays.copyOfRange(header, MAGIC.length + Integer.BYTES, HEADER_SIZE)));
    }

    private SecretKey deriveFileKey(byte[] salt) throws IOException {
        try {
            // 主密钥已有足够熵；随机 256 位 salt 通过带域标识的 HMAC 派生每文件 AES-256 密钥，
            // 使不同文件的顺序号从零开始也不会复用同一 GCM 密钥/nonce 组合。
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
            mac.update(KEY_DOMAIN);
            return new SecretKeySpec(mac.doFinal(salt), "AES");
        } catch (GeneralSecurityException failure) {
            throw new IOException("Excel任务文件密钥派生失败", failure);
        }
    }

    private static Cipher frameCipher(int mode, SecretKey key, byte[] header, long sequence, int length)
            throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(12).put(MAGIC).putLong(sequence).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_SIZE * Byte.SIZE, nonce));
        cipher.updateAAD(header);
        cipher.updateAAD(ByteBuffer.allocate(Long.BYTES + Integer.BYTES).putLong(sequence).putInt(length).array());
        return cipher;
    }

    private static void writeFrame(OutputStream target, byte[] header, SecretKey key, long sequence,
                                   byte[] plaintext, int length) throws IOException {
        try {
            byte[] encrypted = frameCipher(Cipher.ENCRYPT_MODE, key, header, sequence, length)
                    .doFinal(plaintext, 0, length);
            target.write(ByteBuffer.allocate(Integer.BYTES).putInt(length).array());
            target.write(encrypted);
        } catch (GeneralSecurityException failure) {
            throw new IOException("Excel任务文件加密失败", failure);
        }
    }

    private static int readBlock(InputStream source, byte[] target) throws IOException {
        int total = 0;
        while (total < target.length) {
            int count = source.read(target, total, target.length - total);
            if (count < 0) break;
            // 少数包装流可能短暂返回 0，改为单字节读取防止忙循环；普通短读继续填满当前块。
            if (count == 0) {
                int value = source.read();
                if (value < 0) break;
                target[total++] = (byte) value;
            } else {
                total += count;
            }
        }
        return total;
    }

    private static byte[] readExact(InputStream source, int length) throws IOException {
        byte[] bytes = new byte[length];
        if (readBlock(source, bytes) != length) throw new EOFException("Excel任务文件不完整");
        return bytes;
    }

    private static long nextSequence(long sequence) throws IOException {
        if (sequence == Long.MAX_VALUE) throw new IOException("Excel任务文件分块数量超出上限");
        return sequence + 1;
    }

    private static final class DecryptingInputStream extends InputStream {
        private final InputStream source;
        private final byte[] header;
        private final SecretKey key;
        private byte[] block = new byte[0];
        private int position;
        private long sequence;
        private boolean finished;
        private boolean closed;
        private IOException failure;

        private DecryptingInputStream(InputStream source, byte[] header, SecretKey key) {
            this.source = source;
            this.header = header;
            this.key = key;
        }

        @Override
        public int read() throws IOException {
            return ensureBlock() ? Byte.toUnsignedInt(block[position++]) : -1;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            requireUsable();
            if (length == 0) return 0;
            if (!ensureBlock()) return -1;
            int count = Math.min(length, block.length - position);
            System.arraycopy(block, position, bytes, offset, count);
            position += count;
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            requireUsable();
            long remaining = Math.max(0, count);
            // skip 也必须逐块认证，不能通过底层流跳过密文从而绕开顺序及完整性检查。
            while (remaining > 0 && ensureBlock()) {
                int skipped = (int) Math.min(remaining, block.length - position);
                position += skipped;
                remaining -= skipped;
            }
            return Math.max(0, count) - remaining;
        }

        @Override
        public int available() throws IOException {
            requireUsable();
            return block.length - position;
        }

        @Override
        public void close() throws IOException {
            if (closed) return;
            closed = true;
            Arrays.fill(block, (byte) 0);
            block = new byte[0];
            source.close();
        }

        private void requireUsable() throws IOException {
            if (closed) throw new IOException("Excel任务文件流已关闭");
            if (failure != null) throw failure;
        }

        private boolean ensureBlock() throws IOException {
            requireUsable();
            if (position < block.length) return true;
            if (finished) return false;
            Arrays.fill(block, (byte) 0);
            block = new byte[0];
            position = 0;
            try {
                int length = ByteBuffer.wrap(readExact(source, Integer.BYTES)).getInt();
                // 先限定长度再分配，攻击者提供的帧头不能驱动大内存申请。
                if (length < 0 || length > CHUNK_SIZE) throw new IOException("Excel任务文件分块长度无效");
                byte[] encrypted = readExact(source, length + TAG_SIZE);
                byte[] decrypted = frameCipher(Cipher.DECRYPT_MODE, key, header, sequence, length)
                        .doFinal(encrypted);
                if (length == 0) {
                    if (source.read() != -1) throw new IOException("Excel任务文件终止块后存在多余内容");
                    finished = true;
                    return false;
                }
                sequence = nextSequence(sequence);
                block = decrypted;
                return true;
            } catch (GeneralSecurityException cause) {
                failure = new IOException("Excel任务文件认证失败", cause);
                throw failure;
            } catch (IOException cause) {
                // 认证失败后的流永远不可继续读取，防止调用方捕获异常后越过损坏块。
                failure = cause;
                throw cause;
            }
        }
    }
}
