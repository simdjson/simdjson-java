package org.simdjson;

import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.simdjson.TestUtils.chunk;

public class CharactersClassifierTest {

    @Test
    public void classifiesOperators() {
        // given
        CharactersClassifier classifier = new CharactersClassifier();
        String str = "a{bc}1:2,3[efg]aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // when
        JsonCharacterBlock block = classify(classifier, str);

        // then
        assertThat(block.op()).isEqualTo(0x4552);
        assertThat(block.whitespace()).isEqualTo(0);
    }

    @Test
    public void classifiesControlCharactersAsOperators() {
        // given
        CharactersClassifier classifier = new CharactersClassifier();
        String str = new String(new byte[] {
                'a', 'a', 'a', 0x1a, 'a', 0x0c, 'a', 'a', // 0x1a = <SUB>, 0x0c = <FF>
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a',
                'a', 'a', 'a', 'a', 'a', 'a', 'a', 'a'
        }, UTF_8);

        // when
        JsonCharacterBlock block = classify(classifier, str);

        // then
        assertThat(block.op()).isEqualTo(0x28);
        assertThat(block.whitespace()).isEqualTo(0);
    }

    @Test
    public void classifiesWhitespaces() {
        // given
        CharactersClassifier classifier = new CharactersClassifier();
        String str = "a bc\t1\n2\r3efgaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // when
        JsonCharacterBlock block = classify(classifier, str);

        // then
        assertThat(block.whitespace()).isEqualTo(0x152);
        assertThat(block.op()).isEqualTo(0);
    }

    private JsonCharacterBlock classify(CharactersClassifier classifier, String str) {
        return switch (StructuralIndexer.N_CHUNKS) {
            case 1 -> classifier.classify(chunk(str, 0));
            case 2 -> classifier.classify(chunk(str, 0), chunk(str, 1));
            case 4 -> classifier.classify(chunk(str, 0), chunk(str, 1), chunk(str, 2), chunk(str, 3));
            default -> throw new RuntimeException("Unsupported chunk count: " + StructuralIndexer.N_CHUNKS);
        };
    }

}
