package com.workflow.entity.excel.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证真实 AES-GCM 分块边界和认证失败行为，不使用加密组件 mock。 */
class ExcelPrivateFileCipherTest {
    private static final int CHUNK = ExcelPrivateFileCipher.CHUNK_SIZE;
    private static final int HEADER = 40;
    private static final int FRAME_OVERHEAD = Integer.BYTES + 16;
    private static final SecretKeySpec KEY = new SecretKeySpec(new byte[32], "AES");
    private final ExcelPrivateFileCipher cipher = new ExcelPrivateFileCipher(KEY);
    @TempDir Path directory;

    @Test void emptyAndBoundarySizedFilesRoundTripWithAuthenticatedTermination() throws Exception {
        for (int length : new int[] {0, 1, CHUNK - 1, CHUNK, CHUNK + 1, CHUNK * 3 + 317}) {
            byte[] plain = pattern(length);
            byte[] encrypted = encrypt(plain);
            int dataFrames = length / CHUNK + (length % CHUNK == 0 ? 0 : 1);
            assertThat(encrypted.length).isEqualTo(HEADER + length + FRAME_OVERHEAD * (dataFrames + 1));
            assertThat(Arrays.copyOf(encrypted, 4)).containsExactly((byte) 'W', (byte) 'E', (byte) 'X', (byte) '2');
            try (InputStream input = cipher.decryptStream(new ByteArrayInputStream(encrypted))) {
                assertThat(input.readAllBytes()).isEqualTo(plain);
                assertThat(input.read()).isEqualTo(-1);
                assertThat(input.read(new byte[0])).isZero();
            }
        }
    }

    @Test void sameKeyAndPlaintextUseIndependentFileKeys() throws Exception {
        byte[] first = encrypt(pattern(CHUNK + 1));
        byte[] second = encrypt(pattern(CHUNK + 1));
        assertThat(first).isNotEqualTo(second);
        // 相同位置的明文也不能跨文件移植：随机文件头参与密钥派生及每块 AAD。
        System.arraycopy(second, HEADER, first, HEADER, CHUNK + FRAME_OVERHEAD);
        assertRejected(first);
    }

    @Test void corruptionIsDetectedBeforeAnyByteOfAffectedBlockIsReleased() throws Exception {
        byte[] encrypted = encrypt(pattern(CHUNK * 2));
        for (int offset : new int[] {HEADER + Integer.BYTES, HEADER + Integer.BYTES + CHUNK}) {
            byte[] corrupt = encrypted.clone();
            corrupt[offset] ^= 1;
            try (InputStream input = cipher.decryptStream(new ByteArrayInputStream(corrupt))) {
                assertThatThrownBy(input::read).isInstanceOf(IOException.class).hasMessageContaining("认证失败");
                assertThatThrownBy(input::read).isInstanceOf(IOException.class).hasMessageContaining("认证失败");
                assertThatThrownBy(() -> input.skip(CHUNK)).isInstanceOf(IOException.class);
            }
        }
        encrypted[HEADER + CHUNK + FRAME_OVERHEAD + Integer.BYTES + 20] ^= 1;
        try (InputStream input = cipher.decryptStream(new ByteArrayInputStream(encrypted))) {
            assertThat(input.readNBytes(CHUNK)).isEqualTo(pattern(CHUNK));
            assertThatThrownBy(input::read).isInstanceOf(IOException.class).hasMessageContaining("认证失败");
        }
    }

    @Test void wrongKeyAndModifiedFileHeaderCannotAuthenticate() throws Exception {
        byte[] encrypted = encrypt(pattern(12));
        byte[] wrongKey = new byte[32];
        wrongKey[0] = 1;
        ExcelPrivateFileCipher wrong = new ExcelPrivateFileCipher(new SecretKeySpec(wrongKey, "AES"));
        assertThatThrownBy(() -> {
            try (InputStream input = wrong.decryptStream(new ByteArrayInputStream(encrypted))) { input.readAllBytes(); }
        }).isInstanceOf(IOException.class).hasMessageContaining("认证失败");
        for (int offset : new int[] {0, 4, 8, HEADER - 1}) {
            byte[] corrupt = encrypted.clone();
            corrupt[offset] ^= 1;
            assertRejected(corrupt);
        }
    }

