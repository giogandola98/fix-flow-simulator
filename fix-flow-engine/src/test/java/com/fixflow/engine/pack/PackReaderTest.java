package com.fixflow.engine.pack;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackReaderTest {

    private static Map<String, byte[]> entries(String... pathAndContent) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        for (int i = 0; i < pathAndContent.length; i += 2) {
            m.put(pathAndContent[i], pathAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test
    void reads_folder() {
        PackContents c = PackReader.readFolder(PackFakes.DEMO);
        assertThat(c.manifestYaml()).contains("id: demo");
        assertThat(c.dictionaries()).containsOnlyKeys("FIX50SP2-DEMO.xml");
        assertThat(c.datasets()).containsOnlyKeys("instruments.csv");
        assertThat(c.scenarios()).containsOnlyKeys("demo-main.yaml", "demo-send-quote-request.yaml");
        assertThat(c.sessions()).containsOnlyKeys("demo-acceptor.yaml");
    }

    @Test
    void reads_zip_same_as_folder() {
        PackContents folder = PackReader.readFolder(PackFakes.DEMO);
        PackContents zip = PackReader.readZip(new ByteArrayInputStream(PackFakes.zip(PackFakes.DEMO)));
        assertThat(zip.manifestYaml()).isEqualTo(folder.manifestYaml());
        assertThat(zip.datasets()).isEqualTo(folder.datasets());
        assertThat(zip.scenarios()).isEqualTo(folder.scenarios());
        assertThat(zip.sessions()).isEqualTo(folder.sessions());
        assertThat(zip.dictionaries().get("FIX50SP2-DEMO.xml"))
            .isEqualTo(folder.dictionaries().get("FIX50SP2-DEMO.xml"));
    }

    @Test
    void zip_with_one_top_folder_is_accepted() {
        byte[] zip = PackFakes.zip(entries("demo/pack.yaml", "id: demo", "demo/datasets/a.csv", "x\n1\n"));
        PackContents c = PackReader.readZip(new ByteArrayInputStream(zip));
        assertThat(c.manifestYaml()).isEqualTo("id: demo");
        assertThat(c.datasets()).containsOnlyKeys("a.csv");
    }

    @Test
    void rejects_parent_directory_entries() {
        byte[] zip = PackFakes.zip(entries("pack.yaml", "id: demo", "scenarios/../../evil.yaml", "x"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(zip)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scenarios/../../evil.yaml");
    }

    @Test
    void rejects_absolute_entries() {
        byte[] zip = PackFakes.zip(entries("pack.yaml", "id: demo", "/etc/passwd", "x"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(zip)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/etc/passwd");
    }

    @Test
    void rejects_files_outside_the_four_folders() {
        byte[] zip = PackFakes.zip(entries("pack.yaml", "id: demo", "tools/load.py", "print(1)"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(zip)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tools/load.py");
    }

    @Test
    void rejects_nested_folders_and_wrong_extensions() {
        byte[] nested = PackFakes.zip(entries("pack.yaml", "id: demo", "scenarios/sub/a.yaml", "x"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(nested)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scenarios/sub/a.yaml");
        byte[] ext = PackFakes.zip(entries("pack.yaml", "id: demo", "datasets/a.txt", "x"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(ext)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("datasets/a.txt");
    }

    @Test
    void requires_pack_yaml() {
        byte[] zip = PackFakes.zip(entries("datasets/a.csv", "x\n1\n"));
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream(zip)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pack.yaml");
    }

    @Test
    void rejects_garbage_that_is_not_a_zip() {
        assertThatThrownBy(() -> PackReader.readZip(new ByteArrayInputStream("hello".getBytes())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
