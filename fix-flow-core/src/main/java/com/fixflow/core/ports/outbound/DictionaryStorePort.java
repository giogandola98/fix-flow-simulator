package com.fixflow.core.ports.outbound;

import java.util.List;
import java.util.Optional;

/** Named QuickFIX/J data dictionaries (XML files) that sessions select by file name. */
public interface DictionaryStorePort {
    List<String> list();

    /** Throws {@link IllegalArgumentException} when {@code xml} is not a valid data dictionary. */
    void validate(byte[] xml);

    /** Validates and stores, replacing a dictionary of the same name. */
    void saveXml(String name, byte[] xml);

    Optional<byte[]> read(String name);

    void delete(String name);
}
