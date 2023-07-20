package org.simdjson;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockReaderTest {

    @Test
    public void iterateOverEntireBuffer() {
        // given
        int stepSize = 64;
        int fullBlockCount = 2;
        byte[] buffer = new byte[fullBlockCount * stepSize + stepSize / 2];
        Arrays.fill(buffer, (byte) 'a');
        BlockReader reader = new BlockReader(stepSize);
        reader.reset(buffer, buffer.length);

        // when / then
        for (int i = 0; i < fullBlockCount; i++) {
            assertThat(reader.hasFullBlock()).isTrue();
            assertThat(reader.getBlockIndex()).isEqualTo(i * stepSize);
            reader.advance();
            assertThat(reader.getBlockIndex()).isEqualTo((i + 1) * stepSize);
        }
        assertThat(reader.hasFullBlock()).isFalse();
        byte[] remainder = reader.remainder();
        assertThat(remainder.length).isEqualTo(stepSize);
    }

    @Test
    public void lastBlockIsTreatedAsRemainder() {
        // given
        int stepSize = 64;
        int blockCount = 2;
        byte[] buffer = new byte[blockCount * stepSize];
        Arrays.fill(buffer, (byte) 'a');
        BlockReader reader = new BlockReader(stepSize);
        reader.reset(buffer, buffer.length);
        assertThat(reader.hasFullBlock()).isTrue();

        // when
        reader.advance();

        // then
        assertThat(reader.hasFullBlock()).isFalse();
        byte[] remainder = reader.remainder();
        assertThat(remainder.length).isEqualTo(stepSize);
        for (int i = 0; i < remainder.length; i++) {
            assertThat(remainder[i]).isEqualTo(buffer[i]);
        }
    }

    @Test
    public void remainderShouldBeFilledWithSpaces() {
        // given
        int stepSize = 64;
        byte[] buffer = new byte[stepSize / 2];
        Arrays.fill(buffer, (byte) 'a');
        BlockReader reader = new BlockReader(stepSize);
        reader.reset(buffer, buffer.length);
        assertThat(reader.hasFullBlock()).isFalse();

        // when
        byte[] remainder = reader.remainder();

        // then
        assertThat(remainder.length).isEqualTo(stepSize);
        for (int i = 0; i < remainder.length; i++) {
            if (i < buffer.length) {
                assertThat(remainder[i]).isEqualTo(buffer[i]);
            } else {
                assertThat(remainder[i]).isEqualTo((byte) 0x20);
            }
        }
    }
}
