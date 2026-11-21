package com.fixflow.engine.dataset;

import com.fixflow.core.domain.dataset.DataSet;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCodecTest {

    @Test
    void stripsUtf8Bom() {
        DataSet ds = CsvCodec.parse("d", "﻿a,b\n1,2\n");
        assertThat(ds.columns()).containsExactly("a", "b");
        assertThat(ds.rows()).containsExactly(List.of("1", "2"));
    }

    @Test
    void quotedCommaAndEscapedQuote() {
        DataSet ds = CsvCodec.parse("d", "a,b\n\"x,y\",\"say \"\"hi\"\"\"\n");
        assertThat(ds.row(0)).containsEntry("a", "x,y").containsEntry("b", "say \"hi\"");
    }

    @Test
    void quotedNewline() {
        DataSet ds = CsvCodec.parse("d", "a,b\n\"l1\nl2\",z\n");
        assertThat(ds.rows().get(0).get(0)).isEqualTo("l1\nl2");
        assertThat(ds.rows()).hasSize(1);
    }

    @Test
    void crlfAndNoTrailingNewline() {
        DataSet ds = CsvCodec.parse("d", "a,b\r\n1,2\r\n3,4");
        assertThat(ds.rows()).containsExactly(List.of("1", "2"), List.of("3", "4"));
    }

    @Test
    void emptyValuesKept() {
        DataSet ds = CsvCodec.parse("d", "a,b,c\n1,,3\n");
        assertThat(ds.rows().get(0)).containsExactly("1", "", "3");
    }

    @Test
    void raggedRowNamesLine() {
        assertThatThrownBy(() -> CsvCodec.parse("d", "a,b,c\n1,2,3\n4,5\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("line 3: 2 values, header has 3");
    }

    @Test
    void longRowNamesLine() {
        assertThatThrownBy(() -> CsvCodec.parse("d", "a,b\n1,2,3\n"))
            .hasMessage("line 2: 3 values, header has 2");
    }

    @Test
    void duplicateHeaderRejected() {
        assertThatThrownBy(() -> CsvCodec.parse("d", "a,b,a\n1,2,3\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageStartingWith("line 1:")
            .hasMessageContaining("duplicate column");
    }

    @Test
    void emptyInputAndUnterminatedQuoteRejected() {
        assertThatThrownBy(() -> CsvCodec.parse("d", "")).hasMessageStartingWith("line 1:");
        assertThatThrownBy(() -> CsvCodec.parse("d", "a\n\"x\n")).hasMessageStartingWith("line 2:");
    }

    @Test
    void lineNumberCountsQuotedNewlines() {
        assertThatThrownBy(() -> CsvCodec.parse("d", "a,b\n\"x\ny\",1\n2\n"))
            .hasMessage("line 4: 1 values, header has 2");
    }

    @Test
    void roundTrip() {
        String csv = "a,b\n\"x,y\",\"q\"\"\"\n\"l1\nl2\",\n";
        assertThat(CsvCodec.write(CsvCodec.parse("d", csv))).isEqualTo(csv);
    }
}