    @Test void truncationIncludingExactDataFrameBoundaryRequiresTerminalAuthentication() throws Exception {
        byte[] encrypted = encrypt(pattern(CHUNK + 7));
        for (int length : new int[] {0, 3, 7, HEADER - 1, HEADER, HEADER + 3,
                HEADER + CHUNK + FRAME_OVERHEAD - 1, HEADER + CHUNK + FRAME_OVERHEAD,
                encrypted.length - FRAME_OVERHEAD, encrypted.length - 1}) {
            assertRejected(Arrays.copyOf(encrypted, length));
        }
        byte[] empty = encrypt(new byte[0]);
        assertRejected(Arrays.copyOf(empty, HEADER));
        empty[empty.length - 1] ^= 1;
        assertRejected(empty);
    }

    @Test void reorderedDuplicatedOrRemovedFramesAreRejected() throws Exception {
        byte[] encrypted = encrypt(pattern(CHUNK * 3));
        int frame = CHUNK + FRAME_OVERHEAD;
        byte[] reordered = encrypted.clone();
        System.arraycopy(encrypted, HEADER + frame, reordered, HEADER, frame);
        System.arraycopy(encrypted, HEADER, reordered, HEADER + frame, frame);
        assertRejected(reordered);

        byte[] duplicated = new byte[encrypted.length + frame];
        System.arraycopy(encrypted, 0, duplicated, 0, HEADER + frame);
        System.arraycopy(encrypted, HEADER, duplicated, HEADER + frame, encrypted.length - HEADER);
        assertRejected(duplicated);

        byte[] removed = new byte[encrypted.length - frame];
        System.arraycopy(encrypted, 0, removed, 0, HEADER);
        System.arraycopy(encrypted, HEADER + frame, removed, HEADER, encrypted.length - HEADER - frame);
        assertRejected(removed);
    }

    @Test void unauthenticatedLengthCannotCauseUnboundedAllocationOrShortenFile() throws Exception {
        for (int length : new int[] {-1, Integer.MAX_VALUE, CHUNK + 1, 0, 1}) {
            byte[] encrypted = encrypt(pattern(CHUNK));
            ByteBuffer.wrap(encrypted, HEADER, Integer.BYTES).putInt(length);
            assertRejected(encrypted);
        }
    }

    @Test void trailingBytesAndConcatenatedValidFilesAreRejected() throws Exception {
        byte[] encrypted = encrypt(pattern(11));
        assertRejected(Arrays.copyOf(encrypted, encrypted.length + 1));
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        joined.write(encrypted);
        joined.write(encrypt(pattern(7)));
        assertRejected(joined.toByteArray());
    }

    @Test void largeFileIsEncryptedAndDecryptedWithOnlyOneFrameReadAhead() throws Exception {
        // 生成流和文件输出都不持有完整内容；检查实际 I/O 请求长度和第一块读取位置。
        int length = CHUNK * 192 + 113;
        PatternInputStream plaintext = new PatternInputStream(length);
        Path file = directory.resolve("large.wex");
        try (TrackingOutputStream output = new TrackingOutputStream(Files.newOutputStream(file))) {
            cipher.encrypt(plaintext, output);
            assertThat(output.maximumWrite).isLessThanOrEqualTo(CHUNK + 16);
        }
        assertThat(plaintext.maximumRead).isLessThanOrEqualTo(CHUNK);
        try (TrackingInputStream encrypted = new TrackingInputStream(Files.newInputStream(file));
             InputStream decrypted = cipher.decryptStream(encrypted)) {
            assertThat(encrypted.count).isEqualTo(HEADER);
            assertThat(decrypted.available()).isZero();
            assertThat(decrypted.read()).isEqualTo(patternByte(0));
            assertThat(encrypted.count).isEqualTo(HEADER + CHUNK + FRAME_OVERHEAD);
            assertThat(decrypted.available()).isEqualTo(CHUNK - 1);
            byte[] buffer = new byte[8192];
            int position = 1;
            int count;
            while ((count = decrypted.read(buffer)) != -1) {
                for (int i = 0; i < count; i++) {
                    if (Byte.toUnsignedInt(buffer[i]) != patternByte(position + i)) {
                        throw new AssertionError("明文内容错误，位置=" + (position + i));
                    }
                }
                position += count;
            }
            assertThat(position).isEqualTo(length);
            assertThat(encrypted.count).isEqualTo(Files.size(file));
            assertThat(encrypted.maximumRead).isLessThanOrEqualTo(CHUNK + 16);
        }
    }

