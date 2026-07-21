package com.empresa.estructuracion.batch.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PublicationSession {
    private final List<String> blockIds = new ArrayList<>();

    public void addBlockId(String blockId) {
        blockIds.add(blockId);
    }

    public List<String> blockIds() {
        return Collections.unmodifiableList(blockIds);
    }
}
