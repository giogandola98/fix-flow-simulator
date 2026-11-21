package com.fixflow.adapters.dictionary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictionaryStoreTest {

    private static final String VALID = "<fix major='4' minor='4'><header><field name='BeginString' required='Y'/></header>"
        + "<trailer><field name='CheckSum' required='Y'/></trailer><messages><message name='Heartbeat' msgtype='0' msgcat='admin'><field name='BeginString' required='N'/></message></messages><components/>"
        + "<fields><field number='8' name='BeginString' type='STRING'/><field number='10' name='CheckSum' type='STRING'/></fields></fix>";

    private static ByteArrayInputStream xml(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void saveListResolveDeleteRoundTrip(@TempDir Path dir) throws Exception {
        DictionaryStore store = new DictionaryStore(dir.resolve("dicts").toString());
        assertThat(store.list()).isEmpty();

        Path saved = store.save("MY.xml", xml(VALID));

        assertThat(saved).exists();
        assertThat(store.list()).containsExactly("MY.xml");
        assertThat(store.resolve("MY.xml")).contains(saved);
        assertThat(store.resolve("OTHER.xml")).isEmpty();

        store.delete("MY.xml");
        assertThat(store.list()).isEmpty();
        assertThat(Files.exists(saved)).isFalse();
    }

    @Test
    void invalidXmlIsRejectedAndNothingStored(@TempDir Path dir) {
        DictionaryStore store = new DictionaryStore(dir.toString());

        assertThatThrownBy(() -> store.save("BAD.xml", xml("this is not xml")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(store.list()).isEmpty();
    }

    @Test
    void unsafeNamesAreRejected(@TempDir Path dir) {
        DictionaryStore store = new DictionaryStore(dir.toString());

        assertThatThrownBy(() -> store.save("../x.xml", xml(VALID))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save("x.txt", xml(VALID))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.resolve("../x.xml")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.delete("../x.xml")).isInstanceOf(IllegalArgumentException.class);
    }
}