    @Test void shortAndZeroLengthSourceReadsStillProduceCompleteFrames() throws Exception {
        byte[] plain = pattern(CHUNK + 113);
        InputStream intermittent = new ByteArrayInputStream(plain) {
            private int requests;
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                if (++requests % 3 == 0) return 0;
                return super.read(bytes, offset, Math.min(17, length));
            }
        };
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        cipher.encrypt(intermittent, encrypted);
        try (InputStream decrypted = cipher.decryptStream(new ByteArrayInputStream(encrypted.toByteArray()))) {
            assertThat(decrypted.readAllBytes()).isEqualTo(plain);
        }
    }

    @Test void skippingAuthenticatesBlocksAndClosingOwnsOnlyDecryptionSource() throws Exception {
        TrackingInputStream plain = new TrackingInputStream(new ByteArrayInputStream(pattern(CHUNK + 10)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TrackingOutputStream output = new TrackingOutputStream(bytes);
        cipher.encrypt(plain, output);
        assertThat(plain.closed).isFalse();
        assertThat(output.closed).isFalse();

        TrackingInputStream encrypted = new TrackingInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        InputStream decrypted = cipher.decryptStream(encrypted);
        assertThat(decrypted.skip(CHUNK + 3L)).isEqualTo(CHUNK + 3L);
        assertThat(decrypted.read()).isEqualTo(patternByte(CHUNK + 3));
        assertThat(decrypted.skip(Long.MAX_VALUE)).isEqualTo(6);
        assertThat(decrypted.read()).isEqualTo(-1);
        decrypted.close();
        decrypted.close();
        assertThat(encrypted.closed).isTrue();
        assertThatThrownBy(decrypted::read).isInstanceOf(IOException.class).hasMessageContaining("已关闭");

        byte[] corrupt = bytes.toByteArray();
        corrupt[HEADER + CHUNK + FRAME_OVERHEAD + Integer.BYTES] ^= 1;
        try (InputStream input = cipher.decryptStream(new ByteArrayInputStream(corrupt))) {
            assertThatThrownBy(() -> input.skip(Long.MAX_VALUE)).isInstanceOf(IOException.class);
        }
    }

    @Test void earlyCloseDoesNotDrainRemainingFileAndWriteFailuresPropagate() throws Exception {
        TrackingInputStream encrypted = new TrackingInputStream(new ByteArrayInputStream(encrypt(pattern(CHUNK * 3))));
        try (InputStream input = cipher.decryptStream(encrypted)) { assertThat(input.read()).isEqualTo(patternByte(0)); }
        assertThat(encrypted.closed).isTrue();
        assertThat(encrypted.count).isEqualTo(HEADER + CHUNK + FRAME_OVERHEAD);

        OutputStream failing = new OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("disk full"); }
        };
        assertThatThrownBy(() -> cipher.encrypt(new ByteArrayInputStream(pattern(11)), failing))
                .isInstanceOf(IOException.class).hasMessage("disk full");
    }

    private byte[] encrypt(byte[] plaintext) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        cipher.encrypt(new ByteArrayInputStream(plaintext), output);
        return output.toByteArray();
    }

    private void assertRejected(byte[] encrypted) {
        assertThatThrownBy(() -> {
            try (InputStream input = cipher.decryptStream(new ByteArrayInputStream(encrypted))) {
                input.transferTo(OutputStream.nullOutputStream());
            }
        }).isInstanceOf(IOException.class);
    }

    private static int patternByte(int position) { return (position * 31 + 7) & 0xff; }

    private static byte[] pattern(int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) bytes[i] = (byte) patternByte(i);
        return bytes;
    }

    private static final class PatternInputStream extends InputStream {
        private final int length;
        private int position;
        private int maximumRead;
        private PatternInputStream(int length) { this.length = length; }
        @Override public int read() { return position == length ? -1 : patternByte(position++); }
        @Override public int read(byte[] bytes, int offset, int requested) {
            maximumRead = Math.max(maximumRead, requested);
            if (position == length) return -1;
            int count = Math.min(length - position, requested);
            for (int i = 0; i < count; i++) bytes[offset + i] = (byte) patternByte(position++);
            return count;
        }
    }

    private static final class TrackingInputStream extends FilterInputStream {
        private long count;
        private int maximumRead;
        private boolean closed;
        private TrackingInputStream(InputStream input) { super(input); }
        @Override public int read() throws IOException {
            int result = in.read();
            if (result != -1) count++;
            return result;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            maximumRead = Math.max(maximumRead, length);
            int read = in.read(bytes, offset, length);
            if (read > 0) count += read;
            return read;
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private static final class TrackingOutputStream extends FilterOutputStream {
        private int maximumWrite;
        private boolean closed;
        private TrackingOutputStream(OutputStream output) { super(output); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            maximumWrite = Math.max(maximumWrite, length);
            out.write(bytes, offset, length);
        }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }
}
